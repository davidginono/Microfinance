# Client self-service onboarding

## Scope and activation

Clients use **My verification** (`/app/onboarding`) after existing registration and sign-in. They complete their own profile, save incomplete drafts, upload evidence, acknowledge the approved notice, submit, read feedback and correct/resubmit. Registration and OTP verification do not verify KYC. Staff-assisted onboarding remains available through the same component.

Before accepting self-service submissions, staff with the explicitly assigned `CLIENT_ONBOARDING_VIEW` and `CLIENT_ONBOARDING_UPDATE` claims publish **Onboarding notice** from the sidebar. Configuration is branch-scoped and includes the institution-approved English/Kiswahili notice, its version, processing basis and retention reference/review date. No approved text, consent, screening outcome or staff claim is seeded. Drafts remain available when the notice is unavailable or disabled; submission fails with a recovery message.

## Public registration numbers

Public `/register/member` registration does not accept a client number. OTP requests only validate details and deliver a code; after OTP consumption, the registration service allocates `CL-00000001`-style numbers inside the account-creation transaction. PostgreSQL `client_number_seq` is atomic across app instances and never cycles or recycles rolled-back values; gaps are expected. The existing unique `members.member_no` constraint remains the final protection. A bounded indexed collision check also skips older/manual numbers, regardless of letter case.

V136 initializes the sequence above existing matching numbers and adds an index; it does not update any existing identities. Staff-assisted creation retains its current explicit-number contract. Public request DTOs have no number property, so a forged `memberNo` is ignored. The assigned value appears as a read-only output on the login success screen and in the existing profile details. No number is returned by the OTP request endpoint.

The number-allocation regression uses a fresh, disposable PostgreSQL database selected by `MICROFINANCE_REGISTRATION_TEST_URL` (`jdbc:postgresql://127.0.0.1:<port>/microfinance_registration_test`). It migrates through V135, inserts existing-number fixtures, then applies V136 and exercises the real JPA registration service with 24 simultaneous registrations, case-insensitive collision skipping, rollback non-reuse and retained assisted numbers. No operational database or delivery provider is used. A read-only planner diagnostic confirms the upper-case collision lookup can use the new index; no throughput capacity is claimed.

The 30 October registration verification passed 34 focused Java tests (including PostgreSQL allocation, OTP ordering, request binding, Jasper compilation and onboarding access) and 13 shared validation JavaScript tests. Browser checks at 360/768/1366/1920 pixels found no horizontal document overflow. The local app restarted successfully with registration/login returning HTTP 200 and the retired number-availability route returning 404. Initial broader UI suites exposed four unrelated contract failures (institution filters/portfolio, guaranteed-loan surfaces and financial settings) and two remote-select JavaScript failures; their implementations were not changed by this registration work.

## Workflow and architecture

The existing `clients.onboarding` controller → service → repository component owns both assisted and client flows. Client request DTOs contain personal details and acknowledgement choices only. Names/contact/client numbers come from the registered account; clients cannot assign ownership, scope, policies, screening or verification state. The existing registration and sign-in routes remain unchanged.

1. Save personal and business/employment information as a draft.
2. Upload identity, address and activity evidence. Each upload supports PDF/PNG/JPEG, up to 5 MB, with the existing content checks. Replacing a category preserves its earlier evidence.
3. Read the published notice and explicitly acknowledge it and the accuracy declaration. For a consent-based notice, the checkbox explicitly requests consent. The server freezes both language versions, notice identity, policy metadata, accepted choices and time with the submitted profile and document selection. This recorded acknowledgement replaces the separate consent-file requirement for self-service versions. Assisted versions retain their four-category evidence rule.
4. The existing authorized, independent reviewer verifies, returns, rejects or later revokes the version. Verification still requires actual checks, cleared manual screening evidence, the approved KYC policy and next review date. A submitter/uploader cannot verify that version, including in assisted onboarding.
5. Return, rejection and revocation require a separate **Message to client**. Internal reasons and screening notes remain private. Historical decisions receive no fabricated client message. A newer verification clears an older correction message from the current workspace.
6. Corrections create new versions. While awaiting review, editing and uploads are denied. Clients can inspect earlier versions and their linked documents. Verification expiry and stale-contact states retain their existing behavior. Saving a replacement draft requires another verification; historical approval is preserved.

If the notice changes while a form is open, the server rejects the old acknowledgement. The response preserves personal fields, shows the current notice and clears the acknowledgement choices so the client can read and accept it again.

## Security review

