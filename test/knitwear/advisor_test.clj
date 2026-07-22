(ns knitwear.advisor-test
  "Tests for `knitwear.advisor/advise` -- the request-dispatch entry
  point `knitwear.operation`'s `:advise` graph node calls. Did not exist
  before this fix (the op-specific proposal builders were only ever
  called directly from `knitwear.sim`'s hand-chained pipeline)."
  (:require [clojure.test :refer [deftest is]]
            [knitwear.advisor :as advisor]))

(deftest advise-dispatches-log-production-batch
  (let [adv (advisor/mock-advisor)
        p (advisor/advise adv {:op :proposal/log-production-batch :subject "batch-001"})]
    (is (= :proposal/log-production-batch (:op p)))
    (is (= "batch-001" (:subject p)))
    (is (= :propose (:effect p)))))

(deftest advise-dispatches-schedule-maintenance
  (let [adv (advisor/mock-advisor)
        p (advisor/advise adv {:op :proposal/schedule-maintenance :subject "maint-001"})]
    (is (= :proposal/schedule-maintenance (:op p)))
    (is (= "maint-001" (:subject p)))))

(deftest advise-dispatches-flag-safety-concern-with-concern-type
  (let [adv (advisor/mock-advisor)
        p (advisor/advise adv {:op :proposal/flag-safety-concern
                               :subject "batch-002"
                               :concern-type "unusual-vibration"})]
    (is (= :proposal/flag-safety-concern (:op p)))
    (is (= "unusual-vibration" (get-in p [:value :concern-type])))))

(deftest advise-dispatches-coordinate-shipment
  (let [adv (advisor/mock-advisor)
        p (advisor/advise adv {:op :actuation/coordinate-shipment :subject "ship-001"})]
    (is (= :actuation/coordinate-shipment (:op p)))
    (is (= "ship-001" (:subject p)))))

(deftest advise-unknown-op-falls-back-with-zero-confidence
  "An op outside the closed allowlist still produces SOME proposal here
  -- knitwear.governor's own closed op-allowlist check independently
  rejects it regardless, the same 'never trust the advisor's own claim'
  discipline the Governor uses everywhere else."
  (let [adv (advisor/mock-advisor)
        p (advisor/advise adv {:op :actuation/set-knitting-parameters :subject "machine-07"})]
    (is (= :propose (:effect p)))
    (is (= 0.0 (get-in p [:value :confidence])))))
