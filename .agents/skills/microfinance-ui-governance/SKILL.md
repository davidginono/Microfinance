---
name: microfinance-ui-governance
description: Design, edit, or review Microfinance JSP navigation, resource creation and details, accounting onboarding, lending reviews, and financial-action states. Use for domain-specific interface work, not standalone backend changes.
---

# Microfinance UI Governance

## Domain And Compatibility

- Use Institution, Branch, Client, Borrower, Loan Product, Guarantor, Repayment, and Collateral in visible text. Use Client Number for the existing `memberNo` when it identifies a borrower; retain Staff Number for staff.
- Keep existing entity names, routes, permission identifiers, form bindings, and message keys unless the task includes a coordinated migration. Visible wording must not expose their legacy domain names.
- Do not show savings, share balances, contribution requirements, or instructions to load deposits as loan qualification. Do not introduce Foresight controls or external membership verification.
- Retain Board and Credit Committee as distinct roles when the configured approval path uses them. Do not relabel one as the other or collapse their quorum rules for branding.

## Operational Views

- The sidebar owns global workspace navigation. Body actions operate on the current record or task; do not duplicate sidebar destinations inside panels.
- Keep institution portfolios, registries, review queues, and archives as separate focused views. On dashboards, show high-level portfolio KPIs instead of full registries.
- A client or loan detail page may show compact record-specific facts needed for a decision. This is not permission to add portfolio KPI strips to every page.
- On lending review screens, group income and expenses, existing debt, affordability, business/employment evidence, repayment history, guarantors, collateral, and documents around the current loan.
- Distinguish declared values from verified values and identify missing or stale evidence. A missing record is not a zero balance, a positive credit result, or verified identity.
- Guarantor views must explain the specific liability and approval state without exposing unrelated client finances or full credit records.

## Resource Creation And Details

Use Cloudscape's [create](https://cloudscape.design/patterns/resource-management/create/), [details](https://cloudscape.design/patterns/resource-management/details/), and [sub-resource create](https://cloudscape.design/patterns/resource-management/create/sub-resource-create/) guidance as UX references, adapted to the existing JSP shell. These references do not authorize installing React components or replacing a user-requested workflow.

- Choose a single-page form for a manageable task with related configuration. Use a wizard only when distinct steps or substantial dependencies justify it. Prefer a full page for complex creation; preserve an explicitly requested modal workflow.
- Put essential identity and configuration first, with parent-derived defaults and read-only parent context. Group related fields into task-based sections. Collapse optional settings when empty; expand them when they contain values or validation errors. Preserve existing fields and bindings.
- Use task titles and matching current breadcrumb labels. Breadcrumbs provide clickable ancestor context below the top bar and above the title, and stay sticky while scrolling; they are not another global navigation menu.
- Place Cancel followed by a verb-led primary action at the form's bottom right. Allow incomplete forms to reach validation rather than disabling Create merely for missing inputs. Keep permission, inactive-parent, pending-command, and other service-backed restrictions.
- Preserve values on failure, show a concise error summary and errors beside bound fields, and focus the summary or dialog appropriately. Protect changed forms from accidental exit using the shared discard dialog; Continue editing preserves values, while Discard exits deliberately. This is recovery protection, not an accounting approval step.
- Keep details self-contained in related sections when they fit one page. Use tabs only for distinct substantial tasks. Place actions beside the affected resource or section, and preserve list filters and return context where supported.
- For dependent creation, show parent context and return to the relevant parent/register. Embed simple related definitions when the existing service saves them atomically. Add nested creation only when required by the task and supported by existing scope and validation.

## Accounting Configuration Conventions

For COA or activity/transaction configuration, read [the accounting architecture](../../../docs/chart-of-accounts-architecture.md) and [accounting implementation gates](../../../docs/accounting-report-builder-checklist.md). These are configuration flows; saving definitions does not post money.