Every client use case checks a current active client account and exact institution/branch/owner scope. Client routes contain no target client ID. The narrowly scoped `/app/onboarding` access rule and sidebar link do not require loan-workspace claims; other client/staff routes keep their existing permission checks. Staff permissions remain explicitly assigned and independently checked. Client history DTOs omit staff identities, processing/retention references and internal review evidence. A self-service revision carries forward any staff-recorded legal-hold reference, without exposing it to the client. Document reads are scoped, audited private attachments with checksum validation, no-store, nosniff and sandbox headers. Session authentication, CSRF and allowlisted binding remain in place.

## Database and queries

`V135__client_self_onboarding.sql` adds immutable branch notice revisions and client-facing decision messages, updates completeness checks for actual self-service acknowledgements, and reinforces pending-version/document/feedback guards. It preserves historical profiles, decisions, identities, loans and accounting records. A scoped feedback index supports the latest client-facing decision. Histories return 25 rows plus one lookahead; documents are limited to the existing four categories. No external provider request or unbounded registry load is introduced.

## Transactions and concurrency

Existing request keys, payload hashes, version checks and client advisory locks protect writes. Retry ownership is checked as well as the payload. Notice publication/submission use a scoped notice lock so the accepted policy cannot change mid-submission. Upload parsing happens before transaction locks. Independent review, duplicate-identity reservations and loan KYC gates retain their existing safeguards. No new executor or background worker is added.

## Files changed

Changes are contained in the existing onboarding Java component, JSPs, CSS/JavaScript, sidebar, English/Kiswahili bundles, migration and focused tests. The rendered integration test supplies synthetic actors only inside its test web context, bound to loopback; it does not change production authentication.

## Verification

The final focused selection passed **113 tests, zero failures/errors/skips**, including 15 PostgreSQL onboarding cases and a real HTTP/JSP/multipart flow through client draft, uploads, submission, staff return, client correction/resubmission and staff verification. It also checks the historical correction message, absence of internal screening notes and English/Kiswahili forms. The full-suite run adds the dedicated no-loan-permissions route regression to the MVC/security coverage. All database tests use an owned disposable PostgreSQL 17.9 cluster on `127.0.0.1:55467`, database `microfinance_recording_test`, and synthetic data. The temporary server and database are stopped after verification; no operational client records or real delivery providers are used.

The focused command selects `ClientOnboardingPostgresTest,ClientOnboardingRenderedPostgresTest,ClientOnboardingMvcTest,OnboardingArchitectureTest,MemberRegistrationServiceTest,StoredUploadStorageServiceTest,LoanWorkflowServiceTest,LoanQualificationPolicyServiceTest`, with `MICROFINANCE_RECORDING_TEST_URL` set to the owned disposable database. Maven compilation uses `-Dmaven.compiler.useIncrementalCompilation=false` to compile changed sources. The rendered fixture's optional browser window is enabled only for local visual QA, never by the ordinary test run.

The shared JavaScript form validation, resource-details and workspace-navigation selection passed **53 tests**. JavaScript syntax and `git diff --check` passed. Browser inspection covered the client workspace on desktop and at 360 pixels, and the form at 360, 768, 1366 and 1920 pixels, without horizontal document overflow. Checks included the accepted status and masked identity number, section tabs, profile controls, wrapping notice/acknowledgement text, form actions and active navigation for a client without loan claims. The fixture omits unrelated notification/profile-image endpoints; their unavailable state does not block onboarding initialization. This is synthetic authenticated component verification, not a claim of a production login or provider test.

The full `mvn test` selection passed with **1,925 tests selected: 1,310 passed, 615 skipped, zero failures/errors**, across 268 suites. The recording-test environment variable was unset for this run; conditional database cases were covered by the separate focused run above. All 13 onboarding MVC/security tests passed, including the new own-onboarding access rule and continued denial of unrelated loan routes. The shared Jasper test compiled the resource views, including the final onboarding pages. `mvn -DskipTests package` also passed and produced `target/ROOT.war`. Compilation used the same incremental-compilation option; bounded JVM settings were local to the verification commands, with no project runtime configuration change.

## Measured results

This is a correctness/workflow change. No throughput benchmark is claimed; 1,000 RPS capacity remains unproven.

## Remaining deployment requirements

The institution must publish its approved bilingual notice and actual retention settings and explicitly assign staff claims. File validation does not establish document authenticity or malware clearance. Identity-provider verification, storage/backup deployment controls and data-retention disposal remain the existing separate capabilities. KYC remains distinct from affordability and loan approval; the existing branch policy determines whether valid KYC is required at loan submission/funding.
