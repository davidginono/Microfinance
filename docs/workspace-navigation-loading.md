# Workspace transition loading

Implemented 20 October 2026 in the shared JSP navigation runtime and shell CSS.

- Returning to a workspace shows blank outlines at its last measured section positions, with a spinner inside each section. Dashboard widgets, form sections, detail cards and resource tables use the same lifecycle. Table loaders sit below the measured column headings.
- The first visit, a different query/language, or an unmatched viewport/content width uses the workspace spinner at the top left until the authorized server HTML arrives, retaining the original native nodes for recovery. This placement follows the user's 21 October correction. The animated progress line below the top navigation was removed at the user’s request. The client cannot know an unvisited page's exact layout before that response. Ready destination content appears immediately; subsequent asynchronous sections retain their existing request-owned loaders. No speculative backend requests or artificial delays are introduced.
- At most 12 exact-URL layouts and 32 outer sections per layout are kept in memory. Layout values contain only numeric geometry; no HTML, labels, account balances, field values, record IDs or rows are copied into previews or persisted in browser storage. Hidden/nested sections are excluded. Logout clears this memory.
- Header/sidebar roots stay connected. Resize and sidebar-width changes discard mismatched previews. A stable root scrollbar gutter avoids width changes when moving between short forms and tall dashboards. Existing editable nodes and their values survive cancellation, rejection, timeout and failure; retry remains explicit.
- Shared localized accessible status names and reduced-motion behavior remain. Financial submissions and downloads retain their separate button-contained spinners and existing safeguards.

## First paint and component preparation (21 October 2026)

- All 133 JSP views using the shared header/sidebar were cross-checked through their include graph, including account/profile pages, lending roles, administration, finance, repayments and reporting. The shared preparation guard applies to first loads and in-place transitions; it preserves layout measurements while suppressing unfinished controls and sections behind a top-left spinner.
- Shared table, select, details and shell initialization uses DOM readiness through `WorkspaceNavigation.onReady`, rather than waiting for unrelated images or asynchronous resources to finish loading. Dashboard/chart data requests retain their independent loaders.
- Destination styles, including links and inline styles owned by body fragments, are prepared before content replacement. Unchanged styles remain attached and retain their cascade order. Failed stylesheet loads retain the original document and edited fields; unfinished replacement content stays hidden after an initialization failure and offers an explicit retry.
- Removed superseded sidebar link/submenu rules instead of layering another active-state override over the old dark/orange theme. Institution branding uses the correctly cased scoped model attribute. V84 renames only the original `TAHA-SACCOS` institution from its original name to Degra Microfinance; compatibility IDs and stored document snapshots remain intact.

## Verification

### 21 October 2026 branding and component preparation

- All 133 shared-shell JSP views compiled successfully with Jasper. `mvn -DskipTests compile` and all 17 shared UI contract checks succeeded. All 206 JavaScript checks passed, including held stylesheet/script requests, hidden unfinished components across 13 workspace families, and failed-load recovery.
- Statement preview tabs now keep the workspace, tab bar, view settings and save form connected. Only the report section shows a request-bound spinner and is replaced on success; export/print/save context follows the selected report. Tests cover rapid switches, stale responses, retry, per-report view preferences and Back/Forward navigation. The title is Reports Preview and Save Draft sits at the bottom right.
- The supplier SVG has a transparent background and no dark dot. Live authenticated accountant dashboard/loan-record checks confirmed ready components, blue text-only active navigation, the SVG and Degra Microfinance branding without console errors. The browser connection later stopped attaching to verification tabs, so the final report-preview and mobile visual rechecks remain pending; the browser viewport override was reset successfully before that interruption.

### Earlier transition verification

- The subsequent spinner correction passed all 35 focused navigation, shared loading and sidebar checks, plus JavaScript syntax and Git whitespace checks. First visits, query/viewport mismatches and resize fallback now use the shared large circular spinner; the progress-line styles and animation were removed. A fresh real-workspace browser check redirected to login because the authenticated session had expired, so visual verification of this correction remains pending. No server workflow or financial mutation logic changed.

- All 190 JavaScript checks passed. The 24 navigation checks were rerun after the final geometry change; they cover exact section placement, content-free previews, bounded memory, query/viewport matching, resize recovery, cancellation/retry, stale requests, unsaved edits, history and report POST protection.
- `mvn test` succeeded: 1,297 tests, zero failures/errors, 227 environment-dependent skips. Separate account-mapping verification used disposable PostgreSQL databases as recorded in `loan-product-account-links.md`; the full run did not activate those database-dependent suites.
- Chrome component previews using the actual shared assets matched ready/pending section rectangles at 360, 768, 1366 and 1920 pixels without horizontal overflow. Sidebar-width changes restored the original fields and fallback loading state without replaying navigation. No browser warning/error logs were reported. The preview fixture used synthetic content and held test responses explicitly; no delay or helper is included in production.
- Browser checks cover shared components, not authenticated production login/logout or every JSP route. No operational records were changed, and no throughput claim is made. Refresh/restart the application to serve updated static assets; the separate account-mapping change requires normal Flyway startup through V83.
