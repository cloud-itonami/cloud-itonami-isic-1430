(ns knitwear.governor
  "Knitwear (Knitted/Crocheted Apparel) Manufacturing Plant Operations Governor --
  the independent compliance layer that earns the Knitwear Operations Advisor
  the right to propose and log actions.
  The LLM has no notion of labor standards, machinery-safety regulations, or
  when a shipment or batch-logging is a real-world actuation, so this MUST be
  a separate system able to *reject* a proposal and fall back to HOLD.

  HARD invariants (a human approver CANNOT override -- always :holds? true):
    1. Closed op-allowlist -- only :proposal/log-production-batch,
       :proposal/schedule-maintenance, :proposal/flag-safety-concern, and
       :actuation/coordinate-shipment are recognized. Anything else is
       rejected outright.
    2. Effect must be :propose -- this actor NEVER actuates directly; any
       proposal whose :effect is not :propose is rejected outright.
    3. Plant/batch not verified -- the underlying plant and production
       (knitting/linking) batch record must be independently verified and
       registered before ANY action referencing them.
    4. Knitting-line-equipment control -- direct control of knitting-line
       equipment (gauge, tension, needle, carriage, cam, cylinder, feeder,
       take-down, stitch-length, RPM, etc.) is a hard, permanent block.
       Those decisions remain the exclusive authority of licensed knitting
       engineers/technicians.

  ESCALATE (always requires human sign-off; NOT an outright block):
    5. Safety concerns -- :proposal/flag-safety-concern ALWAYS escalates to
       a human. Never silently log or auto-resolve a safety concern.
    6. Confidence floor / high-stakes actuation -- low confidence OR real
       actuation (shipment coordination) always requires human sign-off.

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates PLANT OPERATIONS (logistics, maintenance scheduling,
  quality/safety reporting, shipment coordination paperwork) around knitwear
  manufacturing. It does NOT:
    - Operate circular- or flat-knitting machines
    - Set knitting parameters (gauge, tension, stitch length, RPM, etc.)
    - Make design decisions about patterns, yarns, or garment shaping
    - Approve yarn/fabric quality (that's the mill's responsibility)

  Those remain the exclusive authority of plant production engineers."
  (:require [clojure.string :as str]
            [knitwear.store :as store]))

(def confidence-floor 0.6)

(def closed-op-allowlist
  "The complete, closed set of operations this actor may ever propose.
  Anything outside this set is a hard, permanent block -- no exceptions."
  #{:proposal/log-production-batch
    :proposal/schedule-maintenance
    :proposal/flag-safety-concern
    :actuation/coordinate-shipment})

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Shipment coordination with export/tariff implications."
  #{:actuation/coordinate-shipment})

(def process-control-keywords
  "Words that indicate knitting-line process-engineering authority (FORBIDDEN
  for this actor). If a proposal mentions any of these, it's a hard,
  permanent block."
  #{"gauge" "tension" "needle" "carriage" "cam" "cylinder" "dial" "feeder"
    "yarn-feed" "linking" "looper" "sinker" "racking" "shog" "rpm"
    "take-down" "stitch-length" "operate" "control" "parameter" "adjust"
    "speed"})

;; ----------------------------- checks -----------------------------

(defn- op-allowlist-violations
  "HARD BLOCK: closed op-allowlist. Any op outside the allowlist is rejected."
  [{:keys [op]} _st]
  (when-not (contains? closed-op-allowlist op)
    [{:rule :op-not-allowed
      :detail (str "許可されていない操作です: " op)}]))

(defn- effect-propose-violations
  "HARD BLOCK: this actor never actuates directly -- :effect must always be
  :propose."
  [{:keys [effect]} _st]
  (when (not= effect :propose)
    [{:rule :effect-not-propose
      :detail "この actor は :propose 以外の :effect を持つことができない"}]))

(defn- plant-batch-verification-violations
  "HARD BLOCK: the plant and production (knitting/linking) batch behind an
  action must be independently verified/registered before the action is
  allowed."
  [{:keys [op subject]} st]
  (cond
    (= op :proposal/log-production-batch)
    (let [batch (store/production-batch st subject)
          plant-id (:plant batch)]
      (cond
        (nil? batch)
        [{:rule :batch-not-verified
          :detail "製造ロットが登録されていない"}]

        (not (store/plant-verified? st plant-id))
        [{:rule :plant-not-verified
          :detail "製造施設が登録・検証されていない"}]

        (not (store/batch-verified? st subject))
        [{:rule :batch-not-verified
          :detail "製造ロットが検証されていない"}]))

    (= op :actuation/coordinate-shipment)
    (let [ship (store/shipment st subject)
          batch-id (:batch ship)
          batch (when batch-id (store/production-batch st batch-id))
          plant-id (:plant batch)]
      (cond
        (or (nil? ship) (nil? batch))
        [{:rule :batch-not-verified
          :detail "出荷元の製造ロットが登録されていない"}]

        (not (store/plant-verified? st plant-id))
        [{:rule :plant-not-verified
          :detail "製造施設が登録・検証されていない"}]

        (not (store/batch-verified? st batch-id))
        [{:rule :batch-not-verified
          :detail "出荷対象の製造ロットが検証されていない"}]))

    :else nil))

(defn- process-control-block-violations
  "HARD BLOCK, permanent: this actor does NOT operate knitting-line
  equipment. If a proposal mentions gauge, tension, needle, carriage, or
  other knitting-machine process parameters, reject it immediately.
  Those decisions remain the exclusive authority of licensed knitting
  engineers/technicians."
  [proposal _st]
  (let [detail (str (:detail (:value proposal) "") " " (:op proposal))
        words (re-seq #"[\w-]+" (str/lower-case detail))
        forbidden (some #(contains? process-control-keywords %) words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "設備操作は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- safety-concern-escalation-violations
  "Safety concerns ALWAYS escalate to human. Never silently log or
  auto-resolve a safety concern."
  [{:keys [op]} _st]
  (when (= op :proposal/flag-safety-concern)
    [{:rule :safety-concern-escalates
      :detail "安全上の懸念は必ず人間にエスカレートされる"}]))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [op-allowlist-violations
                           effect-propose-violations
                           plant-batch-verification-violations
                           process-control-block-violations]
        soft-checks-store [safety-concern-escalation-violations]
        soft-checks-value [confidence-gate-violations]
        hard-violations (mapcat #(% proposal st) hard-checks-store)
        soft-violations-store (mapcat #(% proposal st) soft-checks-store)
        soft-violations-value (mapcat #(% proposal (:value proposal)) soft-checks-value)
        soft-violations (concat soft-violations-store soft-violations-value)]
    {:holds? (boolean (seq hard-violations))
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
