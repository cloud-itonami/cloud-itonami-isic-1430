(ns knitwear.sim
  "Demo driver -- `clojure -M:dev-local:run` / `clojure -M:dev:run`.
  Drives the REAL compiled `langgraph-clj` `StateGraph`
  (`knitwear.operation/build`) end-to-end through a phase-2 auto-commit
  (production-batch logging), a phase-1 auto-commit (maintenance
  scheduling), an always-escalating safety-concern flag (compliance
  officer approves), a high-stakes shipment coordination (compliance
  officer approves), a phase-0 hold (clean but not yet phase-eligible),
  and a HARD-block scenario (unverified batch), then prints each
  result.

  FIX: this namespace used to hand-chain
  `advisor/*-proposal -> governor/evaluate` directly with plain
  function calls -- it never required `langgraph.graph` and never
  touched `state-graph`/`add-node`/`compile-graph`, despite
  `knitwear.blueprint` claiming `:itonami.blueprint/maturity
  :implemented`. It now drives `knitwear.operation/build`, the real
  compiled StateGraph, through `langgraph.graph/run*`. Mirrors
  `transportops.sim` (cloud-itonami-isic-869) /
  `tobaccoops.sim` (cloud-itonami-isic-0115)."
  (:require [langgraph.graph :as g]
            [knitwear.operation :as operation]
            [knitwear.store :as store]))

(defn- scenario [title]
  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println (str "Scenario: " title))
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn- exec-op [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through auto-commit, always-escalate,
  high-stakes-escalate, phase-hold, and a HARD-block scenario; print
  each result and the resulting audit ledger."
  []
  (println "Knitwear Manufacturing Plant Operations Coordinator - Demo")

  (scenario "Phase 2: Auto-commit production batch logging (verified batch-001)")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec-op actor "t1"
                        {:op :proposal/log-production-batch :subject "batch-001"}
                        2)]
    (println (:state result))
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Phase 1: Auto-commit maintenance scheduling")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec-op actor "t2"
                        {:op :proposal/schedule-maintenance :subject "maint-001"}
                        1)]
    (println (:state result))
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Phase 0: Clean proposal, still held (nothing auto-commits at phase 0)")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec-op actor "t3"
                        {:op :proposal/log-production-batch :subject "batch-001"}
                        0)]
    (println (:state result))
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Always-escalating: safety concern (ALWAYS pauses at :request-approval)")
  (let [s (store/mem-store)
        actor (operation/build s)
        held (exec-op actor "t4"
                      {:op :proposal/flag-safety-concern :subject "batch-002"
                       :concern-type "unusual-vibration"}
                      3)]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "Ledger before approval:" (store/ledger s))
    (println "-- compliance officer approves --")
    (let [approved (approve! actor "t4")]
      (println (:state approved))
      (println "Decision:" (:decision (:state approved)))
      (println "Ledger after approval:" (store/ledger s))))

  (scenario "High-stakes actuation: shipment coordination (ALWAYS pauses at :request-approval)")
  (let [s (store/mem-store)
        actor (operation/build s)
        held (exec-op actor "t5"
                      {:op :actuation/coordinate-shipment :subject "ship-001"}
                      3)]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- compliance officer approves --")
    (let [approved (approve! actor "t5")]
      (println (:state approved))
      (println "Decision:" (:decision (:state approved)))
      (println "Ledger:" (store/ledger s))))

  (scenario "HARD-block: Unverified production batch (batch-002)")
  (let [s (store/mem-store)
        actor (operation/build s)
        result (exec-op actor "t6"
                        {:op :proposal/log-production-batch :subject "batch-002"}
                        3)]
    (println "Decision:" (:decision (:state result))
             "Audit:" (:audit (:state result)))
    (println "Ledger:" (store/ledger s)))

  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println "Demo completed successfully")
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn -main [& _args]
  (demo))

(comment
  (demo))
