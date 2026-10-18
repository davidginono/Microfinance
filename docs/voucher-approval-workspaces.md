# Configurable voucher approvals

The user-requested 2026-10-18 increment enables approval for individual ordinary receipt, payment and journal vouchers and for batches, independently per branch and voucher type. This supersedes the earlier prohibition on core approval steps **only where configuration enables them**. Unconfigured individual vouchers retain direct posting. Specialized loan repayment receipts and the existing loan collection batch worker retain their established ledger controls; they cannot enter this ordinary-voucher workspace.

## Screens and access

- `/finance/approval`: branch-scoped status tiles and a bounded, sortable register. Validation, approval and posting are independent columns. Select one row to open its details.
- `/finance/approval/new`: one voucher or a homogeneous batch of up to 50 vouchers. Each voucher has up to 50 transactions.
- Record details: permitted actions, approval route, field warning links, evidence and the latest 100 audit events. Approval never implies a posted journal.
- Voucher editor: dense editable lines, warning-to-field focus, scoped account lookup and read-only inspection for reviewers. Save returns to its parent; validation and submission operate on the complete workspace.
- `/finance/approval/configuration`: independent individual/batch switches, non-overlapping inclusive-minimum/exclusive-maximum TZS ranges, explicit automatic approval or up to two approver groups, and a saved-rule amount test. Journal batches use their largest voucher total; receipt/payment batches use the batch total. Gaps fail closed with a missing-rule error.

New claims appear in the existing access matrix. None are automatically granted. Workspace view/create/update/validate/post, initiate/withdraw/hold/approve/reject/force/senior approval, configuration view/configure, and document view/create are independent. Voucher-type view and creation claims still apply. Every command rechecks current claims, active staff access and institution/branch scope in the service. Reviewers cannot approve their own preparation; each sequential stage requires a different person. The administrator assigns claims; deploying this code does not activate approval or grant authority.

## Status and correction behavior

Oracle's [approval actions](https://docs.oracle.com/en/cloud/saas/financials/26d/fappp/considerations-for-approval-actions.html) inform the status semantics: Required → Initiated; withdrawal cancels eligibility for reviewer actions; Hold delays Required; resubmission starts a new review round after rejection, withdrawal, hold or material correction. Force approval requires both its dedicated claim and the enabled option, plus an audit reason, and produces Manually approved. Workflow approved and Manually approved remain separate from Posted.

Validation records field issues and a hash of resolved accounts, mappings and amounts. Editing unposted entries or adding evidence invalidates validation and prior approval. Content cannot change while approval is initiated; withdraw first. Configuration changes require validation and resubmission. Configuration revisions are retained as immutable evidence; each history event also preserves its policy version, assessed amount, statuses and review stage. Current account mappings and periods are checked again before posting. Approved content is not edited after posting: use existing linked voucher reversals.

Posting locks one envelope and commits every voucher, balanced journal, envelope link and audit together. Stable request keys and row locking prevent duplicate/concurrent posting. A failed attempt rolls back its financial writes; the controller records a separate recoverable failure status. Deferred database checks require every item in a posted envelope to be linked and validated and block direct inserts of individual vouchers when approval is configured. Historical posted request retries remain idempotent after configuration changes.

Supporting PDF/PNG/JPEG evidence is branch scoped, immutable, limited to ten files of up to 5 MB each, checked by file signature, served without caching, and protected by its own claims. There are no new exports; posted vouchers retain existing PDF/print.

## Verification

On 2026-10-18 the focused run passed 55 tests: VoucherPostgresTest (20), VoucherMvcTest (12), ChartOfAccountsJspCompilationTest (1, compiling all six changed JSP pages), FinanceWorkspaceSecurityTest (4), UserClaimTest (11), and UserClaimServiceTest (7). Database coverage used a disposable PostgreSQL 18 instance migrated through V73, not operational data. JavaScript syntax and Git whitespace checks passed.

The application started successfully against that database. In-app browser checks used synthetic staff and a synthetic journal voucher: login, claim-visible navigation, single-row View, sort selection reset, status tile filtering, individual/batch creation choices, validation warnings, warning-to-amount focus, scoped account selection, correction saving and successful revalidation. The browser exposed a localized date-input formatting issue; the editor now renders ISO dates, the saved date persisted and the editor JSP compilation check passed again. Configuration rule add/remove controls worked. Register/configuration measurements at 360, 768, 1366 and 1920 pixels showed contained page widths and independent table scrolling; desktop screenshots were saved outside the repository. No operational policy was enabled and no financial posting was performed through the browser. Supporting-document preview was not exercised in this browser check.

The full suite is not green: it reported existing failures in ActiveLoanHydrationViewContractTest, AdminControllerUserAccessSecurityTest, AwsConsoleViewContractTest, DirectOtpGuarantorSearchViewContractTest, ErpTableAlignmentContractTest and JspRecordAccessorCompatibilityTest. It was then interrupted while an unrelated LoanPresentationServiceTest was rendering a PDF watermark. These unrelated sources were left unchanged; the final focused run above passed after the voucher test fixture correction.

The architectural 1,000 requests/second target remains unmeasured for this workload; queues fetch at most size+1 records (10/30/50), account lookup is bounded to 25, and financial batches are capped at 2,500 total transaction rows.
