# Resource UI reference check — 20 October 2026

Scope: shared breadcrumbs, resource facts and copy/link icons, status indicators, detail tabs, supporting-document links and the account-link prerequisite notice. The existing JSP application and financial actions are retained. Amazon Ember is an explicit user override to the reference typography.

Evidence was captured from the signed-in accountant session on localhost:8081: loan details, its supporting-document and repayment-schedule tabs, the account register and its split pane. Desktop and 360px mobile layouts were inspected. The supplied reference was normalized from 3432×1602 to 1716×801 for a paired component comparison with the 1265×523 desktop capture. Different resource data and section counts are intentional.

- Breadcrumbs: blue sentence-case ancestors, neutral current location, official chevrons, sticky under the top bar after scrolling.
- Details: gray labels, dark values, official copy and external-link artwork; copy announced success in the browser.
- Status: green circled ticks verified on a disbursed loan and active accounts in rows and split facts; real server state drives other indicators.
- Tabs: blue active label, dark underline and dividers. Browser switches preserved the document; sorting links retained the active tab. Native form identity, edited values, keyboard navigation and invalid-control recovery passed interaction tests.
- Documents: blue supporting filenames verified. Existing action destinations and authorization remain intact.
- Mobile: actions and prerequisite notice wrapped, facts stacked and breadcrumbs stayed sticky at 360px.
- Prerequisite: the existing account-link POST/CSRF envelope is now a persistent Cloudscape-style warning alert; it was not submitted during verification.

Checks: 63 focused JavaScript checks passed; all workspace JSPs compiled through Jasper. The existing AwsConsoleViewContractTest shared-assets assertion still fails because it expects the literal .erp-page-path__link selector in shell.css; this failure predates this change. Typography expectations were updated for the requested font.

The paired comparison caught compounded spacing around tabs inside finance grid workspaces. The contextual margin has been corrected. Browser access then disconnected, preventing a fresh capture of this final spacing adjustment. No remaining functional failure was observed; final pixel sign-off is pending.

Local evidence: C:/Users/USER/.codex/visualizations/2026/10/20/01a157e7-bf8f-7f01-8132-a52bda1497bb/component-comparison.png, loan-details-summary.jpg, loan-details-attachments.jpg and loan-details-mobile.jpg.

final result: blocked — final visual recapture after the spacing correction requires the browser connection.
