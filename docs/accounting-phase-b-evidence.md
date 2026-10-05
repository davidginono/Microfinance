# Phase B evidence and remaining gates

This increment supplies software controls. It does not supply an institution's approved chart, policy, historical opening balances, or cutover sign-off. The user confirmed those inputs are unavailable; accounting activation must remain gated. No account, journal, policy, opening, or new permission is seeded.

## Implemented surfaces

- Institution-owned chart with headings, posting/control distinctions, readable purpose categories, scoped parent links, immutable classifications and deactivation.
- Branch-scoped 25-row journal and opening registries, record review/post/reversal screens, CSRF forms, exact-cent bounded imports, English/Kiswahili message keys and existing responsive ERP shell.
- Immutable drafts, independent approval, posted originals and exact linked reversals; manual journals cannot change loan control accounts. Scoped request and canonical source uniqueness reject conflicting retries, including changed source checker.
- Approved local policy and exact version/hash pins. Initial opening alone may use the first policy's approved opening cutoff before its effective date; ordinary sources require applicable policy and a date strictly after the reviewed end-of-date boundary.
- Positive balanced monetary journals; an explicit independently reviewed zero-opening certificate creates no invented balancing transaction. Missing legacy loan history blocks cutover. Opening loan principal must reconcile to the dated loan subledger.
- Database tenant composite foreign keys, hierarchy/immutability/approval/state guards, deferred journal balance, source/request uniqueness, period `FOR SHARE` lock, audit and transactional outbox. Closing must use `FOR UPDATE` on the same period row.
- Controlled post-cutoff legacy operational voucher mapping against approved GL codes. The actual immutable source voucher identity is retained; historical operational entries are never changed.
- Internal `MANDATORY`-transaction source posting and correction APIs for phase C, with no generic source HTTP endpoint. EXTERNAL authority remains rejected until a separately approved acknowledged integration exists.

## Verification state

On 2026-10-05, `mvn '-Dtest=AccountingLedgerServiceTest,AccountingLedgerPageControllerTest,AccountingLedgerPostgresTest' test` completed successfully: **20 tests, zero failures/errors/skips** (6 unit, 3 MVC, 11 actual PostgreSQL). Main and test sources compiled with Java 25. PostgreSQL 17.9 validated 44 migrations and upgraded the preserved dedicated disposable database from V43 to V43.1.

The prior 17-test run had 7 passing unit/MVC tests and 10 PostgreSQL fixture errors: the deferred balance function's SQL CASE referenced a line-only field on the journal trigger record. V43.1 fixes record dispatch with an explicit conditional; applied V43 was preserved, with no schema reset, financial history edits or Flyway repair. The successful rerun verifies real balanced opening/post/source/reversal transactions, zero certification, source correction lookups, caller rollback, changed checker/key retries, concurrent duplicate source posting, immutability, and period-close-versus-post behavior. Direct unbalanced approval fails at commit with the expected database balance error.

Rendered desktop/mobile English/Kiswahili browser checks have not yet run. MVC tests verify claim/CSRF controls, server-derived scope and safe errors but do not compile JSP views. No throughput benchmark or 1,000-RPS claim is made. Java PREPAYMENT and EXPENSE_ACCRUAL classification/role support is present; EXPENSE_ACCRUAL database creation remains gated on phase C's additive V44 category/guard extension and must be verified with that migration during integrated checks.

Focused fixtures are explicitly synthetic, in a dedicated loopback disposable PostgreSQL database. They exercise exact cents/high amounts, changed retries, maker/checker, manual control denial, scoped reads, mutable history rejection, caller rollback, concurrent same-source posting, close-versus-post, genuine zero certificate and cutover protection. Production datasource credentials are not loaded by these fixtures.

## Acceptance boundaries

B01/B03-B07 and the implemented software portions of B08-B10 passed the focused checks above. B02 actual institution account definitions and B08/B09 reviewed opening/cutover reconciliation remain human gates. Phase C's separately reviewed legacy loan opening/settlement workflow must supply verified per-loan balance coverage; a GL opening alone cannot make a missing loan ledger known. Legacy per-loan balances are never reconstructed from financial JSON/status snapshots; unknown records stay unavailable. B11 is gated because no external GL was selected or integration credentials/approval supplied. Full phase-B acceptance remains open until approved inputs, integrated C/D/E reconciliation and rendered release verification exist.

The period creation advisory lock is limited to infrequent institution configuration. Source idempotency locks are scoped per branch/request/source; normal posting shares the period lock and account read locks, with no global posting mutex. Lists use indexed bounded queries. Import size is at most 64,000 characters and 200 lines; opening/chart mutations hold no network/file operations under locks.
