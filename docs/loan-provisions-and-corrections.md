# Loan provisions, write-offs and disbursement corrections

Implemented increment: 2026-10-16. These are dedicated lending adjustments, not additional approval stages on core receipt, payment or journal vouchers.

## Reference inspection

The user authorized read-only inspection of FERPS. Its authenticated navigation and forms were inspected at:

- `home.php?write_off_loan&sub_module_id=182`: client and loan selection, loan balances, instalment principal/interest and selection, followed by a proceed control.
- `home.php?approve_write_off_loan&sub_module_id=183`: a separate approval register with loan reference, client, product, approved amount, period, amount written off, initiating staff/date/reason, Approve and Reject. The account had no pending rows.
- `home.php?recover_written_off_loan&sub_module_id=184`: a separate written-off-loan recovery/write-back register. The account had no rows, so its later form and financial effects could not be observed.
- `home.php?reverse_loan&sub_module_id=166`: searchable disbursement register with tranche amount/date/staff, payment method, credited account, reference, receipt and Reverse headings. Tranche rows were absent in the inspected account.

No FERPS write-off, approval, recovery, reversal or other financial mutation was submitted. Credentials and client data are not stored in this repository. FERPS is a workflow reference, not an authoritative policy or compliance source. This increment adapts the observed separation of requests, review and source corrections to this application's verified sources and shared JSP interface; it does not claim to reproduce unobservable FERPS posting rules.

The Bank of Tanzania's currently published [2019 Mainland Tier 2 regulations](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/en/2020021122490967551.pdf), checked on 2026-10-16, place write-off approval/authorization criteria in the institution's lending policy (37(2)(p)) and distinguish loan classification/provisioning (45). No institutional policy, eligibility decision, regulatory return, or impairment methodology is approved by this code. Existing regulatory reference estimates remain separate from actual posted allowances.

## Supported workflow

Open **Accountant Office â†’ Loan provisions and corrections**, `/finance/loan-adjustments`. Staff select a verified recorded loan and adjustment type, review its current principal, allowance and write-off balances, enter effective date, reason and policy/evidence reference, then submit for independent review. The detail page shows the exact debit, credit and signed balance effects. Approval posts once in a single transaction; rejection moves no money. Registers use scoped, bounded server filtering/sorting/paging and shared single-row selection. Documents are PDF/print only.

| Operation | Posting | Contractual effect |
| --- | --- | --- |
| Provision | Increase: debit impairment expense, credit allowance control. Decrease: reverse this direction. Only the delta to the entered target allowance is posted. | No payment or debt reduction. |
| Full principal write-off | Debit the loan's exact posted allowance, credit its original principal control. The remaining principal must be fully provided. | Written off is distinct from Paid. Contract, principal paid, interest paid, instalments and payment history remain intact. No debt forgiveness or posting of future scheduled interest. |
| Write-back | Exact linked reversal of the write-off: debit original principal control, credit allowance. | Returns to Defaulted for reassessment and collection. No cash receipt is fabricated. Reassess/release the allowance as appropriate; record actual recoveries through existing due-date-aware loan repayment receipts. |
| Full disbursement reversal | Exact linked reversal of the original disbursement accounts. | Terminal Disbursement reversed state; no repayment mutation or redisbursement on that loan. Original contract, schedule, number and disbursement evidence remain. A new loan requires a new application/review. |

Disbursement reversal requires no repayment history, including previously reversed payments, and no remaining allowance/write-off. The original posting officer can request a correction but cannot approve its disbursement reversal or write-back. Staff must supply evidence that funds do not remain with the client; the system does not claim to recover funds from a bank or payment channel.

All types require a different authorized reviewer. These dedicated lending adjustments do not alter direct accountant posting of ordinary core vouchers or existing product review paths.

## Source integrity and security

