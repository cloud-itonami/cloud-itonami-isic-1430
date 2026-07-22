(ns knitwear.phase-test
  "Tests for the 0->3 phase rollout control -- REPLACES the previous
  phase_test.clj, which tested `phase-table` (a plain EDN description of
  graph topology this repo's `operation.cljc` never existed to consume).
  `knitwear.phase` is now a real rollout auto-commit gate, mirroring
  `transportops.phase-test` (cloud-itonami-isic-869)."
  (:require [clojure.test :refer [deftest is testing]]
            [knitwear.phase :as phase]))

(deftest phase-0-all-held
  (testing "Phase 0: All ops are held for human approval"
    (doseq [op [:proposal/log-production-batch :proposal/schedule-maintenance
                :proposal/flag-safety-concern :actuation/coordinate-shipment]]
      (is (false? (phase/may-auto-commit? op 0))))))

(deftest phase-1-low-risk-auto
  (testing "Phase 1: Only maintenance scheduling auto-commits"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 1)))
    (is (false? (phase/may-auto-commit? :proposal/log-production-batch 1)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 1)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 1)))))

(deftest phase-2-medium-risk-auto
  (testing "Phase 2: Maintenance + production-batch logging auto-commit"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 2)))
    (is (true? (phase/may-auto-commit? :proposal/log-production-batch 2)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 2)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 2)))))

(deftest phase-3-same-auto-set-as-phase-2
  (testing "Phase 3: same auto-commit set as phase 2 -- safety-concern
            and shipment coordination NEVER auto-commit at any phase"
    (is (true? (phase/may-auto-commit? :proposal/schedule-maintenance 3)))
    (is (true? (phase/may-auto-commit? :proposal/log-production-batch 3)))
    (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern 3)))
    (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment 3)))))

(deftest safety-concern-never-auto-commits
  (testing ":proposal/flag-safety-concern is NEVER auto-commit at any phase"
    (doseq [phase-num [0 1 2 3]]
      (is (false? (phase/may-auto-commit? :proposal/flag-safety-concern phase-num))))))

(deftest shipment-never-auto-commits
  (testing ":actuation/coordinate-shipment (high-stakes actuation) is
            NEVER auto-commit at any phase"
    (doseq [phase-num [0 1 2 3]]
      (is (false? (phase/may-auto-commit? :actuation/coordinate-shipment phase-num))))))

(deftest unknown-phase-defaults-conservative
  (testing "an unrecognized phase number defaults to all-held"
    (is (false? (phase/may-auto-commit? :proposal/schedule-maintenance 99)))))

(deftest allowed-ops-for-phase-is-always-the-closed-allowlist
  (testing "allowed-ops-for-phase is the closed allowlist regardless of
            phase -- phase only gates auto-commit eligibility, not the
            allowlist itself"
    (is (= #{:proposal/log-production-batch :proposal/schedule-maintenance
             :proposal/flag-safety-concern :actuation/coordinate-shipment}
           (phase/allowed-ops-for-phase 0)
           (phase/allowed-ops-for-phase 3)))))
