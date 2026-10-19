# Early repayment, dated settlement and interest concessions

Implemented for the existing local repayment ledger and directly recorded individual/batch receipts. This is an Oracle-inspired servicing increment, not an Oracle integration or certification of an institution's lending policy.

## Defaults and product configuration

From a product's edit page, open **Early repayment rules**. Configuration requires the existing `LOAN_PRODUCTS_CONFIGURE` claim and an institution-scoped workspace administrator. Save retains the policy reference/reason and before/after rules in the permanent audit.

Defaults are `LAST_INSTALMENTS`, `RECALCULATE`, and `ACTUAL_PERIOD`:

- Apply extra principal to the last instalments first. Alternatives are next instalments first and proportional allocation across future principal instalments, with exact cent reconciliation.
- Recalculate unearned interest after partial principal prepayment. A policy-specific alternative retains the schedule until full settlement. It is explicitly labelled and requires a policy reference; software availability does not establish its permissibility for an institution or contract.
- Prorate each fixed contractual interest period using its actual calendar days. The alternative uses 30E/360 day weights within each contractual period. Both retain the contractual period's total interest and rate; neither silently converts an annual rate or reprices a disbursed contract.
- Full settlement always removes remaining unearned interest, for both interest methods. No product setting can switch this off, prohibit principal prepayment, or add an early-repayment penalty.
- Receipts retain due/accrued-interest, supported-charge, then principal priority. Future interest is never recorded as cash received.

