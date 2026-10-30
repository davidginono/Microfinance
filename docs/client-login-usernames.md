# Client login usernames

Client and staff password login and password recovery accept an email address or username. Client
numbers remain unchanged business identifiers; they are no longer looked up as login
credentials. Staff numbers remain business identifiers and are no longer login credentials.
The separate System Admin ID keeps its existing login flow.
Email OTP and Google sign-in retain their existing account and scope checks.

Self-registration requires a username of 3–32 ASCII characters, starting with a letter,
followed by letters, digits, dots, underscores or hyphens. Values are stored in lowercase.
Usernames are globally unique because public login does not require selecting an institution.

The registration page checks availability 350 milliseconds after typing stops. Invalid or
empty input sends no request. Changing the value cancels the previous request and ignores
stale responses. A failed check is shown as unavailable information. The endpoint returns
only a boolean with `Cache-Control: no-store`; it does not return account or institution data.
The service checks again before issuing a registration OTP and when saving. A unique
database index resolves simultaneous claims, returning the losing request to the username
field without creating a second account. Availability never reserves a username.

Migration V137 assigns usernames to existing user records using sanitized email prefixes.
Short or non-letter prefixes receive a `user_` prefix; records without an email start with
`user`. Collisions receive `_2`, `_3`, and subsequent suffixes, including collisions with
email prefixes that already contain such suffixes. The migration changes only the new
username column; it preserves IDs, client/staff numbers, passwords, roles and access flags.
It does not enable inactive accounts or grant permissions. Pending/assisted accounts can
still be created without a username outside the public self-registration flow.

Username login uses the unique index. Email login uses an indexed, case-insensitive lookup
bounded to two records and rejects ambiguous duplicate email addresses. Both paths retain
active-client, platform-admin exclusion, branch suspension, CSRF and session checks.

Verification includes real PostgreSQL migration and registration concurrency checks in the
disposable `MICROFINANCE_REGISTRATION_TEST_URL` database, service and password-filter
isolation tests, username validation, password recovery, JSP compilation and JavaScript
debounce/stale-response tests. These correctness checks make no throughput claim.

## Verification (2026-10-30)

- Fifty focused Java checks passed across the authentication, registration, PostgreSQL,
  JSP and updated auth-view checks. The PostgreSQL test exercises a real uniqueness
  conflict between simultaneous registrations and verifies that migration preserves all
  pre-existing non-username member fields. Nineteen JavaScript checks passed, covering
  the 350ms pause, invalid input, cancellation, stale replies and unavailable responses.
- Flyway validation passed against the current local database at V137. All three user
  records have non-null, distinct usernames. Existing users can sign in with their email
  without needing to know the generated username.
- The complete application started with scheduled work disabled for verification. Both
  login and registration rendered HTTP 200 in English and Kiswahili. The live endpoint
  returned available for an unused valid username, unavailable for invalid input and an
  existing username (including uppercase input), with `Cache-Control: no-store`.
- Visual/mobile browser checks remain pending because local browser access is disabled
  in Work settings. This is not a full-suite or load-test success claim.

## UI contract follow-up (2026-10-30)

- Resolved the three stale AWS view assertions for institution-wide branch controls,
  workspace-aware toast placement and the read-only Platform Settings overview. The
  controls retain responsive wrapping; platform edits still require the UPDATE claim.
- Updated the neighboring institution loan-summary assertion to check its localized
  message keys in both languages and its scoped summary bindings instead of superseded
  hardcoded English text. Production UI, authorization and business rules are unchanged.
- All 77 checks passed across `AwsConsoleViewContractTest`,
  `AdminConfigurationUiContractTest` and `AdminControllerUserAccessSecurityTest` using
  `mvn -Dmaven.compiler.useIncrementalCompilation=false -Dtest=AwsConsoleViewContractTest,AdminConfigurationUiContractTest,AdminControllerUserAccessSecurityTest test`.
  All 23 affected JavaScript checks passed for institution tabs, platform forms, shared
  details and shell navigation. A bounded read-only query confirmed the three current
  local user records retain their backfilled usernames; no account data was changed.

## Staff login follow-up (2026-10-30)

- Staff password login and password recovery now use the same indexed email/username
  resolution as clients. Email matching is case-insensitive, bounded to two records and
  rejects ambiguity. Username matching uses the existing unique index. Staff and client
  numbers no longer resolve credentials; staff numbers remain available for business
  records and audit compatibility. No schema or account-data changes were needed.
- Staff login labels, password recovery, invitations and activation confirmation use
  email/username wording. Both language bundles are updated. Active staff access, branch
  suspension, client/staff session isolation, MFA and the separate System Admin ID remain
  enforced. Newly invited staff without usernames can use their registered email.
- Seventy focused Java checks passed across user-details authentication, the password
  filter, password recovery, principal compatibility, reauthentication, MFA, invitations,
  view contracts and system-admin isolation. The focused Jasper login-page compilation
  check also passed. Live page checks remain pending because
  the local application was not listening on port 8081; visual/mobile browser access
  remains disabled. No throughput claim is made.
