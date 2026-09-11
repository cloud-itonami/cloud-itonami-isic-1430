(ns knitwear.store
  "In-memory store for knitwear (knitted/crocheted apparel) manufacturing
  plant operations state.
  This is a reference implementation; production systems would use Datomic
  or similar persistent event store for audit and replay.

  The append-only audit ledger (`ledger`/`append-ledger!`) is this
  actor's core missing plumbing before this fix: no such function
  existed anywhere in the codebase -- only prose comments in other
  namespaces' docstrings *mentioned* an audit ledger, aspirational, not
  real. `knitwear.operation`'s `:commit`/`:hold` graph nodes now append
  every committed/held/approval-rejected decision fact here, so a
  plant's operating history (every `:proposal/log-production-batch` /
  `:proposal/schedule-maintenance` / `:proposal/flag-safety-concern` /
  `:actuation/coordinate-shipment` decision) is always a query over an
  immutable log -- the same discipline every sibling
  `cloud-itonami-isic-*` actor's ledger provides. The ledger stays
  append-only.")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference data for knitwear manufacturing."
  []
  {:data (atom {
           :plants {
             "plant-001" {:name "Community Knitwear Mill A"
                         :location "Vietnam"
                         :registered? true
                         :jurisdiction :VNM}}
           :production-batches {
             "batch-001" {:plant "plant-001"
                         :style "circular-knit-crewneck-L"
                         :process :circular-knit
                         :quantity 400
                         :verified? true
                         :quality-grade "standard"}
             "batch-002" {:plant "plant-001"
                         :style "crochet-cardigan-M"
                         :process :crochet-linking
                         :quantity 150
                         :verified? false
                         :quality-grade "standard"}}
           :shipments {
             "ship-001" {:batch "batch-001"
                        :destination "wholesale-buyer-A"
                        :qty 400
                        :scheduled-date "2026-07-25"
                        :status :pending}}
           :maintenance-log {
             "maint-001" {:equipment "circular-knitting-machine-07"
                         :last-service "2026-06-20"
                         :status :operational}}
           :ledger []})})

;; ----------------------------- accessors -----------------------------

(defn plant
  "Get plant record by ID."
  [st plant-id]
  (get-in @(:data st) [:plants plant-id]))

(defn production-batch
  "Get production batch (knitting/linking batch) record by ID."
  [st batch-id]
  (get-in @(:data st) [:production-batches batch-id]))

(defn shipment
  "Get shipment record by ID."
  [st shipment-id]
  (get-in @(:data st) [:shipments shipment-id]))

(defn equipment
  "Get knitting-line equipment maintenance record by ID."
  [st equipment-id]
  (get-in @(:data st) [:maintenance-log equipment-id]))

;; ----------------------------- guards -----------------------------

(defn plant-verified?
  "Check if plant is registered and authorized."
  [st plant-id]
  (let [p (plant st plant-id)]
    (:registered? p false)))

(defn batch-verified?
  "Check if production batch is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)]
    (:verified? b false)))

(defn batch-plant-verified?
  "Check if batch's plant is verified."
  [st batch-id]
  (let [b (production-batch st batch-id)
        plant-id (:plant b)]
    (plant-verified? st plant-id)))

(defn shipment-batch-verified?
  "Check if a shipment's underlying production batch (and that batch's
  plant) are both verified."
  [st shipment-id]
  (let [s (shipment st shipment-id)
        batch-id (:batch s)]
    (and batch-id
         (batch-verified? st batch-id)
         (batch-plant-verified? st batch-id))))

;; ----------------------------- audit ledger (append-only) -----------------------------

(defn ledger
  "The append-only audit ledger: every committed/held/approval-rejected
  decision fact, in append order."
  [st]
  (:ledger @(:data st) []))

(defn append-ledger!
  "Append one immutable decision fact to the ledger. Returns the fact.
  Genuinely wired into `knitwear.operation`'s `:commit`/`:hold` graph
  nodes -- not test-only plumbing."
  [st fact]
  (swap! (:data st) update :ledger (fnil conj []) fact)
  fact)
