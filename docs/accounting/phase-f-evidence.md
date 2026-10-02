# Phase F: controlled operational report builder

Implementation date: 2026-10-02. This document records software evidence, not institution accounting approval or live release approval.

## Scope and contracts

The component `com.sacco.mvp.reporting` provides a schema-version-1 dataset/field catalog, controlled report definition, institution-owned immutable template versions, independent publication checking, bounded preview and typed exports. Routes live under `/reports/builder`; the shared sidebar owns navigation.

First layouts are Collections, Disbursements and Loan Portfolio. They do not expose unrestricted SQL, formulas, database tables, borrower contacts or ledger/financial statement fields. Collections use immutable receipts and negative linked reversals. Disbursements cover confirmed ordinary ledger origins. Portfolio principal is reconstructed from confirmed origins less signed principal postings through the effective and recorded cutoffs; mutable paid counters are not its historical source.

The catalog identifies source, grain, date semantics, coverage, source permission and stable definition version. Field metadata identifies stable ID, type, precision/unit, sensitivity, allowed operators and grouping. Nulls remain unavailable; totals over an empty verified result are zero while excluded untracked loans remain explicitly unknown.

`OperationalReportService.execute(definition, actor, from, through, recordedCutoff, page, size)` returns `Result` with typed rows, full-scope totals, coverage, scope, cutoffs and pagination. It uses a repeatable-read transaction and bounded indexed queries; it neither locks posting rows nor loads full borrower histories. A job service must use one repeatable-read outer transaction across its page iteration: recording cutoffs alone do not prove a coherent database snapshot in the presence of commit races.

## Security, persistence and concurrency

Dedicated claims are `REPORT_TEMPLATE_DESIGN`, `REPORT_TEMPLATE_PUBLISH`, `REPORT_RUN`, `REPORT_EXPORT`, and `REPORT_TEMPLATE_SHARE`. They are not automatically assigned to roles. Data access additionally requires `LOAN_REPAYMENTS_VIEW` for Collections or `LOAN_REPORTS_VIEW` for Disbursements/Portfolio. Execution and template operations refresh directory status, exact institution/branch, effective claims and active institution/branch; sharing does not grant dataset or branch access. Platform identities cannot operate institution report workflows.

Migration `V46__operational_report_templates.sql` stores institution-owned templates and immutable definitions, maker/checker identity and publication timestamps. A trigger rejects definition edits/deletes and invalid state transitions; database checks require different maker/checker identities. Revisions lock the template row briefly to allocate its next version. Publication/retirement lock only their version. Existing financial history is untouched. Report and template SQL remain in their owning repositories.

The server validates supported fields, operator types, dates (maximum 366-day span), money precision, text length/control characters, visible columns, grouping, totals and sorts. SQL identifiers come exclusively from enums; values are parameters. Loan amounts cannot be multiplied through payment or guarantor joins. Every request derives institution/branch from the trusted principal. CSRF remains required for designer mutations and previews.

## UI and exports

The JSP uses the compact ERP shell, wrapping controls and an inner table scroll viewport. It supports column show/hide, custom additional labels, widths, keyboard-accessible Up/Down ordering, allowlisted filters, sorting/grouping/totals, draft creation, cloning/revision, reviewer publication/retirement, previews, runs and CSV/XLSX/PDF. Canonical field names and TZS remain visible when custom labels are supplied. English/Kiswahili strings are in existing bundles.

All output formats consume one typed result. CSV neutralizes spreadsheet-formula prefixes in text and preserves negative numeric values; XLSX uses typed dates/numbers and exact decimal text for amounts above Excel's 15 significant-digit numeric limit. PDF repeats headers, wraps text, numbers pages and splits wide reports into column bands with identifying context. Unsupported font characters fail visibly rather than silently changing financial data.

Synchronous exports use the existing export semaphore and a maximum 2,000-row complete result. Partial pages are rejected. Durable run/artifact retention, asynchronous large exports, quotas and protected downloads belong to Phase H; they are not asserted by this increment. Safe owned-logo selection is not implemented in the report output: existing institution branding remains in the application shell, and no arbitrary remote asset is accepted.

## Verification record

- `mvn -DskipTests compile`: passed initial backend and controller compilation. Final verification follows below.
- `mvn -Dtest=OperationalReportDefinitionTest,OperationalReportExportTest test`: 7 tests passed, zero failures/errors. Covers system catalog definitions, invalid grouping/text/precision/dates, CSV formula protection and signed cents, high-value XLSX precision, PDF labels/units/page numbers and rejection of partial exports.
- Disposable PostgreSQL tests and final compiler results: pending at this checkpoint.
- English/Kiswahili JSP rendering and 360/768/1366/1920 widths: pending integrated browser verification; compilation is not rendering proof.
- No representative query plans, load benchmark or 1,000-requests-per-second capacity result are claimed. Queries are scoped/indexed and bounded; capacity remains unproven.

## Remaining approval and release gates

Institution reviewers must approve dataset meanings and at least two institution layouts. Unknown legacy openings, channel reconciliation, accountant policies and authoritative general-ledger financial definitions remain independent gates. Financial statements and financial dataset fields remain unavailable. F04 owned-logo selection and complete Unicode PDF font support remain explicit feature gaps; H run/artifact controls and integrated rendered/security/performance acceptance must be verified before release.
