# Phase B: integrated local general ledger

Implementation date: 2026-10-02. Worktree: `accounting-b-ledger/Microfinance`. This is implementation evidence, not institution accountant approval or live opening data.

## Implemented boundaries

- B01/B03: institution-owned unique account codes, types and normal balances, immutable heading/posting/control hierarchy, inactive state instead of deletion. New parents must already exist in the same institution and be headings of the same type; immutable references and immediate foreign keys prohibit cycles. Journals retain first-class institution/branch dimensions independent of later borrower moves.
- B02: explicit account-purpose catalog covers cash, bank, mobile money, clearing/suspense, loans/interest/fees/allowances, payables, institutional funding, owner capital, recognized income, expenses, fixed assets, prepayments, tax and internal transfers. No account codes or balances are seeded. The actual chart remains an accountant decision.
- B04/B05: immutable payload/lines with DRAFT → APPROVED → POSTED transitions, effective and recorded timestamps, TZS, policy ID/version, source/evidence/staff references. Reversed presentation is derived from a posted linked reversal, preserving original POSTED rows. Services and PostgreSQL enforce scoped valid accounts, exact decimal precision (16 whole digits, two decimal places), one positive side per line, balancing and an open accounting period.
- B06: scoped advisory request locks, unique source/request keys and canonical SHA-256 payload checks serialize exact retries. Database period row SHARE locks prevent posting across concurrent close operations. Posting, existing audit persistence and dedicated accounting outbox insert share one transaction; failures roll back all of them. The existing notification outbox swallows some failures and is deliberately not used for financial durability.
- B07: independently made/checked linked manual-journal reversals exactly reverse original lines. Generic manual journals cannot use control accounts; loan principal/interest/fee/allowance purposes must be control accounts. Generic GL-only reversals of operational or loan sources are blocked; they require source-specific atomic correction workflows in Phase C.
- B08: bounded code/debit/credit import, preview, balanced immutable draft, duplicate controls, independent approval evidence, and confirmed posting of reviewed opening balances. Checker reconciliation evidence is stored separately from maker evidence and retained in immutable branch cutover coverage. No legacy repayment is invented or unexplained plug inserted.
- B09: bounded operational voucher bridging uses approved immutable account mappings and retains original clearing journals. Source-voucher uniqueness and source payload checks block duplicate bridging. Entries through the inclusive opening date belong to reviewed cutover evidence; ordinary movements must follow that date.
- B10: branch coverage counts unbridged source vouchers and loans lacking local ledger history; incomplete records are explicitly non-authoritative. Reviewed complete cutover evidence is required to cover legacy history. Missing balances are not auto-created.
- B11: external official books are gated by the policy service. This local implementation does not claim external acknowledged delivery.

## Staff workflows

`/finance/accounts` supports bounded chart browsing, account creation/deactivation and creation of approved non-overlapping periods. `/finance/journals` supports scoped history, opening/manual import preview and draft creation. `/finance/journals/{id}` shows account codes, amounts and supporting evidence with independent approval, confirmed posting and linked manual correction. Forms retain CSRF and all services recheck current staff access, explicit claims, institution and branch. English/Kiswahili messages and the existing ERP shell are reused; page/table widths are bounded by shared responsive controls.

## Verification

- `mvn -DskipTests compile`: passed.
- `MICROFINANCE_ACCOUNTING_B_DATABASE_URL=jdbc:postgresql://127.0.0.1:55439/microfinance_accounting_b_test` with `mvn -Dtest=GeneralLedgerValidationTest,GeneralLedgerPostgresTest test`: 12 passed (8 actual PostgreSQL, 4 unit), no skips. All V1–V43 migrations applied to the dedicated synthetic database; no operational credentials/data used.
- PostgreSQL checks exercised independently reviewed opening posting, exact/changed retries, concurrent identical requests, original/linked reversal totals, append-only enforcement, decimal rejection, audit failure rollback, closing-versus-posting row locks, branch isolation and revoked claims. One first-run assertion was corrected to expect the deferred constraint's JDBC commit exception; the database rejected the invalid transaction as required.
- `mvn -Dtest=GeneralLedgerPostgresTest#databaseRejectsUnbalancedPostingEvenWithOutboxAndReviewedOpening+operationalBridgePreservesSourceAndUsesApprovedMappingsExactlyOnce test`: 2 additional PostgreSQL tests passed, no skips. Verified deferred balance rejection rolls back outbox/state and a mapped operational bridge preserves its source and posts exactly once. Total focused coverage: 14 passing scenarios (10 PostgreSQL, 4 unit).
- Browser rendering at 360/768/1366/1920, both languages, full integrated suite/package, recoverability and measured performance remain Phase H verification. No throughput or live-release claim is made.

## Remaining approvals and dependencies

The user confirmed approved policies, posting matrix, actual chart and verified openings are unavailable. All monetary posting is therefore restricted until those explicit independently reviewed records exist. The software can prepare/validate evidence; it does not certify that human approval evidence is true. No institution complete opening or approval was seeded. Source-specific accounting and subledger coordination, reconciliations and closing snapshots, final financial statements and report publication remain dependent phases. Institution-wide totals require their own approved scope; this UI and history remain branch scoped.

`GeneralLedgerService.hasInstitutionHistory(String)` and `hasMemberHistory(UUID)` let deletion workflows stop before filesystem/database side effects and deactivate access instead. Accounting audit namespaces are retained financial history. Financial source APIs create drafts inside a mandatory source transaction; Phase C must complete trustworthy source approval and atomic posting rather than expose arbitrary source types to callers.
