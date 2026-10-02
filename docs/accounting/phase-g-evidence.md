# Financial statement designer evidence

This increment implements the institution designer and protected manual regulatory-submission workflows. No approved institution policies, chart of accounts, opening balances, or regulator-prescribed return artifact were supplied. Production finalization therefore requires real independent approvals through the accounting workflows; synthetic approvals occur only in the explicitly opted-in test database.

## Requirement coverage

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| G01 | Separate `/reports/statements` row designer; headings, account groups, notes, subtotals, ratios, units, signs, comparative sources, keyboard reorder controls and localized labels | `StatementDefinition`, controller, JSP, shared-shell assets |
| G02 | Immutable mapping revisions with independent review; complete eligible-account placement or explicitly evidenced exclusion; foreign, omitted, overlap, duplicate-leaf, sign, section and undefined-account checks | DSL and real PostgreSQL mapping tests |
| G03 | Schema/calculation version 1; bounded typed SUM/DIFFERENCE/RATIO expressions over stable row identifiers; maximum depth 8; cycles rejected; unavailable and zero-denominator states remain explicit | DSL calculation, cycle, depth, precision and memo tests |
| G04 | Authoritative D close snapshots; required reconciliation, balance and disclosure rows appended independently of custom layout; archived JSON and checksum immutable; publication holds current mapping and period locks; prior-result restatement linkage | B → D → G PostgreSQL tests, including concurrent publication and duplicate finalization |
| G05 | Separately installed protected official-format versions, explicit official reference/applicability evidence, prescribed fields/calculations and calendar period/deadline checks; retained manual file bytes/checksum, independent reviewer and correction chain | Protected-format and file-validation tests; real PostgreSQL manual file workflow |

## Source and approval boundaries

Draft previews read only scoped posted GL entries under one repeatable-read transaction, with an effective-period and recorded-data cutoff. Imported opening journals are included in opening balances and excluded from period movements. Missing reviewed opening coverage yields unavailable amounts, not inferred zeros. Finalization reads immutable independently reviewed closing evidence through `ReconciliationService.finalizedSnapshotForPublication`; no request-supplied amounts or proof flags are accepted.

Finalization retains the typed result, definition, mapping/checker evidence, calculation version, source close/checksum, current/comparison cutoffs and generated timestamp. Closed-period and approved-mapping locks remain held until the result commits. Reopening or retiring a mapping does not rewrite an archived result. New publication must use the latest approved closed source. A restatement links to the prior retained result and preserves both versions.

`verifiedResult`, `verifiedResultDigest`, `authorizeExport`, and the mandatory `verifiedVersionForPublication` boundary are provided to the H durable-artifact workstream. `StatementMappingChangeListener` allows a repository-only H implementation to invalidate release evidence atomically without creating service cycles. H owns CSV/XLSX/PDF statement artifacts and final rendered/export verification.

## Cash flow dependency

Simple one-money-leg/one-counterpart journals can produce exact reviewed cash flows from frozen counterpart evidence. Aggregate net-cash equality cannot classify a compound journal. Final cash flow therefore rejects a source with frozen ambiguous journals or unmatched transfer movement. E must supply independently approved immutable journal-level cash-flow allocations for compound cases; this integration remains a prerequisite for publishing those cash-flow statements. This gate is not evidence that allocation functionality is implemented.

## Protected official formats

No regulator return is invented or seeded. An administrator-controlled deployment manifest is loaded only when an explicit local file path and matching SHA256 are configured (`app.accounting.regulatory-format-manifest`, `app.accounting.regulatory-format-sha256`). It must contain a primary official BoT/NBAA reference, applicability evidence, fixed version, prescribed period/deadline, fields, controlled calculations and permitted file format. Institution routes cannot modify those artifacts. Approved institution layouts cannot replace protected official calculations.

The Bank of Tanzania's enacted Mainland Tier 2 regulations provide for periodic reports in a form and frequency specified by the Bank; they do not supply an institution's approved return artifact for this implementation. Primary source rechecked 2026-10-02: [Microfinance (Non-Deposit Taking Microfinance Service Providers) Regulations, 2019](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/en/2020021122490967551.pdf). Actual format and jurisdiction approval remain external prerequisites.

Manual uploads retain actual bounded bytes with a server-generated checksum. CSV rejects executable formula cells and invalid UTF-8; XLSX rejects formulas, macros and external links; PDF rejects active actions and embedded content. These checks validate file safety/format. Independent reviewer evidence establishes that the manual file matches prescribed fields; the workflow records prepared/reviewed status and does not claim regulator delivery.

## Verification status

Dedicated synthetic database: PostgreSQL 17 at `127.0.0.1:55439`, `microfinance_accounting_g_test`, opt-in environment `MICROFINANCE_ACCOUNTING_G_DATABASE_URL`. The tests use real GL posting, bank-statement matching, certificate review and independent closing before designer finalization. No production database is used.

Verified 2026-10-02 at 15:30:27 Africa/Nairobi: Maven BUILD SUCCESS, 35 tests, zero failures/errors/skips (18 actual PostgreSQL, four MVC/CSRF, seven DSL/default-claim, three protected-format, three file-validation). Java 25 compiled 308 main and 145 test files during the stable passes. All 48 migrations applied naturally to the initially empty PostgreSQL 17.9 database, including V47; later runs validated them. The full focused command was `mvn -Dtest=StatementDefinitionTest,RegulatoryFormatCatalogTest,SubmissionFileValidationTest,StatementDesignerControllerTest,StatementDesignerPostgresTest test` with the opt-in database environment set.

The final passing suite includes actual two-period profit/equity comparisons, signed expense effects, approved memo disclosure without double counting, immutable linked restatements, high exact cents, missing-opening unknown state, compound-cash ambiguity rejection, both publication lock races, concurrent duplicate finalization, audit/lifecycle rollback, fresh role/member/institution/branch/claim denial, actual safe manual file bytes/corrections and current-source rejection after reopening.

Rendered desktop/mobile English/Kiswahili flows and H artifact export verification remain integration checks; these backend tests do not establish them. The JSP calls derived record getter `result.getVisibleRows()` explicitly, matching the resolver behavior observed in the parent integration.
