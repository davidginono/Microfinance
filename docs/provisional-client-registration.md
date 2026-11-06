# Provisional client registration

31 October 2026. Provisional registration supports staff-assisted intake and self-service onboarding before the client's verification and normal loan application are complete.

## Identity and access

Both channels allocate `CL-00000001`-style client numbers from the existing PostgreSQL `client_number_seq`, using the existing collision check. Staff cannot submit a client number. Existing numbers are preserved; activation never allocates another number. Rollbacks may leave sequence gaps, and numbers are never recycled.

The existing `Member` remains the compatible client identity referenced by KYC and loans. It does not by itself establish login access. A new staff-assisted record has `client_portal_enabled=false`, no username, no usable password and no user settings. The non-password marker satisfies the existing non-null database column. Password login, OTP login, password reset and Google SSO cannot authenticate this client. KYC verification does not enable portal access. These records remain in Client onboarding and are excluded from the Users register and user-access details until portal activation; user/role editing cannot turn them into system users.

Self-service uses the existing registration form and registration OTP. A new client receives restricted onboarding access. A staff-created client can use the same registration form to establish portal access: the stored names, email, phone, institution, branch and employee number (when present) must match, and registration OTP is still required. Activation OTPs are bound to that existing client ID, and the same activation context is checked again under the registration contact lock. An earlier unbound signup code cannot activate a newly created assisted record. Delivery cannot be redirected to a replacement phone. Activation changes credentials/access on the original client and preserves the number, profile evidence and loan request. It does not invent a signature, KYC decision, consent or phone-verification event. The `CLIENT_PORTAL_ACTIVATED` audit event is written in the same transaction as the access change and portal settings; an audit failure rolls the activation back. Existing users continue to sign in; they cannot be reactivated through registration.

Registration no longer creates or rewrites institution lending settings. Institutional configuration stays with its existing authorized configuration workflows.

## Profile-only onboarding (2026-11-02)

Client onboarding opens My profile directly at `/app/onboarding`, with the complete bio data, photo and document tabs. Verification, privacy, declared-information and staff-feedback notices remain on the form. Draft saving, uploads and sending for review retain their scoped services. Submitted profiles show frozen values and notice acknowledgements read-only until staff return them. Staff review and historical evidence remain in the staff workspace. Old client version/detail bookmarks redirect to the current profile.

The separate onboarding loan-request feature is retired for clients and staff. Its pages, controller, service, repository and prefill path into loan applications are removed. Existing GET bookmarks redirect to the corresponding profile; retired writes are unavailable. System clients apply through the normal authorized borrower workspace after verification, and staff use their normal lending workspace. V138 request records and append-only database protections remain as historical evidence; this change deletes no stored records and introduces no schema migration.

## Architecture, scope and concurrency

The onboarding component owns the profile DTOs, MVC controllers, service and repository. Controllers allowlist fields and never access persistence. The services recheck current staff claims or client account/ownership and institution/branch scope. Existing session authentication, CSRF and client-workspace verification remain in force. No provider integration, executor or scheduler is introduced.

V138 introduced the portal-access flag (existing records default to enabled) and the append-only, scoped `client_onboarding_loan_request` table. Its credential constraint continues to prevent credentials being assigned while portal access is disabled. The retired request table retains its immutable historical versions, scope, indexes and database protections; no application route writes new intake requests.

Registration takes consistently ordered transaction-local contact/employee advisory locks before duplicate checks, shared by assisted and public registration. Activation also uses a scoped client lock and a conditional disabled-to-enabled update. These are short database transactions without external delivery or file work. Registration OTP retains its existing delivery/consumption flow outside this new persistence work. Registration contact locks protect these registration paths; they do not retrofit database contact uniqueness onto historical/imported or separately administered records.

## Verification

### Profile-only flow, 2026-11-02

All 64 distinct focused cases passed across the full selection and targeted recheck. The initial selection passed 63 cases; one retired-route mock expectation incorrectly excluded the verification filter's read-only status check. After correcting that expectation, the targeted run passed all three cases, including the complete rendered HTTP/JSP flow again.

The selection covered `ClientOnboardingMvcTest`, `ClientOnboardingAccessMvcTest`, `ProvisionalClientMvcTest`, `OnboardingArchitectureTest`, `ClientOnboardingPostgresTest`, `ClientOnboardingRenderedPostgresTest`, `ProvisionalClientPostgresTest` and `ChartOfAccountsJspCompilationTest`. Jasper compiled the profile, staff details and normal borrower application pages. PostgreSQL cases used an owned disposable cluster at `127.0.0.1:55543`, database `microfinance_recording_test`, with synthetic actors and documents. The operational datasource and delivery providers were not used.

