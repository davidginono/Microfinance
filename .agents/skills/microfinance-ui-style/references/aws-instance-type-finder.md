# AWS instance type finder workflow

User-selected reference, observed on 2026-10-20: [39-second AWS animation](https://docs.aws.amazon.com/images/AWSEC2/latest/UserGuide/images/use-ec2-instance-type-finder-animation.gif).

## Observed sequence

- 0–3 seconds: dashboard to resource register; stationary tools and column headings, loading indicator inside the table, compact refresh/preferences controls and an empty lower selection pane.
- 6–21 seconds: focused finder with a slim hamburger rail, blue ancestor breadcrumbs, sentence-case title, bounded white section with a pale heading, two columns of related labeled selects, expandable advanced parameters and repeated paired minimum/maximum inputs. Cancel and an orange task action sit below the section on the right.
- 24–27 seconds: the task shows its result and returns to a filtered register. Filter tokens, Clear filters, paging and a selected-record Actions menu remain near the table. A diagonal external-link icon identifies a separate-window destination.
- 30–36 seconds: dragging the horizontal divider trades table height for preview height. A selected checkbox highlights its row in pale blue; selected facts appear below. Multiple real content sections use blue selected tab labels, a dark underline and short vertical separators. Actions relate to the selected resource.
- 39 seconds: another focused create page retains grouped sections and a local loading indicator.

The GIF shows visual state transitions. It does not expose requests, server authorization, error recovery, keyboard behavior or mobile breakpoints. Implement those with the application's real scoped services and verify them separately. Do not claim that a GIF establishes backend or accessibility behavior.

## Apply to Microfinance

- Use the existing JSP shell, bundled Amazon Ember, classic square white sections, gray workspace, thin neutral rules, compact controls and orange primary task action. Keep existing lending fields, required disclosures, translations and server bindings. Pair genuinely related inputs; stack them on narrow screens. Optional sections expand in place and reveal validation errors.
- The user's explicit requirement is collapsed navigation on initial dashboard/workspace loads, although the animation's dashboard has expanded navigation. Reserve a 28px desktop rail with one reveal control; hide its destinations from visual layout and keyboard navigation while closed. Expanded navigation retains the existing institution/branch-aware sidebar. Opening it survives in-page updates and viewport changes, then resets on a new document. Preserve the existing mobile drawer.
- Keep filters, sorting, pagination, refresh, detail tabs and preview changes in place. Reuse `resource-table.js`, `console-resource-details.js`, existing authenticated choice adapters and server pagination. Preserve native nodes and edited form values. Resource navigation links retain their normal destination and modified-click behavior. Do not build a general HTML-swapping router that reexecutes financial scripts.
- `ui-loading.js` owns table-canvas loading and explicitly opted-in non-table data previews. Show actual pending work immediately; keep headings and tools available, cancel stale requests, restore rows on failure and offer local retry. Remote selects retain their own pending list state. Do not fabricate delays or show loading over ready data. Full navigation and financial submissions retain their existing lifecycle and dirty-form protections.
- Keep IDs blue and native. Add the official shared external icon and localized new-tab hint only to actual separate-window links; never force internal IDs into new tabs. Refresh arrows, sort carets, menu carets, close and resize affordances have distinct purposes. Retain readable labels and accessible names for icon-only buttons.
- `resource-split-view.js` displays only facts already authorized in the selected row. For explicitly opted-in single-selection tables, show the empty lower pane initially at widths of at least 768px; smaller screens start closed. A deliberate Close stays closed until Show details. No selection clears the facts and displays the selection heading. Use a white pane with unboxed label/value columns; a single preview needs no tab strip. Keep financial commands in the existing Actions menu and full details available through the authorized ID link.
- The separator supports pointer capture, cancellation, and keyboard resizing in its current orientation. Clamp its complete pane size to retain usable table space. Side placement falls back to bottom on narrow screens. Store only size/placement, never financial data or selected record IDs.

## Checks

Exercise actual requests, stale-response races, retry, page exit/restoration, selection reset, native ID navigation, new-tab hints, pointer/keyboard resize and close/reopen. Check 360px, 768px, 1366px and 1920px, long labels, English/Kiswahili, reduced motion and visible keyboard focus. Only the table canvas may overflow horizontally. Authenticated route checks require an existing test session; component checks do not establish full-page rendering or financial workflow correctness.
