---
name: microfinance-table-standard
description: Create, edit, or review Microfinance JSP registries, accounting configuration lists, review queues, repayments, collections, archives, and reports using the shared ERP table layout and scoped server-side pagination.
---

# Microfinance Table Standard

## Permanent accounting register reference

Apply Cloudscape [table view](https://cloudscape.design/patterns/resource-management/view/table-view/) with [create](https://cloudscape.design/patterns/resource-management/create/) and [details](https://cloudscape.design/patterns/resource-management/details/) navigation. Receipt, payment and journal registers are separate full-width scoped resources. Use single-row checkbox selection, keyboard access, a live selected-record announcement, disabled View/PDF/Print until selection, and an always-independent Create action. Never add a row Actions column. Clear selection on filters, sorting, pagination and restored navigation; provide explicit empty/no-match states and pagination even on one page. Outputs are PDF/print, never CSV/Excel. Creation supports multiple transactions and direct accountant posting, not approval queues.

## Reusable console table component

Use `resource-table.js`, `resource-table.css` and the localized `fragments/resource-table-labels.jspf`, loaded by the shared header. This is the maintained JSP implementation of the [Cloudscape table view](https://cloudscape.design/patterns/resource-management/view/table-view/) and the compact AWS console reference supplied on 2026-10-13. Read these component files before changing table behavior; extend them instead of copying controls into each page.

- Opt a resource wrapper in with a stable `data-resource-table="workspace-resource"` key. The key also scopes local display preferences; never put client names, financial data or selection in storage.
- Title/count and compact actions occupy the first row. Use neutral secondary buttons, a refresh icon at the start, a single independent primary action at the end, 28px desktop control height, 2px corners and 6px action gaps. Wrap groups on mobile without stretching individual buttons.
- Filters occupy the left of the second row; borderless previous/current-page/next controls and a separated gear icon occupy the right. Always show pagination, including one-page and empty results. Only the inner table canvas scrolls horizontally; title, actions, filters, paginator and gear remain stationary.
- Add `data-table-pagination data-page="${records.page}" data-table-for="workspace-resource"` to the server-rendered pager. Page is zero-based. Mark controls `data-page-direction="previous"` or `"next"`; preserve their scoped server URLs and filter/sort parameters. Omitted controls become disabled chevrons. A shared server pager can name several table keys separated by spaces. Do not infer paging from translated labels or paginate only the loaded page in JavaScript.
- The gear opens a body-mounted, keyboard-accessible Preferences dialog with Confirm/Cancel. Display settings are compact rows, line wrapping and visible columns. Keep the identifying column and editable form columns available. Page-size choices are appropriate only when the server endpoint accepts and caps that size; do not add a cosmetic client-only page-size setting.
- For existing single-record controllers, add `data-table-selection="single"`. The leading header checkbox selects the first visible selectable row when empty and clears the selection when selected; its accessible label states that behavior. It never enables a bulk financial command. Use a mixed state when one of several rows is selected. Clear selection on filters, paging, confirmed preferences and restored navigation. Row selection itself remains owned by the page's existing permission-aware controller.
- For a legacy resource table with a final command column, mark its header `data-table-actions-header`. The component replaces that column with leading selection and moves the selected row's actual links/forms into the toolbar, retaining CSRF, targets, values and permission conditions. Unselected commands are disabled. Use this adapter for native commands; custom delegated handlers that depend on row ancestry require an explicit page controller. Do not apply it to an inline editor or account picker where cell controls are the task itself.
- Do not add selectors to financial summary tables that have no record commands. Do not apply interactive tools to PDF/print stationery. Keep helper explanations out of the toolbar; labels, empty/error states and required financial context remain.

The accountant rollout includes accounting registers/configuration, loan recording/client ledgers, reconciliation, the legacy accountant queues/archives and financial/report tables. Other workspaces adopt this component as they are migrated; shared assets alone do not opt them in.

## Shared Structure

Use the existing JSP table contract rather than introducing a React/Cloudscape adapter:

```jsp
<div class="erp-table-wrap" data-resource-table="loan-repayments">
    <%-- Title, record actions, and filters stay outside the scroll viewport. --%>
    <div class="erp-table-scroll" data-view-position-key="loan-repayments-table">
        <table class="erp-table">
            <!-- Table header and rows -->
        </table>
    </div>
    <%-- Server pager is promoted beside filters; it stays outside the scroll viewport. --%>
    <div data-table-pagination data-page="${records.page}" data-table-for="loan-repayments">
        <c:if test="${records.page > 0}"><a data-page-direction="previous" href="<c:out value='${previousUrl}' />"><spring:message code="common.previous" /></a></c:if>
        <c:if test="${records.hasNext}"><a data-page-direction="next" href="<c:out value='${nextUrl}' />"><spring:message code="common.next" /></a></c:if>
    </div>
</div>
```

- Reuse the definitions in `src/main/resources/static/css/shell.css` and the nearest established table on the same workspace.
- The outer wrapper owns the title, filters, record commands, and pagination. Only table rows and the sticky column header belong in the inner scroll viewport.
- Do not combine `erp-table-wrap` and `erp-table-scroll` on one new element. Avoid a second card around a standalone table.
- Keep shared sticky headers and bounded scrolling. Use `erp-table-scroll-sm` for compact embedded tables and `erp-table-scroll-lg` only when the task needs a taller list.
- Choose a stable, non-sensitive view-position key and preserve existing filter, pagination, and return context where the shared shell supports it.

## Loan And Financial Columns

- Use short concrete headers and client-facing identifiers: Loan ID, Client Number, Branch, Due Date, Payment Date, Reference, Principal, Interest, Fees, Amount, and Status as relevant.
- Workspace table headings and values share the left leading edge, including Debit, Credit and monetary columns, so values do not drift to the far edge of wide columns. Use consistent precision and tabular numerals. Explicit centered selection/utility columns may remain centered in both header and body. Standalone PDF/print stationery retains its financial layout. State `TZS` in a visible column heading or shared context; distinguish zero from unavailable data.
- In repayment tables, distinguish actual received payments, allocation components, unapplied amounts, reversals, and projected instalments. Do not mix a repayment schedule into transaction history without clear separation.
- Collections tables distinguish days past due, overdue amount, and outstanding principal; PAR is not the same as the sum of missed instalments.
- Keep identifying information and financial consequences visible on mobile. Let the inner viewport scroll horizontally instead of hiding essential amounts, statuses, or receipt actions.
- Allow names and descriptions to wrap. Reserve `whitespace-nowrap` for short identifiers, dates and amounts.

## Selection And Record Commands

- Do not add an Actions column or command buttons to table rows. Put record-specific commands in the compact table-header/toolbar action group, outside the row scroll viewport. This applies to new and edited tables; do not migrate unrelated pages without a requested scope.
- Disable record commands until the required selection is present. For a single-record workflow, use one selected row with a leading, accessibly named checkbox: selecting another replaces selection, and clicking the selected checkbox or row body again clears it. Support native Tab/Space, a visible row highlight/focus indicator and a screen-reader-only selection announcement. Do not add a visible selection summary or Clear selection button.
- Bind navigation and lifecycle commands to the selected record, derive labels such as Deactivate/Reactivate from its state, and retain service-backed permission and eligibility restrictions. Keep CSRF on POST commands; UI selection is not authorization.
- Clear selection on filter changes, pagination and cached-page restoration so commands cannot target a stale record. Row-body selection must ignore links, buttons and form controls. Keep independent Create/Add New commands available without selection.
- Reuse existing accounting controllers in `accounting-accounts.js`, `accounting-library.js` and `accounting-vouchers.js`; the shared resource-table component supplies their header selector and preference/filter reset contract.

## Data And Interaction

- Use institution/branch-scoped server-side `Pageable`, projections, counts, and aggregates. Never load a complete portfolio into the browser to paginate or calculate totals.
- Preserve sort and filter parameters through pagination and exports. Use deterministic sorting with an ID tie-breaker; cap page sizes and validate filter input on the server.
- A displayed total must match the authorized filter scope and be computed from the financial source of truth. Do not sum only the current page and label it a portfolio total.
- Search, clear, loading, empty, error, and refresh states must be functional. Keep table dimensions stable as data loads; show an empty-state row with the correct `colspan`.
- Distinguish an empty register from a filtered search with no matches. Give no-match states a short recovery instruction to change or clear filters. Keep a register's Add New action compact and top right in its titlebar.
- When navigating to a register from a parent row, preselect the parent in a visible filter and preserve it through search/pagination. Avoid repeating the selected parent as a separate page-body label when the filter already conveys it; the filter is context, not authorization.
- Keep exports and receipts subject to the same authorization as the list. Large reports should be batchable rather than unbounded synchronous downloads.

## Verification

Check sticky headers, pagination, sorting, filter persistence, long labels, zero/missing amounts, empty/error states, selection toggling/replacement/reset, command targets/states, and branch/client isolation. Confirm there is no Actions column or row command button. At mobile and desktop widths, confirm the page does not overflow and table tools remain outside the scrolling rows. Verify reversed transactions cannot be mistaken for new receipts or silently removed from history.
