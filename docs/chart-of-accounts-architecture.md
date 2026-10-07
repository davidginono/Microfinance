# Chart of accounts onboarding

Implemented for the user-requested direct-accountant setup on 2026-10-07. Account creation and activation have no approval stage. This increment creates account metadata; it does not initialize balances or change the existing journal, repayment, policy, reconciliation, closing, or release workflows.

## Ownership and permission model

One chart belongs to one institution (`sacco_id`), shared by its branches. Account metadata has no branch reference by design; balances and journal visibility retain their historical branch dimensions. An active, scoped staff session with current COA claims is required. The accountant role bundle now includes `ACCOUNTING_ACCOUNTS_VIEW`, `ACCOUNTING_ACCOUNTS_CREATE`, and `ACCOUNTING_ACCOUNTS_UPDATE`. V51 adds these claims to existing active accountants with materialized claim bundles, preserving their other claims. Subsequent explicit revocation remains authoritative. Sign in again to refresh an existing session after migration.

## Hierarchy and codes

| Level | Format | Example | Posting |
| --- | --- | --- | --- |
| Main group | G00000 | 100000 Assets | No |
| Subgroup | GS0000 | 120000 Non-current assets | No |
| Account family | GSF000 | 121000 Property, plant and equipment | No |
| Posting/control account | GSFNNN | 121001 Land — cost | Yes, according to account kind |

G is 1 Assets, 2 Liabilities, 3 Equity, 4 Expenses, or 5 Income. S and F range from 1 to 9; NNN ranges from 001 to 999. Heading groups use the existing `HEADING` kind, ordinary accounts `POSTING`, and subledger accounts `CONTROL`. Main groups are initialized by an explicit, repeatable accountant command. Existing codes are never reassigned; incompatible main-group codes stop the entire setup transaction.

The parent ID is authoritative. Onboarding validates both the six-digit code and the actual active institution-owned ancestor chain. Classification is derived from the parent, rather than accepted from a form. Debit is the default for assets/expenses, credit for liabilities/equity/income. Posting accounts may use the opposite normal balance for contra accounts. Loan principal, interest/fee receivables and allowances retain mandatory control-account restrictions.

Existing alphanumeric accounts remain visible and retain their identifiers, mappings and balances. New six-digit onboarding requires parents in the new hierarchy; it does not reparent or renumber legacy records. Report mappings and current/non-current maturity treatment remain separate accounting definitions.

The existing POST `/finance/accounts` command is retained for compatibility with earlier integrations/forms. It keeps the original account validation and authorization; the new onboarding commands additionally enforce the six-digit hierarchy and derive classification on the server.

## Request and persistence architecture

`ChartOfAccountsPageController → GeneralLedgerService → GeneralLedgerRepository → gl_account`

- `AccountOnboardingForm` allowlists parent ID, code, names, balance, kind, purpose and description. Institution, classification, IDs and activation metadata are server-owned.
- `AccountFilter`, `AccountRow` and the existing `Page` DTO provide a bounded registry and its parent labels.
- V51 adds optional `name_sw` and `description` columns and active-parent/heading lookup indexes. The existing institution/code uniqueness and cross-institution parent foreign key remain authoritative.
- Setup/create/deactivate/reactivate and their audit event commit in one transaction. The existing institution setup lock serializes low-volume chart configuration and code conflicts. No file work, external calls, new executors or asynchronous jobs are introduced.
- Code suggestions find the first unused code using a bounded PostgreSQL series (nine group slots or 999 posting slots). Suggestions do not reserve a code. A concurrent duplicate receives an inline error and preserves its values.
- Registry and parent searches fetch at most 26 records to display 25 with a next-page indicator. Parent labels use one scoped join; there are no per-row repository calls. Ancestor reads are bounded by the three heading levels.
- Posted account codes, classification and hierarchy remain immutable under the existing database protection. Referenced accounts are deactivated, never deleted. Main groups cannot be deactivated; groups with active children cannot be deactivated; reactivation requires an active parent.

## Forms and routes

| Method | Route | Function |
| --- | --- | --- |
| GET | /finance/accounts | Search/filter/paginate groups and accounts |
| POST | /finance/accounts/initialize | Add missing main groups atomically |
| GET | /finance/accounts/groups/new | Search/select a parent and enter a subgroup/family |
| POST | /finance/accounts/groups | Save an active heading |
| GET | /finance/accounts/posting/new | Search/select a family and enter a posting/control account |
| POST | /finance/accounts/posting | Save an active posting/control account |
| POST | /finance/accounts/{id}/deactivate | Deactivate while preserving history |
| POST | /finance/accounts/{id}/reactivate | Restore availability |

Use Finance & Accounting → Chart of accounts in the existing sidebar. Add main groups, add a subgroup, add its account family, then add posting accounts. Example: 100000 → 120000 → 121000 → 121001 Land and 121002 Motor vehicles. Names use English with optional Kiswahili metadata; translated screens fall back to the English account name when a Kiswahili name is unavailable. All commands retain session authentication, CSRF and current permission checks. Validation failures keep entered values. Forms use a single column on mobile, and only the table viewport scrolls horizontally.

## Activity and transaction library architecture

The next separate component should model `Activity 1 → many TransactionDefinition`, `TransactionDefinition 1 → many immutable TemplateVersion`, and `TemplateVersion 1 → many PostingLineDefinition`. Institution-owned codes identify definitions; a journal reference identifies an individual occurrence. Posting lines use controlled debit/credit sides, account IDs or constrained account selectors, and named amount components from the owning workflow. They must never execute user-entered formulas or SQL or recalculate loan allocations.

An accountant directly saves/activates a definition and publishes a new version when its rules change. Each journal retains the exact template version. Existing `PostingEvent` values remain the source-event compatibility boundary. Library forms and posting integration are not implemented by this COA onboarding increment.

## Verification and capacity

Relevant MVC/CSRF/claim tests, live PostgreSQL hierarchy/concurrency/rollback tests, and runtime JSP checks are recorded in the accounting checklist evidence log. This increment makes no throughput claim; sustained 1,000 requests/second remains unproven. Substring searches are bounded in output but may scan an institution's chart; measure real chart sizes before adopting additional search indexes.
