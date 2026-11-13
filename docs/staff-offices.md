# Staff offices

Each institution staff group has `/staff/offices/{office}/dashboard`, `/queue` and `/reports`. The fixed office slugs are `loan-officer`, `general-manager`, `finance-manager`, `chief-accountant`, `ict-manager`, `credit-officer`, `accounts-clerk`, `customer-care` and `accounts-clerk-2`. Super Administrator retains the separate platform workspace.

`StaffOffice` defines assignment, display labels and landing order using `Position.staffAssignableRoles()`. Historical aliases normalize for presentation only. All assigned offices appear in the sidebar. Password, OTP, MFA and the compatibility `/staff/dashboard` route share the same landing resolver. Valid saved destinations survive authentication; retired or unassigned office destinations fall back to the assigned office. Canonical endpoints still authorize every request.

Active staff with a current institution and branch can enter their office with an empty explicit permission set and incomplete optional profile details. They see **No work assigned** without business-data queries. Profile editing remains optional. Invitation activation, password/MFA requirements and action-specific evidence checks remain in force. Group membership grants no operation claims.

## Work and scope

Dashboards show at most three authorized summary cards. Queues reuse existing feature services and detail/review routes, with server-side search, sorting and 25-row pages. A further row determines whether another page exists; `25+` means a bounded first-page count, not an exact total. Selection resets after navigation and filter changes. Validated office return context preserves the selected queue, search, sort and page through detail pages and command redirects.

Loan review projections use the latest round, frozen plan, current unfinished step, selected office group, live membership and explicit decision claims. They exclude the recorder, submitter, related borrowers and prior voters. Decisions continue through `LoanGroupApprovalService`, which rechecks eligibility and unchanged assessed terms. Voucher reviews use their frozen stage and existing independence rules; account-version reviews require an independent authorized reviewer. Own drafts, preparation work, waiting work and actionable reviews retain distinct states.

All reads enforce institution and branch scope in owning services. Live assignment changes invalidate an old principal; revoked claims disappear from office work. The support model has no first-class branch reference: its office queue uses the reporter's current institution and branch and excludes unassigned reporters. It does not expose institution-wide incidents. Queue reads are bounded and avoid per-row eligibility queries.

Office Reports links to permitted existing reports. Filters, cutoff dates, calculations, export restrictions and audit behavior remain owned by those reports. Empty report catalogues show an explicit empty state. Both clerk offices reuse the same accounting capabilities, subject to individually assigned claims. Client recording remains separate from independent verification.

No schema migration, permission backfill, financial transition, report calculation or borrower access is introduced. Deploy from a clean build so the removed generic staff dashboard service and template cannot survive in compiled output. Do not clean a running application's output directory.

## Verification (2026-11-13)

The focused suite passed 104 tests, including password/OTP landings, all nine institution groups, platform isolation, aliases, multiple assignments, empty claims, optional profiles, stale assignments, CSRF, saved destinations, scoped queues, independent review ordering, frozen rounds, rejection/resubmission and JSP compilation. PostgreSQL checks used isolated PostgreSQL 18 databases and synthetic records. Two JavaScript cases passed for navigation context and source/sort changes.

All 104 focused tests also passed from a fresh isolated snapshot containing only the staged changes, without the unrelated worktree edits.

The packaged application was checked at desktop and 390 px mobile widths: direct login/logout, empty offices, multiple assigned offices, populated client queues, sorting/selection reset, existing client details, report parameters and return links. Tables scroll within their containers without page-wide horizontal overflow. No production permissions, financial records or database were changed.

A clean isolated full verification exposed two existing source-contract failures after the office fixes: `AwsConsoleViewContractTest` and `TableSortingCoverageTest`. They concern unchanged `accounting/loans/group-history.jsp`, `accounting/loans/simple-group-details.jspf`, `accounting/statements/movements.jsp`, `accounting/statements/setup.jsp` and `groups/loan-drafts.jspf`; these files match HEAD. At that checkpoint, the two checks also failed when run separately. The follow-up group and statement table-contract fix resolved them, with 25 focused Java tests, 56 shared table JavaScript tests and desktop/mobile checks passing. The focused office suite and packaged build pass. Correctness checks do not establish measured throughput.
