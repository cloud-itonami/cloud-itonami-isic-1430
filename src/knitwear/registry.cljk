(ns knitwear.registry
  "Proposal registry and drafting helpers for knitwear plant operations.
  Every proposal carries its spec-basis and evidence checklist. The op set
  here is the SAME closed allowlist enforced by knitwear.governor -- this
  namespace never drafts an op outside it.")

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - Operation must be in the closed allowlist.
  - :effect must always be :propose."
  [op-type effect]
  (let [allowlist #{:proposal/log-production-batch
                    :proposal/schedule-maintenance
                    :proposal/flag-safety-concern
                    :actuation/coordinate-shipment}]
    (cond-> []
      (not (contains? allowlist op-type))
      (conj {:rule :op-not-allowed
             :detail (str "許可されていない操作です: " op-type)})

      (not= effect :propose)
      (conj {:rule :effect-not-propose
             :detail "この actor は :propose 以外の :effect を持つことができない"}))))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be silently
  auto-approved:
  - Shipment coordination (even proposals -- real-world actuation)
  - Safety concern flagging (always escalates)"
  [op-type]
  (when (contains? #{:actuation/coordinate-shipment :proposal/flag-safety-concern} op-type)
    [{:rule :requires-human-approval
      :detail "出荷調整と安全上の懸念報告には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn batch-log-draft
  "Draft a knitting/linking production batch logging proposal.
  subject: batch ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items (batch verification, etc.)"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/log-production-batch
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn maintenance-draft
  "Draft a knitting-line-equipment maintenance scheduling proposal.
  subject: equipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified maintenance records"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/schedule-maintenance
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn safety-concern-draft
  "Draft an equipment-safety/quality-defect concern flagging proposal
  (ALWAYS escalates).
  subject: batch or equipment ID
  cites: spec-basis citations
  concern-type: category of concern (equipment-safety, quality-defect, etc.)
  evidence-checklist: map of verified concern evidence
  detail: narrative description"
  [subject cites concern-type evidence-checklist confidence detail]
  {:op :proposal/flag-safety-concern
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :concern-type concern-type
           :detail detail}})

(defn shipment-draft
  "Draft a shipment coordination proposal (high-stakes actuation).
  subject: shipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified shipping/tariff documentation"
  [subject cites evidence-checklist confidence detail]
  {:op :actuation/coordinate-shipment
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})
