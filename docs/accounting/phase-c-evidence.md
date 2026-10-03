# Business accounting implementation evidence

2026-10-02. This is an increment, not completion of C01–C10 or live accounting authorization. No accountant-approved institution policy/openings are available; reviewed synthetic fixtures are confined to loopback test databases.

## Verified baseline

The dedicated C worktree retains source documents, independent maker/checker actions, scoped idempotency, immutable posted history, supplier/payable and funding controls, owner capital/distributions, unapplied-funds refunds, fixed assets/depreciation/disposal, prepayment/accrual/tax entries and balancing branch transfers. Unsupported advanced loan treatments fail closed.

Coordinator resumed the saved implementation after three attempts to resume the existing worker were rejected by the agent runtime. Verified A/B/D/E/F/H prerequisites were merged without discarding C source work; the explicit scoped backup stash remains retained.

Command: mvn '-Dtest=BusinessAccountingValidationTest,BusinessAccountingPostgresTest,LoanRepaymentLedgerServiceTest' test. Result: **31 passed, zero failures/errors/skips**, finished 2026-10-02 16:09:14 EAT (5:03). Ten scenarios used actual PostgreSQL; two validate typed commands, nineteen protect existing ordinary repayment behavior. Normal strict Flyway validated 50 migrations on microfinance_accounting_c_test_baseline_20261002. No out-of-order configuration was used. Bounded Hikari/loopback SSL settings are test-only.

An earlier 31-scenario run failed because the direct-expense fixture created no control entries and its DELETE therefore affected no rows. The regression now creates an invoice and reversal, verifies two actual retained control entries, zero expense/payable/control balance, and database rejection of DELETE/UPDATE. The subsequent full focused run passed.

Real PostgreSQL scenarios include partial invoice payments and credits without double expense, concurrent payments serialized against one obligation, funding principal/interest separation, unapplied-money liability/refund, asset-register preservation, reversal immutability, atomic audit rollback, active branch/current-claim enforcement. Manager/repayment collaborators are mocked in these source tests; they do not prove actual source-to-loan-subledger agreement.

## Remaining required implementation and integrated verification

- Connect D's mandatory linked source reversal/cancellation APIs so rejected unposted sources cannot leave orphan GL drafts and corrections link the original journal.
- Connect H's approved live-release leaf gate in the owning transaction, preserving period then release lock order.
- Prove real ordinary disbursement/repayment/reversal plus operational voucher bridging atomically against actual repositories. Do not double-post historic clearing entries or mistake mocks for evidence.
- Implement approved historical source controls/opening coverage with immutable provenance; a zero movement sum does not establish no legacy obligations. Freeze C coverage IDs/hashes into close evidence.
- Complete approved fees, advances/applications, partial/full settlement, top-up/internal settlement, restructuring, impairment/write-offs/recoveries and their preserved contractual history. Contractual schedules do not establish accounting recognition.
- Verify branch-transfer eliminations/shared allocations, populated UI in both languages/mobile sizes and all financial concurrency/reconciliation cases.
- Add source/history deletion guards after the final authoritative ports exist.

No unchecked requirement is considered complete because this baseline compiles or its focused tests pass.

Retention follow-up command: mvn '-Dtest=SaccoDataDeletionServiceTest,BusinessAccountingValidationTest,BusinessAccountingPostgresTest,LoanRepaymentLedgerServiceTest' test. **48 passed, zero failures/errors/skips**, finished16:29:43 EAT (8:21); includes12 actual PostgreSQL cases and15 early deletion guards. Separate supplier-only and draft/rejected source-only history now protects institution and maker/checker references before file/row effects, even without a GL journal. This proves retention queries and guard boundaries, not source-to-loan voucher reconciliation.

## Verified source corrections — 2026-10-03

Source rejection now commits an immutable general-ledger cancellation with the independently checked business document; repeated requests must preserve reviewer and evidence. Corrected replacements can reuse a cancelled money reference while the original remains retained. Business and loan-repayment reversals use the general-ledger source-reversal path and preserve the original receipt date; a later correction carries its own explicit effective date. Supplier evidence and asset acquisition basis are immutable, and deferred database checks reconcile posted asset facts and source/receipt relationships. Migration: `V44_1__business_source_correction_guards.sql`.

