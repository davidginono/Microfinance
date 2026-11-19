# Resource creation return flow

The 8 November 2026 instruction selects the originating register plus a persistent,
dismissible success Flashbar for completed top-level creation. The Flashbar appears
above the destination title/table. The Cloudscape create-resource reference allows
table, card and details destinations; this application chooses the register for the
completed flows below.

Reference: https://cloudscape.design/patterns/resource-management/create/

## Completed flows corrected

| Creation | Destination after success | Feedback |
| --- | --- | --- |
| Journal voucher | `/finance/vouchers/journals` | Posted to the ledger |
| Payment voucher | `/finance/vouchers/payments` | Posted to the ledger |
| Receipt voucher, ordinary or loan repayment | `/finance/vouchers/receipts` | Posted to the ledger |
| Group receipt | `/finance/vouchers/receipts` | Posted to the ledger |
| Group | `/finance/groups` | Group created |
| Collateral | `/finance/credit/collateral` | Collateral registered |
| Collection case | `/finance/credit/collections` | Collection case opened |

Controllers set a server-owned `successMessageCode` only after the owning service
returns successfully. `fragments/alerts.jspf` resolves the existing message bundles
and the shared shell displays the escaped message in its workspace Flashbar.
The destination owns the Flashbar, outside the paginated table. Native POST/redirect/GET
and workspace navigation use the same response. No new financial submission owner,
posting command, persistence query, claim or transaction was introduced.

The previous voucher-only post-success blocks and their exclusive CSS were removed.
The voucher details routes remain live for authorized inspection, PDF/print and
independent linked reversals. Optional pre-post previews and failed-submission forms
retain their existing behavior and values.

## Other creation workflows checked

This is a source audit of MVC creation handlers and their JSP destinations, not a
browser certification of every route.

| Workflow | Existing destination / reason retained |
| --- | --- |
| Institution, institution administrator, staff user | Already return to their registry with a saved-result message. |
| Loan product | Returns to its catalog with a success message; retain the expressly configured modal workflow. |
| Accounting activity/transaction onboarding | Returns to its register; the existing success-dialog/X behavior is an explicit project exception. |
| Chart of accounts / account versions | Draft and pending-review proposals continue in their version workspace. The current account is unchanged until the configured approval completes. |
| Individual voucher approval / batch creation | Continues in its draft/review editor; capture is not ledger posting. |
| Receipt batch | Opens its editor for adding/validating rows before posting. |
| Loan recording / client loan application | Draft, underwriting, submission and disbursement are separate tasks; retain their continuation context. |
| Staff provisional client | Opens Client details as explicitly required by the onboarding governance. |
| Group cycle, authority, guarantee, coverage and agreement | Dependent records stay within the owning group/cycle task, where participants, evidence and approvals continue. |
| Financial statement generation | Opens Reports Preview for reviewing the generated draft; generating a preview is not Save Draft. |
| Client registration / administrator setup | Continues to verification or sign-in; registration is not automatic authentication. |

No details page was deleted where it still owns authorized record actions or evidence.
No financial status, service validation, institution/branch scope, idempotency or
posting/reversal behavior was changed.

## Verification

Verified on 8 November 2026 using Java 25 and the project's Spring Boot 4.1 build.
A clean source export of `fc1d1213` was overlaid with only this request's controllers,
JSP/CSS changes, tests and three new English/Kiswahili message entries. This excluded
the concurrent loan-charge edits: a mixed-worktree build encountered eight unrelated
`GroupCycleService` errors for changed `ChargeQuote` accessors. Those files were not
changed by this request.

The clean application and test compilation passed. The focused run passed **48 tests,
zero failures, errors or skips** across `VoucherMvcTest`, `GroupLendingMvcTest`,
`GroupCollectionMvcTest`, `CreditControlMvcTest` and `ChartOfAccountsJspCompilationTest`.
The Jasper check compiled the voucher register, form and both details views, plus
the group and credit registers. Controller checks cover the success redirect/flash,
preserved preview/error values, CSRF, claims, scope and retained document access.

```text
mvn -B -ntp -Dtest=VoucherMvcTest,GroupLendingMvcTest,GroupCollectionMvcTest,CreditControlMvcTest,ChartOfAccountsJspCompilationTest -Dmicrofinance.jsp.pages=vouchers/register.jsp,vouchers/form.jsp,vouchers/details.jsp,vouchers/loan-details.jsp,../groups/register.jsp,../credit/register.jsp test
```

Both message bundles have one entry per new key; the old voucher success marker and
its exclusive style have no remaining references in their sources. The full suite
was not repeated for this presentation increment. Browser initialization timed out,
so desktop/mobile visual and interaction checks remain pending. No database migration,
new query, executor or thread was added. No throughput benchmark was run; sustained
1,000 RPS remains unproven.


## Create and edit form copy cleanup (19 November 2026)

Reviewed 55 form pages and fragments across accounting, administration, lending, client recording, groups, credit controls, payroll and settings. Removed routine account introductions, institution/branch edit subtitles and logo-paste explanations, including their unused labels and script updates. The retired account introduction incorrectly claimed that saving activates an account immediately; the live version/approval flow is unchanged. File selection, clipboard paste, preview, file metadata, format/size limits and the ready state remain.

Account version and approval-policy guidance is now inside the form beside its save actions, once per form. Shortened English/Kiswahili guidance for contra balances, account versions, institution-wide approval rules, product account links, joint outside debt, payroll instructions/calculation/request-only behavior and voucher previews. Retained active posting-account requirements, future-disbursement-only account changes, independent authorized review, one entry per shared debt, recorded monthly debt matching, employee/mandate/employer references, contractual due-date/limit/cutoff basis, excluded charges and no-money/no-save preview effects.

Required disclosures, declarations versus verification, unknown-value guidance, client privacy/consent, guarantor papers, upload limits, password/secret handling, validation, edited values, CSRF, explicit claims and native financial commands remain. No request fields, backend methods, schema, queries, posting/approval rules, transactions, locks or executors changed. No throughput benchmark was run.

Verification: 37 Java/JSP checks passed, including twelve current form/detail JSPs, account-version MVC access, administration/AWS view contracts, file controls, session settings and client profile actions. Ran `mvn -B -Dmicrofinance.build.directory=target/resource-details-cleanup-build -Dtest=ChartOfAccountsJspCompilationTest,AdminConfigurationUiContractTest,AwsConsoleViewContractTest,FileUploadControlViewContractTest,SessionSettingsJspRenderingTest,ClientProfileActionsJspRenderingTest,AccountVersionMvcTest -Dmicrofinance.jsp.pages=... surefire:test` against unchanged compiled Java classes and the current JSP sources. All 69 JavaScript checks passed for accounting/platform form recovery, validation, action placement, profile wizard/documents and loan product editing. Bilingual orphan/reference checks and `git diff --check` passed. Authenticated browser automation was not repeated after the user's earlier stop; these results are component, contract and rendered-JSP verification.

The local app restarted successfully with the updated resources. Login returned HTTP 200. No database migration was added or changed.
