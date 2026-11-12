# Equity statement and Notes configuration

The 12 November 2026 instruction authorizes a component matrix, controlled movement types and institution-specific Notes. The reference image supplies a layout only; its funds, balances and percentages are not accounting policy.

## Staff workflow

Financial Reports owns Statement setup and Equity movements. Both require explicit FINANCIAL_REPORTS_VIEW and the new FINANCIAL_REPORTS_CONFIGURE claim. It appears as Financial Reports → Update in the access matrix. No role bundle or migration grants that claim. Institution setup is shared across its branches; journal classification is limited to the staff member's current branch. Live assignment/claim checks use GeneralLedgerService.requireActor.

Statement setup saves an immutable institution revision with ordered equity components, exact posting-account membership, optional retained-profit aggregation, controlled movement labels and numbered Notes. English and Kiswahili labels/text are supported. Components accept equity accounts only. Accounts cannot overlap components; Notes cannot overlap each other. A financial Note uses one account type and a closing-balance or period-movement basis. An equity closing-balance Note may explicitly include untransferred ledger profit, including as its only figure; this metric can belong to one Note only. Its amount comes from captured income/expense closing balances and its Note number also links the financial-position profit line. Text-only Notes have no accounts and require actual disclosure text. The accountant supplies policies and disclosures; no compliance declaration or reserve percentage is generated.

Approved COA equity-component labels remain the fallback for accounts without a configured override. Unclassified components remain explicit. The engine never omits their figures. A retained-profit component can combine its posted equity accounts with untransferred ledger profit; otherwise the latter has a separate column.

Equity movements lists posted journals with equity lines, scoped dates and fixed 30-row server pages. Staff select one journal and save a movement type with a reason through Actions. Types are capital added, owner distributions, reserve transfers, transfer of earned profit, other comprehensive income, other equity changes and unclassified. Optional custom labels retain the standard meaning in parentheses. Transfers between reserves require equity-only journals; transfers of earned profit require equity plus income/expense lines only. Capital additions, owner distributions and OCI cannot contain income/expense lines. Classification records do not amend journals or prove that an accounting policy is appropriate. Retrospective restatement is not inferred from an ordinary backdated journal.

## Ledger and history

New statement snapshots use version 3. Current and comparative matrices separately show opening balance, profit/loss, individual movement types and closing balance. Every component reconciles independently. Transfers retain signed debit/credit effects; a reserve transfer increases one component and decreases another with no net equity increase. Configuration accounts missing from the captured chart stop generation instead of becoming zero figures. Missing classifications remain visible and produce review checks even when offsetting unknown transactions net to zero. There is no automatic distribution or posting.

Explicitly classified earned-profit closing journals are removed from period income/expense movements for profit reporting. Their amounts remain in the raw trial balance and ledger and move between equity components. This avoids erasing earned profit or counting it twice after closing. Movement-based income/expense Notes use the same exclusion. Balance-based Notes always use posted closing balances. Generating a report makes no monetary mutation.

Setup, classified movement aggregates, both matrices, Note membership/text and source journal membership are frozen in the same existing bounded repeatable-read capture. Saved version 1/2 snapshots and retained export bytes remain readable. Later setup edits, classifications or backdated postings require a new preview; saved reports never recalculate from them. Screen, PDF, print and Excel consume one typed document. Existing export claims and institution aggregation restrictions remain.

V158 adds only append-only setup/classification tables and scope/history protections. It does not change accounts, journals, balances, loan contracts, old statement payloads or seeded permissions. Apply deployment migrations with the project's verified-backup and clean-build procedures; no operational migration is run by this increment. Deployment must include pending lower-numbered migrations before V158; do not apply a cherry-picked V158 ahead of them.

## Boundaries and verification

There are at most nine configured components, ten final equity columns, thirty Notes, 2,000 posting accounts, seven movement categories and 32,000 grouped account/category/period movement results. Queries use scoped IDs, parameters, deterministic bounded pages and existing export admission control. Setup saves serialize by institution; classification saves serialize by institution/branch/journal. Expected revision and exact retry checks prevent lost updates. No executor, network integration, monetary lock, cache or posting path is added.

Reports retain the existing provisional recorded-ledger notice. This feature does not establish verified institution openings, complete disclosures, an approved reporting framework, final accounts or audited compliance. Configuration cannot remove reconciliation totals or review warnings. No throughput benchmark has been run; 1,000 requests/second remains unproven.

Verification completed against isolated output and a temporary PostgreSQL 17.9 database:

- Maven compiled all 433 main sources. Final affected classes and the request-only commit interfaces also compiled after the last refinements.
- All 56 distinct focused checks passed across the initial runs and targeted reruns: 34 calculator/service/controller/export checks, 21 PostgreSQL checks and one Jasper check covering four statement views. The final setup/movement JSPs were compiled again after their last changes.
- A fresh database applied and validated 179 migrations through V158. Tests covered scoped append-only setup/classification, exact retries and concurrent saves, frozen older snapshots, earnings transfers, missing accounts, cleared Notes and unchanged journal balances.
- English/Kiswahili PDF and Excel samples passed export checks; rendered equity/Notes pages were visually inspected. JavaScript syntax and scoped diff checks passed. Test fixtures were corrected to use valid session CSRF tokens and active staff principals; production authentication was unchanged.
- EXPLAIN (ANALYZE, BUFFERS) on the small synthetic database confirmed bounded setup/journal/movement queries, with classification primary-key index access. PostgreSQL chose some sequential scans for the tiny fixture tables. These plans are diagnostic evidence only, not production capacity measurements.

The browser connection was unavailable, so live screen/mobile widths, login/logout and interactive navigation were not verified. The full test suite, packaged deployment and throughput benchmark were not run. No operational database or running application was changed. Temporary databases, compiler output, runner source and synthetic visual artifacts remain under ignored target/equity-statement-check/.
