# Financial statement workspace

User instruction: 15 October 2026, after the accounting reset. The six supplied screenshots are typography/layout references, not opening balances, institution policies, fund allocation percentages or regulatory specifications.

## Workflow

`Accountant Office → Financial statements → period and scope → Generate preview → statement tabs → Save snapshot → PDF / Excel / Print`.

The workspace at `/reports/financial/statements` provides financial position, profit or loss, direct cash flow, changes in equity, notes and a supporting trial balance. Report-wide controls default to the current calendar year through today; custom dates are supported. Comparative dates are the same dates one year earlier. A calendar-year preset does not create, close or reopen accounting periods.

Statement account mappings are separate from transaction posting templates. Each posting/control account has a statement line, compatible section, cash-flow activity, optional equity component and numbered note. Equal line/section/note definitions group accounts. Optimistic revisions prevent overwriting concurrent mapping edits. Mappings never post money.

Details on a financial-position line (for example Investments) resolves that frozen line's exact account IDs to a filtered trial balance. Its account Details opens the captured account activity. A filtered trial is not expected to balance by itself. No client-supplied account set controls scope. Institution-wide permission permits aggregation and aggregate trial details; it does not permit cross-branch transaction registries.

The report paper uses Arial/Helvetica for ordinary statements, Times for trial/ledger pages and Courier for the equity matrix, with the reference's red equity title. It retains grouped headings, numbered notes, comparison columns, accounting parentheses, tabular amounts and underlined totals. Preview-only font/density/comparison preferences contain no record data. PDF/Excel always use the reference layout. The enclosing JSP workspace retains the shared ERP shell and resource tables. Global destinations remain in the sidebar.

## Source and calculation boundaries

Every generation runs inside one bounded PostgreSQL repeatable-read transaction. It freezes account/mapping/identity/balance data and exact journal membership for current/comparison activity. Opening balances are aggregated over the same visible posted-ledger snapshot; old journal histories are not loaded into application memory. Subsequent backdated postings, mapping changes and account-name changes cannot alter the saved figures or drill-down membership. Request keys bind retries to actor, scope and dates.

Only posted GL journal lines count. Receipt envelopes do not create another reporting amount. Original entries and linked corrections remain in the books. Profit is calculated from income/expense movements; residual untransferred income/expense balances enter financial position and equity exactly once. Generating reports makes no closing entry or monetary mutation.

Direct cash flow offsets cash/bank/mobile-money movements against their noncash counterparts and classifies those counterparts by statement mappings. Transfers entirely within these money accounts are excluded. Mixed journals that cannot be allocated without assumptions produce explicit exception checks and a cash reconciliation difference; the engine does not invent allocations. This is a direct-method implementation using the supplied operating/investing/financing layout, rather than copying the reference's surplus-based indirect calculation.

Checks cover current/comparative trial balance, financial position, cash and equity agreement, missing statement/cash classifications and ambiguous cash movements. Full source openings, accounting recognition and reconciliation are not verified by the surviving application after the reset. Consequently all outputs explicitly state **Provisional — recorded ledger only**, including saved snapshots. Recorded-ledger brought-forward amounts are not presented as independently verified full institution balances. Saving does not certify completeness, finalize accounts or fabricate approval evidence.

## Documents and retention

The user's latest instruction explicitly enables Excel for this workspace, its trial/ledger details and statement packages. Other voucher/operational exporters and historical non-PDF download restrictions are unchanged. There is no CSV output.

Preview, PDF and XLSX consume the same typed frozen data. Details controls and source links are omitted from PDF/XLSX. Print serves the PDF inline. Excel uses real numeric money cells, accounting number formats, repeated headings, frozen panes and A4 fit-to-width print areas. Amounts exceeding Excel's 15-digit numeric precision are retained as text instead of silently losing digits. User text is written as text, never executable formulas. Statement packages contain all statements, notes and the trial balance.

Full-statement PDF/Excel artifacts are retained with checksums and language. Scope/claims and checksums are rechecked on downloads. Filtered trial and ledger documents are reproducible from the immutable snapshot and journal membership. Database triggers prohibit changing/deleting captured figures, changing journal membership or replacing retained artifacts. Archived financial history is untouched.

## Operational limits

Generation accepts at most a 366-day span, 2,000 posting/control accounts, 100,000 captured period/comparison journals and 20,000 cash-counterpart groups. Account activity is paged at 30 rows; ledger exports cap at 5,000 rows. Oversized selections fail visibly instead of producing partial output. Output work uses the existing export admission limiter; no new executor or external integration is introduced. This is not a measured throughput claim.

## Verification

Focused calculation, document, PostgreSQL, browser and visual evidence is recorded in the implementation's final verification notes and `design-qa.md`. Existing unrelated working-tree changes must remain outside this request's commit.

The final focused set contains 22 checks: calculation, individual and combined PDF/XLSX packages, PostgreSQL snapshot/drill-down/scoping/retry protections, reset-route compatibility and ledger access. Standard Maven compilation passes. Browser checks used a separate local application and synthetic database, covering normal password/OTP sign-in, generation, all six tabs, saving, mapping edits, exact-account drill-downs, downloads, view preferences, selection clearing and responsive widths of 360/768/1366/1920px.

The full-suite run was not green: 1,028 tests, 11 failures, one error and 87 skipped. Subsequently corrected the new export GET-form contract classification: download forms are not search filters. Repeating the two affected generic table-contract classes after their narrow financial-stationery exemption reports eight remaining failures on existing views/assets. Other full-run failures concern existing dashboard/guarantor views and a loan-product controller fixture. Logs and synthetic visual artifacts are retained under ignored `target/statement-workspace/`; no production balances or load-test result are implied.
