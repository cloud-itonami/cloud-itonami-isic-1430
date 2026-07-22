(ns knitwear.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [knitwear.facts :as facts]))

(deftest catalog-has-jurisdictions
  "Catalog should define at least 5 jurisdictions with official spec-basis."
  (is (>= (count facts/catalog) 5))
  (is (contains? facts/catalog :VNM))
  (is (contains? facts/catalog :BGD))
  (is (contains? facts/catalog :USA))
  (is (contains? facts/catalog :KHM))
  (is (contains? facts/catalog :FRA)))

(deftest jurisdiction-coverage-honest
  "Coverage reporting should be honest about scope."
  (let [cov (facts/coverage)]
    (is (map? cov))
    (is (>= (:implemented cov) 5))
    (is (= (:worldwide-jurisdictions cov) 194))
    (is (> (:coverage-pct cov) 0))
    (is (contains? cov :note))))

(deftest france-extended-producer-responsibility
  "France (FRA) has an extended-producer-responsibility citation distinct in
  kind from the manufacturing-safety/labor-standards shape of VNM/BGD/USA/KHM:
  a post-consumer waste-management obligation, not a production-side rule."
  (let [reqs (facts/requirement-citations :FRA)]
    (is (map? reqs))
    (is (contains? reqs :extended-producer-responsibility))
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))
    (is (facts/required-evidence-satisfied? :FRA
          {:eco-organisme-membership true :eco-contribution-paid true}))
    (is (not (facts/required-evidence-satisfied? :FRA
               {:eco-organisme-membership true})))))

(deftest vietnam-requirements
  "Vietnam jurisdiction should have official spec-basis for all requirements."
  (let [reqs (facts/requirement-citations :VNM)]
    (is (map? reqs))
    (is (contains? reqs :plant-registration))
    (is (contains? reqs :labor-standards))
    (is (contains? reqs :knit-quality-labeling))
    (is (contains? reqs :export-compliance))
    ;; Each requirement should have spec-basis
    (doseq [[_key req] reqs]
      (is (:spec-basis req) (str "Requirement should have spec-basis: " _key))
      (is (seq (:evidence req)) (str "Requirement should list evidence checklist: " _key)))))

(deftest evidence-satisfaction
  "Test jurisdiction-specific evidence checklist satisfaction."
  (testing "Vietnam complete plant registration requirement"
    (let [complete {:plant-license true :environmental-permit true :worker-contract true :wage-record true :safety-training true :quality-cert true :labeling-audit true :export-permit true :shipment-manifest true}]
      (is (facts/required-evidence-satisfied? :VNM complete))))

  (testing "Incomplete evidence should fail"
    (let [checklist {:plant-license true}]
      (is (not (facts/required-evidence-satisfied? :VNM checklist)))))

  (testing "USA complete requirements"
    (let [checklist {:tariff-cert true :origin-marking true :wage-hour-record true :safety-training true :fiber-analysis true :label-affidavit true :machine-guarding-audit true :interlock-test true}]
      (is (facts/required-evidence-satisfied? :USA checklist))))

  (testing "Cambodia complete requirements"
    (let [checklist {:enterprise-declaration true :plant-license true
                     :worker-contract true :wage-record true :payroll-record true
                     :quality-cert true :conformity-assessment true}]
      (is (facts/required-evidence-satisfied? :KHM checklist)))))

(deftest spec-basis-citations
  "All spec-basis citations should be strings (official references)."
  (doseq [[_jurisdiction jurisdiction-data] facts/catalog]
    (let [reqs (:requirements jurisdiction-data)]
      (doseq [[_req-key req-spec] reqs]
        (is (string? (:spec-basis req-spec))
          (str "Spec-basis should be a string in " _jurisdiction "/" _req-key))))))
