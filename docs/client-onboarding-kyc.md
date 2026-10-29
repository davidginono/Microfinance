# Client onboarding and KYC

Implementation date: 27 October 2026. This implements the individual-client operational workflow in G01 of the research audit. Existing registration and client identifiers remain in place. Registration, an OTP, a signature label and a loan estimate do not establish verified KYC.

## 1. Scope, policy and activation

Staff use **Client onboarding** at `/finance/clients/onboarding`. Clients complete their own drafts, uploads, acknowledgements and submissions and view outcomes, correction messages and version history at `/app/onboarding`. The 29 October self-service extension and branch notice activation are documented in [Client self-service onboarding](client-self-onboarding.md). Staff assistance remains optional. Institution and branch scope come from the current staff session and the current client record. Historical evidence retains its original branch. Moving a client does not expose the previous branch's evidence or transfer its verification; the destination branch needs its own review.

Assign these new claims explicitly using the existing access editor. No role receives them automatically:

| Claim | Use |
| --- | --- |
| `CLIENT_ONBOARDING_VIEW` | Registry, scoped profiles and history |
| `CLIENT_ONBOARDING_CREATE` | Staff-assisted client registration |
| `CLIENT_ONBOARDING_UPDATE` | Drafts, submission and evidence upload |
| `CLIENT_ONBOARDING_REVIEW` | Verify, return, reject or revoke; shown under Approve in the access matrix |
| `CLIENT_ONBOARDING_DOCUMENTS` | Authorized document downloads; shown under Export in the access matrix |

Every staff command also requires View. Reviewers should have document access when they must inspect stored evidence. The institution must approve its evidence, privacy notice, lawful basis, retention schedule, screening procedure and review cadence. The software records their actual references and dates; it seeds no approved policy, acceptable-ID decision, screening result or verification.

After preparing client records, enable **Require verified client KYC for individual loan submission and funding** in **Branch credit controls**, with its approved policy reference. The flag defaults off and each change creates an immutable policy revision. Submission/funding fails when the required verification is missing, expired, revoked or no longer matches current client contact details. Existing disbursed contracts are not repriced or retrospectively marked verified. Repayment and independent financial corrections continue through their existing services.

Organization and pooled-borrower loans keep their specialized authority/onboarding safeguards. This individual-client increment does not certify their beneficial owners, directors, group members or external guarantors. It does not change borrower affordability or guarantor-capacity decisions into KYC decisions.

## 2. Architecture and operating workflow

`com.sacco.mvp.clients.onboarding` contains the controller, request/view DTOs, owning transactional service and JDBC repository. JSPs reuse the current ERP shell, forms, scoped resource-table sorting, selection and dirty-form handling. Native session authentication and CSRF remain in place.

1. Select an existing client or use **Create client** for staff-assisted registration. This reuses registration validation and uniqueness checks, preserves the exact authorized institution/branch IDs, creates no business-policy defaults, and does not invent a client signature, OTP verification or accepted consent. An unshared random password prevents staff from choosing a reusable client password. Existing client login/reset/OTP procedures remain separate.
2. Save a draft containing identity type, issuing country, document number, birth date, expiry/no-expiry declaration, home address, business/employment and work address, and source of funds. Capture the actual privacy-notice version, processing basis, acknowledgement/consent evidence and date, approved retention reference/review date, and any legal-hold reference. Incomplete drafts remain unverified.
3. Add identity, address, activity and privacy/consent evidence. Replacing a category keeps the original file and metadata; a submitted version freezes the current document selection. Complete profiles and current evidence in all four categories are required for submission.
4. A different officer reviews the submitted snapshot. Verification requires explicit identity, address, activity, contact and duplicate checks; dated manual PEP/sanctions sources/results; the approved KYC policy; and the next review date. The verifier cannot be the client, the submitter or an uploader of that version's documents. A missing usable client phone cannot pass a contact-check tick. Unavailable or escalated screening cannot pass verification. Return/reject retains the actual reason and permits a corrected draft.
5. Verification, return, rejection and revocation are permanent decisions. Corrections create new versions; drafts awaiting review cannot be silently edited. The history opens older snapshots, decisions and their protected documents. Starting another draft leaves previous decisions inspectable and makes the current profile unverified until reviewed again.

