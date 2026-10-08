---
name: microfinance-table-standard
description: Create, edit, or review Microfinance JSP registries, accounting configuration lists, review queues, repayments, collections, archives, and reports using the shared ERP table layout and scoped server-side pagination.
---

# Microfinance Table Standard

## Permanent accounting register reference

Apply Cloudscape [table view](https://cloudscape.design/patterns/resource-management/view/table-view/) with [create](https://cloudscape.design/patterns/resource-management/create/) and [details](https://cloudscape.design/patterns/resource-management/details/) navigation. Receipt, payment and journal registers are separate full-width scoped resources. Use single-row checkbox selection, keyboard access, a live selected-record announcement, disabled View/PDF/Print until selection, and an always-independent Create action. Never add a row Actions column. Clear selection on filters, sorting, pagination and restored navigation; provide explicit empty/no-match states and pagination even on one page. Outputs are PDF/print, never CSV/Excel. Creation supports multiple transactions and direct accountant posting, not approval queues.

## Shared Structure

Use the existing JSP table contract rather than introducing a React/Cloudscape adapter:

```jsp
<div class="erp-table-wrap">
    <%-- Title, record actions, and filters stay outside the scroll viewport. --%>
    <div class="erp-table-scroll" data-view-position-key="loan-repayments-table">
        <table class="erp-table">
            <!-- Table header and rows -->
        </table>
    </div>
    <%-- Pagination stays outside the scroll viewport. --%>
</div>
```

- Reuse the definitions in `src/main/resources/static/css/shell.css` and the nearest established table on the same workspace.
- The outer wrapper owns the title, filters, record commands, and pagination. Only table rows and the sticky column header belong in the inner scroll viewport.
- Do not combine `erp-table-wrap` and `erp-table-scroll` on one new element. Avoid a second card around a standalone table.
- Keep shared sticky headers and bounded scrolling. Use `erp-table-scroll-sm` for compact embedded tables and `erp-table-scroll-lg` only when the task needs a taller list.
- Choose a stable, non-sensitive view-position key and preserve existing filter, pagination, and return context where the shared shell supports it.

## Loan And Financial Columns

- Use short concrete headers and client-facing identifiers: Loan ID, Client Number, Branch, Due Date, Payment Date, Reference, Principal, Interest, Fees, Amount, and Status as relevant.
- Right-align monetary columns with consistent precision and tabular numerals. State `TZS` in a visible column heading or shared context; distinguish zero from unavailable data.
- In repayment tables, distinguish actual received payments, allocation components, unapplied amounts, reversals, and projected instalments. Do not mix a repayment schedule into transaction history without clear separation.
- Collections tables distinguish days past due, overdue amount, and outstanding principal; PAR is not the same as the sum of missed instalments.
- Keep identifying information and financial consequences visible on mobile. Let the inner viewport scroll horizontally instead of hiding essential amounts, statuses, or receipt actions.
- Allow names and descriptions to wrap. Reserve `whitespace-nowrap` for short identifiers, dates and amounts.

## Selection And Record Commands

- Do not add an Actions column or command buttons to table rows. Put record-specific commands in the compact table-header/toolbar action group, outside the row scroll viewport. This applies to new and edited tables; do not migrate unrelated pages without a requested scope.
- Disable record commands until the required selection is present. For a single-record workflow, use one selected row with a leading, accessibly named checkbox: selecting another replaces selection, and clicking the selected checkbox or row body again clears it. Support native Tab/Space, a visible row highlight/focus indicator and a screen-reader-only selection announcement. Do not add a visible selection summary or Clear selection button.
- Bind navigation and lifecycle commands to the selected record, derive labels such as Deactivate/Reactivate from its state, and retain service-backed permission and eligibility restrictions. Keep CSRF on POST commands; UI selection is not authorization.
- Clear selection on filter changes, pagination and cached-page restoration so commands cannot target a stale record. Row-body selection must ignore links, buttons and form controls. Keep independent Create/Add New commands available without selection.
- Reuse the accounting register behavior in `src/main/resources/static/js/accounting-accounts.js` and `accounting-library.js` and the scoped selection styles in `accounting-ledger.css` for these workspaces.

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
