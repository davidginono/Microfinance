# Group lending delivery evidence

Goal: implement and verify all three models in [the implementation plan](group-lending-implementation-plan.md), including accounting and servicing. Work started 23 October 2026.

## Delivery checklist

- [x] Scoped group registry, membership/office history, explicit claims and responsive screens with English/Kiswahili labels. Borrowing/signatory mandates follow with cycles and organization lending.
- [ ] Product/model policy configuration and frozen borrowing cycles with verified assessment evidence.
- [ ] Model 2 individual loans, explicit guarantees/capacity and existing product review/funding routes.
- [ ] Atomic group collections, payer/beneficiary attribution, preview/recheck, independent complete corrections and documents.
- [ ] Typed organization/shared borrowers across application, ledger, ownership, queues, documents and reports.
- [ ] Model 3 organization agreements, mandate, funding and servicing.
- [ ] Model 1 shared facility, conserving allocation components, contribution/coverage attribution and servicing.
- [ ] Statements, arrears/risk and supported SL/GL/attribution diagnostics without duplicated debt/cash.
- [ ] PostgreSQL constraints/races, claims/CSRF, calculation/financial regression, Jasper and responsive browser verification.
- [ ] Final build/package and local commits with known limitations recorded.

## Baseline

- Clean worktree before implementation. `mvn -DskipTests compile` passed on 23 October 2026 (11.293 seconds).
- The existing group plan is design evidence, not completed functionality. New claims will require explicit assignment; operational policy and financial data will not be seeded or fabricated.
- Each delivered increment records its checks here. Unchecked items remain required work; no throughput claim is established by compilation or correctness tests.

## Registry increment

- Added group identity, effective-dated membership/office records, immutable evidence, scoped idempotency and controlled deactivation. V90 has no loan, ledger or money mutation and assigns no claims. New claims are available for explicit assignment; group-only staff can reach Groups after login.
- Reused the shared JSP shell, tables, search selector, command menu, date controls and dirty-form recovery. Group creation, adding a member and ending a membership use focused server commands; ending membership preserves the original appointment and does not release a loan/guarantee.
- Production compilation passed. The focused Maven run passed 29 tests, zero failures/errors/skips: six PostgreSQL scope/history/retry/concurrency/rollback checks, six MVC/CSRF/binding checks, thirteen access-claim checks, one feature architecture check, two existing web-layer isolation checks and one Jasper test compiling all five group JSPs. PostgreSQL 17.9 validated all 111 migrations through V90 in a disposable loopback database on port 55521.
- The real application started against that disposable database on loopback port 8089. Synthetic staff login, group creation, remote search finding client 31 beyond the first page, membership save and membership exit/history were exercised. Browser checks found the missing table-toolbar binding; the corrected fresh build displays the shared Actions menu. The synthetic login fixture initially used lowercase staff numbers; it was corrected to the application's uppercase login convention without changing authentication code.
- Group detail containment was checked at 360/768/1366/1920 pixels, with no document overflow. The settled 360px screenshot was inspected after closing mobile navigation; table overflow stays inside its viewport and form controls fit. Screenshots/fixture files remain outside the repository under the thread's visualization directory. Current live interaction checks used English; both message bundles contain matching keys. Full bilingual rendered coverage, broader regression, packaging and throughput remain pending.
- Browser plugin was unavailable; existing Playwright and installed Chrome were used. Synthetic profile-image and notification endpoints produced 404/403 resource errors; no group-page JavaScript exception was observed. This fixture does not certify operational sign-in/notification integrations. Financial services and operational institution data were not changed.
- JavaScript syntax and whitespace checks passed. Follow-up Jasper/MVC/claim/architecture run after toolbar, localization and shared form-action corrections passed all 21 selected checks with zero failures/errors/skips.
