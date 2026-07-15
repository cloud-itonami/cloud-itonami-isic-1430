# cloud-itonami-isic-1430

Open Business Blueprint + actor implementation for **ISIC 1430**:
manufacture of knitted and crocheted apparel — garments knitted or
crocheted directly to shape (circular- and flat-knitting machine lines
producing shaped garment panels/whole garments and linking them together),
distinct from
[cloud-itonami-isic-1410](https://github.com/cloud-itonami/cloud-itonami-isic-1410)
(wearing apparel except fur, which is cut-and-sew from woven/knit fabric).

**Maturity: `:implemented`** — this repository publishes both the business
blueprint and a working `KnitwearOps-LLM ⊣ Knitwear Governor` actor
(langgraph-clj StateGraph, independent governor, append-only-ledger-ready
proposal contract). It is a **plant operations coordination** actor, not
direct knitting-line control authority.

## What the actor does

**KnitwearOps-LLM ⊣ Knitwear Governor** — the fleet-standard pattern: the
advisor LLM drafts knitting/linking production-batch logging (with
output-quality data), knitting-line-equipment maintenance scheduling,
equipment-safety/quality-defect concern flagging, and outbound shipment
coordination; the independent `:knitwear-governor` gates every proposal.
Physical-domain work (circular/flat knitting, linking, pressing, packing)
remains the exclusive authority of licensed knitting engineers/technicians
— this actor never operates or sets parameters on knitting-line equipment.

Closed proposal allowlist (all `:effect :propose`):

| op                                | purpose                                                              |
|------------------------------------|-----------------------------------------------------------------------|
| `:proposal/log-production-batch`   | Log a knitting/linking batch + output-quality data to the audit ledger |
| `:proposal/schedule-maintenance`   | Propose knitting-line-equipment maintenance scheduling                 |
| `:proposal/flag-safety-concern`    | Surface an equipment-safety/quality-defect concern (ALWAYS escalates)  |
| `:actuation/coordinate-shipment`   | Coordinate outbound product shipment (high-stakes actuation, escalates)|

### Governor invariants

HARD (a human approver CANNOT override — always `:holds? true`):

1. Closed op-allowlist — any op outside the four above is rejected outright.
2. `:effect` must be `:propose` — this actor never actuates directly.
3. Plant/batch not independently verified/registered — blocks any action
   referencing an unverified plant or production batch.
4. Knitting-line-equipment control language (gauge, tension, needle,
   carriage, cam, cylinder, feeder, take-down, stitch-length, RPM, etc.) —
   a hard, permanent block. Those decisions remain the exclusive authority
   of licensed knitting engineers/technicians.

ESCALATE (always requires human sign-off, not an outright block):

5. `:proposal/flag-safety-concern` ALWAYS escalates to a human.
6. Low confidence or high-stakes actuation (shipment coordination) always
   requires human sign-off.

Operating states: `spec → design → produce → inspect → package → audit`.

## Run it

```bash
clojure -M:dev:run    # drive the demo through one OperationActor scenario set
clojure -M:test       # run the test suite
clojure -M:lint        # clj-kondo static analysis
```

## Why open

AGPL-3.0-or-later, forkable by any qualified operator, so local knitwear
mills never surrender production and provenance data to a closed SaaS.
Part of the [cloud-itonami](https://itonami.cloud) open business fleet.
