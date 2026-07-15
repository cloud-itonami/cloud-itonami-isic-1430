(ns knitwear.advisor
  "Knitwear Manufacturing Plant Operations Advisor -- the LLM-driven suggestion
  layer. Proposes operations to the Governor for approval. Every op it can
  ever draft is :effect :propose -- this actor never actuates directly.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn batch-log-proposal
  "Propose logging a completed knitting/linking production batch (with
  output-quality data) to the audit ledger."
  [_advisor batch-id]
  {:op :proposal/log-production-batch
   :subject batch-id
   :effect :propose
   :cites ["TCVN 6113:2020"]
   :value {:evidence {:batch-verified true :quantity-confirmed true :quality-grade-assigned true}
           :confidence 0.87
           :detail "Production batch logged and quality verified"}})

(defn maintenance-proposal
  "Propose scheduling knitting-line-equipment maintenance."
  [_advisor equipment-id]
  {:op :proposal/schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["ISO 11111-1:2016"]
   :value {:evidence {:equipment-record true :maintenance-schedule-ok true}
           :confidence 0.85
           :detail "Maintenance scheduled for equipment"}})

(defn safety-concern-proposal
  "Propose flagging an equipment-safety or quality-defect concern
  (ALWAYS escalates to human)."
  [_advisor subject-id concern-type]
  {:op :proposal/flag-safety-concern
   :subject subject-id
   :effect :propose
   :cites ["ISO 11111-1:2016 (Textile machinery -- Safety requirements)"]
   :value {:evidence {:concern-documented true :photos-attached true}
           :confidence 0.82
           :concern-type concern-type
           :detail (str "Safety concern flagged: " concern-type " -- escalation required")}})

(defn shipment-proposal
  "Propose outbound product shipment coordination (high-stakes actuation)."
  [_advisor shipment-id]
  {:op :actuation/coordinate-shipment
   :subject shipment-id
   :effect :propose
   :cites ["19 CFR § 12.131 (Tariff Compliance)"]
   :value {:evidence {:export-permit true :shipping-manifest true :invoice-attached true}
           :confidence 0.89
           :detail "Shipment ready for export coordination"}})
