# Simple staff-recorded group loans

This increment replaces the former individual/shared/organization group-origination choices, loan cycles, borrowing mandates, reciprocal guarantees and coverage workspaces. Staff approval groups remain the institution's ordinary loan approval mechanism.

## Staff flow

1. Open Groups, create a group, and register clients through the shared client form or add existing scoped clients. Registration creates a real membership record and never creates client credentials.
2. Choose one active client as leader. Record the checked group bank/mobile-money account and its evidence reference.
3. Open Record loan and choose Individual or Group. Group borrowing includes the complete current eligible roster, with a minimum of two and maximum of 50 clients. Unavailable or out-of-branch members prevent submission.
4. Enter one total amount and the existing configured/customised terms, including the first payment date. Each client needs a recorded budget, income evidence and assessment reference. Configured identity, product-document, collateral and independent approval checks still apply.
5. Record each outside debt once with its evidence reference, monthly payment and all clients who owe it. Each client's outside-debt allocation must reconcile to their recorded monthly debt after known system obligations. Known loans are deduplicated by Loan ID. Weekly payments use the calculator's monthly conversion and conservative cent rounding.
6. Upload the actual agreement signed by every borrower. Record its reference, language and each signature date; confirm the roster, terms, joint responsibility and payout instruction. Separately required guarantors must be registered clients outside the borrowing roster, with signed consent and checked capacity. Borrowers are never added automatically as guarantors.
7. Save a draft or record the application. Drafts retain uploads and require renewed confirmation. Material changes invalidate retained agreement/consent evidence. Recording freezes the roster, leader, account, assessments, original PDF and contractual terms. Normal configured staff review follows; approval moves no money.
8. The authorized funding action posts one parent loan to the existing ledger and journals, using the frozen group destination and ordinary payment evidence requirements. Record all repayments against that Loan ID and name the actual payer.

The leader is the contact. The joint group is the borrower identity. There are no member principal shares, member loan accounts or extra receivables.

## Five-client example

Asha, Juma, Neema, Peter and Rehema sign one TZS 5,000,000 contract. For an illustrative zero-interest ten-payment contract, each instalment is TZS 500,000. One TZS 5,000,000 payout opens one ledger. A posted TZS 400,000 payment leaves TZS 100,000 of the due instalment unpaid. Rehema's posted TZS 100,000 payment clears that shortfall and reduces the same principal balance to TZS 4,500,000. Neither a second loan nor an invented payment is created.

The existing arrears calculation continues to classify an uncovered due payment. Covering it requires actual posted money. This preserves the uncovered group-payment treatment in regulation 44(3) of the [BoT 2019 non-deposit-taking microfinance regulations](https://www.bot.go.tz/Publications/Acts%2C%20Regulations%2C%20Circulars%2C%20Guidelines/Regulations/en/2020021122490967551.pdf). This implementation is not a certification of the institution's lending policy or contract.

## Storage, scope and history

V157 adds immutable STAFF_GROUP_V1 contracts, named borrower/profile revisions, signed external guarantees, funding links, explicit withdrawals and scoped drafts. Server-side roster resolution, expected group revisions, request fingerprints, sorted client capacity locks and database guards protect submission and posting. Existing explicit claims are checked live; none are assigned by this migration. Group-account changes are audited and affect future contracts only.

Each borrower carries the full joint obligation for later credit/guarantee assessments. The accounting ledger and portfolio reports still count the actual loan once. The report contact view contains one row per loan, using the frozen group name/leader. Balances and accounting totals continue to come from posted financial sources, with missing history retained as unknown.

Legacy unfunded applications are left in their recorded states but cannot submit, approve or receive new funding. Staff may explicitly withdraw them from loan details and recreate them through the new form. No automatic conversion, repricing or status rewrite occurs. Existing funded loans retain repayment, early-settlement, attribution and independent-correction behavior. Their original group PDFs are available through ordinary loan details/history, subject to document and historical coverage evidence claims. Historical organization PDF readers and shared monetary allocators remain only to service those contracts. Old tables and enum/claim identifiers remain protected compatibility data.

Payment corrections on funded loans do not restart application assessment or approval. They retain the original contract and linked reversal rules, including when a borrower's later assignment changes.

## Verification and deployment

Use isolated PostgreSQL databases only. The synthetic V156 fixture in test resources was generated through the prior guarded staff workflow; it contains no live customers. Its restore helper is strictly test-only. New-flow tests cover the five-client example, stale rosters/accounts, missing signatures/budgets, shared outside debt, idempotency, concurrent requests, branch isolation, statements/reports, early payment and linked corrections. Historical servicing has a separate database gate.

Build output is directed to target/group-loan-releaseverify so a running application under the ordinary target directory is untouched. Release verification results are recorded after execution below. No 1,000-RPS capacity claim is made.

Before a live rollout, verify a restorable database backup and deploy the clean package with V157. Do not remove the historical financial tables, repair migration checksums on a live database, or run the synthetic test fixture there.

Verification on 2026-11-13: 237 selected tests passed with no skips across the main run and the focused approval-fixture retest. This includes 146 isolated PostgreSQL cases, unit/MVC/document tests and compilation of all 17 affected JSP pages. The application and test classes were rebuilt in the separate clean output directory; the final verify run packages that output. The fixture corrections create real registered clients and preserve production identity and budget validation.

Browser checks used a separate synthetic database and actual staff sessions. English and Kiswahili group forms passed at 360, 768, 1366 and 1920 pixels with no horizontal overflow. Checks covered keyboard selection, retained draft values, real memberships, signed-PDF download, loan details, independent review, read-only approved funding terms, mobile navigation, dropdowns, login and logout. Browser checks posted no money. The five-client funding and repayment example is proved by the isolated database tests.

An earlier broader run also passed the credit-control, prepayment, reconciliation and payroll-feedback database suites. That run was stopped during the all-page JSP sweep; it is not reported as a full-suite pass. The affected-page sweep and relevant tests above are the completed verification scope. No throughput measurement or capacity claim was made.
