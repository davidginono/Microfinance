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