Browser checks exercised all three profile tabs and widths of 360, 768, 1366 and 1920 pixels. The profile posts to the existing review command, has no onboarding loan-request links and has no page-level horizontal overflow. These correctness and rendering checks are not throughput measurements.

### Earlier registration validation

Validation uses only owned disposable PostgreSQL 17.9 clusters at `127.0.0.1:55543` and `127.0.0.1:55544`, databases `microfinance_recording_test` and `microfinance_registration_test`, with synthetic actors and evidence. The packaged startup check uses the separate fresh `microfinance_provisional_smoke_test` database. The operational datasource and delivery providers are not used.

Coverage includes shared numbering and historical-number preservation; mixed-channel concurrent registrations; same-request and duplicate-contact submissions; activation identity preservation, mismatched contact/scope rejection and concurrent activation; disabled password/OTP/reset access; draft and cash-flow validation; immutable request versions; moved-client branch isolation; expected-version conflicts; verification-gated continuation; MVC CSRF/session/binding/recovery checks; architecture, bilingual labels and JSP compilation; and real HTTP/JSP/multipart onboarding with loan intake before verification.

Before the profile-only retirement, the isolated registration/intake run passed **206 tests across 15 suites, zero failures/errors/skips**, after the last activation, audit and user-registry changes. It used `mvn -q -Dmicrofinance.build.directory=target/provisional-final -Dtest=ProvisionalClientPostgresTest,ProvisionalClientMvcTest,ClientOnboardingRenderedPostgresTest,ClientOnboardingPostgresTest,ClientOnboardingMvcTest,ClientOnboardingAccessMvcTest,MemberRegistrationServiceTest,ClientNumberAllocationPostgresTest,PasswordLoginIsolationTest,AuthControllerSaccoAccessTest,EmailOtpServiceTest,AdminServiceTest,AdminControllerUserAccessSecurityTest,OnboardingArchitectureTest,ChartOfAccountsJspCompilationTest test`, both disposable datasource environment variables, and the JSP selection covering the six affected onboarding/loan forms and details. The optional browser window was enabled only for local QA. This includes 14 provisional-client PostgreSQL cases and real HTTP/JSP submission before verification.

The broader `mvn -q test` run, started before the final activation hardening, selected **2,038 tests: 1,888 passed, six failures, nine errors, 135 skipped**. It is not a passing full suite. The failures concern untouched files/contracts:

- `OutboxPublisherSchedulerTest`: its notification expectation uses the older email overload; the delivery service supplies the institution scope.
- `AdminConfigurationUiContractTest`: expects the retired `SaccosUiState.clearOpenModal` call in the product form script.
- `SessionTimeoutUiContractTest`: three platform-settings source/layout assertions.
- `TableSortingCoverageTest`: existing tables in `admin/platform-settings-top-up.jsp` and `admin/sacco-detail.jsp`.
- `LoanDocumentControllerSmsUsageSecurityTest` (four errors) and `StaffAnalyticsControllerSecurityTest` (five errors): their test contexts lack the existing `OnboardingService` dependency of `SecurityConfig`. The dependency is present in the pre-change Git revision too.

These tests and their referenced notification/admin sources were unchanged by this increment. Their findings were retained rather than weakening assertions or changing unrelated production workflows. The final focused run separately verifies the changed registration, authorization, admin user-access and onboarding paths.

Chrome checks of the real synthetic fixture verified English/Kiswahili forms, product search/no matches/Escape recovery, negative-cash-flow validation and correction, mobile navigation, and widths 360/768/1366/1920 without document overflow. The final form was rechecked after the accessible product label and repayment/monthly-debt wording changes. The fixture deliberately omits profile-image and notification endpoints; their fallback warnings do not establish provider or notification delivery acceptance. No real client records, credentials or financial postings were used in browser QA.

A small synthetic `EXPLAIN (ANALYZE, BUFFERS)` for the exact scoped latest-request query used `ix_onboarding_loan_scope`, returned one row with two buffer hits and 0.041 ms execution. This is an index diagnostic, not a controlled load benchmark. No production throughput, 1,000-RPS capacity or live identity/OTP-provider verification is claimed.

Java 25 compilation, the six-page Jasper selection, bilingual-key checks, `node --check src/main/resources/static/js/loan-intake.js`, and `git diff --check` passed. Packaging with `mvn -q -Dmicrofinance.build.directory=target/provisional-final -Dmaven.test.skip=true package` produced `target/provisional-final/ROOT.war` successfully. No operational database migration or deployment was performed.


