# Local startup migration compatibility

On 2026-10-18, the configured database had applied the original V69 checksum `-1591283630`; subsequent edits to V69 blocked normal Flyway validation. V69 now retains that original definition and checksum. V75 applies the corrected deferred adjustment validator and repayment-date guard as a new migration. It preserves V71 charge-aware reversal checks and the V70/V71 top-up, adjustment and charge source guards. No schema-history repair, disabled validation or financial-row rewrite is used.

The opt-in `LoanAdjustmentMigrationPostgresTest` upgrades the original V69 to V75, verifies that V69's checksum is unchanged, validates all 95 migrations, and checks that the later source protections remain installed. Fresh-database lending adjustment, charge correction and top-up regressions cover the resulting behavior.

Verification: Java 25 compiled all 378 application sources. Eleven focused PostgreSQL tests passed with no failures or skips, covering the upgrade plus provisions, allowance changes, independent review, write-off/write-back, linked disbursement corrections, audit rollback, closed periods, concurrency, backdating, charge corrections and top-up corrections. A clean database applied all 95 migrations; the configured local database validated and upgraded normally from V69 to V75. The full repository suite and load tests were not run.

Development databases that previously applied the edited V69 checksum require separate history/schema reconciliation. This fix supports the originally applied V69 and fresh installations; it does not silently repair other migration histories.
