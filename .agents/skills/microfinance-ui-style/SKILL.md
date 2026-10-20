---
name: microfinance-ui-style
description: Build or restyle Microfinance JSP dashboards, data visualizations, and interfaces using the existing compact ERP shell, shared CSS, forms, controls, and modals. Use for rendered UI changes without introducing a new frontend framework.
---

# Microfinance UI Style

## Permanent resource-management patterns

Follow Cloudscape [create](https://cloudscape.design/patterns/resource-management/create/), [table view](https://cloudscape.design/patterns/resource-management/view/table-view/), and [details](https://cloudscape.design/patterns/resource-management/details/) using existing JSP/shared ERP components. Focused create pages use grouped primary fields, expandable optional information, repeated numbered transaction fieldsets, preserved validation values, dirty-exit confirmation and bottom Cancel/Preview/Post actions. Details show the institution, branch, transaction breakdown, totals, posting identity and linked corrections. Voucher approval follows the branch configuration and explicit claims in `AGENTS.md`; retain direct posting where disabled. Core vouchers remain PDF/print; the financial-statement workspace has its authorized Excel exception; PDF vouchers repeat table headings and page numbers and never clip long descriptions.

## Dashboards and data visuals

For dashboard changes and reusable charts, follow the [Cloudscape dashboard and data visualization contract](references/cloudscape-dashboards.md). Use the shared configurable JSP widgets, scoped layout preferences, accessible charts and real data lifecycle. The supplied Console Home reference controls dashboard layout; resource registries retain their separate table patterns.

## Workflow animation reference

Use the [observed AWS instance type finder workflow](references/aws-instance-type-finder.md) for shared shell, focused forms, pending data, links and split-pane behavior. This is the user-selected classic console reference; keep the existing JSP implementation.

Use unlabeled, request-bound spinners inside pending values, tables, charts and data sections, with localized screen-reader names. Retain visible header/sidebar roots, progressively reveal ready sections, and preserve editable controls. The reference documents the observed canvas-to-component transitions, sizes and recovery lifecycle; a labeled page strip or artificial loading delay does not satisfy this behavior.

## Start With The Shared Shell

Read the relevant definitions before changing individual pages:

- `src/main/webapp/WEB-INF/jsp/fragments/header.jspf`
- `src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf`
- `src/main/resources/static/css/shell.css`
- `src/main/resources/static/css/console-components.css`

The current interface is a compact operational ERP, not a lending marketing site. Preserve its predictable topbar/sidebar and shared controls. Existing `sacco-*` and `aws-*` CSS names are implementation details; do not mass-rename them during a terminology change.

## Surfaces And Typography

- Keep optional helper copy absent or limited to one or two short, plain-language sentences, following the [governance copy guidance](../microfinance-ui-governance/SKILL.md#localization-and-privacy). Essential on-screen notes should use at least 14px text, comfortable line spacing and readable contrast, and wrap naturally on mobile. Do not shrink dense explanations into small-print footers.
- Reuse shared tokens and ERP classes for workspace canvas, neutral surfaces, borders, text, and status colors. Keep navigation and institution identity distinct from the data workspace.
- Forms and resource details must use framed sections matching the user's 2026-10-19 classic AWS references: light gray workspace canvas, square white containers, thin `#d5dbdb` borders, subtle bottom shadow, full-width `#fafafa` section headings and padded content. Never leave form fields or record facts directly on the page background. Keep the page title, breadcrumbs and bottom form actions outside the section containers; do not wrap the entire page in another card or frame each individual fact.
- Use square resource cards/table containers and 2px control corners for the supplied classic AWS reference; use modest corners elsewhere, compact headings, ordinary letter spacing, and locally bundled Amazon Ember. Do not introduce oversized headings, viewport-scaled type, decorative gradients, or visual effects behind financial data.
- Use semantic status colors with readable labels or icons; color alone must not communicate approval, delinquency, or reversal.
- Institution logos and names belong in the shared shell. Do not add SACCO/cooperative branding or decorative stock imagery to operational loan screens.

## Dates

- Display calendar dates as `DD/MM/YYYY` across workspaces, tables, details, statements, reports and exports; timestamps retain their time and application timezone after that date. Reuse `appTime:date` / `appTime:display` and `ApplicationTimestamps` for presentation.
- Shared `date-controls.js` wraps native date inputs with a fixed-order text field and calendar action. Keep the named native input and ISO values for form submissions, filtering, links, sorting, JSON and stored snapshots; never format machine values as display dates. Preserve required/min/max checks, reset and dynamic table replacement.

## Controls And Forms

- Resource tables use the [reusable console table contract](../microfinance-table-standard/SKILL.md#reusable-console-table-component): compact rectangular header actions, a separate filter/paginator/preferences row, and an independently scrolling table canvas. Reuse `resource-table.css` and `resource-table.js`; do not create page-specific gear dialogs or paginator styling.
- Render navigable table IDs/codes with the shared `resource-table-id-link` class: blue (`--aws-blue`, fallback `#0972d3`) in normal and visited states, darker blue and underlined on hover/focus, with a visible keyboard focus outline. These are native resource-navigation links, separate from checkbox selection and toolbar commands; do not style them as action buttons.
- Use the shared default Cloudscape-style pagination in that tools row for every table, including batch receipt editors. Never add boxed Previous/Next footer buttons or a separate "Page 1" footer. JSON editors use the table skill's external paging contract so draft saving and server-side paging remain intact.
- `workspace-resources.css` promotes the shared square form/card surfaces, gray headings, thin borders and compact controls across member and staff workspaces. Keep workflow-specific wizards, permission states, mandatory disclosures and inline editors intact. Avoid loading legacy table enhancement on opted-in resource tables, which otherwise duplicates filters, actions or paging.

- Reuse `app-btn`, `btn-primary`, `btn-neutral`, and `erp-icon-btn` rather than creating page-specific button systems.
- Independent Add/Create actions and the main bottom action on creation, editing and configuration forms use the shared console orange (`--aws-orange`, `#ec7211`) via `btn-launch`. This includes Create, Record, Post, Save and a wizard's forward action. Keep Cancel, Back, Preview, secondary Save draft, search/filter controls, navigation and downloads neutral; retain semantic danger styles for destructive commands. If Save draft is the only final creation action, it is orange. Keep disabled states distinct and form actions in their footer rather than relocating them into table toolbars.
- Resource table refresh, pagination, sort and Actions carets use the shared Cloudscape SVG geometry and the [AWS header/command contract](../microfinance-table-standard/SKILL.md#aws-reference-and-split-details). Keep hollow inactive sort carets distinct from filled active/menu carets. Use the available Lucide integration for other new tool icons. Give icon-only controls an accessible name and a tooltip when their purpose is not obvious; retain short verb labels for financial commands.
- Adding an icon factory alone does not adopt the reference. Bind each supported column to its real server sort with the shared heading contract, and verify the trailing hollow gray caret, dark active caret, ascending/descending direction and short header divider together. Provide real server ordering for missing data columns before adding the control; sorting must cover all matching records across pages.
- Use selects for option sets, segmented controls for modes, checkboxes/toggles for binary choices, and suitable numeric inputs for amounts and rates. Keep currency, rate basis, and repayment frequency adjacent to their values.
- Resource filters show names/labels only in selected values and dropdown options, without code prefixes. Preserve the submitted IDs/codes and localized names; apply this at the option source so native and enhanced selects agree.
- Group fields by identity, business/employment, cash flow, guarantors, collateral, and documents when relevant. Preserve field values and inline errors across failed submissions.
- Arrange related controls with wrapping flex or responsive grid tracks and `min-width: 0`. Keep command buttons compact, allowing labels to wrap when necessary without clipping.

## AWS searchable selectors

- Result menus reserve the fixed search row plus the measured height of up to six full option rows, loading status and Load more. Never size a new result set from a previously constrained popup `scrollHeight`; this clips single-result queries. Option label spans wrap fully, with a non-shrinking checkmark. Clamp/flip the popup at viewport edges and scroll only its results.

Use `fragments/select-enhancer.jspf`, `select-enhancer.js` and `select-enhancer.css` for single-value dropdowns, following the [Cloudscape select](https://cloudscape.design/components/select/) reference. The 2026-10-13 correction replaces centered selected values, blank unlabelled search fields, bold gray option rows and an intervening previous/next bar.

- Keep selected labels left aligned, with the shared filled 16px caret at the trailing edge. The popup matches the control width, uses a white surface, thin gray border, 2px corners, 14px Amazon Ember options and a blue checkmark for selection. Search belongs at the top with a magnifier, localized accessible name and italic placeholder. Options scroll beneath this fixed search row; remote Load more belongs below the list, without a separate arrow toolbar or Search/Apply button.
- Lists with eight or more loaded options acquire local search automatically. Mark smaller searchable lists `data-searchable="true"`; remote select adapters use `window.NeoSelect.search(select)` to share the input, footer and loading state. Small enums remain simple dropdowns; preserve native opt-outs and multi-select contracts. Preserve submitted IDs, permissions, required validation and source labels; omit the blank placeholder from searchable result rows.
- Filter authorized loaded options immediately on input. For remote choices, reuse `AppSearch.bind` (350ms after typing stops), scoped bounded endpoints, AbortController and a request-version check. A new query invalidates older responses immediately. Never call the server on each keystroke, navigate/reload while typing, submit a financial form on search Enter, or change the selected native value merely because results change. Emit native input/change only on actual option selection. Keep real loading, empty and failure states.
- Loan client/product choices fetch 25 plus lookahead and offer Load more; retain at most 100 fetched choices plus the current selection. Search resets server paging. Retained selections outside a query remain bound to the form but are not presented as matching results. Closing/unloading cancels pending requests; reopening can retry unfinished/failed queries. Keep server tenant/branch authorization authoritative and never fetch a complete registry to populate a dropdown.
- Mount menus under `document.body`, position them against their trigger and clamp/flip within the viewport so cards and modals cannot clip them. Keep search outside the listbox role. Support focus on search, Up/Down/Home/End option navigation, Escape and Tab dismissal, selection focus restoration, and cleanup when a dynamic control is removed. Test real-time filtering, debounce/IME behavior, stale responses, no form submission/reload, value preservation, bounded loading and viewport placement.

## Creation Layout, Breadcrumbs, And Validation

- Apply this contract to every workspace's create/edit forms and resource details, including loan application recording, top-ups, charge policies, client/staff loan details and accounting configuration. Preserve existing wizard steps, financial disclosures, permissions and validation; these layout references do not authorize workflow changes. Do not add choice cards, tabs or collapse controls unless the task actually has those choices or distinct sections.
- Reuse `.erp-resource-section` for a new standalone section; existing `.accounting-section` and `.erp-detail-card` share its surface in `workspace-resources.css`. Put a localized `h2` first (or `summary` for native `details`), followed by the section content. The shared styles own the edge-to-edge header, 20px content padding (16px on mobile), border and square corners. Use `.erp-panel` with `.erp-panel-header`/`.erp-panel-body` or the existing `.loan-flow-panel` header/body when those components already exist. Do not remove their frame or override it with transparent backgrounds.
- Details follow the supplied CloudWatch reference: a named white summary container, readable label/value groups, quiet vertical separators between columns and separate framed related-resource tables below. Use `.erp-detail-facts` for the responsive three/two/one-column summary. Avoid boxed cells, decorative KPI cards for ordinary facts and uncontained detail text. Keep long values wrapping and remove column dividers when stacked on mobile. Existing printable stationery retains its own layout.
- Adapt Cloudscape resource layouts with the existing JSP classes. For focused creation pages, use a bounded workspace (the accounting implementation uses 960px), square white section surfaces with shaded `#fafafa` headings, thin `#d5dbdb` borders and a subtle bottom shadow. Stack primary configuration fields in a readable column up to 728px wide; keep genuinely related repeated transaction controls together, stacking on mobile. Registers and detail tables can use the available workspace width.
- Reuse `erp-page-path` and shared sticky shell styling for breadcrumbs below the top bar and above the page title. Match the current label to the title, mark it `aria-current="page"`, and link ancestors to real authorized pages. Derive the sticky offset from the shared top bar rather than a page-specific constant.
- All breadcrumb hyperlinks, including shell-generated links and page-authored ancestor links, must use the shared blue link color (`--aws-blue`, fallback `#0972d3`) in normal and visited states. Hover and keyboard focus use the darker blue token with an underline and a visible focus outline. Enforce this in shared breadcrumb CSS; never let links inherit muted breadcrumb text or page-specific link colors. Keep non-link current-page labels and separators neutral.
- Use compact key/value summaries for read-only parent context, fieldsets/legends for related settings, and expandable optional sections. Keep Cancel/Create actions right aligned; avoid full-width toolbar buttons and redundant nested cards.
- Put error summaries in focusable alert regions. Associate field errors with controls using `aria-invalid` and `aria-describedby`; open any collapsed section containing an error. When cloning repeatable rows, remove stale error associations and preserve unique field IDs/bindings.
- Use `min-width:0` on grid/flex children and enhanced select wrappers; ensure the select fits its parent at every breakpoint. Group amount component, debit account, credit account and row removal coherently on desktop, then stack them on mobile.
- Reuse the accounting reference implementation in `src/main/resources/static/css/accounting-ledger.css`, `src/main/resources/static/js/accounting-form-ux.js`, and `src/main/webapp/WEB-INF/jsp/accounting/accounting-form-support.jspf` when extending those pages. Promote patterns to shared styles only when another workspace needs them; do not spread accounting-specific selectors globally.

## Read-only resource preview

Reuse the [table skill split-view contract](../microfinance-table-standard/SKILL.md#aws-reference-and-split-details) for switching between records without losing the register. Use `data-resource-split` on a table that already has single-record selection. Keep full details available and financial commands in the existing toolbar. Avoid instructional paragraphs in headers, filters, preferences and preview panes; retain field labels, errors and required financial disclosures.

## Shared Modal Contract

- Reuse the existing modal implementation and `app-modal-panel` / `app-modal-actions` styling. Keep a white surface, thin neutral border, modest corners, simple close icon, and right-aligned footer commands.
- Mount overlays under `document.body` so fixed headers and shell overflow do not clip them. Keep the dialog within the viewport and let its body scroll rather than hiding its actions.
- Lock background scrolling on both body and the document root, restore their prior overflow values on close, and keep footer actions visible during long form scrolling. Ensure the close icon renders after dynamically mounting a dialog.
- Provide a dialog name, sensible initial focus, focus containment, and focus restoration. Support Escape where cancellation is safe; do not dismiss an in-flight financial command as if it had not been submitted.
- Keep success, failure, and pending feedback concise. Never imply a payment succeeded before the server confirms posting.
- Use the shared discard dialog for changed forms, with focus containment and restoration for both the form and confirmation. Preserve the existing success/X behavior where the workflow requires it. UI submission locks must not re-enable buttons disabled by a business rule when restoring a cached page.

## Component loading and refreshing

Use `ui-loading.js` / `ui-loading.css` for actual pending data requests. `resource-table.js` fetches scoped paginated HTML, replaces body rows and updates the pager/sort state without a document reload. The spinner belongs in the table canvas under the headings; hide existing rows temporarily while retaining native nodes for recovery on failure. Tools and filters stay stationary and selection-dependent actions reset. Show a spinner only during a real pending request. For non-table quote/charge previews, opt the data region into `data-ui-loading-region` and drive `aria-busy` from the existing request lifecycle; `AppLoading` places the indicator beneath its heading and preserves native controls. Remote selects own their existing menu indicator. Never attach spinners to idle dashboards or cover an entire financial form. Respect reduced motion and preserve localized accessible status text. Clear indicators on success, failure, cancellation and cached-page restoration. Show ready data immediately.

The global page preloader has been removed. Same-origin workspace links, Create/Record/detail toolbar actions and GET report forms use `workspace-navigation.js`, with a spinner inside the main canvas until authorized HTML is ready. Programmatic buttons use `WorkspaceNavigation.navigate(url)`; preserve local table/tab handlers, downloads, modified clicks and dirty-exit confirmation. Only explicitly opted-in report generation POST forms use `data-workspace-submit`, retaining CSRF and request keys, preventing duplicate sends and never replaying a POST through browser history. Financial mutations keep their existing command/confirmation controllers and use an unlabeled spinner beside the submitter. Downloads and print preparation use local action spinners, with cleanup on every completion path and page exit. Never restore a body overlay, blurred backdrop or a document-wide interaction lock. Report-wide filter forms remain with Report controls above KPI cards. Eligible split details start open with an empty selection heading at desktop/tablet widths (768px and above), matching the animation. Small screens start closed. After deliberate closure, only Show details reopens the pane; selecting a row never reopens it. Preview facts sit directly on a white surface with quiet column dividers, without a decorative nested card or a pretend single tab.

## Responsive And Accessible Checks

- Check representative widths of 360px, 768px, 1366px, and 1920px, plus long institution/client names and both languages.
- Only a table's scroll viewport may overflow horizontally. Page content, menus, dialogs, and toolbars must fit without clipped labels or overlapping actions.
- Preserve the mobile sidebar, profile/notification dropdowns, visible focus, label associations, and touch-friendly controls. Keep the key loan context near the action at every width.
- Capture rendered screenshots and exercise affected interactions when browser access is available. If the application cannot be run, report that visual verification is pending rather than claiming it passed.
- JSP changes need runtime rendering checks; Java compilation alone does not prove JSP correctness. Keep any temporary screenshots or diagnostic helpers out of production commits.
- For creation flows, also check invalid submissions, value preservation, optional-section errors, dirty Cancel/navigation, Escape and Tab behavior, repeated row add/remove, clean modal reopen, success/X return, and breadcrumb position after scrolling. Keep JSP fragments containing localized text or punctuation explicitly UTF-8.

## Resource details reference (2026-10-20)

- Use the supplied classic AWS reference across workspaces: sentence-case 14px breadcrumbs with blue ancestors, gray current item and the official 16px angle-right icon. Preserve sticky position under the measured shared top bar.
- Read-only facts use gray labels, dark values, the official Cloudscape copy icon before copyable values, and blue resource links. Only links opening a separate tab/window receive the official external icon; retain authorized destinations. Supporting-document filenames use the same blue link treatment. Use locally bundled Amazon Ember throughout the application.
- Resource statuses in tables, full details and split panes use the shared console status indicator: no pill/badge fill, a readable label and the official Cloudscape status icon. Success uses the reference green circled tick; errors use red circled X, warnings amber triangle, pending gray clock, stopped gray minus, and progress gray ellipsis. Map the real server state, never infer financial success from missing data.
- Detail pages with multiple sections keep their summary and prerequisite/error notices visible above the shared horizontal tab bar. Use the reference blue selected label, dark underline and short vertical dividers.
- **Every UI tab switch must avoid a document reload.** Use in-page panels for loaded content, or bounded authenticated fetches for data-dependent tabs. Retain native nodes, unsaved values, selection/paging context and existing permissions/CSRF; never clone a financial form. Failed tab requests show a local error and retry without location.assign/location.reload. Support keyboard arrows/Home/End, tab/tabpanel roles, ARIA selection, focus and mobile scrolling. Open the affected panel for validation errors and keep all sections available to print.

## Detail identity and breadcrumb placement

Use stable task/resource titles on detail pages and section headings. Keep client numbers, client IDs, loan IDs and combined ID/name labels in labeled detail fields only, including client statements, loan postings, repayment ledgers and review pages. Breadcrumb current labels match the task title; place the breadcrumb above the page title even when templates, notices or wrappers separate them in the JSP. Repayment allocation is a built-in interest-first rule, not a configurable Repayment settings workspace; retain historical allocation evidence and existing product-account routing.
