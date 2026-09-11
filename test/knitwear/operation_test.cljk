(ns knitwear.operation-test
  "Integration tests for `knitwear.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / phase-hold /
  escalate-approve / escalate-reject / high-stakes-actuation routes.
  This namespace did not exist before this fix: `knitwear.sim` hand-
  chained `advisor/*-proposal -> governor/evaluate` directly, a plain
  pass-through with zero `require` of `langgraph.graph` and zero use of
  `state-graph`/`add-node`/`compile-graph` anywhere in the file --
  despite `blueprint.edn` claiming `:itonami.blueprint/maturity
  :implemented`. These tests are FALSIFIABLE on real StateGraph
  behavior, not hardcoded pass strings: the ledger stays empty until a
  real commit, escalated proposals hold-until-approved, and a governor
  rejection blocks commit entirely. Mirrors
  `transportops.operation-test` (cloud-itonami-isic-869) /
  `tobaccoops.operation-test` (cloud-itonami-isic-0115)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [knitwear.operation :as operation]
            [knitwear.store :as store]))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(deftest ledger-starts-empty
  (testing "a freshly built store's audit ledger is empty until a real
            commit lands -- no proposal, no evaluation, no graph run has
            happened yet"
    (let [s (store/mem-store)]
      (is (empty? (store/ledger s))))))

(deftest commit-path-clean-proposal-auto-commits-at-phase-2
  (testing "a clean, phase-2, verified production-batch logging request
            commits through the real compiled graph and appends exactly
            one fact to the audit ledger"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :proposal/log-production-batch :subject "batch-001"}
                       2)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :proposal/log-production-batch (:op (first ledger))))
        (is (= "batch-001" (:subject (first ledger))))))))

(deftest commit-path-maintenance-auto-commits-at-phase-1
  (testing "maintenance scheduling is the LOWEST-risk op -- it already
            auto-commits at phase 1, unlike batch logging which needs
            phase 2"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-maint"
                       {:op :proposal/schedule-maintenance :subject "maint-001"}
                       1)]
      (is (= :commit (:decision (:state result))))
      (is (= 1 (count (store/ledger s)))))))

(deftest hard-hold-path-unverified-batch
  (testing "an unverified production batch (batch-002) is a HARD,
            permanent governor violation -- the real graph routes
            straight to :hold (no interrupt, no human-approval detour)
            and durably records the hold fact -- the ledger never gets
            a :committed fact"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :proposal/log-production-batch :subject "batch-002"}
                       3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :governor-violation (:reason (first ledger))))
        (is (some #(= :batch-not-verified (:rule %)) (:violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest hard-hold-path-unknown-batch
  (testing "logging a completely unknown batch ID is also a HARD
            violation, re-derived from the store's own record -- never
            trusted from the proposal"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-hold-unknown"
                       {:op :proposal/log-production-batch :subject "batch-does-not-exist"}
                       3)]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :batch-not-verified (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest phase-hold-path-clean-but-not-eligible
  (testing "a governor-clean proposal that isn't in the current phase's
            auto-commit set is held (not committed, not escalated) --
            and STILL durably audited, distinguished from a governor
            violation by :reason"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-phase-hold"
                       {:op :proposal/log-production-batch :subject "batch-001"}
                       0)
          state (:state result)]
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :not-in-phase-auto-set (:reason (first ledger))))
        (is (empty? (:violations (first ledger))))))))

(deftest phase-1-clean-batch-logging-still-held
  (testing "production-batch logging is NOT in phase 1's auto-commit
            set (only maintenance is) -- a clean batch-logging request
            at phase 1 still holds"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-phase1-batch"
                       {:op :proposal/log-production-batch :subject "batch-001"}
                       1)]
      (is (= :hold (:decision (:state result))))
      (is (= :not-in-phase-auto-set (:reason (first (store/ledger s))))))))

(deftest escalate-then-approve-commits-safety-concern
  (testing ":proposal/flag-safety-concern ALWAYS escalates -- the real
            graph GENUINELY interrupts (checkpointed) at
            :request-approval; the ledger stays EMPTY until a human
            compliance officer approve! resumes the SAME compiled graph
            and commits via the graph's own :request-approval -> :commit
            edge"
    (let [s (store/mem-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :proposal/flag-safety-concern :subject "batch-002"
                      :concern-type "unusual-vibration"}
                     3)]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- ledger stays empty
          until a human signs off")
      (let [approved (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:decision approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :proposal/flag-safety-concern (:op (first ledger))))
          (is (= "compliance-officer-01" (:approved-by (first ledger)))))))))

(deftest escalate-then-reject-holds
  (testing "a human compliance officer rejecting an escalated request
            routes to :hold via the :request-approval node's own
            decision -- governor rejection blocks commit"
    (let [s (store/mem-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :proposal/flag-safety-concern :subject "batch-002"
                       :concern-type "unusual-vibration"}
                      3)
          rejected (g/run* actor {:approval {:status :rejected :by "compliance-officer-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest high-stakes-shipment-always-escalates-then-commits
  (testing "shipment coordination is a high-stakes actuation
            (`knitwear.governor/high-stakes`) -- ALWAYS escalates even
            when the underlying batch/plant are fully verified/clean;
            approval resumes the SAME compiled graph"
    (let [s (store/mem-store)
          actor (operation/build s)
          held (exec actor "t-shipment"
                     {:op :actuation/coordinate-shipment :subject "ship-001"}
                     3)]
      (is (= :interrupted (:status held)))
      (is (empty? (store/ledger s)))
      (let [approved (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
                             {:thread-id "t-shipment" :resume? true})]
        (is (= :commit (:decision (:state approved))))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :actuation/coordinate-shipment (:op (first ledger)))))))))

(deftest never-auto-commit-holds-at-every-phase-for-safety-concern
  (testing "even at phase 3 (full autonomy for what CAN auto-commit),
            :proposal/flag-safety-concern never auto-commits -- it
            always interrupts, proven against the real graph"
    (doseq [phase-num [0 1 2 3]]
      (let [s (store/mem-store)
            actor (operation/build s)
            held (exec actor (str "t-safety-" phase-num)
                       {:op :proposal/flag-safety-concern :subject "batch-002"
                        :concern-type "unusual-vibration"}
                       phase-num)]
        (is (= :interrupted (:status held))
            (str "phase " phase-num " should still interrupt for a safety concern"))))))