- V69 adds immutable requests and immutable decisions. Approved decisions own their GL journal and outbox. Original GL journals, loan repayment transactions and allocations are never edited or deleted.
- The request freezes remaining principal, posted allowance, written-off principal, repayment sequence and latest adjustment. Approval compares these under the same per-record/per-ledger lock order used by receipts; a changed assessment must be rejected and resubmitted. A pending request does not prevent receiving ordinary money.
- Audit events use the protected `ACCOUNTING_LOAN_ADJUSTMENT` category and remain excluded from routine operational audit cleanup.
- Scoped request keys and decision keys protect identical retries, changed payloads and concurrent approvals. Audit failure rolls back the decision, journal, outbox and status together.
- The existing GL validator is extended at an explicit `LOAN_ADJUSTMENT` source boundary. Database checks enforce period, scope, independent staff, exact account directions/amounts, snapshot freshness, reversal linkage, balance and matching final loan state. Generic vouchers still cannot post to loan principal or allowance control accounts.
- Repayment insertion rejects written-off/reversed loans and dates earlier than a posted adjustment. Write-back is required before ordinary collection resumes. Current staff claims and institution/branch activation are rechecked by the accounting guard on reads, writes and exports.
- New permissions: `LOAN_ADJUSTMENTS_VIEW`, `LOAN_ADJUSTMENTS_CREATE`, `LOAN_ADJUSTMENTS_APPROVE`, `LOAN_ADJUSTMENTS_EXPORT`. No grants or seeded role rules are changed. An administrator must assign the appropriate permissions through the existing access matrix.

## Balances and coverage

The adjustment detail distinguishes contractual principal outstanding, principal on the books before allowance, posted allowance, and written-off principal. Written-off contractual debt remains in borrower/guarantor risk checks. Reversed disbursements show no current debt, cannot receive money, and are not reactivated by projection refresh.

Portfolio sources apply approved principal adjustments using effective date and recorded cutoff. Write-off/write-back postings do not masquerade as collections. The portfolio displays posted allowance and net write-offs from these controlled sources, with its existing coverage limitations. GL-based statements, trial balances and ledger drill-downs include the actual posted journals. Cashbook includes the offsetting cash/bank entry from a disbursement reversal.

Scope is verified, ordinary full disbursements owned by `accountant_loan_record`/`accountant_loan_post`. Legacy openings, top-ups, tranches, partial instalment write-offs, accrued-interest/fee impairments, debt forgiveness, direct off-book cash recovery, early settlement and channel reconciliation are separate work. They are not inferred from FERPS or from a missing balance. Write-back followed by an ordinary due-date-aware receipt supports the existing collection engine; it does not add advance or settlement allocation rules.

This increment does not complete the broader accounting/report-builder checklist or claim measured 1,000 requests/second throughput. Validation evidence is recorded after verification below.


## Verification — 2026-10-16

- Java 25 compiled the application and test sources. A clean disposable PostgreSQL 17 database applied and validated all 88 migrations, including V69, without repair or disabled financial triggers.
- The 47-test PostgreSQL loan-recording regression passed, including provisions, releases, full/remaining-principal write-offs, write-back, recovery receipts, independent review, branch scope, revoked claims, idempotency, concurrent approval, period protection, tampering rejection and atomic audit rollback. The final audit-category/date-boundary recheck is recorded below.
- An exact-loan `EXPLAIN (ANALYZE,BUFFERS)` of the adjustment balance view used keyed joins and completed in 2.160 ms on the small synthetic fixture (planning 54.242 ms). This is a query review, not a production throughput benchmark.
- English/Kiswahili static and dynamic adjustment labels were checked against both message bundles.
- Local browser use was disabled in Work settings at the final visual-check step. No desktop/mobile visual pass is claimed. JSP compilation and MVC/security checks provide automated coverage; live visual verification remains outstanding. The temporary synthetic preview harness and PostgreSQL cluster are outside the repository.
- A broad suite was started, then stopped to apply the borrower-display follow-up. The completed focused runs below, rather than that interrupted run, are the verification evidence.

- Final focused verification: 191 selected tests passed across the completed final run and the 69-test expectation-correction rerun (0 failures/errors in the latest results for every selected class). This includes eight fresh-database financial cases, four new adjustment MVC/security cases, the eight affected JSPs compiled by Jasper, portfolio controllers, repayment commands/controllers, borrowing/guarantor workflow, debt displays, login CSRF/isolation, keepalive and access control. The three rerun classes only updated expectations to include written-off contractual debt and assert the borrower label on the borrower row; production code was unchanged between those runs.
- Financial audit events retain the protected accounting category; the fresh-database audit rollback case passed after that change. Payments dated before a posted provision produce the localized service validation error; the targeted date-boundary case passed.
- No complete-suite pass or live desktop/mobile visual pass is claimed. All selected tests ran without skips. No production data or FERPS financial records were mutated, no permission grants were seeded, and no commit was pushed.
