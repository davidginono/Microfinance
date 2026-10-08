# Core accounting vouchers

User mandate: 2026-10-08. Receipt, payment and journal vouchers post directly, in one transaction, into the existing general ledger. No submit, verify, checker, policy approval or accounting release approval is required for these core vouchers. Ordinary accounting validation is not an approval workflow.

## Resource structure

- `/finance/vouchers/receipts`, `/payments`, `/journals`: separate institution/branch-scoped registers, 25 records per page, date/search/status/sort filters and single-row selection. View/PDF/Print are enabled only for one selected record, with an independent Create action.
- `/{kind}/new`: focused full-page creation, primary header fields, optional context and 1–50 numbered transactions. Preview resolves balanced entries on the server without saving; Post creates all entries atomically. A shared money account is used for every receipt/payment transaction. Journal transactions each supply a debit and credit account; split amounts into pairs for compound entries.
- `/{kind}/{id}`: immutable document details, account snapshots, exact TZS totals, poster/time, PDF, printable document, and linked direct reversal. Reversals preserve original evidence and swap all entries, not overwrite them. The authorized accountant may reverse their own posting; there is no checker requirement.
- `/{id}/pdf` and `/{id}/print`: authorization and branch scope are checked again. PDF uses embedded Noto Sans, institution/branch identity, a teal document masthead, transaction/account details, totals, repeated headings and page numbers. Print has a clean A4 stylesheet and an explicit Print button.

## Posting boundaries

All monetary arithmetic is decimal, server-calculated and limited to two fractional digits. Each transaction creates a debit/credit pair. Both journal and voucher are append-only and must agree. A stable request key with exact payload/actor matching returns the original posting on retries; changed payloads are rejected. Receipt/payment references are unique within the branch and money account. A linked reversal is permitted once. Audit failure rolls the entire transaction back, including outbox and document records.

Accounts are active, institution-scoped ordinary `POSTING` accounts, not headings or loan controls. Receipts debit a CASH/BANK/MOBILE_MONEY account; payments credit it. Transfers between money accounts use journals. Configured transaction templates may be selected per row/component; mappings are resolved and locked on the server, the selected template revision must still match, and the actual account names/codes are snapshotted. No client-supplied total, mapping or approval is authoritative.

An existing open accounting period is shared-locked while posting. When no period covers the date, core accounting creates a one-day open period under the existing setup advisory lock; it cannot post in a closed period or open a date before a later close. Direct journals have a distinct `direct_post`/`CORE_*` identity and no invented policy/checker records. Historical policy-backed journals keep their existing constraints.

Generic vouchers do **not** post loan repayments/disbursements, calculate fees, reconcile bank channels, establish historical opening balances, or certify financial statements. Loan controls and loan subledgers must use their owning services. These features do not finish the larger accounting/report-builder checklist. Branch display uses the existing branch location, with branch ID fallback because the current branch entity has no dedicated display-name field.

## Permissions and output policy

Core voucher access uses existing BUSINESS_VIEW/CREATE/REVERSE and JOURNALS_VIEW/CREATE/REVERSE claims; PDF/print additionally require FINANCIAL_REPORTS_EXPORT. Active accountant defaults and existing materialized accountant claims receive these capabilities in V55. Current permissions/workspace status are rechecked in services; subsequent explicit revocation remains effective. No lending claims are granted.

Accounting/report exporters and queued report requests accept only PDF. Retained CSV/XLSX enum values identify historical records, not enabled output formats. Historical non-PDF downloads are blocked rather than deleting retained evidence. Existing import formats are not exports and are not changed by this policy.

## Permanent UX references

Use [Cloudscape create](https://cloudscape.design/patterns/resource-management/create/), [table view](https://cloudscape.design/patterns/resource-management/view/table-view/), and [details](https://cloudscape.design/patterns/resource-management/details/) as behavioral architecture references, implemented in the maintained JSP/shared ERP shell. Keep a bounded create workspace, full-width registers, concise descriptions, optional information progressively disclosed, responsive repeated rows, keyboard selection, dirty-exit protection and resource-specific actions. Use an explicit shared table titlebar to keep View/PDF/Print/Create together, with a screen-reader selection announcement and pagination outside the scroll viewport. Do not introduce React merely to copy these patterns.

## Verification

Validated all 71 migrations on an isolated PostgreSQL 17 database through V55. Focused tests cover decimal bounds, direct multi-row posting, configured template revisions, retries/concurrent duplicates, immutable records, linked reversal, revoked permissions, foreign branches, closed periods, audit rollback, CSRF/binding limits, JSP compilation and PDF-only report output. Voucher PDF tests exercise English/Kiswahili, all three types, 50 rows, long descriptions and large amounts; first/last pages were rendered and inspected.

Live Playwright checks use synthetic staff and accounts in the disposable database: create two-row receipts/payments/journals, preview, post, view, PDF, print, add/remove, dirty-exit protection, row selection/replacement, filter reset/no matches and direct reversal. Desktop/mobile screens were inspected with no page-level horizontal overflow. An absent profile photo on the synthetic staff account returns the existing expected `/profile/image/me` 404; no voucher script errors were observed. This is focused verification, not a full-suite, load-test or regulatory-certification claim.
