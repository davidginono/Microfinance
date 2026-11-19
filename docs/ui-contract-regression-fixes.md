# UI contracts and borrower identity regression fixes

27 October 2026. Follow-up to the failures recorded during client onboarding/KYC delivery.

## 1. Scope

Fix the reported page-header/filter assertions, table sorting coverage, and the borrower-identity fixture. Preserve actual application eligibility rules, loan posting and institution/branch/client scope.

## 2. Architecture and behavior

Operational organization-account, personal-guarantee and legacy coverage lists now use the existing `ResourceTableSort` helper. Each displayed column has a server-side whitelist expression; ordering precedes the existing 26-row lookahead and offset, with stable identity ties. Separate account/source pagination parameters keep neighboring lists in place when sorting. Default order is unchanged when no sort is requested.

Bounded adjustment allocations, receipt contributions and the funding detail use the shared local adapter. Collection row editors, frozen consent/agreement evidence and financial statements retain their recorded order with explicit purposes. Append-only case histories declare their chronological evidence purpose. The scanner recognizes both HTML quote styles and keeps a finite purpose whitelist; operational registers are not exempted as documents.

Page-header checks now recognize a register's single table heading and headings supplied by actual JSP includes, excluding shared navigation/header/footer chrome. Filter checks recognize current scoped table markers and the existing voucher-type context selector. The coverage picker distinguishes its responsive search form from its selection command. Statement date controls use the shared toolbar. No duplicate headings or decorative sorting arrows were added to satisfy assertions.

The borrower-identity fixture now proves that an outstanding recorded application blocks group submission. It then funds and settles that ordinary loan through the real posting services before resubmitting the same group cycle and verifying reuse of the historical borrower identity. It does not change a loan status by SQL or relax `GroupCycleService` eligibility.

## 3. Security

Existing service authorization and institution/branch/owner predicates are unchanged. Sort input selects only fixed whitelist expressions, applies only to GET reads, and cannot become arbitrary SQL. No new claims, default grants, exports or financial commands are introduced. Scope tests accompany the new ordering checks.

## 4. Database and query review

No migration or schema change. PostgreSQL successfully prepared/planned all 22 new ordering expressions against the disposable test database. Correctness tests exercise every new displayed-column order in both directions, nullable decisions, pagination and cross-branch denial. SQL planning alone is not representative load evidence.

## 5. Threads and transactions

No new thread, executor or mutation transaction. Read-only services retain their current boundaries and bounded queries. Sorting tests clear their mock request context after each test. Fixture settlement uses the existing transactional accounting and repayment services.

## 6. Files

- Group coverage, organization account and personal guarantee repositories.
- Corresponding group list JSPs and explicit ordering metadata on affected statement/document/editor/detail tables.
- Coverage picker and statement filter forms.
- AWS/table contract checks, the borrower-identity fixture and PostgreSQL sorting tests.

## 7. Verification

Completed checks:

- `mvn -q -Dtest=AwsConsoleViewContractTest,TableSortingCoverageTest test`: 28 tests, zero failures/errors/skips.
- `mvn -q -Dtest=BorrowerIdentityPostgresTest,GroupCyclePostgresTest,GroupCoveragePostgresTest,GroupOrganizationAccountPostgresTest,GroupPersonalGuaranteePostgresTest,SharedCreditExposurePostgresTest,GroupLendingArchitectureTest,ChartOfAccountsJspCompilationTest test`: 78 tests, zero failures/errors/skips, using the recording-test URL and `microfinance.jsp.pages` selecting the 15 affected standalone JSPs (including their fragments).
- PostgreSQL prepared/planned all 22 whitelisted ordering expressions successfully.

The final `mvn -q test` exited successfully: **1,892 tests selected across 264 suites; 1,763 passed, zero failures/errors and 129 skipped**. Skips require separately configured database environment variables for voucher, account-version, accounting, library, statements, payroll, permission, reset, dashboard and migration suites. `MICROFINANCE_RECORDING_TEST_URL` was set, so the affected borrower identity, group, onboarding/KYC and credit-control PostgreSQL tests executed.

`mvn -q -DskipTests package` and `git diff --check` passed. The updated `target/ROOT.war` was produced. Database checks used only the owned loopback PostgreSQL 17.9 cluster and synthetic `microfinance_recording_test` data; the helper was stopped after verification.

## 8. Measured results

No throughput benchmark was run. The 1,000-RPS capacity target remains unproven. Whitelisted, bounded reads and passing correctness tests are not a capacity result.

## 9. Remaining validation boundary

Browser inventory contained no authenticated Microfinance application tab. Authenticated desktop/mobile interaction and visual acceptance are unverified; source-contract and Jasper checks do not replace them. No unrelated browser project or operational database was used.


## Resource details cleanup (19 November 2026)

