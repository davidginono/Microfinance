---
name: microfinance-ui-governance
description: Design, edit, or review JSP page flows, navigation, loan assessments, approval screens, and financial-action states in the Microfinance repository. Use for domain-specific interface work, not standalone backend changes.
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

## Financial Information

- Separate requested principal, approved principal, net disbursement, paid principal, due interest, fees, arrears, and outstanding debt. Label projected schedules as schedules, not payment history.
- Show the currency and rate basis. A monthly rate must not look like an annual rate; an instalment amount must include its repayment frequency.
- Do not call future scheduled interest a current settlement balance. Use server-calculated, dated values and disclose stale or unavailable balances.
- Keep prices, contractual disclosures, and consent visible at the relevant decision, even when removing explanatory copy. Do not create new fees or settlement rules in the UI.
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
