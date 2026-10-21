# Configurable dashboards and data visuals

The user's 2026-10-20 Console Home screenshot defines the dashboard layout: a compact header, gray canvas, white rectangular widgets, shaded widget headings, drag grips, overflow menus, resize handles and page-level Reset to default layout / Add widgets controls. The user's 2026-10-21 instruction requires the shared AWS navy top navigation bar (`#232f3e`) and white sidebar across every workspace, including dashboards; do not override the shell colors in dashboard styles.

Authoritative design references:

- [Configurable dashboards](https://cloudscape.design/patterns/general/service-dashboard/configurable-dashboard/)
- [Dashboard items](https://cloudscape.design/patterns/general/service-dashboard/dashboard-items/)
- [Data visualization](https://cloudscape.design/patterns/general/data-vis/)
- [Visualization colors](https://cloudscape.design/foundation/visual-foundation/data-vis-colors/)

## Repository components

Reuse `fragments/dashboard-support.jspf`, `dashboard-board.js`, `dashboard-charts.js`, `dashboard-data.js` and `dashboard-board.css`. These adapt the patterns to JSP; they are not the React Cloudscape Board or chart components. Do not introduce a framework migration to change a dashboard. In an existing React surface, prefer the installed public Cloudscape visualization APIs.

Mark a dashboard with `data-dashboard-board` and authorized native sections with stable `data-dashboard-widget` keys. Set default width, minimum width and height on each section. Reordering, hiding, adding and resizing retain original nodes, forms and edited values. The palette contains metadata rather than copies of widget data. Keep Preferences first and Remove last in each menu. Provide pointer and keyboard operations, recoverable removal, restoration of defaults and responsive stacking. The narrow-screen palette uses the shared modal rules: body mounting, focus containment, Escape, background isolation and scroll restoration.

Save layout only, keyed by user, institution, branch, role and dashboard. Validate stored sizes/IDs against the current authorized widgets. Never persist chart payloads, record selections, financial values or permission decisions in layout storage. The layout cannot grant access.

## Widget content

Use one clear goal per widget and at most two visualization forms together. Give useful defaults and enough space to read the content. Favor overview metrics, short bounded recent summaries and trends. Keep full portfolios, registries, logs and complex filtering on their own authorized workspaces. Do not copy AWS service launchers, security scores, cost forecasts or sample numbers into production widgets. Do not infer operational health or creditworthiness from unavailable information.

## Reusable visuals

Use `DashboardCharts` for the JSP chart presentation and its readable data alternative. Choose bars for comparison, lines for changes over time, and stacked or part-to-whole visuals only when the data supports that relationship. Split unrelated units into separate charts. Start quantitative axes at zero, use meaningful units, and keep real missing observations as gaps/unavailable rather than zero. A real zero period receives an empty-activity state.

Follow the Cloudscape categorical palette sequence consistently across series. Keep semantic status colors distinct from arbitrary category colors. Labels, legends, keyboard point navigation, values and the accessible data table must communicate meaning independently of color. Keep series counts small; use separate views when the chart becomes crowded. Charts are data displays, never decorative images or fabricated financial projections.

Live widgets use existing scoped bounded endpoints with localized loading/error/empty states, a last-update label and local retry. Cancel stale requests, stop work for hidden widgets/pages, and preserve verified prior results with an explicit refresh-error message. Resize redraws cached chart data and must not fetch. Keep polling intervals bounded and avoid overlapping requests.

Verify layout restoration, permissions changing between visits, native control preservation, drag/keyboard reordering and resizing, palette add/remove/reset, missing/zero values, chart legends and data alternatives, stale responses and cancellation. Check 360/768/1366/1920px, English/Kiswahili and reduced motion. Component previews with sample data do not establish authenticated route or tenant-isolation correctness.
