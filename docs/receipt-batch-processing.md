# Receipt batch processing

2026-10-19: principal and interest routing now uses immutable loan bindings from product account links; batch entry shows the loan product without its GL mapping. The shared interest-account setup described in the historical increment below is superseded. Existing loans require explicit linking before collection. See [loan product account links](loan-product-account-links.md).

The staff sidebar provides **Batch Processing → Receipt Batch Processing** at `/finance/batches/receipts`. This increment covers verified TZS repayments for directly recorded, disbursed loans with an existing ledger opening.

## Excel entry

Open a batch you own and choose **Import repayments**. **Download template** lists eligible individual loans in your branch. Search by Loan ID or client to narrow the list. The Loan ID dropdown updates the client and first unpaid scheduled instalment shown for reference. Fill in the actual amount received, payment date and collection reference. Enter dates as YYYY-MM-DD and keep references as text to preserve leading zeros. Leave payment cells blank on rows you are skipping.

Choose the receiving account and upload the completed template to the same batch. The file can contain up to 1,000 rows and be up to 2 MB. Each filled row adds one draft receipt. The server resolves Loan IDs within the selected branch and rejects the entire upload on an invalid or duplicate row. Existing batch limits apply. Review the drafts, then use the existing **Validate batch** and **Post batch** actions.

## Entry and review

Each main grid row is one receipt. Select a branch client and loan, then enter the actual payment date and received amount. The receiving account and evidence reference are editable in that same row. New receipts inherit the receiving-account/date defaults. Selecting a default account also fills empty accounts on the displayed editable receipts, without replacing explicit selections; clearing a receipt's account falls back to the selected default. The resolved account is saved with the receipt and remains subject to server validation. A receipt can be expanded to add transactions for up to 50 distinct loans of that same client, with one payment date and receiving account. The total is calculated from its transactions. Manual drafts get a stable server-generated reference. Existing references and references supplied through Excel or payroll are retained. Receipt numbers are generated on posting.

Drafts autosave with optimistic version checks. Client changes clear dependent loan selections. Searchable choices use scoped, bounded lookups and preserve keyboard behaviour. Normal one-transaction receipts are editable directly in the grid; additional transactions and allocation context use the expanded editor. Receipt fields stay in one table row at every width, with horizontal scrolling contained inside the table on smaller screens. Pagination returns 25 receipts, and a batch is limited to 1,000 receipts / 2,000 transactions. JSON edits are limited to 128 KiB before binding.

The batch state and receipt totals appear in a framed Batch details section. The page title has no separate Draft label. Live checks and retained validation failures appear directly below their relevant controls, using short localized messages and field-specific accessible error associations. Errors in collapsed receipt details open that section. Editing clears stale messages; a pending check is announced without moving focus or clearing inputs.

Draft estimates use recorded balances. Batch validation processes receipts in payment-date/entry order and includes earlier unposted allocations for repeated loans. These projections are explicitly labelled, and never reduce a recorded balance. The accountant reviews the calculated principal/interest split, loan-owned principal account, receiving account and configured interest collections account before direct posting. Validation is a technical check, not a checker/approval stage.

The Balances column shows **Total outstanding**, **Outstanding principal**, and **Interest due**. The total is unpaid principal plus interest due on the payment date, excluding future unearned interest. Outstanding principal includes instalments that have yet to fall due. Interest follows the retained servicing terms and can be zero before the first instalment. Multi-loan receipts show these values for each loan in the expanded table. Missing ledger balances and dates outside the supported payment range show a dash. Validated projections and retained pre-payment totals have their own labels.

## Protected repayment definitions and policy

Migration V66 provisions the immutable **Loan repayment** activity and standard **Principal repayment** / **Interest repayment** transaction definitions for existing and new institutions. Services and database triggers prevent edits, deactivation, ownership changes and ordinary posting templates for these definitions. Explicit receipt purpose and stable system keys select them; activity names do not authorise lending operations.

Repayment allocation uses the built-in interest-first rule. New individual receipts and batches do not require a settings version. Legacy policy rows and their immutable payment/reversal references remain readable; loan product bindings continue to supply principal and interest accounts.

