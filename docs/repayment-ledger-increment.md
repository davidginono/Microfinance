# Repayment Ledger Increment

## Simple Workflow

1. Disburse a new ordinary loan through the existing approval/disbursement workflow. The same database transaction verifies its stored schedule and opens the local ledger.
2. An authorized branch officer opens **Repayments**, selects the loan, and enters a verified received amount, actual payment date, channel, and collection reference.
3. Posting pays all due interest first, then due principal, commits the receipt/allocations/journal/audit together, and updates the loan's balance projection.
4. Staff and the owning client can read the receipt and loan-specific history. Clients reach the record from their loan detail page; they cannot post repayments or see the staff journal.
5. A different authorized officer corrects the latest unreversed payment with a reason. The original remains visible with a linked reversal; its allocations and financial entries are reversed once.

## Access And Records

- Staff routes: `/repayments`, `/repayments/loans/{applicationId}`, and loan-specific receipt routes. Access requires a staff session and an exact institution/branch match.
- Assign `LOAN_REPAYMENTS_VIEW`, `LOAN_REPAYMENTS_CREATE`, and `LOAN_REPAYMENTS_REVERSE` through the existing access matrix. Roles do not receive these new claims automatically. Give view permission to officers who post or reverse so they can inspect the result.
- Client reads require `MEMBER_LOANS_VIEW`, institution match, and ownership. No global repayment registry is exposed to clients.
- CSRF, session authentication, existing access controls, approval stages, and quorum rules remain in place.
- Migration `V41__local_repayment_ledger.sql` adds ledgers, dated instalments, immutable transactions/allocations, and immutable journal entries. Database checks enforce valid amounts, unique scoped request keys/channel references, and balanced vouchers.
- Contract amounts/due dates cannot be edited after ledger creation. Paid counters are projections of append-only allocations and linked reversals.
- Archive, institution, and account deletion refuse records linked to the ledger before deleting files or related rows. Routine operational cleanup retains `LOAN_REPAYMENT` audit events; legal retention/anonymization rules still need a separately approved lifecycle.
- Lists/history use 25-record pages. Calculations load at most 600 contractual instalments per loan, not a complete institution portfolio.
- A per-loan database lock serializes posting and reversals. Identical request retries return their original receipt; changed payloads cannot reuse the key. A reversed channel reference stays reserved, so corrections cannot disguise reuse of original channel evidence.
- Payment dates are distinct from recording timestamps. Payments cannot be dated after today, before disbursement, or before the latest unreversed payment. There is no closed-period or historical reallocation workflow yet.
- Local ledger obligations supply arrears dates for tracked loans. Balance refresh really recalculates tracked projections and explicitly reports unavailable/reconciliation status for untracked loans.

## Financial Boundaries

This is a controlled implementation increment, not a production-readiness or compliance certification.

- Existing loans are not backfilled from snapshots, status flags, or schedules. Opening balances and known historical receipts require a reviewed cutover procedure. Unknown is not zero.
- Top-up loans do not receive an invented ledger opening. Top-up disbursement cannot mark a ledger-backed source paid; a verified settlement transaction must be implemented first. Legacy top-up handling remains unchanged and is a release gate.
- Only scheduled instalments due on the payment date may be paid. Excess/advance payments and full or partial early settlement are rejected with a clear message; unmatched funds/refunds require a separate approved workflow. Staff must not treat rejection as a receipt for money already received.
- No new fee/penalty obligations are assessed or collected by this command. Product fees, permitted costs, and their accrual/financing treatment still need approval and accounting work.
- Receipt entries debit cash/bank/mobile-money **clearing** and credit principal or interest-collections clearing. Disbursement entries debit principal and credit disbursement clearing. These are balanced operational entries, not mapped bank accounts, an interest-accrual model, a general ledger, or channel reconciliation.
- Pricing, weekly date/rate conventions, decimal annuity calculation, agreement versions, and lawful early settlement remain release gates. Stored schedule validation checks cents, dates, positive amounts, and principal conservation; it does not certify the pricing formula.
- Receipts are displayed after commit. Queued receipt delivery, closed accounting periods, reporting reconciliation, collection case management, verified opening imports, and settlement/refund operations are not implemented by this increment.
- Existing analytics outside the updated refresh/risk paths must still be audited against local allocations. Do not assume every report has become ledger-backed.

## Verification

Unit/request tests cover partial/full payments, effective dates, allocation order, retries, duplicate references, invalid amounts, branch/client isolation, permissions, CSRF, confirmation, reversals, refresh results, and local-ledger arrears decisions.

The opt-in PostgreSQL tests migrate an isolated database, validate entity/repository mappings, post/retry/reverse real transactions, race concurrent settlements, enforce immutable history/contract terms and balanced vouchers, and verify rollback on audit failure. They never use `application.yml` credentials.

Run the ordinary suite with `mvn test`. For PostgreSQL verification, create a disposable loopback PostgreSQL database named `microfinance_ledger_test` with user `microfinance_test` and trust authentication on a dedicated port, then run:

```powershell
$env:MICROFINANCE_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55439/microfinance_ledger_test'
mvn '-Dtest=LoanRepaymentLedgerPostgresTest' test
```

Never point that opt-in variable at operational data. The integration fixture intentionally appends synthetic test loans; the database is disposable.

Rendered JSP checks and the final verification results are recorded in the conversion checklist. No 1,000-RPS throughput claim is made.

## Local Icons

Five icons use a 4.4 KB local bundle generated from Lucide 1.49.0. Source: `tools/ui/repayment-icons.js`; output and upstream ISC/MIT notices: `src/main/resources/static/js/vendor/lucide-repayments.*`. No icon CDN is required at runtime.

To regenerate, install exact `lucide@1.49.0` and `esbuild@0.28.2` in a temporary tools directory. Bundle the source with esbuild's `bundle`, `minify`, `format: 'iife'`, `platform: 'browser'`, `target: 'es2020'`, and `legalComments: 'eof'` options, resolving `lucide` to that installed package. Retain the upstream `LICENSE` alongside the generated bundle. The package source and API are documented at [Lucide's vanilla JavaScript guide](https://lucide.dev/guide/lucide/getting-started).
