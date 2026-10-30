# Staff client access

An institution administrator with the explicitly assigned `USER_ACCESS_UPDATE` permission can open **Users → select user → Edit → Client access → Enable client access** for active staff in their assigned institution and branch. Invited, inactive, pending-acknowledgement and platform administrator accounts are excluded. An institution administrator may enable their own client access; independent client verification and lending decisions still apply.

The command adds client access to the existing person. It assigns a `CL-` client number from the existing database sequence, keeps the UUID, staff number, roles, access state, credentials and exact effective staff permissions, and records the old/new identity in `STAFF_CLIENT_ACCESS_ENABLED`. It creates no duplicate person, deposit account, financial posting or verification evidence. Existing client numbers remain unchanged on retry.

The person signs out and uses **Client login** with their existing email/username and password. Client login carries client permissions and no staff roles. The existing onboarding workspace requires the normal evidence, declarations and independent verification before lending access. Staff login continues to use its assigned permissions.

Manager, accountant, loan officer, board, credit committee and chairperson decisions cannot be made by the borrower. Both ordinary and recorded-loan disbursement reject the borrower as the posting officer. Automatic loan officer assignment skips the borrower. A configured review panel containing the borrower stops routing with an actionable error; it never silently removes a required vote. An authorized administrator must arrange independent reviewers through the existing product configuration. Frozen pending workflows retain their existing revision and safeguards.

The service checks the authenticated administrator's current assignment and permission, scopes the target lookup by institution and branch, and locks the target person before loading the administrator identity. A short transaction covers identity, settings, unchanged staff claims and audit. Concurrent retries produce one client number and one event; failure rolls the complete change back. There are no new migrations, permission defaults, remote calls or executors.

Verification covers explicit permission/CSRF checks, record scope, repeated and concurrent requests, rollback, separate login contexts, unverified onboarding, own-loan decisions, disbursement and preserved review panels. JSP rendering is checked in English and Kiswahili. This increment does not establish institution policy eligibility for staff lending or prove a 1,000 requests/second capacity.

## Verification — 30 October 2026

- Compilation and 13 affected test suites: **101 tests passed**, no failures, errors or skipped tests across the focused runs. The full repository suite was not run.
- Disposable PostgreSQL 17: all migrations and repository queries initialized; six concurrent conversions of one staff member and four concurrent administrator self-conversions each retained one client number and one audit event. Explicit staff claims survived, client login excluded staff powers, and onboarding remained `NOT_STARTED`. A rolled-back conversion left the original account and no conversion event.
- MockMvc checked authenticated actor binding, ignored submitted scope fields, required update permission and CSRF. Service tests rejected stale scope, inactive/invited/pending accounts and platform identities. Ordinary and recorded-loan posting tests blocked borrower disbursement before ledger writes.
- The actual JSP fragment rendered through an isolated Tomcat/Jasper fixture in English and Kiswahili, with the record-specific POST destination and CSRF field; ineligible accounts had no action. The fixture uses explicit Jasper initialization and cannot bootstrap the application.
- Browser visual and mobile checks remain outstanding because browser control is disabled in Work settings. Production adds no executors, external requests, schema changes or unbounded lists; throughput remains unmeasured.

Repeat the affected suites with `MICROFINANCE_REGISTRATION_TEST_URL` pointing to a disposable loopback PostgreSQL database named `microfinance_registration_test`:

```powershell
mvn "-Dmaven.compiler.useIncrementalCompilation=false" "-Dtest=StaffClient*Test,StaffLoanDisbursementTest,ManagerServiceTest,BoardServiceTest,WorkflowRoutingServiceTest,MemberRegistrationServiceTest,UserClaimServiceTest,ClientOnboardingAccessMvcTest,AdminControllerUserAccessSecurityTest,AdminUsersJspLayoutTest" test
```

The existing `AdminController → AdminService → MemberRepository` path owns the mutation; `UserClaimService` preserves existing assignments. Changed UI files are the user edit page, its client-access fragment, shared error localization and both message bundles. Loan safeguards reside in `ManagerService`, `BoardService`, `WorkflowRoutingService` and `LoanRecordingService`. The added tests and this document cover the complete increment.