Exact duplicate matching normalizes document type, issuing country and document number. An institution-scoped hash supports matching; full numbers remain protected profile data and are masked in ordinary details. Cross-branch matching returns only a conflict, never the other client's name or document. Concurrent duplicate reviews cannot both verify. A duplicate pending case must be returned/rejected or corrected before verification; previously verified identities remain reserved to their original client, including after revocation. No fuzzy match, automatic merge or identity reassignment is introduced.

## 3. Security review

The owning service rechecks active staff, current explicit claims, institution and branch on every staff use case, including exact retries and document reads. Client use cases separately recheck active account, institution, branch and ownership. Clients receive their own personal fields, protected document links, frozen notices, client-facing feedback and bounded history; internal screening, staff reasons and retention references remain private. Forms allowlist editable fields; client number, name/contact snapshot, actor, scope and verification state are derived on the server. Full identity numbers are available for authorized own-profile editing and staff editing/review, and remain masked in ordinary details.

Uploads use protected `StoredUploadStorageService`, generated paths outside static resources, and owner/category-bound metadata. The application accepts one PDF/PNG/JPEG of at most 5 MB, inspects actual format, limits decoded image dimensions, and rejects encrypted or over-40-page PDFs. A content checksum is checked at download. Files are forced private attachments with `no-store`, `nosniff` and a sandbox policy. File parsing occurs before transactional metadata/record locks. Bounded storage writes occur before the client evidence lock, with rollback cleanup. Metadata is flushed before JDBC foreign-key links are written.

Download permission is separate and reads are audited. Audit events carry record/action identifiers, not identity documents, contact details or screening narratives. Shared registration logging no longer prints client numbers, email addresses or telephone numbers. SQL uses bound parameters and whitelisted sort expressions; JSP output is escaped and forms preserve validation feedback and entered values.

## 4. Database and queries

`V133__client_onboarding_kyc.sql` adds scoped creation receipts, profile revisions, documents/links, decisions and reserved identities, plus the existing branch policy's KYC flag. Evidence rejects update, delete and truncate; referenced file identity/checksum/size cannot be changed or deleted. Foreign keys preserve clients, staff, documents and version scope. Final checks also protect independent verification and duplicate identity reservations at the database boundary.

Loan-owning services invoke the native scoped gate inside their transactions at client submission, recorded application creation/submission and ordinary funding. A loan transition trigger provides secondary enforcement. The current profile must match the loan's institution and historical `station_id`; missing scope does not widen visibility. Names/contact changes invalidate current verification without overwriting the original snapshot.

Registry and history requests fetch 25 rows plus one lookahead. All registry data columns sort on whitelisted server expressions before pagination with an ID tie-breaker. Latest-version and identity indexes support the bounded reads; current evidence selection returns at most four categories. There is no per-row Java repository loop or external identity call.

## 5. Transactions and concurrency

Request keys and payload hashes make exact retries idempotent and reject changed reuse. Client advisory locks and expected version numbers prevent lost edits or reviewing a superseded snapshot. Identity reservation locks serialize competing verifications. Loan gates use loan → branch policy → client ordering; KYC writes do not acquire loan locks. Branch policy changes and credit gates share the existing policy lock. These short serialized sections require production workload measurements before capacity claims.

No scheduler, executor or provider integration is added. Review due dates and expiry are evaluated using `ApplicationClock` by services and Tanzania-local dates at database transition guards. No background action guesses a clean screening result or silently re-verifies a client.

## 6. Files changed

- New backend: `src/main/java/com/sacco/mvp/clients/onboarding/`.
- New migration, onboarding JSPs, scoped CSS/JavaScript and English/Kiswahili message keys.
- New explicit claims, sidebar destinations and staff login landing.
- Onboarding and credit-control links live in the scrolling navigation, including mobile, rather than the desktop-only toggle header.
- Existing branch credit controls, owning loan services/repository and localized errors integrate the gate.
- Registration adds a scoped assisted path; shared upload storage flushes metadata before returning it for evidence links.
- PostgreSQL/MVC tests and the existing recording/Jasper fixtures cover the new component.

## 7. Verification

Validation uses only the owned disposable PostgreSQL 17.9 cluster on `127.0.0.1:55461`, database `microfinance_recording_test`, with synthetic data. The opt-in environment variable is `MICROFINANCE_RECORDING_TEST_URL`. Never point these integration tests at operational data.

The affected regression passed **220 tests, zero failures/errors/skips**: ten new PostgreSQL onboarding cases, seven onboarding MVC/security checks, previous credit controls, 82 recorded-loan cases, ordinary loan workflow/manager/qualification checks, registration/upload-storage/claim tests and Jasper compilation of all five onboarding JSPs plus the changed branch-controls form. The full suite and final package checks follow this regression.

