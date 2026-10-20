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

Checks: the focused shared-assets Java contract passed (1 test, 0 failures); 63 focused JavaScript checks passed; all workspace JSPs compiled through Jasper. The shared-assets contract was updated to validate the shared breadcrumb anchor/focus selectors and the current loading initializer rather than retired implementation names. Typography expectations use the requested Amazon Ember family.

The paired comparison caught compounded spacing around tabs inside finance grid workspaces. The contextual margin was corrected, and the final signed-in desktop page was recaptured at 1265×578. The refreshed paired comparison confirms the intended spacing above the tab strip and the 16px separation below it, blue selected label, dark underline, dividers, blue document filenames and sticky breadcrumb. No actionable P0/P1/P2 visual difference remains in these shared components. Different resource content, section counts and Amazon Ember typography follow the application requirements. The previously verified mobile layout is retained; the temporary viewport override was reset.

Local evidence: C:/Users/USER/.codex/visualizations/2026/10/20/01a157e7-bf8f-7f01-8132-a52bda1497bb/component-comparison-final.png, loan-details-final.jpg, loan-details-summary.jpg and loan-details-mobile.jpg.

final result: passed — final component comparison and visual recapture completed.

Follow-up heading correction: Loan details no longer includes the Loan ID, and the client container heading shows the client name only. Loan ID and Client Number are labeled summary facts using existing English/Kiswahili messages and the responsive fact grid. The source diff was verified; browser recapture of this text-only correction was unavailable after the Chrome connection disconnected. Earlier screenshots show the prior headings.
