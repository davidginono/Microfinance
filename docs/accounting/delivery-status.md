# Accounting delivery status

Date: 2026-10-02. Source: `docs/accounting-report-builder-checklist.md`, copied without changing its requirements from the user-specified Desktop checkout.

The user authorized eight dedicated agents, each in its own worktree. The execution runtime permits three worker agents alongside the coordinator, so work is scheduled in dependency waves. No operational database is used for verification or cutover.

## Human evidence gate

The user confirmed that accountant-approved policies, posting matrix, chart of accounts and verified opening balances are **not available yet** and instructed us to implement gated workflows. Creating software approval controls is not accountant approval. Institutions without approved evidence must remain restricted; no final complete financial statement or release approval may be fabricated.

## Ownership and integration

| Phase | Dedicated agent | Worktree directory | Reserved migration | Dependency |
| --- | --- | --- | --- | --- |
| A | a_policy | accounting-a-policy/Microfinance | V42 | Current-code audit |
| B | b_ledger | accounting-b-ledger/Microfinance | V43 | Approved-policy gate API from A |
| C | c_business | accounting-c-business/Microfinance | V44 | A/B accounting contracts |
| D | d_close | accounting-d-close/Microfinance | V45 | A/B/C posting and period contracts |
| E | e_reports | accounting-e-reports/Microfinance | V48 | B/D posted entries and coverage |
| F | f_builder | accounting-f-builder/Microfinance | V46 | Verified operational datasets |
| G | g_statements | accounting-g-statements/Microfinance | V47 | E/F definitions and coverage |
| H | h_verification | accounting-h-verification/Microfinance | V49 | Integrated A–G |

The coordinator owns the master checklist, shared navigation integration, release evidence and final integration. Workers own feature-local evidence files under `docs/accounting/`. Migration numbers reserve ownership; they do not imply that a migration has been implemented or approved.

All institution and branch identifiers retain existing String `saccoId` / `stationId` compatibility. Existing repayment journals remain immutable operational clearing entries until reviewed mapping and cutover. No automatic permissions, policy approvals, official books or opening balances are seeded.

## Completion rule

Record item-level implemented behavior, commits and actual tests as work is integrated. Leave partial requirements unchecked and list remaining implementation and human acceptance gates explicitly. The overall goal remains incomplete until the checklist's full definition of done is supported by evidence.