The focused command used `mvn -q -Dtest=ClientOnboardingPostgresTest,ClientOnboardingMvcTest,CreditControlPostgresTest,CreditControlMvcTest,LoanRecordingPostgresTest,LoanWorkflowServiceTest,ManagerServiceTest,LoanQualificationPolicyServiceTest,MemberRegistrationServiceTest,StoredUploadStorageServiceTest,UserClaimTest,ChartOfAccountsJspCompilationTest test`, with the recording test URL and the existing JSP-selection properties. The final full-suite command is `mvn -q test` with the same disposable datasource.

The full run selected **1,888 tests: three failures, two errors, 129 skipped**. It is not a passing full suite. Remaining findings outside onboarding are the existing account-version header and voucher-configuration filter assertions in `AwsConsoleViewContractTest`, the existing table-sorting coverage assertions elsewhere, and `BorrowerIdentityPostgresTest.recordedAndGroupIndividualLoansReuseOneIdentityPerClient`, whose fixture attempts another application while its recorded application is pending (`group.error.sharedApplicationPending`). The fifth finding was an encoding error in the newly added label section; those sections were corrected to UTF-8 and the affected assertion was rerun separately.

Follow-up: the reported failures and fixture error are resolved. The new full run passed with zero failures/errors; see [UI-contract and borrower identity regression fixes](ui-contract-regression-fixes.md) for counts, changes and remaining validation limits.

After the final contact and self-review safeguards, a fresh migration and the final focused selection passed **27 tests**: ten PostgreSQL cases, seven MVC cases, the component architecture check, Jasper compilation, four registration checks and four shared-storage checks. This includes rejection of a missing client phone and of a staff client's attempt to verify their own profile.

The separate UTF-8 assertion and repeated MVC/architecture checks passed (nine checks). Both full message files decode as UTF-8. JavaScript syntax validation and `git diff --check` passed. `mvn -q -DskipTests package` produced the WAR successfully.

The packaged WAR started on **127.0.0.1:8089**, using only the disposable database and temporary upload storage. Outbox delivery, SMS and retention cleanup were disabled, and mail was pointed at loopback. Login returned **HTTP 200** with a form; the new JS/CSS returned **HTTP 200**; an unauthenticated onboarding request returned **HTTP 302** to login. The temporary app and database helpers were stopped after verification. This confirms startup and unauthenticated routing, not authenticated screen rendering.

Browser inventory was available but contained no authenticated application tab. Authenticated desktop/mobile, both-language visual acceptance and provider-independent original-document review remain rollout checks; compilation alone is not visual acceptance.

## 8. Measured results

No load/spike/soak benchmark has been run; 1,000 RPS capacity remains unproven. Correctness tests and small synthetic query plans do not establish production throughput.

`EXPLAIN (ANALYZE, BUFFERS)` exposed a global latest-revision scan in the first registry query. The revised lateral query uses a backward index lookup by the scoped client's ID before reading its decision. A small synthetic run returned one row under the 26-row limit (312 client/staff records, 42 profile revisions): planning 12.480 ms, execution 0.682 ms. The earlier run used 93 client/staff records and the same 42 revisions, with 1.886 ms execution; these changing fixtures are not a controlled speed comparison. Representative institution-size data, sorting/search distributions and concurrent traffic still need measurement.

## 9. Remaining boundaries

Manual evidence records do not constitute a live NIDA, CRB, sanctions-list or government identity verification. The institution must validate the applicability and current version of its AML/CFT and privacy procedures. The official [FIU guidance index](https://www.fiu.go.tz/publications/compliance-guidelines-notes) was checked for discovery; it lists identity-verification and targeted-sanctions guidance. The [PDPC privacy notice](https://pdpc.go.tz/privacy-notice/) and [data-subject rights](https://pdpc.go.tz/data-subject-rights/) identify privacy/consent and retention considerations. These references do not certify this deployment or establish an automatic retention period.

Retention policy references, review dates and legal-hold evidence are recorded; automated disposal, erasure/consent-withdrawal requests and legal-hold release require a separately authorized lifecycle that preserves required lending evidence. No automatic purge is introduced. Storage encryption/backups, malware scanning, provider verification, full organization beneficial-owner due diligence and CRB submission are separate deployment/capability work. File-type validation does not claim malware clearance. Maintain protected storage and backup access along with database access.
