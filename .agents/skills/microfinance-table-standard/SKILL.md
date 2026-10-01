---
name: microfinance-table-standard
description: Create, edit, or review Microfinance JSP client registries, review queues, repayments, collections, archives, and reports using the shared ERP table layout and scoped server-side pagination.
---

# Microfinance Table Standard

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
- Allow names and descriptions to wrap. Reserve `whitespace-nowrap` for short identifiers, dates, amounts, and stable action cells.
- Put record-specific actions in the last column. Use shared compact controls, accessible tooltips for icons, and service-backed permission states.

## Data And Interaction

- Use institution/branch-scoped server-side `Pageable`, projections, counts, and aggregates. Never load a complete portfolio into the browser to paginate or calculate totals.
- Preserve sort and filter parameters through pagination and exports. Use deterministic sorting with an ID tie-breaker; cap page sizes and validate filter input on the server.
- A displayed total must match the authorized filter scope and be computed from the financial source of truth. Do not sum only the current page and label it a portfolio total.
- Search, clear, loading, empty, error, and refresh states must be functional. Keep table dimensions stable as data loads; show an empty-state row with the correct `colspan`.
- Keep exports and receipts subject to the same authorization as the list. Large reports should be batchable rather than unbounded synchronous downloads.

## Verification

Check sticky headers, pagination, sorting, filter persistence, long labels, zero/missing amounts, empty/error states, row actions, and branch/client isolation. At mobile and desktop widths, confirm the page does not overflow and table tools remain outside the scrolling rows. Verify reversed transactions cannot be mistaken for new receipts or silently removed from history.