Terminal command: `mvn -Dtest=SaccoDataDeletionServiceTest,BusinessAccountingValidationTest,BusinessAccountingPostgresTest,LoanRepaymentLedgerServiceTest test`, with `MICROFINANCE_ACCOUNTING_C_DATABASE_URL=jdbc:postgresql://127.0.0.1:55439/microfinance_accounting_c_test_source_corrections_20261003` and a 768 MiB Maven heap. Fresh ordered schema applied 53 migrations. The final run finished 2026-10-03 20:54:17 +03:00: **59 tests, zero failures/errors/skips** (19 PostgreSQL, 2 business validation, 21 repayment service regression, 17 financial-history deletion guards). Log: `%TEMP%/microfinance-accounting-c-source-corrections-final-20261003.log`. The earlier 59-case run had one test-fixture assertion that forgot the reviewed opening capital balance; the corrected assertion captures the opening balance before replacement and the failed log remains retained. No accounting engine failure was suppressed.

PostgreSQL evidence includes concurrent repeated rejection, cancellation rollback on audit failure, direct SQL rejection without a cancellation denied, reversal reservation replacement, money-reference replacement, and supplier/asset tamper protection. The PostgreSQL business fixture still mocks the ordinary manager/repayment collaborators; these tests do **not** establish actual loan-subledger/control-account agreement. Explicit correction-date repayment tests use service unit fixtures. Actual owning loan/GL bridge, typed reviewed source openings, live-release activation, advanced settlement/provision/recovery paths, and rendered acceptance remain separate unfinished gates. No real institution policy, opening balance, or release approval is claimed.


## 2026-10-03: live-release gate for owned source posting

Business source approval now requires the exact currently approved local-policy release before any posting mutation. The owner uses READ_COMMITTED, takes the shared GL setup advisory lock, and pins all preceding/target accounting periods before entering the mandatory release leaf. Missing, withdrawn, or invalidated acceptance blocks new money; draft, submission, rejection and retained exact posted retries remain available. The submitted journal must still carry the same policy ID/version and exact recalculated lines. Account deactivation takes the matching exclusive setup lock.

Verified command: `mvn -o -Dtest=BusinessAccountingPostgresTest,BusinessAccountingValidationTest,LoanRepaymentLedgerServiceTest,SaccoDataDeletionServiceTest test` with the opt-in PostgreSQL URL restricted to `jdbc:postgresql://127.0.0.1:55439/microfinance_accounting_c_test_release_gate_20261003`, user `microfinance_test`, disposable synthetic database only. Final log: `%TEMP%/microfinance-accounting-c-live-release-gate-final-20261003.log`. Terminal BUILD SUCCESS at 2026-10-03 22:00:11+03:00: **63 tests, zero failures/errors/skips**, comprising 23 actual PostgreSQL cases, 2 business validations, 21 repayment-service cases and 17 deletion guards; 340 main and 158 test sources compiled, 57 migrations validated in normal order.

Four new PostgreSQL cases cover the actual empty release leaf with rollback, setup workflows before activation, controlled withdrawal versus retained exact retry, and setup/preceding/target locks held before the release check. The last test verifies exact PostgreSQL SQLSTATE `55P03` for competing metadata/period changes; unrelated SQL failures do not count as blocking. The initial run (63 tests, one fixture error) is retained in `%TEMP%/microfinance-accounting-c-live-release-gate-20261003.log`: Spring 4.1 translated the expected lock timeout as UncategorizedSQLException, so the fixture now checks the actual SQLSTATE. Positive/withdrawal acceptance remains a controlled leaf fixture; this evidence does not claim a complete actual H human-review chain.

Remaining: truthful release-restriction controller rendering, actual ordinary loan/clearing/GL source bridge, typed reviewed source openings and reconciliation provider, advanced settlement/fees/refunds/impairment and branch allocations, full bilingual rendered workflows and integrated release acceptance. No institutional accountant or compliance approval is asserted.
