# Staff-only accounting and loan records

The application is a staff accounting package. Clients are business records, not login accounts. Staff record client details, applications, signed documents and group loans. Existing loan contracts, decisions, repayments, journal entries, corrections and audit evidence remain historical records.

## Access and user groups

The assignable groups are Super Administrator, Loan Officer, General Manager, Finance Manager, Chief Accountant, ICT Manager, Credit Officer, Accounts Clerk, Customer Care and Accounts Clerk 2. Super Administrator uses the separate platform login. Institution staff use the staff login and invitation flow, including staff who also have a client record.

Groups describe assignments; explicit access claims authorize each operation. Group membership does not automatically grant loan approval, repayment, correction, configuration or financial posting permissions. Institution and branch scope remain mandatory. Old borrower sessions cannot access staff routes.

Staff enter their assigned workspace directly. The retired institution selector and branch-switching action are removed; old selection-session values cannot override the current assignment.

Named group offices, direct authentication landings, bounded work queues and existing report links are described in [Staff offices](staff-offices.md). Personal profile completion is optional for staff workspace access.

Legacy role identifiers remain on existing assignments where frozen voucher rules and historical evidence reference them. The cutover adds their current group equivalent without granting permissions: Minor Administrator → ICT Manager; Chairperson → Finance Manager; Board/Credit Committee → Credit Officer; Disbursement Officer → Accounts Clerk. The user editor presents the current groups and retains a legacy alias only while its corresponding assignment remains selected.

Historical manager, board, guarantor and reversal entities remain where reports, evidence joins or archival queries still use them. Their retired workspaces and write services are removed.

## Loan recording and approval

1. Staff record a draft or a complete application in Loan records. Drafts are unassessed records and cannot be funded.
2. Configure ordered approval groups and the required number of different people for each product and branch inside Create Loan Product and Edit Loan Product → Approval Workflow. Custom loans also require this setup.
3. The recorder sends a complete recorded application for review. Required client checks and financial assessment must be present. A signed individual application is optional; guarantor evidence and signed group agreements retain their existing requirements. The application uses the latest approval workflow for its own loan product and branch; a missing workflow blocks submission. The system verifies that enough independent, authorized staff are available. Applications appear in the current configured group's loan review queue, then move to the next group after the required approvals. General Manager and Loan Officer receive applications only when selected by that product's workflow.
4. Each reviewer records a real decision, reason and evidence. The recorder, submitter and borrower cannot approve the application. This includes named shared borrowers and company representatives. One person can decide only once in a review round, including when they belong to multiple groups.
5. All required approvals make the unchanged application ready for disbursement. A rejection stops that round. A later round keeps the earlier decisions and freezes its own rule revision and terms.
6. Authorized staff record disbursement separately, using the existing accounting, proof, closed-period, idempotency and reconciliation safeguards. Approval itself moves no money.

Pending reviews retain their recorded rule revision when configuration changes. Current membership and permissions are checked when deciding. Database guards also reject incomplete, changed, out-of-order or self-approved requests. Existing unfinished applications can enter the new review flow; existing funded contracts are not repriced or reapproved.

## Group loans and signed paper

Staff can continue creating new individual, shared and organization group loans. The Signed papers page records the named signer, actual signature date, reference, language, staff recorder and original PDF. Shared payout details and personal guarantee capacity remain explicit. Organization authority, liability limits and funding checks still apply.

Signed papers create physical-document evidence. They do not fabricate a borrower login, an online document opening, an OTP challenge or a digital signature. Earlier online evidence remains immutable history. Original documents and reports retain their scope and export permissions. Exact retries reuse the recorded paper; changed retries are rejected.

Recording a client profile does not verify it. Independent checks and the product's configured KYC gate remain separate. Staff can record employee numbers for payroll lending; this does not grant the client access.

## Database cutover

`V154__staff_only_accounting.sql` snapshots the former borrower access flag and status, disables borrower access, retires borrower-only credentials, adds current group aliases and creates immutable loan review rules, requests and decisions. Unfinished borrower signups become unverified client records without credentials. Combined staff/client identities retain their staff credentials. The retired portal flag remains constrained false for database compatibility and historical evidence.

`V155__staff_recorded_group_papers.sql` adds immutable physical paper evidence and links it to the existing group liability and payout records. Historical digital evidence continues to use its existing validation rules.

Deploy from a clean build (`mvn clean verify`) with a verified database backup, and run Flyway through the application deployment process. A clean build removes compiled copies of the deleted controllers; do not clean the output of an application currently running from that directory. These migrations do not delete financial records or automatically assign new financial claims. Review group assignments and explicitly grant the required claims before routing new requests. A database backup is required for a full cutover rollback; restoring borrower credentials from application code is intentionally unsupported.

The accounting/report-builder backlog remains in `accounting-report-builder-checklist.md`. This change does not establish completion of that backlog or measured 1,000 requests/second capacity.

## Verification (2026-11-11)

The clean isolated build compiled 475 production files and 257 test files. The full unit suite passed 1,177 cases, including shared JSP compilation. The 380-case PostgreSQL regression run passed 379 cases; after correcting the remaining signed-document test fixture, its complete six-case approval class passed against a fresh database. The final cutover test and shared JavaScript syntax checks also passed. Testing used disposable PostgreSQL 18 databases and synthetic records.

After the dashboard cleanup, all 86 affected dashboard, access and record tests passed. Browser checks covered staff login/logout, direct branch access, group approval rules, client and application recording, new group cycles, signed-paper history, navigation and profile/notification controls. Desktop (1280 px) and mobile (390 px) layouts contained table scrolling without page-wide horizontal overflow. The signed-paper readiness callback and shared photo fallback were verified in the running isolated app. The operational application and database were not migrated during these checks.

Customized loans are recorded using staff-entered terms. Customized Loan Configuration contains only their branch approval workflow, with no Add Product step or pricing settings. Saving this workflow maintains an internal reference required by existing loan and approval foreign keys; this is not an editable loan product. Product creation permissions are not required to configure this workflow. Existing contracts and posted records remain unchanged.

Configuration follow-up (2026-11-12): a fresh isolated Java 25 build compiled all 476 production files and 257 test files. All 148 focused tests passed, covering populated staff-page rendering, JSP compilation, approval claims/CSRF, initial product workflows, revision conflicts and custom pricing independent of product limits. Shared editor and shell JavaScript syntax checks and four navigation cases passed. The local app was restarted from that build with unchanged migrations. Browser checks confirmed staff access pages and the approval-only custom form, including contained scrolling at 390 px. Browser connectivity failed before the remaining live product-editor checks; those pages passed Jasper compilation. No business approval plans, user permissions or financial records were changed for these checks.
