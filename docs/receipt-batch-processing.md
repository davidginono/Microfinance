# Receipt batch processing

2026-10-19: principal and interest routing now uses immutable loan bindings from product account links; batch entry shows the loan product without its GL mapping. The shared interest-account setup described in the historical increment below is superseded. Existing loans require explicit linking before collection. See [loan product account links](loan-product-account-links.md).

The staff sidebar provides **Batch Processing → Receipt Batch Processing** at `/finance/batches/receipts`. This increment covers verified TZS repayments for directly recorded, disbursed loans with an existing ledger opening. It does not import Excel or enable new voucher export formats.

## Entry and review

Each main grid row is one receipt. Select a branch client and loan, enter the actual payment date, received amount and collection reference. The receiving-account/date defaults apply only to newly added receipts. A receipt can be expanded to add transactions for up to 50 distinct loans of that same client, with one payment date, receiving account and collection reference. The total is calculated from its transactions.

Drafts autosave with optimistic version checks. Client changes clear dependent loan selections. Searchable choices use scoped, bounded lookups and preserve keyboard behaviour. Normal one-transaction receipts are editable directly in the grid; additional transactions, evidence and account mappings use the expanded editor. Small screens use labelled stacked fields. Pagination returns 25 receipts, and a batch is limited to 1,000 receipts / 2,000 transactions. JSON edits are limited to 128 KiB before binding.

Draft estimates use recorded balances. Batch validation processes receipts in payment-date/entry order and includes earlier unposted allocations for repeated loans. These projections are explicitly labelled, and never reduce a recorded balance. The accountant reviews the calculated principal/interest split, loan-owned principal account, receiving account and configured interest collections account before direct posting. Validation is a technical check, not a checker/approval stage.

## Protected repayment definitions and policy

Migration V66 provisions the immutable **Loan repayment** activity and standard **Principal repayment** / **Interest repayment** transaction definitions for existing and new institutions. Services and database triggers prevent edits, deactivation, ownership changes and ordinary posting templates for these definitions. Explicit receipt purpose and stable system keys select them; activity names do not authorise lending operations.

Repayment settings configure an institution-scoped, append-only policy version containing effective date, allocation order, interest collections account, reason/applicable terms, configuring staff and request identity. Principal remains tied to the account used by the loan's disbursement. The policy determines allocation; staff cannot choose an allocation order on individual rows or enter manual credits to loan controls.

For this Tanzania Mainland non-deposit-taking institution scope, Regulation 42(2) requires due interest, then outstanding fees/charges, then due principal. The settings and server enforce interest-first; V67 also guards new policy records in PostgreSQL while retaining immutable historical snapshots. The current engine supports interest and principal only and does not introduce fee collection. See the [official regulations, section 42](https://www.mof.go.tz/uploads/documents/en-1636619029-MICROFINANCE%203.pdf). Allocation applies only to amounts due on the payment date. Preview and ledger posting use the same decimal component calculator, with the ledger preserving dated instalment allocation and checking the resulting component totals. New direct-recording repayments retain their policy ID; reversals retain their original policy and allocations. Existing records are never repriced or rewritten. Account mappings and effective policy versions remain configurable.

Initial historical configuration can preserve the existing interest-first behaviour. Subsequent policy versions cannot be backdated across recorded payments. Subsequent versions must also follow the last configured effective date. Batch posting requires applicable settings. Individual receipts retain the existing interest-first/manual-account compatibility path where no settings apply; configured dates resolve their accounts automatically. The older borrower-workflow repayment command keeps its existing allocation behaviour.

## Execution and recovery

Validation and posting are durable background phases. A dedicated single-thread scheduler claims one bounded receipt step using PostgreSQL `SKIP LOCKED`, a worker token and a lease. The token is checked again under the batch lock; a stale worker cannot post. Expired work is recoverable after restart. Other application instances may work on other batches; the existing per-loan locks retain monetary concurrency protection.

At posting, the worker resolves the original requesting staff member's current claims, active institution/branch and scope. It obtains the existing deterministic loan-source locks, recalculates the receipt and checks it against the validated policy, accounts and balances. Changed information fails that receipt for revalidation. A receipt's complete repayment allocations, journals, existing audits/outbox records, receipt envelope and immutable batch-result link commit in one transaction. No second collection journal is created.

Successful receipts remain posted when another receipt fails. Financial exceptions roll back before the failure is stored in a separate transaction. Transient database failures retry at most three times with a delay; validation/business failures need correction or revalidation. Retrying works only on unposted items, using their stable receipt request identities and existing collection-reference protections. Stop prevents subsequent work after any in-flight receipt commits; it never reverses a posted receipt. Original results link to the existing receipt details, PDF/print and independent linked-correction workflow.

## Scope and boundaries

Read routes require existing business-voucher and loan-recording view capabilities. Draft mutation and execution also require business create / loan-recording post and the original batch owner. Settings use existing account view/update capabilities and offer their own bounded account lookup; configuring policy does not require the ability to post repayments. No claims are assigned automatically. All browser mutations retain session authentication and CSRF. New batch/item foreign keys, protected posted-result links and policy snapshots enforce financial lineage at the database boundary.

Payments before a loan's latest unreversed payment, before disbursement, in a closed period or after today remain rejected. This does not reconstruct omitted historical payments around newer postings. That requires a separate historical-correction/reallocation capability. Advances, early settlement, new fees, historical ledger openings and channel reconciliation are not implemented by this batch command. Data entry is not independent evidence that funds were received.

## Verification

Use the existing opt-in `LoanRecordingPostgresTest` only with the disposable loopback `microfinance_recording_test` database and its `MICROFINANCE_RECORDING_TEST_URL` guard. Added scenarios cover staged repeated loans, multi-transaction atomicity, stale balances, independent corrections, current access checks and protected system definitions. `RepaymentAllocationPolicyTest` exercises priorities and cents; `ReceiptBatchMvcTest` covers CSRF, access, decimal input, reviewed versions and localised failures. The shared Jasper test can compile `batches/register.jsp,batches/new.jsp,batches/editor.jsp,batches/settings.jsp` using `microfinance.jsp.pages`.

Executed on 15 October 2026:

- Maven `mvn -DskipTests test-compile` passed for the application and test sources.
- The six focused JUnit classes passed 59 tests, including real PostgreSQL posting, worker lease recovery/stop/resume, duplicate references, statutory policy guards and oversized declared/chunked requests. All 86 migrations through V67 validated on the disposable database.
- Jasper compiled the four batch pages, the activity and transaction library pages, and the ordinary voucher form (seven pages).
- JavaScript syntax and Git whitespace checks passed.
- A synthetic browser session using the real controllers, JSPs, financial services and disposable PostgreSQL database completed autosave, multi-loan entry, validation, direct posting and a completed receipt with two transactions totalling TZS 171.03. Further checks verified sidebar expansion, register selection enabling View, read-only allocation order, and displayed policy/account mappings. Mobile (360px), tablet (768px) and desktop (1366px) DOM measurements confirmed contained page widths; tablet grid overflow remained inside its scroll container. The browser fixture used a mocked staff directory and synthetic accounts, not production records.

The full test suite and a throughput load test were not run. No throughput or regulatory-certification claim is made.

## Initial setup

Apply V66 and V67 through the normal Flyway startup. An authorised accounts administrator opens **Repayment settings** from the batch register, selects the existing interest collections account and saves an effective policy version with its applicable terms. Choose an initial effective date that covers the intended payment dates. Accountants then create a batch, select the receiving account/date defaults, enter verified receipts, validate and post. Existing loan-ledger opening and payment-date restrictions still apply to historical collections.
