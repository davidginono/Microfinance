# Workspace transition loading

Implemented 20 October 2026 in the shared JSP navigation runtime and shell CSS.

- Returning to a workspace shows blank outlines at its last measured section positions, with a spinner inside each section. Dashboard widgets, form sections, detail cards and resource tables use the same lifecycle. Table loaders sit below the measured column headings.
- The first visit, a different query/language, or an unmatched viewport/content width keeps the current workspace available with slim progress until the authorized server HTML arrives. The client cannot know an unvisited page's exact layout before that response. Ready destination content appears immediately; subsequent asynchronous sections retain their existing request-owned loaders. No speculative backend requests or artificial delays are introduced.
- At most 12 exact-URL layouts and 32 outer sections per layout are kept in memory. Layout values contain only numeric geometry; no HTML, labels, account balances, field values, record IDs or rows are copied into previews or persisted in browser storage. Hidden/nested sections are excluded. Logout clears this memory.
- Header/sidebar roots stay connected. Resize and sidebar-width changes discard mismatched previews. A stable root scrollbar gutter avoids width changes when moving between short forms and tall dashboards. Existing editable nodes and their values survive cancellation, rejection, timeout and failure; retry remains explicit.
- Shared localized accessible status names and reduced-motion behavior remain. Financial submissions and downloads retain their separate button-contained spinners and existing safeguards.

## Verification

- All 190 JavaScript checks passed. The 24 navigation checks were rerun after the final geometry change; they cover exact section placement, content-free previews, bounded memory, query/viewport matching, resize recovery, cancellation/retry, stale requests, unsaved edits, history and report POST protection.
- `mvn test` succeeded: 1,297 tests, zero failures/errors, 227 environment-dependent skips. Separate account-mapping verification used disposable PostgreSQL databases as recorded in `loan-product-account-links.md`; the full run did not activate those database-dependent suites.
- Chrome component previews using the actual shared assets matched ready/pending section rectangles at 360, 768, 1366 and 1920 pixels without horizontal overflow. Sidebar-width changes restored the original fields and slim progress without replaying navigation. No browser warning/error logs were reported. The preview fixture used synthetic content and held test responses explicitly; no delay or helper is included in production.
- Browser checks cover shared components, not authenticated production login/logout or every JSP route. No operational records were changed, and no throughput claim is made. Refresh/restart the application to serve updated static assets; the separate account-mapping change requires normal Flyway startup through V83.
