# Loan report references

The three reports extend the existing Financial Reports management workspace. They use the supplied Disbursed Loans Audit, Expected Repayments and Loan Repayment Transactions PDFs as visual references, not as instructions or evidence of institution policy. Existing JSP controls surround white paper previews with sans-serif titles, serif column headings, monospaced rows, grouped underlined totals and repeated export headings. Wide paper tables scroll within the preview on phones. PDF and print remain the only outputs.

## Routes and meaning

All routes start with /reports/financial/operations/ and retain /pdf and /print outputs.

| Report | Suffix | Meaning |
| --- | --- | --- |
| Disbursed loans audit | disbursed-loans-audit | One local-ledger loan per row, filtered by disbursement date, grouped by month and product, with product, monthly and grand totals. Principal, contractual interest, largest contractual instalment, frequency, maturity and period count come from checked contract/ledger sources. Instalments are not totalled across different frequencies. A recorded disbursement account/reference appears only with its posted owning journal. This is gross principal, not net cash after charges. |
| Expected repayments | expected-repayments | One unpaid contractual instalment per row within the selected due dates. Remaining principal and interest subtract immutable signed allocations, future-interest exclusions and dated waivers. The principal balance repeats for context and is never totalled. Fees and penalties are omitted; this is neither an earned-interest demand nor a settlement quote. Cancelled and written-off sources are excluded until their revised obligations can be established. |
| Loan repayment transactions | loan-repayment-transactions | Receipts and linked reversals grouped by client and loan. Negative correction amounts retain their original receipt reference. Opening/closing principal uses the complete scoped event history before the date filter, including posted principal adjustments. Only cash principal/interest and noncash interest exclusions are totalled; balances are not. Charges retain their separate ledger. Loans without matched transactions produce no transaction rows. |

Employer, department, cheque-only, age, gender and ambiguous default-balance fields are not fabricated. These reports target individual clients. Group/no-client sources and unsupported legacy/top-up histories remain counted exclusions. This increment changes no loan transitions, posting, pricing, institution policy, permissions, schema or financial records.

## Scope, evidence and bounds

The existing ManagementReportController -> ManagementReportService -> ManagementReportRepository path owns each use case. Existing fresh FINANCIAL_REPORTS_VIEW and LOAN_REPORTS_VIEW claims and institution/branch checks apply. PDF and print additionally require FINANCIAL_REPORTS_EXPORT. No claims are granted. Client selectors remain scoped and outputs remain no-store. Bounded native SQL reuses LoanPortfolioReportRepository's posted-origin, immutable allocation/journal, contract-completeness and missing-history checks; no per-row database calls or external work is added.

Expected repayment dates may extend up to 366 days ahead; every selected range remains at most 366 days. Its accounting knowledge stops at the earlier of the range end and the recorded cutoff's Tanzania date, even for a future due window. Other report dates cannot extend beyond today. Preview and export share the recorded cutoff, parameters, complete filtered totals and integrity rules. These remain live management reports rather than retained/finalized statements; client/product display names may change later.

Previews fetch 25 rows plus lookahead, exports cap at 5,000 rows, groups cap at 1,000, and the existing admission limiter and 30-second repeatable-read read transaction are retained. Empty results with excluded loan history keep monetary totals unavailable rather than asserting a zero receivable. Counts of excluded history describe the branch before client/product filters. Partial totals cover supported records only. No new thread, executor, cache, row lock or write transaction is introduced. Capacity at 1,000 requests/second remains unmeasured.

## Verification

Verification uses disposable loopback PostgreSQL data and synthetic browser fixtures. Focused tests cover partial payments, linked reversals, running principal before the filter, future due windows with past recorded cutoffs, branch isolation, incomplete origins, pagination/full totals, controller/export authorization paths and bilingual multipage PDFs. Results and remaining limits are recorded in the accounting checklist evidence log. Temporary classes, databases, preview servers, PDFs and screenshots stay under ignored target/.

## Validation environment and limits

The focused Java 25 checks run through JUnit Platform against isolated compiled report classes and the existing application classes, because the clean Maven build fails in unchanged GroupCycleService code: calls to ChargeQuote.supported(), total() and deductions() no longer match that DTO. The affected report classes compile. This is not a successful full-suite or packaged-build result, and the application must load a corrected build before the new routes are operational.

The PostgreSQL 17.9 fixture uses a disposable loopback database on port 55447, with 172 migrations through V151. Its sequential checks reuse one connection to avoid local connection-handshake delays; production datasource settings are unchanged. The three EXPLAIN (ANALYZE, BUFFERS) samples used 31 scoped synthetic loans in a database with about 100 loans while local verification jobs were running. Execution times were 157.676 ms (audit), 305.639 ms (expected) and 301.062 ms (transactions), with planning times of 839.586, 853.159 and 681.662 ms. Existing repayment, allocation, waiver and posting indexes were visible in the plans; tiny fixture tables also used sequential scans. These samples justify no new index or throughput claim. Production-sized plan and load validation remain open.

Browser checks use the real report JSP/document fragments with synthetic service data and a fixture shell on 127.0.0.1:8087. The Browser plugin is unavailable; desktop browser discovery timed out, so installed Edge was driven with Playwright. English and Kiswahili previews were checked at 360/768/1366/1920 pixels (24 layouts), without page overflow. Paper tables scroll inside the preview. Generation, 25/3-row pagination, unchanged full totals/cutoff, a complete PDF response and a 28-row print document were checked. The fixture supplies shared selector initialization and permits export controls; live login/logout, full-shell navigation and production permission acceptance were not browser-tested. Controller and service checks remain authoritative for authorization. Bilingual multipage PDF checks exercise all eight fixed report kinds, long names, large amounts, repeated headings and final totals; Poppler samples were visually inspected.

Final focused result: 28 checks passed (11 unit/controller/PDF checks and 17 PostgreSQL checks). Git whitespace and matching new English/Kiswahili message-key checks passed.
