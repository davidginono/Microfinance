# Phase E financial and management reporting evidence

Date: 2026-10-02. Dedicated owner: e_reports; worktree accounting-e-reports/Microfinance. The coordinator progressed this prerequisite while the worker was stopped by the account usage limit. Phase E remains incomplete.

## Verified posted-book read increment

E01 backend exposes branch-scoped trial balance and account activity from actual POSTED general-ledger entries. Both effective date and recorded/posted cutoff are applied; read transactions use REPEATABLE_READ. Imported end-of-day OPENING entries form opening balances even when their date is the reporting start and are excluded from period movements. Activity preserves immutable source, evidence and linked reversal identifiers. Queries return 25 rows plus a next-page sentinel; totals aggregate the full authorized scope independently of pagination.

FINANCIAL_REPORTS_VIEW is checked against current active staff, institution, branch and effective permissions. Institution totals additionally require FINANCIAL_REPORTS_INSTITUTION. Claims are explicit with no default grants. Branch deactivation does not erase historical institution coverage requirements. Incomplete opening/subledger/operational-voucher coverage keeps opening and closing balances unknown; movements remain available without claiming a complete financial statement.

Verification: `mvn '-Dtest=LedgerReportServiceTest,LedgerReportPostgresTest' test` with MICROFINANCE_ACCOUNTING_B_DATABASE_URL set to the disposable loopback microfinance_accounting_b_test database. BUILD SUCCESS at 14:10:47 EAT: **10 passed, zero failures/errors/skips**, including **four PostgreSQL 17.9 scenarios**. Flyway validated 45 existing migrations. This increment required no schema change. The earlier compile succeeded at 14:05:13 EAT.

Actual scenarios cover reviewed 1000.01 opening cash plus 1.01 current movement; an additional later-recorded backdated 2.00 posting excluded at the saved cutoff; trial-balance equality; retained linked correcting entries netting back to opening; branch amounts and foreign account isolation; unknown openings and inactive historical branch coverage; revoked/moved/inactive/suspended staff scope; separate institution permission; bounded dates/cutoffs/pages. Synthetic policies, accounts and reviews exist only in disposable tests.

## Verified financial page increment

The controller and localized JSP add trial-balance/account activity filters, retained cutoff drill-down, full-scope totals, signed TZS balances, unknown coverage states and permission-aware source journal links. The sidebar contains the destination; no alternate global page-body launcher was added. Journal review/posting controls are shown only for the appropriate independent reviewer.

The dedicated command `mvn -Dtest=LedgerReportServiceTest,LedgerReportPostgresTest,LedgerReportPageControllerTest,MemberLocaleInterceptorTest test` passed **19 scenarios, zero failures/errors/skips**, finishing at 14:35:14 EAT, including four PostgreSQL scenarios and three MVC authorization/filter/error scenarios.

Actual browser interactions on a disposable loopback database used two separate synthetic staff sessions to review a synthetic policy, create cash/capital accounts, import/preview/save opening entries, independently attest and post the opening, then independently approve/post a manual movement. The posted cash row reconciled 1000.01 opening + 1.01 debit - 0.00 credit = 1001.02 closing; drill-down excluded the opening from period movements. Synthetic approvals have no institutional/compliance significance and never change operational data. A separate approved-but-unposted test journal remains preserved.

Eight restricted trial-balance renders and **16 populated trial/account-activity renders** passed in English/Kiswahili at 360/768/1366/1920 pixels, HTTP200, saved language correct, document scroll width equal to viewport width, and no JavaScript errors. The populated matrix finished at 14:57:20 EAT. Actual browser checks also returned HTTP403 for another institution and another branch guessing the opening journal; the maker had no approval command, and only the independent reviewer saw the post command (finished 14:57:46 EAT). Mobile and desktop screenshots were visually inspected. Explicit localized table labels were subsequently added to avoid the shell default English suffix; the final 16-page matrix also passed at 15:16:46 EAT with zero JavaScript errors or page overflow. Final mobile/desktop Kiswahili screenshots were visually inspected.

Ephemeral artifacts: `%TEMP%/microfinance-financial-flow-evidence.json`, `microfinance-financial-populated-browser-evidence.json`, `microfinance-financial-access-evidence.json` and corresponding scripts/logs/screenshots. Browser runtime: bundled Playwright1.62.1/Chromium; Browser plugin unavailable. This does not complete export/print, D/G/C report reconciliation, or full H10 coverage.

## Outstanding full phase requirements

E01 still requires export and integrated source acceptance; rendered report/drill-down flows are verified above. E02/E03/E07 require reviewed D/G statement integration and complete C historical control reconciliation. E04/E05 require verified management ageing, PAR, classification, provision, write-off/recovery, exception and branch metrics with published definitions; F collections/portfolio alone do not complete these. E06 requires current prescribed regulatory classification/format evidence. E08 comparisons, approved budgets where implemented, localized reporting metadata and final/reproducible state remain outstanding. No master checklist item or full phase is marked complete by this increment.
