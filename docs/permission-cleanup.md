# Retired report permissions — 2026-10-19

The eight report permissions below retain their enum identifiers for compatibility, but are unavailable in the access matrix, feature/action lookup, stored-row and legacy-JSON loading, role defaults, new principals and permission helpers. Assignment discards unavailable permissions before adding implied view permissions or storing grants. An explicit empty assignment is retained as an empty preference so revocation cannot restore role defaults.

| Retired claims | Dependency result |
| --- | --- |
| `REPORT_TEMPLATE_DESIGN`, `REPORT_TEMPLATE_PUBLISH`, `REPORT_TEMPLATE_SHARE` | No remaining controller or JSP workspace. Removed unused operational template services, definition/catalog and repositories after moving history checks to `RetainedFinancialHistoryRepository`. |
| `REPORT_RUN`, `REPORT_EXPORT` | Removed unused operational dataset and export implementation. Current loan, management and financial statement reports use their own controllers/services and permissions. |
| `REPORT_RUN_APPROVE`, `REPORT_JOBS_REVIEW` | No remaining request/review workspace. Removed run commands, generator, codec, worker control, scheduled worker, dedicated executor and DTO/repository implementation. Existing queued jobs are left unchanged and no longer execute. |
| `FINANCIAL_REPORTS_FINALIZE` | No implemented consumer; removed from selectable financial-report actions. Current report viewing, PDF/print, statement Excel exports and explicitly authorized institution aggregates remain. |
| `ACCESS_MATRIX_VIEW`, `ACCESS_MATRIX_UPDATE` | Additional ineffective claims found during the remaining-permission audit. The embedded matrix uses the user-access edit/update routes, guarded by `USER_ACCESS_UPDATE`. Removed unused default/recovery grants; existing user-access controls remain. |
| `LOAN_DOCUMENTS_CREATE` | Additional ineffective claim: attachment uploads use loan draft creation/edit, guarded by `MEMBER_LOANS_CREATE`/`MEMBER_LOANS_UPDATE` and ownership. No separate document-creation operation consumes this claim. Existing uploads, document viewing and export remain. |

Removed the two unused report-builder/execution stylesheets, retired English/Kiswahili labels and obsolete navigation gates. The shared `report-print.js` is retained because client loan statement printing still uses it. The live `reporting/loan-portfolio*.jsp` pages and current reporting services are retained. Tests for deleted runtime behavior were removed; history preservation now has dedicated database coverage.

## History and financial integrity

No migration, stored grant deletion, table move, financial record deletion or financial posting is part of this cleanup. V46/V49 report tables, frozen results, artifacts, logos, original definitions, audit events, foreign keys and immutability triggers remain. `RetainedFinancialHistoryRepository` checks institution report definitions/runs and member ownership, template author/reviewer and run requester/approver references. `SaccoDataDeletionService` retains its existing early refusal before files, audit rows or institution/member records can be deleted. Other retained accounting and repayment checks are unchanged.

This supersedes the descriptions of the report builder and report execution runtime in [phase F](accounting/phase-f-evidence.md) and [phase H](accounting/phase-h-evidence.md). Those files remain historical implementation evidence, not descriptions of currently available workspaces.

## Active permissions

All 169 enum identifiers remain; 128 are selectable and 41 are retired. The [complete selectable-claim trace](selectable-permission-trace.md) identifies each production consumer, including seven dynamic claim checks. Account version review/configuration, configured voucher draft capture/review/posting, financial/management reports, current financial statements and SL–GL reconciliation remain active. `ACCOUNTING_RECONCILIATION_VIEW` still protects the current reconciliation report; other old reconciliation workflow claims remain unavailable. `VOUCHER_WORKSPACE_CREATE` still protects approval-draft capture from ordinary voucher forms. Account Approve/Reject claims are constructed dynamically and remain explicitly assignable. No new reviewer/configuration permission is granted through role defaults.

## Verification

Clean Java 25 compilation of all 364 remaining production sources and selected tests passed in isolated output. Across verification runs, all 179 distinct focused checks passed. The initial run passed 178; a new direct-route test had incorrectly mocked away service authorization. Its corrected fixture exercises the real voucher service and authorization guard. The final rerun passed all 61 selected checks, with no skipped or failed checks. Production behavior did not need a workaround for that fixture error.

Coverage includes matrix availability and default/recovery grants, structured and legacy JSON loading, batched claim resolution, explicit assignment/revocation, old-session rejection, direct-route denial, CSRF and password login isolation. Existing voucher, account-version and statement tests exercise current claims, institution/branch boundaries, immutable evidence, independent decisions, audit rollback, duplicate requests, concurrent posting/review, linked corrections and current exports. Four disposable loopback PostgreSQL 18 databases exercised real migrations through V75 and real posting/history protections; no application datasource credentials were used. The new history checks cover all five historical report roles and database refusal to alter frozen definitions, runs and artifacts.

Jasper compiled eight affected/shared-shell views: the three approval pages, account-version register/details, user-access matrix, live portfolio report and ordinary voucher form. Source checks found no retired-runtime imports, compiled classes in the clean output or retired-label consumers. Matching English/Kiswahili retirements and Git whitespace checks passed. Verification logs and temporary helpers remain ignored under `target/permission-verification` and `target/`.

This was focused direct JUnit Platform verification, not a full Maven suite or packaging run. Live browser acceptance, production sign-in, institution acceptance and throughput measurements were not repeated. Use a clean rebuild and restart to activate runtime removal; pre-existing build output and running application processes can still contain the old classes. The separate pre-existing loan-adjustment form layout edit is excluded from this commit.