The repayment right, allocation priority and exclusion of remaining-period interest follow [BoT Tier 2 regulation 42](https://www.bot.go.tz/Publications/Acts,%20Regulations,%20Circulars,%20Guidelines/Regulations/sw/2020021122490967551.pdf), printed page 19. The precise day weights, principal allocation strategy, and proportional flat-interest rebate are disclosed software conventions, not formulas prescribed by that regulation. The institution remains responsible for its approved policy and agreement disclosures. Existing pricing, fee legality and wider compliance gates remain in force.

Oracle references: [FLEXCUBE prepayment schedule preferences and interest recomputation](https://docs.oracle.com/cd/F58338_01/html/LN/LN04_Prod.htm); [Lending and Leasing contract rebate settings](https://docs.oracle.com/cd/E95814_01/html/Loan_Collection_Setup_Guide/Products.htm); [dated payoff quotes and separate waiver transactions](https://docs.oracle.com/cd/E89525_01/html/Loan_Servicing_User_Guide/Appendix_Transaction_parameters_with_grabs.htm). This increment implements the relevant choices using this application's ledger; it does not copy Oracle's entire product catalogue or its optional penalty rules.

## Calculations and retained contracts

New server-generated financial snapshots contain `prepaymentTerms`, version `PREPAYMENT_V1`. At actual disbursement, V80 retains those accepted terms and the loan's interest method in immutable `loan_prepayment_terms`. Later product edits do not reroute a loan or change its servicing rules. Contractual instalment dates, principal, interest and agreement schedules remain unchanged.

For a period starting on S and ending on D, earned interest before any rebates is contractual period interest multiplied by the elapsed fraction from S to the effective payment date, capped at the whole period. S is disbursement for the first period and the previous contractual due date thereafter. The payment date starts the reduced-balance period; it is excluded from the elapsed period. Interest already paid is subtracted from earned interest. Thus a payment between due dates can owe earned interest even though the next instalment has not fallen due. This is contractual servicing arithmetic, not new GL interest-accrual recognition.

On partial prepayment, only principal allocated ahead of its due date earns a rebate. For reducing balance, each prepaid principal portion reduces the interest periods before that portion's original principal due date, proportionally to the original scheduled opening principal and the remaining time. For flat rate, the ratio is extra principal divided by original loan principal, applied to each remaining period's unearned interest. Rebates are rounded to cents per period and capped at unearned interest. Sequential rebates accumulate without changing already earned interest or contractual interest. On full settlement, exclude every remaining unearned cent regardless of the partial-interest option.

Interest exclusions continue to use immutable repayment transactions/allocations and their constrained `interest_excluded` projection. Each dated exclusion reduces accrual only over its remaining period. Exclusions are not principal payments, cash interest collections or GL income. Linked payment reversals restore both the original cash allocation and exclusions. V80 extends the existing top-up checks to recognize earned interest between due dates while retaining their scope, exact journal, immutable source and independent correction protections.

Individual and batch receipt previews use the same payment calculation as posting. Batch plans retain dated per-instalment allocation evidence, including rebates, so a later receipt for the same loan uses the earlier planned changes. Old plans without this evidence must be revalidated. A posting rechecks the plan under the existing loan locks.

## Settlement and waivers

The standalone settlement page was removed at the user's request. Its old URLs redirect to the existing loan record or repayment details. Authorized staff use the optional **Waive unpaid earned interest** section in those details; waiver history and independent linked reversals remain with the loan. Failed commands retain entered values and return to the same workspace. Product configuration, dated backend calculations and automatic receipt/batch prepayment treatment remain unchanged. Future scheduled interest is not today's settlement amount.

`LOAN_INTEREST_WAIVE` is a new, explicitly assigned claim, shown as **Waive interest** in the access matrix. It is not added to role defaults. A waiver has a positive exact-cent amount limited to unpaid earned interest, an effective date, reason/evidence, scoped idempotency key and permanent audit. It changes neither cash received nor interest paid and posts no invented cash or accrual journal. The current accounting model recognizes interest on collection; a later GL accrual/recognition increment will require its own waiver posting matrix.

Waivers and parts are append-only. A different authorized officer with both the waiver and existing correction claim can reverse a waiver. Later unreversed payments/waivers must be corrected first. Waiver dates participate in subsequent payment/date guards; closed accounting periods cannot receive a new waiver. Database constraints reconcile waiver totals, parts and projections and prevent cross-loan/branch evidence. Operational portfolio/management reports subtract dated waiver evidence, rather than counting it as collected interest.

## Existing loans and migration

V80 does not backfill servicing rules or rewrite old receipts, exclusions, journals or agreements. Loans without retained terms continue their original servicing conventions, retained in the loan's servicing evidence. An authorized waiver officer can explicitly adopt the default rules, with agreed-change evidence, only on an active ledger with no prior payment, waiver or adjustment sequence. Loans with existing activity require a separate reviewed conversion; do not silently reinterpret their cash history. Existing V79 full early-settlement exclusions remain supported.

No new fees, refunds, unapplied credit, overdue penalty accrual, automatic interest-rate amendments or arbitrary retrospective payment insertion are introduced. Quotes support today through one year ahead; postings use verified effective dates and the established chronological safeguards. Institution/branch scope, ownership, CSRF, accounting periods, request idempotency, loan locks and independent linked corrections remain enforced.

## Verification

Focused decimal tests and disposable PostgreSQL tests exercise actual-period/30E/360 dates, all principal strategies, flat/reducing rebates, mid-period settlement, sequential dated batch payments, immutable contracts, waived-versus-paid amounts, request retries, branch isolation and independent reversals. Executed verification: `mvn -DskipTests compile` passed; 125 focused JUnit checks (including disposable PostgreSQL, batch posting, concurrency and JSP compilation) passed. The final Maven calculation/controller/JSP selection also passed. Browser checks covered both new screens in English/Kiswahili at 360, 768, 1366 and 1920 pixels, a synthetic waiver changing only earned interest, and login/logout. The broad current-source test run was not green: existing date/UI contract failures remain; unrelated UI and date-format expectations were not rewritten by this increment. No throughput benchmark is implied; calculations and per-loan schedules are bounded to 600 instalments and registries/history remain server-paginated.

2026-10-19 page removal verification: 22 focused controller, model-advice and JSP checks passed. Browser verification on a disposable loan covered old-link redirection, a successful waiver, rejected excessive waiver with retained values, and English/Kiswahili layouts at 360/768/1366/1920 pixels. The packaged artifact excludes the retired JSP. No repayment calculations, permissions, migrations or posted history were removed.
