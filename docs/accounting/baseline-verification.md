# Baseline verification

Date: 2026-10-02. Application baseline: `db8f5c2` (before new accounting implementation).

| Command | Result | Scope |
| --- | --- | --- |
| `mvn test` | PASS: 704 tests, 0 failures, 0 errors, 5 skipped; 3m38s | Existing unit, request, security, JSP contract and workflow protections; database suite is opt-in. |
| `mvn -Dtest=LoanRepaymentLedgerPostgresTest test` with dedicated `MICROFINANCE_TEST_DATABASE_URL` | PASS: 5 tests, 0 failures, 0 errors, 0 skipped; 53.771s | All existing migrations, JPA/repository validation, real post/retry/reversal, concurrent settlement, rollback on audit failure, immutable history, balanced vouchers. |

Environment: Windows 11 amd64, Maven 3.9.9, Oracle Java 25.0.2, PostgreSQL 17.9, loopback port 55439, freshly initialized disposable `microfinance_ledger_test` database, test-only user `microfinance_test`. The PostgreSQL fixture never uses application datasource credentials. Synthetic records only; no operational data was changed.

These results establish the starting point. They do not verify any new accounting package, browser responsiveness, recovery procedure, accountant acceptance or throughput. Subsequent evidence must identify the integrated implementation commit and test scope.
