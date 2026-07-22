(ns knitwear.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [knitwear.store :as store]))

(deftest mem-store-initialization
  "In-memory store should initialize with reference data."
  (let [st (store/mem-store)]
    (is (map? st))
    (is (contains? st :data))))

(deftest plant-accessors
  "Store should provide plant lookups."
  (let [st (store/mem-store)
        plant (store/plant st "plant-001")]
    (is (map? plant))
    (is (= (:name plant) "Community Knitwear Mill A"))
    (is (= (:location plant) "Vietnam"))))

(deftest production-batch-accessors
  "Store should provide batch lookups."
  (let [st (store/mem-store)
        batch (store/production-batch st "batch-001")]
    (is (map? batch))
    (is (= (:style batch) "circular-knit-crewneck-L"))
    (is (= (:quantity batch) 400))
    (is (= (:plant batch) "plant-001"))))

(deftest shipment-accessors
  "Store should provide shipment lookups."
  (let [st (store/mem-store)
        shipment (store/shipment st "ship-001")]
    (is (map? shipment))
    (is (= (:batch shipment) "batch-001"))
    (is (= (:qty shipment) 400))))

(deftest equipment-accessors
  "Store should provide equipment lookups."
  (let [st (store/mem-store)
        equipment (store/equipment st "maint-001")]
    (is (map? equipment))
    (is (= (:equipment equipment) "circular-knitting-machine-07"))
    (is (= (:status equipment) :operational))))

(deftest plant-verified-guard
  "Plant verification guard should check registration status."
  (let [st (store/mem-store)]
    (is (store/plant-verified? st "plant-001"))
    (is (not (store/plant-verified? st "plant-unknown")))))

(deftest batch-verified-guard
  "Batch verification guard should check verification status."
  (let [st (store/mem-store)]
    (is (store/batch-verified? st "batch-001"))
    (is (not (store/batch-verified? st "batch-002")))))

(deftest batch-plant-verified-guard
  "Batch-plant verification should check both batch and its plant."
  (let [st (store/mem-store)]
    (is (store/batch-plant-verified? st "batch-001"))
    ;; batch-001's plant is verified, but we can test a non-existent batch
    (is (not (store/batch-plant-verified? st "batch-unknown")))))

(deftest shipment-batch-verified-guard
  "Shipment-batch verification should resolve through the shipment record to
  its underlying production batch and that batch's plant."
  (let [st (store/mem-store)]
    (is (store/shipment-batch-verified? st "ship-001"))
    (is (not (store/shipment-batch-verified? st "ship-unknown")))))

(deftest missing-records
  "Accessors should handle missing records gracefully."
  (let [st (store/mem-store)]
    (is (nil? (store/plant st "nonexistent")))
    (is (nil? (store/production-batch st "nonexistent")))
    (is (nil? (store/shipment st "nonexistent")))
    (is (nil? (store/equipment st "nonexistent")))))

;; ----------------------------- audit ledger (append-only) -----------------------------

(deftest ledger-starts-empty
  "A freshly created store's audit ledger is empty -- no function
  anywhere in the codebase existed to write to it before this fix."
  (let [st (store/mem-store)]
    (is (= [] (store/ledger st)))))

(deftest append-ledger-appends-in-order
  "append-ledger! appends facts in call order and returns the appended
  fact; the ledger is never reordered or deduplicated."
  (let [st (store/mem-store)
        f1 (store/append-ledger! st {:t :committed :op :proposal/schedule-maintenance :subject "maint-001"})
        f2 (store/append-ledger! st {:t :governor-hold :op :proposal/log-production-batch :subject "batch-002"})]
    (is (= {:t :committed :op :proposal/schedule-maintenance :subject "maint-001"} f1))
    (is (= [f1 f2] (store/ledger st)))
    (is (= 2 (count (store/ledger st))))))

(deftest ledger-is-per-store-instance
  "Each mem-store has its own independent ledger -- appending to one
  store's ledger never leaks into another store's."
  (let [st1 (store/mem-store)
        st2 (store/mem-store)]
    (store/append-ledger! st1 {:t :committed :op :test})
    (is (= 1 (count (store/ledger st1))))
    (is (= 0 (count (store/ledger st2))))))
