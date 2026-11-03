# Simplified onboarding data use

3 November 2026. The user's instruction replaces configurable branch notices with a standard bilingual message in the client profile. The message covers identity checks, loan assessment and administration, repayments, fraud prevention, required records and client support. It describes restricted access, authorised sharing, necessary retention and contacting the branch about access, corrections, withdrawal of consent or privacy concerns. Its wording is informed by the [PDPC privacy guidance](https://pdpc.go.tz/privacy-notice/); it does not certify an institution's compliance or provide permission for unrelated uses.

## Current behavior

- Remove the Onboarding notice sidebar destination, configuration JSP, request DTO and publication service/repository commands. An authorised staff GET to the retired URL returns to Client onboarding; its POST cannot publish anything.
- Display the built-in English/Kiswahili message on the profile. Clients explicitly agree to the described use and confirm the accuracy of their information before sending for review. Draft saving needs neither acknowledgement nor a published branch notice. Existing ownership, institution/branch scope, CSRF, evidence and independent-review rules remain in force.
- Staff-assisted profiles display the same message without notice version, processing-basis or retention configuration fields. Submission still needs a client-consent document. The service records that document's ID and the date its evidence was recorded; it never manufactures a client's checkbox acknowledgement or signature. Staff can still record a genuine legal-hold reference.
- Missing, disabled or expired historical branch notices do not affect new submissions. Do not infer approved institutional retention policies, retention dates, completed identity checks or loan approval from this standard message.

## Storage and compatibility

V144 introduces `client_onboarding_data_notice(version)`, an immutable SQL function holding the versioned bilingual text. The default is `MICROFINANCE_DATA_USE_V1`. This is the single source used by the view service and database completeness check; it adds no configuration table. Future versions must keep earlier versions available for completeness checks on their frozen snapshots.

Each new profile snapshot stores the server-selected ID, version, both language texts, stated consent basis and actual consent evidence. Client acknowledgement uses its actual timestamp; assisted consent points to its uploaded evidence. No approved retention reference or review date is fabricated. The database compares standard snapshots with the canonical message and checks the relevant consent evidence.

Earlier branch notice rows and profile snapshots remain immutable and readable with their original text, acknowledgement, basis and retention metadata. Their previous completeness rules remain applicable. The migration does not rewrite or retrospectively approve them. Existing loan contracts, ledger balances, posted payments and financial controls are unaffected.

The current path removes the branch notice lock and uses a bounded single-row SQL-function read. No throughput benchmark is implied.

## Implementation review

The existing onboarding controllers delegate to `OnboardingService`, which owns scoped reads, transactional saves and the repository. The portal and normal client workspace use the same service and JSP. Request allowlists exclude notice text, processing basis, retention settings and consent audit metadata. Self-service consent must be explicitly supplied with the current message ID. Staff cannot manufacture a client acknowledgement; assisted evidence retains its protected upload ID. Escaped text, session authentication, CSRF, current-account checks, institution/branch/owner scope and explicit staff claims are preserved.

The notice lookup is a single SQL-function row (`EXPLAIN` reports `Result`); it contains no institution/client data or network work. No new table, index, scheduler, executor or thread is added. Existing request-key locks, payload checks, expected revisions and client locks protect retry and concurrency behavior. Profile and document evidence remains append-only, with an independent reviewer.

Changed production files are the onboarding/portal controllers, onboarding DTO/service/repository, V144, the shared sidebar, onboarding forms, message bundles and unused version-display CSS. The obsolete configuration JSP is deleted. The onboarding and portal regression fixtures now run without a published notice; the legacy-notice fixture is retained only to test historical compatibility.

Browser checks used the loopback-only rendered test server and synthetic records. English and Kiswahili messages and unchecked required consent controls were inspected at desktop and 360px mobile widths, without horizontal document overflow. The console reported the existing missing Lucide `x` icon warning in the shared discard dialog; its CSS fallback remains visible. No application error was observed in the message flow. These checks do not certify the institution's actual organisational safeguards, record-retention practices, or 1,000-RPS capacity.

## Verification results

The focused selection passed **69 tests, zero failures/errors/skips** after updating the portal end-to-end test's stale Profile photo heading assertion to the existing Attachments and Documents heading. This includes 21 PostgreSQL onboarding cases, the complete real JSP/HTTP/upload/correction/review flow, six portal PostgreSQL cases, the full application/OTP portal end-to-end case, 38 MVC/security cases, the component architecture test and Jasper compilation of four affected views. No full-suite or throughput result is claimed.

Tests used only the owned disposable PostgreSQL 18.3 database `microfinance_recording_test` on `127.0.0.1:55571`. Flyway validated all 165 migrations through V144. An initial run could not connect after the interrupted turn stopped the helper; it was rerun with the helper running. Output was isolated under `target/simple-onboarding-verification` because another Maven build was using the default target folder. The real Boot application started on a random loopback port as part of the portal test. The suite still logs an existing receipt-batch scheduler/EntityManager shutdown warning after that test completes; it does not fail the test or change this onboarding flow.

The main command was `mvn -q -Dmicrofinance.build.directory=target/simple-onboarding-verification -Dtest=ClientOnboardingPostgresTest,ClientOnboardingRenderedPostgresTest,ClientApplicationEndToEndPostgresTest,ClientPortalPostgresTest,ClientOnboardingMvcTest,ClientOnboardingAccessMvcTest,ClientPortalSecurityMvcTest,OnboardingArchitectureTest,ChartOfAccountsJspCompilationTest -Dmicrofinance.jsp.pages=../onboarding/self-form.jsp,../onboarding/form.jsp,../onboarding/details.jsp,../portal/entry.jsp test`, with `MICROFINANCE_RECORDING_TEST_URL` pointing to that disposable database. The corrected portal end-to-end case was rerun separately with the same build directory. The optional rendered-test browser window used `-Dmicrofinance.onboarding.browser-seconds=300` for visual checks only.

`mvn -q -Dmicrofinance.build.directory=target/simple-onboarding-verification war:war spring-boot:repackage` packaged the already compiled and tested classes successfully. ZIP checks confirmed V144 and the Boot WAR launcher are present, while the retired JSP, configuration DTO, sidebar destination and missing-branch-notice warning are absent. UTF-8 and `git diff --check` passed. The temporary browser tab, loopback web servers and owned PostgreSQL helper were closed/stopped after verification.
