# Release and retained-history integration verification

2026-10-03. Root integrated H feature commit `accc378` as `71459a9` and its whitespace follow-up as `bf8e15b`, preserving the already integrated E cash-flow claims and bilingual resources. C independently checked correction commit `dfb4b36` was integrated as `7f28a0e`; its exact 59-case PostgreSQL/regression evidence remains in `phase-c-evidence.md`.

Cash-flow classification history and statement-file/release history now block institution and former-staff deletion before any filesystem or database deletion. The release history leaf includes statement-file generation/review, proposals, independent decisions, and withdrawal/invalidation actors. Existing append-only records remain retained.

Terminal root command: `mvn -Dtest=SaccoDataDeletionServiceTest,MemberLocaleInterceptorTest,UserClaimTest,AccessControlServiceTest,UserClaimServiceTest test`, Maven heap 768 MiB. Finished **2026-10-03 21:12:37 +03:00**, **52 tests, zero failures/errors/skips**, 2:13 elapsed: 21 retained-history deletion guards, 6 locale interceptor checks, 8 claim-definition checks, 10 access-control checks, and 7 current-user claim checks. Log: `%TEMP%/microfinance-accounting-integrated-release-retention-20261003.log`. This focused command selects no PostgreSQL suites and is not a full regression/package result.

The institution has not supplied approved policies, account mappings, verified openings, or release acceptance. Software approval workflows do not constitute those approvals. Live source-posting gate integration, real loan/subledger/control agreement, typed reviewed source coverage, institution statement aggregation, rendered/print acceptance, full Maven regression/package, and representative concurrency remain unfinished gates.