For this Tanzania Mainland non-deposit-taking institution scope, Regulation 42(2) requires due interest, then outstanding fees/charges, then due principal. The server enforces the built-in interest-first rule; V67 also guards new policy records in PostgreSQL while retaining immutable historical snapshots. Batch receipts still exclude loans with outstanding charges; individual receipts retain their existing charge collection path. See the [official regulations, section 42](https://bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/sw/2020021122490967551.pdf), rechecked on 19/10/2026. The user's 2026-10-19 instruction permits arbitrary positive amounts up to outstanding principal and due interest, with excess over due instalments allocated to future principal. Full early repayment excludes remaining future interest under section 42(4), preserving the original contract and recording an independently reversible exclusion separately from collected interest. Preview and ledger posting use the same decimal component calculator. New direct-recording repayments use the built-in order and retain any applicable legacy policy reference; reversals retain their original policy and allocations. Existing records are never repriced or rewritten. Product-account mappings remain configurable; repayment allocation is built in.

Initial historical configuration can preserve the existing interest-first behaviour. Subsequent policy versions cannot be backdated across recorded payments. Subsequent versions must also follow the last configured effective date. Batch posting requires applicable settings. Individual receipts retain the existing interest-first/manual-account compatibility path where no settings apply; configured dates resolve their accounts automatically. The older borrower-workflow repayment command uses the same future-principal allocation and early-exclusion handling while retaining its own permissions.

## Execution and recovery

Validation and posting are durable background phases. A dedicated single-thread scheduler claims one bounded receipt step using PostgreSQL `SKIP LOCKED`, a worker token and a lease. The token is checked again under the batch lock; a stale worker cannot post. Expired work is recoverable after restart. Other application instances may work on other batches; the existing per-loan locks retain monetary concurrency protection.

At posting, the worker resolves the original requesting staff member's current claims, active institution/branch and scope. It obtains the existing deterministic loan-source locks, recalculates the receipt and checks it against the validated policy, accounts and balances. Changed information fails that receipt for revalidation. A receipt's complete repayment allocations, journals, existing audits/outbox records, receipt envelope and immutable batch-result link commit in one transaction. No second collection journal is created.

Successful receipts remain posted when another receipt fails. Financial exceptions roll back before the failure is stored in a separate transaction. Transient database failures retry at most three times with a delay; validation/business failures need correction or revalidation. Retrying works only on unposted items, using their stable receipt request identities and existing collection-reference protections. Stop prevents subsequent work after any in-flight receipt commits; it never reverses a posted receipt. Original results link to the existing receipt details, PDF/print and independent linked-correction workflow.

## Scope and boundaries

Read routes require existing business-voucher and loan-recording view capabilities. Draft mutation and execution also require business create / loan-recording post and the original batch owner. The retired settings and account-lookup endpoints no longer expose policy configuration. No claims are assigned automatically. All browser mutations retain session authentication and CSRF. New batch/item foreign keys, protected posted-result links and policy snapshots enforce financial lineage at the database boundary.

Payments before a loan's latest unreversed payment, before disbursement, in a closed period or after today remain rejected. This does not reconstruct omitted historical payments around newer postings. That requires a separate historical-correction/reallocation capability. Overpayments/unapplied credit, new fees, historical ledger openings and channel reconciliation are not implemented by this batch command. Data entry is not independent evidence that funds were received.

## Verification

Use the existing opt-in `LoanRecordingPostgresTest` only with the disposable loopback `microfinance_recording_test` database and its `MICROFINANCE_RECORDING_TEST_URL` guard. Added scenarios cover staged repeated loans, multi-transaction atomicity, stale balances, independent corrections, current access checks and protected system definitions. `RepaymentAllocationPolicyTest` exercises priorities and cents; `ReceiptBatchMvcTest` covers CSRF, access, decimal input, reviewed versions and localised failures. The shared Jasper test can compile `batches/register.jsp,batches/new.jsp,batches/editor.jsp` using `microfinance.jsp.pages`.

Executed on 15 October 2026:

- Maven `mvn -DskipTests test-compile` passed for the application and test sources.
- The six focused JUnit classes passed 59 tests, including real PostgreSQL posting, worker lease recovery/stop/resume, duplicate references, statutory policy guards and oversized declared/chunked requests. All 86 migrations through V67 validated on the disposable database.
- Jasper compiled the four batch pages, the activity and transaction library pages, and the ordinary voucher form (seven pages).
- JavaScript syntax and Git whitespace checks passed.
- A synthetic browser session using the real controllers, JSPs, financial services and disposable PostgreSQL database completed autosave, multi-loan entry, validation, direct posting and a completed receipt with two transactions totalling TZS 171.03. Further checks verified sidebar expansion, register selection enabling View, read-only allocation order, and displayed policy/account mappings. Mobile (360px), tablet (768px) and desktop (1366px) DOM measurements confirmed contained page widths; tablet grid overflow remained inside its scroll container. The browser fixture used a mocked staff directory and synthetic accounts, not production records.

The full test suite and a throughput load test were not run. No throughput or regulatory-certification claim is made.

## Initial setup

Apply existing migrations through normal Flyway startup. Accountants create a batch, select the receiving account/date defaults, enter verified receipts, validate and post. Interest-first allocation is built in; no Repayment settings step is required. Existing product-account bindings, loan-ledger opening, authorization and payment-date restrictions still apply.

Repayment templates include scoped client loans with a recorded funding date and ledger in DISBURSED, PAR or DEFAULTED status. An empty branch cannot produce a loan-filled template. Receiving accounts must be active asset posting accounts with CASH, BANK or MOBILE_MONEY purpose, or accounts with OTHER purpose under the institution's configured cash family. Collections into an account with OTHER purpose use the cash-equivalents ledger channel and post to the selected GL account. Configure accounts through the existing account-version flow and record/fund real loans through the authorized loan flow. Import creates drafts for validation and posting.

The shared shell moves the batch confirmation dialog to the document body before receipt initialization. Resolve it there; looking for it inside the editor prevents initial loading and breaks workspace navigation even though a later table refresh may load rows.

2026-11-12 verification: 15 focused JavaScript checks passed, including the body-mounted modal, initial loading and cancellation. The isolated PostgreSQL import scenario verified atomic rejection, duplicate references, draft balance preservation and the existing worker. A browser session against that synthetic database downloaded the real Excel template, selected a Cash account, imported TZS 119.99 as one unposted draft and rejected a second upload without adding another receipt. Direct loading and register-to-editor navigation also passed in the existing staff session. No operational repayments or account classifications were changed.
