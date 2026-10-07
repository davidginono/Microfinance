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

Use Finance & Accounting → Chart of accounts in the existing sidebar. Add main groups, add a subgroup, add its account family, then add posting accounts. Example: 100000 → 120000 → 121000 → 121001 Land and 121002 Motor vehicles. Onboarding uses one **Name** field; existing Kiswahili metadata remains stored. All commands retain session authentication, CSRF and current permission checks. Validation failures keep entered values. Forms use a single column on mobile, and only the table viewport scrolls horizontally. Breadcrumbs use the shared shell placement and sticky styling below the top bar and above the page title, with links to their parent pages.

## Activity and transaction library architecture

Implemented on 2026-10-07: `Activity 1 → many TransactionCode`, `TransactionCode 1 → many immutable TemplateVersion`, and `TemplateVersion 1 → many PostingLineDefinition`. Institution-owned codes identify definitions; a journal reference identifies an individual occurrence. The library stores configuration only. It does not generate, approve or post journals, change balances, or replace the existing source-event mappings.

Use **Finance & Accounting → Transactions Config → Activities / Transactions**. The Activities Register has a top-right **Add New Activity** button opening a modal for code, name and description. The Transactions Register has **Add New Transaction** opening a modal for activity code, transaction details, accounting event, amount component, debit/credit account codes and version purpose. Both use one **Name** field. Save keeps a success modal open; close it with X to see the created record in its register. Validation errors preserve entered values in the open modal. An activity may have many transaction codes; each transaction belongs to exactly one activity. Codes are unique within the institution, uppercase, and immutable. The accountant saves definitions directly without an approval queue.

Transaction onboarding creates the transaction and its first immutable account template in one service transaction; an invalid account or component rolls back both. It requires the existing COA CREATE and UPDATE claims. The existing transaction-detail and **New template version** flows remain available for subsequent versions. Activity and account inputs offer institution-scoped, active-record lookup with a maximum of 25 suggestions per request; selectors do not confer ownership or posting authority.

An activity row's **Transactions** action opens `/finance/library/transactions?activityId=...`. The selected activity appears in the register's **Activity** filter rather than in a separate line above the register. Search and pagination preserve the activity; **All** removes only activity scope and **Clear** resets all filters. Filter options use the first bounded activity page, including inactive activities; the selected activity is always included even when outside that page. Existing activity-detail URLs redirect to this register after verifying institution ownership and retain their search/status/page parameters.

Transaction details use a fixed **Transaction templates** title. Activity, transaction, accounting event and status appear in filter controls, with a separate **Template version** selector above the posting lines. Existing detail URLs preselect the record's context; filter submissions use `/finance/library/templates`. Activity/event/status narrow institution-owned transaction choices, and a changed scope selects the first matching transaction or shows an empty result. Changing scope clears the previous version/page. Clear resets the filters. History links and pagination preserve the selected context and version. Options use bounded first pages plus the selected record, and the Transactions register remains the entry point for finding other codes. Breadcrumb parent links remain sticky below the top bar.

The template editor offers a bounded account-code lookup and up to five account pairs. Each named component (`TOTAL`, `PRINCIPAL`, `INTEREST`, `FEES`, `TAX`) may appear once; its debit and credit sides use the identical amount. Total must be used alone, rather than added to its parts. Account codes resolve to active institution-owned posting/control IDs. Headings and same-account pairs are rejected. Manual-journal definitions cannot use control accounts. Definitions accept no formulas, SQL, ratios, amounts, fees or pricing rules. Component names describe a future adapter's input; saving them does not prove that a workflow currently supplies those values.

Each save creates an immutable version with its accounting event, purpose, request reference, checksum, responsible accountant and recording time. The transaction retains its current version; previous versions remain viewable. A retry with the same actor/reference and exact payload returns the saved version. Changed retries and concurrent edits based on an old revision fail without replacing history. Source event and parent activity are immutable. Deactivation preserves templates; an activity with active transactions cannot be deactivated, and transaction reactivation requires an active activity.

`AccountingCodeLibraryController → AccountingCodeLibraryService → AccountingCodeLibraryRepository → gl_activity / gl_transaction_code / gl_template_version / gl_template_line`. V52 provides scoped uniqueness and composite foreign keys, immutable history/identity triggers, deferred balance/current-version checks, and transaction/activity lookup indexes. Publishing locks the activity, transaction and bounded account set in that order; lines use one bounded JDBC batch. Audit and all definition mutations commit together. No external work, executor, cache, or money-posting transaction is added.

The library shares the existing COA `VIEW`, `CREATE`, and `UPDATE` claims and their current staff/institution/branch checks. Metadata is institution-wide by design. All lists and account lookup use 25 rows plus a next-page sentinel; template line reads are bounded by five component pairs. Search output is bounded but substring searches may scan an institution's definitions. Session authentication, CSRF, allowlisted form fields, escaped output, localized errors and responsive shared controls remain in place.

V52 retains its original applied checksum (`1498718278`). V53 corrects the shared balance trigger using separate PL/pgSQL branches for the version and line record fields. Existing V52 installations upgrade normally; Flyway validation remains enabled and no schema-history repair is required.

Future posting integration must explicitly select and retain the immutable template version on each journal occurrence, obtain verified amounts from the owning workflow, and keep the ledger's current period, policy, source ownership, idempotency, subledger and reconciliation protections. That integration is outside the library onboarding forms implemented here.

## Verification and capacity

### Cloudscape UX adaptation (2026-10-07)

Reviewed the [create resource](https://cloudscape.design/patterns/resource-management/create/), [single-page create](https://cloudscape.design/patterns/resource-management/create/single-page-create/), [resource details](https://cloudscape.design/patterns/resource-management/details/), [details page](https://cloudscape.design/patterns/resource-management/details/details-page/), and [sub-resource create](https://cloudscape.design/patterns/resource-management/create/sub-resource-create/) guidance.

- Creation uses a bounded 960-pixel workspace, task-based sections, visible required configuration, expandable optional descriptions, verb-led page titles, matching breadcrumbs and Cancel/Create footer actions. Code/name ordering remains consistent with the registers. Parent-derived defaults and the existing account/event choices remain authoritative. Missing inputs receive validation rather than a disabled COA Create button.
- Details remain on one page because posting lines and history fit two related sections; tabs or a resource hub would add navigation without a separate task. The existing transaction filters retain record context as requested. Template creation belongs beside the posting-template heading; activation belongs with the selected transaction context. Creation and details share compact section headers and scoped responsive controls.
- Account pairs remain an embedded dependency saved atomically with a transaction. Existing activity/transaction register modals are retained under the user's explicit workflow requirement, an intentional adaptation of Cloudscape's full-page/sub-resource recommendations. No nested activity/account creation or new approval flow is introduced.
- Optional sections open when they contain values or errors. Server errors appear in a summary and beside bound fields with accessible associations; failed submissions retain values. Changed forms use the shared discard dialog on Cancel/navigation and a browser leave warning on reload/close. Duplicate submission protection complements existing service idempotency. Successful register creation still waits for X before returning to the register.
- The shared JSP shell, session/CSRF security, institution checks, bounded queries, immutable codes/history and financial-definition-only scope remain unchanged. Wide tables scroll inside their existing viewports; only form/filter presentation changes.

Relevant MVC/CSRF/claim tests, live PostgreSQL hierarchy/concurrency/rollback tests, and runtime JSP checks are recorded in the accounting checklist evidence log. This increment makes no throughput claim; sustained 1,000 requests/second remains unproven. Substring searches are bounded in output but may scan an institution's chart; measure real chart sizes before adopting additional search indexes.