The packaged WAR started successfully on loopback port 8092 against the fresh smoke database, migrated through V138, and initialized the full application context. Outbox processing, SMS and retention cleanup were disabled; mail was restricted to loopback. Health, login and public registration returned HTTP 200; anonymous client/staff onboarding requests returned HTTP 302 to login. The served registration contained the activation hint and no `memberNo` input. Its 360px browser check had no document overflow. Temporary app/database helpers were stopped after verification. Authenticated workflow rendering was verified through the separate real-service synthetic fixture, not by signing in to an operational account.

## Extended registration and profile fields

### Optional staff attachments and creation layout (2026-11-05)

Staff may register a provisional client without uploading documents. The creation form also accepts optional categorized PNG/JPEG attachments before the client exists, within the existing 10-image, 1 MB per image and 10 MB total limits. Image validation runs before the creation transaction; the client, initial profile, attachments and audit events commit together. Repeating the same creation request does not duplicate the client or its attachments. Uploading a copy does not establish verification, consent or portal access; independent review and borrower evidence requirements retain their existing rules.

Staff provisional creation has no draft-saving action, endpoint or session persistence. The shared footer renders Cancel, Previous/Next and Create client for this flow; existing-client profile editing and portal registration retain their own draft behavior. Stale native or former JSON draft commands are rejected without saving data or creating a client. Recoverable creation errors preserve the live entries and selected files; the shared dirty-exit guard protects unfinished work.

The staff creation layout uses a white step panel, shaded section headers and a footer aligned with the form. Staff use the same file chooser, drag and drop, thumbnails and file tokens as Complete My Profile. Done applies the selection to the live form. Create client submits the selected files with their categories and expiry dates. Client details display saved files with the same token layout and existing access controls.

Verification covered the shared chooser and multipart payload, attachment limits, native fallback, non-scrolling detail tabs, rejection of obsolete draft requests, JSP compilation and HTTP rendering of the shared footer in English and Kiswahili. Browser visual checks remain pending because browser access was unavailable.

The staff-assisted creation form and both profile editors share the requested bio data, contact, business, spouse and bank sections. First and last names and the phone number identify a new assisted client; middle name and assisted email are optional. Existing portal users retain the protected account-contact change flow. The client number is read-only and uses the same shared allocation sequence. Saving a structured assisted registration atomically creates its first profile draft without a second client identity.

Group is an optional, branch-scoped selection of an existing active lending group. The selection is a declared profile association: it does not enroll the client, establish borrowing authority or create guarantee liability. Lookups return at most 26 choices per page; the database also rejects a foreign or inactive group on a new snapshot. Existing snapshot names, documents and decisions remain unchanged.

Declared daily sales and expenditure use exact decimals and preserve unknown values as null. They do not populate monthly affordability figures or establish verified income. Declared bank details do not change a verified payout instruction or any accounting account. Account numbers are masked in the read-only profile; authorized editors can view the submitted value.

Profile and spouse photos use the existing private, append-only KYC evidence storage. V139 permits the two optional photo categories and keeps the existing three/four required-evidence gate independent of photos. Only validated PNG/JPEG images are accepted as photos (5 MB and nine million pixels maximum); photos have no expiry. Scoped no-store photo routes require the existing staff Documents claim or client ownership. Save a draft before opening the existing upload task; dirty navigation protection preserves the option to continue editing.

Staff can correct a provisional client's name and contact details in a new draft under the existing onboarding Update permission. Registration/contact locks, optimistic profile sequence checks, contact uniqueness and the disabled-portal condition protect the change. It preserves the client number and all previous profile versions, invalidates outstanding client-bound registration codes and writes a permanent audit event in the same transaction. It does not invent phone verification or enable portal access.

Verification for this increment: 137 focused tests passed across registration, ownership/security, profile/loan intake, OTP, architecture, PostgreSQL and real Jasper/HTTP/multipart rendering in both languages. JavaScript syntax checks, packaging and packaged application startup passed; login and registration returned HTTP 200. Additional HTTP checks confirmed staff/self prefilling and inline decimal validation with preserved values. On 1 November 2026, interactive browser checks passed in the isolated fixture at 360, 768, 1366 and 1920 pixels for staff and self-service editors. Checks covered English/Kiswahili mobile layout, overflow, retained values across tabs, Home-key tab selection, dirty-Cancel recovery, mobile navigation/profile dropdowns, private client/spouse photo previews, generated client number preservation, invalid amounts and successful draft saves. The browser run found and fixed command buttons named `action` shadowing the form URL: shared workspace submissions now read the HTML action attribute. The fixture does not provide the unrelated notification/avatar endpoints. After the submission fix, 47 console/MVC regression checks and JavaScript syntax validation passed. No throughput capacity is claimed.