Reviewed 22 resource details pages and their shared fragments, covering institution/client/user records, loan and repayment records, vouchers, account versions, approvals, adjustments, budgets and payroll. Resource-wide filters belong in registers. Record-specific financial forms, subtable search/sort/paging, evidence, real status, required disclosures and retry/error controls remain.

Removed the Institution details branch filter card and its Apply/Reset controls at source. Institution and branch IDs in the existing summary now link to their authorized details context. A scoped page shows its selected branch clearly; its institution ID returns to institution details. Branch access forms keep their branch, claims, CSRF, confirmation and edited values. Tabs continue to replace only their section, update native resource-link context, reject stale/authentication responses and offer local retry. Removed the filter-specific script updates, message keys and obsolete test exception. Existing institution/branch validation and data queries are unchanged.

Removed the redundant fixed-schedule explanation from disbursement posting details. Shortened English/Kiswahili notices for account approval evidence, protected repayment definitions, posting corrections, group adjustment allocations, payroll delivery/results and stopping payroll instructions. Retained equal/opposite reversal amounts, independent authorized correction, latest-unreversed-payment restrictions, frozen loan order and per-loan caps, delivery-only and no-money effects, missing-data warnings, client consent/KYC and historical evidence. No journal, repayment, approval, schema, stored financial record, permission, transaction, lock, cache or executor change was made. No throughput benchmark was run; the 1,000-RPS target remains unproven.


Verification for this cleanup:

- `mvn -B -Dmicrofinance.build.directory=target/resource-details-cleanup-build -Dtest=ChartOfAccountsJspCompilationTest,PlatformAdminServiceTest -Dmicrofinance.jsp.pages=... test`: all application/test sources compiled; the ten affected detail JSPs and platform scope test passed (two test methods).
- `mvn -B -Dmicrofinance.build.directory=target/resource-details-cleanup-build -Dtest=AdminConfigurationUiContractTest,AwsConsoleViewContractTest,ChartOfAccountsJspCompilationTest -Dmicrofinance.jsp.pages=../admin/sacco-detail.jsp surefire:test`: 27 tests passed, including the final Institution JSP.
- `node --test src/test/js/institution-details.test.cjs src/test/js/console-resource-details.test.cjs src/test/js/resource-table.test.cjs`: 74 passed. Re-ran the five institution tests against the final navigation fixture: all passed. They preserve live branch-form values, record scope, tab-link context, cancellation, keyboard navigation and local retry.
- English/Kiswahili orphan-label checks and `git diff --check` passed. The retained financial-warning keys remain present in both bundles.

Browser automation was not repeated after the user's earlier stop. Component, source-contract and Jasper checks cover this change; authenticated visual acceptance remains unverified. The local app uses the verified classes and updated resources in `target/resource-details-cleanup-build` on port 8081.

The updated local app started successfully. Login returned HTTP 200, and startup confirmed that no database migration was necessary.

## Registry and list cleanup (19 November 2026)

Reviewed 29 register/list workspace views. Seventeen now render their single primary title in the native table header instead of supplying a separate page heading for JavaScript to replace. This covers incidents, archives, notifications, the outbox, institution/branch records, groups, loan/client ledgers, budgets, adjustments, statement history, voucher approvals and payroll feedback. Title IDs and table references remain stable. Creation-mode headings and distinct summary sections remain; budget currency, employer context and selected approval status remain visible.

Removed the routine account-version scope explanation and six orphaned English/Kiswahili labels at source. Existing filters, selection, sorting, pagination, native forms, CSRF, permission gates, financial notices, errors and recovery controls are unchanged. No controller, service, query, financial record, database migration or shared JavaScript change was required.

Verification includes exact before/after comparison of native forms, buttons and table-control markers across all 17 title changes, the existing JavaScript table/title/group/notification/form-action checks, JSP compilation and UI/table/MVC contract checks. Browser automation was not repeated after the user's earlier stop; authenticated visual acceptance remains unverified.

- JavaScript checks: 67 passed, zero failures/skips (`resource-table`, `resource-table-title`, `group-lending`, `notification-display`, `form-action-placement`).
- Jasper and Java source/MVC contracts: 39 passed, zero failures/errors/skips. Compiled the 18 affected JSPs with the shared shell. Ran `ChartOfAccountsJspCompilationTest`, `AdminConfigurationUiContractTest`, `AwsConsoleViewContractTest`, `TableSortingCoverageTest`, `FileUploadControlViewContractTest`, `PayrollFeedbackMvcTest`, `AdminUsersJspLayoutTest` and `AdminMinorAdminsJspLayoutTest` through `surefire:test` using the existing unchanged compiled Java sources in `target/resource-details-cleanup-build`.
- English/Kiswahili orphan-label checks and `git diff --check` passed.

The updated local app started on port 8081; the login page returned HTTP 200. No schema migration was needed.
