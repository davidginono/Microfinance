<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .disbursement-archive-search-form {
        width: 100%;
        display: grid;
        gap: 0.85rem;
        padding: 0.9rem 1rem;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #f8fafc;
    }
    .disbursement-archive-search-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.55rem;
    }
    @media (min-width: 768px) {
        .disbursement-archive-search-form {
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: end;
        }
        .disbursement-archive-search-actions {
            justify-content: flex-end;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Disbursement Panel / Archive</p>
    <h1 class="erp-page-title">Disbursement Archive</h1>
    <p class="erp-page-subtitle">Open the loans you already released and track where they are now.</p>
</div>

<div class="erp-toolbar">
    <div class="space-y-3">
        <div>
            <p class="erp-widget-title">Current Filter</p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
        <div class="erp-filter-row">
            <a href="/disbursement/archive?filter=ALL"
               class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                All Disbursed Loans
            </a>
            <a href="/disbursement/archive?filter=DISBURSED"
               class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
                Active Disbursed Loans
            </a>
            <a href="/disbursement/archive?filter=PAID"
               class="erp-filter-tab ${currentFilterKey eq 'PAID' ? 'is-active' : ''}">
                Paid Loans
            </a>
            <a href="/disbursement/archive?filter=DEFAULTED"
               class="erp-filter-tab ${currentFilterKey eq 'DEFAULTED' ? 'is-active' : ''}">
                Defaulted Loans
            </a>
        </div>
        <form action="/disbursement/archive" method="get" class="erp-filter-form disbursement-archive-search-form">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Loan ID
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="Search loan ID"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="disbursement-archive-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/disbursement/archive?filter=${currentFilterKey}" class="app-btn btn-neutral">Reset</a>
                </c:if>
                <button type="submit" class="app-btn btn-primary">Search</button>
            </div>
        </form>
    </div>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Loan ID</th>
            <th>Loan Application ID</th>
            <th>Applicant</th>
            <th>Amount</th>
            <th>Disbursed At</th>
            <th>Current Status</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${archiveRows}" var="row">
            <tr>
                <td><c:out value="${row.loanId}" /></td>
                <td>${row.applicationNumber}</td>
                <td>${row.applicantName}</td>
                <td>${row.amount}</td>
                <td>${row.disbursedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/disbursement/loan-applications/${row.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveRows}">
            <tr>
                <td colspan="7" class="px-3 py-8 text-center text-slate-500">
                    No disbursed loans matched the current filter.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