- The authorized accountant onboards COA groups/accounts and library definitions directly, without an approval queue. Preserve current claims, institution/branch checks, CSRF, and lifecycle protections; this convention does not change lending approvals or financial posting controls.
- COA creation selects a valid parent, shows its code/name and derived classification, then captures code, one Name field, accounting settings where applicable, and optional description. Retain stored language metadata; do not reintroduce separate English/Kiswahili name inputs into these forms. Translate UI labels using the existing bundles.
- COA selection uses the checkbox, row highlight and enabled header commands. A second click on the selected checkbox/row clears selection. Keep the selection announcement screen-reader-only; do not add a visible selection summary or Clear selection button above the table.
- Keep the sidebar destination Transactions Config with Activities and Transactions children. Each register has a compact top-right Add New action and the existing creation modal. Successful creation keeps the success dialog open until X is clicked, then the saved record appears in the register.
- Activity-row Transactions opens the canonical Transactions register with the activity selected automatically in its Activity filter. Keep context in the filter rather than repeating an Activity line above the register. Search/pagination preserve scope; Clear resets filters.
- Transaction creation captures activity code, transaction identity/accounting event and debit/credit account pairs. Keep pairs embedded in the atomic onboarding command. Each transaction has one editable posting template; do not add version numbers, version-purpose fields, selectors or template history. Keep concurrent-edit and retry protection internal to the save command.
- Transaction details use the stable Transaction configuration title with Activity, Transaction, Accounting event and Status filters. Put Add posting template or Edit posting template beside Posting template and lifecycle actions with the selected transaction context. Saving replaces the current account mappings; it does not post a journal or change a balance. Preserve financial ledger and standard security audit records separately.

## Financial Information

- Separate requested principal, approved principal, net disbursement, paid principal, due interest, fees, arrears, and outstanding debt. Label projected schedules as schedules, not payment history.
- Show the currency and rate basis. A monthly rate must not look like an annual rate; an instalment amount must include its repayment frequency.
- Do not call future scheduled interest a current settlement balance. Use server-calculated, dated values and disclose stale or unavailable balances.
- Keep prices, contractual disclosures, and consent visible at the relevant decision, even when removing explanatory copy. Do not create new fees or settlement rules in the UI.
- Present credit estimates as declared and unverified, not approved. Display the actual periodic instalment and its frequency separately from the monthly affordability amount; failed assessment must not receive a success state.
- Receipts identify the loan, transaction reference, actual payment date, amount, channel, and posting status. An unconfirmed payment must not appear as posted or reduce the balance.

## Actions And Recovery

- Reflect service-layer permissions and valid workflow transitions in visible actions. Hiding a control is not authorization; server checks remain mandatory.
- Confirm disbursement, payment reversal, restructuring, write-off, and other high-impact actions with the specific loan and financial consequence.
- Financial corrections preserve the original record and show a linked reversal or adjustment. Do not offer silent edit/delete actions for posted payments.
- Prevent accidental resubmission in the UI and retain server-side idempotency. Distinguish saving, pending verification, posted, rejected, and failed states.
- On failure, keep entered values where safe and show a specific recovery message without stack traces or internal tenant identifiers. After a successful posting, show the receipt reference and authoritative balance.

## Localization And Privacy

- Use the existing `messages_en.properties` and `messages_sw.properties` bundles. Check both languages for fit and consistent financial meaning.
- Treat Kiswahili-default digital lending as a release gate to verify against the current official guidance, not as a reason to silently change every existing user's preference.
- Do not remove mandatory contract, liability, complaint, or consent text under a generic "minimal copy" rule. Keep other helper text brief and task-specific.
- Preserve client ownership and institution/branch scope in views, exports, documents, and actions. A branch selector does not grant access.
- Mask unnecessary identity/contact details. Never propose collecting phone contact lists, ATM PINs, passwords, or unrelated personal data for credit assessment or collections.

## Verification

For changed flows, exercise valid and invalid actions, wrong-role and wrong-branch access, client ownership, empty/missing data, loading/error states, and English/Kiswahili text. Use the style skill for rendered responsiveness checks and the table skill for operational lists.
