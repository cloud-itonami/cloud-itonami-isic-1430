(ns knitwear.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [knitwear.store :as store]
            [knitwear.advisor :as advisor]
            [knitwear.governor :as governor]
            [knitwear.registry :as registry]))

(deftest op-allowlist-hard-gate
  "Closed op-allowlist is a HARD gate: any op outside the allowlist is
  rejected outright, no exceptions."
  (let [st (store/mem-store)
        proposal {:op :actuation/set-knitting-parameters
                  :subject "circular-knitting-machine-07"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {}
                          :confidence 0.9
                          :detail "Not a recognized operation"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with unrecognized op should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :op-not-allowed) (:hard-violations eval))))))

(deftest effect-not-propose-hard-gate
  "Effect must always be :propose -- this actor never actuates directly."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :actuate
                  :cites ["TCVN 6113:2020"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Batch logged"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with non-propose effect should hold")
      (is (some #(= (:rule %) :effect-not-propose) (:hard-violations eval))
        "Should have effect-not-propose violation"))))

(deftest process-control-block
  "HARD BLOCK, permanent: proposals mentioning knitting-machine tension,
  needle, carriage, or other equipment-control parameters are immediately
  rejected. Those remain engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Please increase carriage speed to 500 courses/min"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest safety-concern-always-escalates
  "Safety concerns ALWAYS escalate to human -- this is an ESCALATE gate
  (soft violation, human sign-off required), not an outright hard block,
  so a well-formed concern proposal is not :holds?."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        concern-proposal (advisor/safety-concern-proposal adv "batch-002" "unusual-vibration")]
    (let [eval (governor/evaluate concern-proposal st)]
      (is (not (:holds? eval)) "Safety concern should not be a hard block")
      (is (not (:clean? eval)) "Safety concern should never be silently clean")
      (is (some #(= (:rule %) :safety-concern-escalates) (:soft-violations eval))
        "Should have safety-concern-escalates soft violation"))))

(deftest shipment-requires-escalation
  "Shipment coordination is high-stakes actuation and requires human
  sign-off, even when the underlying batch/plant are fully verified and
  clean."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        shipment-proposal (advisor/shipment-proposal adv "ship-001")]
    (let [eval (governor/evaluate shipment-proposal st)]
      (is (not (:holds? eval)) "Verified shipment should not hard-block")
      (is (seq (:soft-violations eval)) "Should have soft violations for actuation")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate high-stakes actuation"))))

(deftest shipment-unverified-batch-blocks
  "Shipment coordination referencing an unverified production batch is a
  hard block."
  (let [st (store/mem-store)
        _ (swap! (:data st) assoc-in [:shipments "ship-unverified"]
                 {:batch "batch-002" :destination "buyer-B" :qty 100
                  :scheduled-date "2026-08-01" :status :pending})
        proposal (registry/shipment-draft "ship-unverified"
                   ["19 CFR § 12.131"]
                   {:export-permit true}
                   0.9
                   "Shipment ready for export coordination")]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Should have hard violations")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block shipment of unverified batch"))))

(deftest plant-not-verified-blocks
  "Production batch from unverified plant is blocked."
  (let [st (store/mem-store)
        ;; Create a batch with unverified plant
        _ (swap! (-> st :data) assoc-in [:production-batches "batch-unverified" :plant] "plant-unknown")
        proposal (registry/batch-log-draft "batch-unverified"
                   ["TCVN 6113:2020"]
                   {:batch-verified true}
                   0.85
                   "Log batch from plant")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :plant-not-verified) (:hard-violations eval))
        "Should block unverified plant"))))

(deftest batch-not-verified-blocks
  "Production batch logging with unverified batch is blocked."
  (let [st (store/mem-store)
        proposal (registry/batch-log-draft "batch-002"
                   ["TCVN 6113:2020"]
                   {:batch-verified true}
                   0.88
                   "Log unverified batch")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block unverified batch"))))

(deftest unknown-batch-blocks
  "Logging a completely unknown batch ID is blocked."
  (let [st (store/mem-store)
        proposal (registry/batch-log-draft "batch-does-not-exist"
                   ["TCVN 6113:2020"]
                   {:batch-verified true}
                   0.85
                   "Log unknown batch")]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Should hold")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))))))

(deftest low-confidence-escalates
  "Low confidence proposals escalate to human, even if otherwise clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["TCVN 6113:2020"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.45
                          :detail "Batch logged"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (not (:holds? eval)) "Low confidence alone should not hard-block")
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate low-confidence"))))

(deftest clean-proposal
  "A proposal with all evidence, valid subject, high confidence,
  and no high-stakes actuation or process-control is fully clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/schedule-maintenance
                  :subject "maint-001"
                  :effect :propose
                  :cites ["ISO 11111-1:2016"]
                  :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
                          :confidence 0.9
                          :detail "Maintenance scheduled"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations")
      (is (empty? (:soft-violations eval)) "Should have no soft violations"))))
