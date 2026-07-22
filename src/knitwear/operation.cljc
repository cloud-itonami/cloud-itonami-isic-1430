(ns knitwear.operation
  "OperationActor -- one Knitwear Manufacturing Plant Operations request =
  one supervised actor run, expressed as a REAL compiled `langgraph-clj`
  `StateGraph` (`langgraph.graph/state-graph` + `compile-graph`). The
  advisor (`knitwear.advisor`) is sealed into a single node (`:advise`);
  its proposal is ALWAYS routed through the independent Governor
  (`knitwear.governor/evaluate`, `:govern`) and the rollout phase gate
  (`knitwear.phase`, `:decide`) before anything commits to the SSoT.

  This namespace did not exist before this fix. `knitwear.blueprint`
  claimed `:itonami.blueprint/maturity :implemented`, but that was
  FALSE: there was no `operation.cljc` and no `langgraph.graph` usage
  anywhere in `src/` -- `knitwear.sim` hand-chained
  `advisor/*-proposal -> governor/evaluate` directly, a textbook fake
  StateGraph, worse than the usual deferred-stub pattern in sibling
  repos since no StateGraph was even attempted (contrast
  `transportops.operation`, cloud-itonami-isic-869, and
  `tobaccoops.operation`, cloud-itonami-isic-0115, whose old namespaces
  at least ATTEMPTED a threading-pipeline stand-in and mis-labeled it).
  The real `langgraph-clj` dependency was correctly placed in `deps.edn`
  (`io.github.com-junkawasaki/langgraph-clj` in the main `:deps` map,
  `:dev-local` alias overriding it to the in-monorepo
  `../../kotoba-lang/langgraph` checkout), but nothing in `src/` ever
  required `langgraph.graph`.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`knitwear.store/mem-store`, or any store shaped map
                     exposing the same accessor/ledger functions)
    - the Advisor  (mock today; `knitwear.advisor/advise` is already the
                     injection point)
    - the Phase    (0->3 rollout; passed per-request via `:phase-num`,
                     not frozen at `build` time)

  One graph run = one knitwear plant operations request. No unbounded
  inner loop -- each run is auditable and checkpointed. Every
  commit/hold/approval-rejected decision fact lands in
  `knitwear.store`'s append-only ledger (`store/append-ledger!`) -- that
  function also did not exist anywhere in the codebase before this fix
  (only prose comments elsewhere MENTIONED an audit ledger,
  aspirational, not real); it is now genuinely wired into both the
  `:commit` and `:hold` terminal nodes, not test-only.

  ALL of `knitwear.advisor`'s proposal builders and ALL of
  `knitwear.governor`'s hard/soft checks are reused UNCHANGED -- this
  fix only wires the existing domain policy into a real compiled graph
  and a real ledger, it does not redesign the knitwear-specific
  compliance rules.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human plant operator/compliance
  officer resumes it with a decision. `:proposal/flag-safety-concern`
  and `:actuation/coordinate-shipment` ALWAYS reach this node when the
  Governor's hard checks are clean -- `knitwear.governor`'s own
  `safety-concern-escalation-violations` (soft, ALWAYS fires for
  `:flag-safety-concern`) and `confidence-gate-violations` (soft,
  ALWAYS fires for the `high-stakes` `:actuation/coordinate-shipment`)
  guarantee this; `knitwear.phase`'s independent `never-auto-commit` set
  agrees, a second layer, not one."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [knitwear.advisor :as advisor]
            [knitwear.governor :as governor]
            [knitwear.phase :as phase]
            [knitwear.store :as store]))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(defn- hold-fact
  "The audit fact written when a proposal is held -- either a permanent
  HARD governor block (`:violations` non-empty) or a clean-but-not-yet-
  phase-eligible proposal. `reason` distinguishes the cases in the
  ledger without changing the terminal node they route to."
  [request proposal violations reason]
  {:t           :governor-hold
   :op          (:op request)
   :subject     (:subject proposal)
   :disposition :hold
   :violations  violations
   :reason      reason})

(defn- commit-fact
  "The audit fact written when a proposal commits. `:proposal` carries
  the full advisor proposal (production-batch/maintenance/safety-flag/
  shipment data, spec-basis citations, evidence checklist) -- knitwear
  has no separate stateful commit-record! entity beyond the plant/batch/
  shipment/maintenance directory, so the ledger fact itself is the
  durable record of what happened."
  [request proposal approval]
  (cond-> {:t           :committed
           :op          (:op request)
           :subject     (:subject proposal)
           :disposition :commit
           :basis       (:cites proposal)
           :detail      (get-in proposal [:value :detail])
           :proposal    proposal}
    approval (assoc :approved-by (:by approval))))

(defn- escalation-reason
  "Distinguish WHY a proposal escalated for the ledger, without changing
  the terminal routing: safety-concern flags always escalate
  (`knitwear.governor/safety-concern-escalation-violations`), high-
  stakes actuations (`knitwear.governor/high-stakes`, i.e. shipment
  coordination) always escalate, everything else in `soft-violations`
  escalates on low confidence."
  [proposal soft-violations]
  (cond
    (some #(= :safety-concern-escalates (:rule %)) soft-violations)
    :safety-concern-always-escalate

    (contains? governor/high-stakes (:op proposal))
    :high-stakes-actuation

    :else :low-confidence))

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `knitwear.advisor` advisor value (default:
                     `advisor/mock-advisor`)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :phase-num ..}` (phase
  is per-request, not frozen at `build` time)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request    {:default nil}
         :phase-num  {:default 0}
         :proposal   {:default nil}
         :evaluation {:default nil}
         :decision   {:default nil}
         :approval   {:default nil}
         :audit      {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          {:proposal (advisor/advise advisor request)}))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:evaluation (governor/evaluate proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request proposal evaluation phase-num]}]
          (let [{:keys [holds? hard-violations soft-violations]} evaluation
                auto-commit? (and (not holds?)
                                   (empty? soft-violations)
                                   (phase/may-auto-commit? (:op proposal) phase-num))]
            (cond
              ;; HARD governor violations are a permanent block -- NEVER
              ;; routed through human approval, straight to :hold.
              holds?
              {:decision :hold
               :audit [(hold-fact request proposal hard-violations :governor-violation)]}

              ;; Soft violations (safety-concern-always-escalate,
              ;; high-stakes actuation, low-confidence) ALWAYS route
              ;; through human sign-off, regardless of phase.
              (seq soft-violations)
              {:decision :escalate
               :audit [{:t          :approval-requested
                        :op         (:op request)
                        :subject    (:subject proposal)
                        :reason     (escalation-reason proposal soft-violations)
                        :phase      phase-num
                        :confidence (get-in proposal [:value :confidence])}]}

              auto-commit?
              {:decision :commit}

              :else
              {:decision :hold
               :audit [(hold-fact request proposal [] :not-in-phase-auto-set)]}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval evaluation]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject proposal) :by (:by approval)}]}
            {:decision :hold
             :audit [(assoc (hold-fact request proposal (:soft-violations evaluation) :approver-rejected)
                            :t :approval-rejected)]})))

      (g/add-node :commit
        (fn [{:keys [request proposal approval]}]
          (let [f (commit-fact request proposal approval)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
