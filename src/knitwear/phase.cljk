(ns knitwear.phase
  "Phase 0->3 rollout control for the Knitwear Manufacturing Plant
  Operations actor.

  Phase 0: Intake-only -- no op auto-commits; everything the Governor
    leaves clean is still held for human review (a clean-but-not-yet-
    eligible hold, distinct from a Governor violation).
  Phase 1: Low-risk auto-commit -- maintenance scheduling only
    (internal, no plant/batch verification chain, no external
    actuation).
  Phase 2: Medium-risk auto-commit -- + production-batch logging (plant/
    batch verification chain, still no external actuation).
  Phase 3: Full autonomy for what this actor may EVER auto-commit --
    same set as phase 2, because safety-concern flagging and shipment
    coordination NEVER auto-commit at any phase (see
    `never-auto-commit` below): the Governor's own soft-violation gates
    (`knitwear.governor/safety-concern-escalation-violations` and the
    `:actuation/coordinate-shipment` member of
    `knitwear.governor/high-stakes`) already guarantee this
    independently, so this is a second, independent layer -- not the
    only one.

  This replaces the previous `phase.cljc`, which was NOT a rollout gate
  at all: it was `phase-table`, a plain EDN description of graph
  topology (`ADVISOR-NODE`/`GOVERNOR-NODE`/`HOLD-NODE`/`COMPLETE-NODE`,
  `:edges`) that `knitwear.operation` never existed to consume and that
  no `langgraph.graph` `state-graph`/`add-node`/`compile-graph` call
  ever read -- a textbook fake StateGraph-as-data. `knitwear.operation`
  now builds the REAL compiled graph; this namespace's job is the
  rollout-phase auto-commit gate every sibling `cloud-itonami-isic-*`
  actor's own `phase.cljc` provides (mirrors `transportops.phase`,
  cloud-itonami-isic-869 / `tobaccoops.phase`, cloud-itonami-isic-0115).")

(def default-phase 0)

;; ----------------------------- phase table (rollout auto-commit sets) -----------------------------

(defn phase-config
  "The phase rollout table. Each phase specifies which ops may auto-commit
  (members of :auto set) once the Governor has already found the
  proposal clean (no hard OR soft violations). An op outside :auto is
  held for human approval."
  [phase-num]
  (case phase-num
    0 {:auto #{}}                                        ;; Phase 0: all held
    1 {:auto #{:proposal/schedule-maintenance}}           ;; Phase 1: low-risk
    2 {:auto #{:proposal/schedule-maintenance
               :proposal/log-production-batch}}           ;; Phase 2: medium-risk
    3 {:auto #{:proposal/schedule-maintenance
               :proposal/log-production-batch}}           ;; Phase 3: same auto set (see docstring)
    ;; Unknown phase: conservative default (all held)
    {:auto #{}}))

(def never-auto-commit
  "Ops that are NEVER auto-commit at any phase, independently of the
  phase table above. `:proposal/flag-safety-concern` (always escalates)
  and `:actuation/coordinate-shipment` (high-stakes actuation) already
  never reach the auto-commit check in practice -- the Governor's own
  soft-violation gates route them to :escalate first -- but this makes
  the same guarantee at the phase layer too, so a future Governor change
  can't silently make either of these auto-commit-eligible."
  #{:proposal/flag-safety-concern :actuation/coordinate-shipment})

(defn may-auto-commit?
  "True if the given op may auto-commit in the given phase (after the
  Governor clears it clean, i.e. no hard AND no soft violations).
  `never-auto-commit` members are NEVER auto-commit, regardless of
  phase."
  [op phase-num]
  (if (contains? never-auto-commit op)
    false
    (contains? (:auto (phase-config phase-num)) op)))

(defn allowed-ops-for-phase
  "All ops allowed to be proposed at this phase. For knitwear, this is
  always the closed allowlist regardless of phase -- phase only controls
  whether a clean proposal auto-commits or holds."
  [_phase-num]
  #{:proposal/log-production-batch :proposal/schedule-maintenance
    :proposal/flag-safety-concern :actuation/coordinate-shipment})
