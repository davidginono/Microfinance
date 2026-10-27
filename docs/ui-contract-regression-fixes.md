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
