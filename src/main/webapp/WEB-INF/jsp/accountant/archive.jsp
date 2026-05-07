<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .accountant-archive-search-form {
        width: 100%;
        display: grid;
        gap: 0.85rem;
        padding: 0.9rem 1rem;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #f8fafc;
    }
    .accountant-archive-search-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.55rem;
    }
    @media (min-width: 768px) {
        .accountant-archive-search-form {
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: end;
        }
        .accountant-archive-search-actions {
            justify-content: flex-end;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Accountant Panel / Archive</p>
    <h1 class="erp-page-title">Accountant Archive</h1>
    <p class="erp-page-subtitle">Open the loan applications you have already reviewed and track where they are now.</p>
</div>

<div class="erp-toolbar">
    <div class="space-y-3">
        <div>
            <p class="erp-widget-title">Current Filter</p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
        <div class="erp-filter-row">
            <a href="/accountant/archive?filter=ALL"
               class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                All Reviewed Loans
            </a>
            <a href="/accountant/archive?filter=APPROVED"
               class="erp-filter-tab ${currentFilterKey eq 'APPROVED' ? 'is-active' : ''}">
                Approved for Disbursement
            </a>
            <a href="/accountant/archive?filter=REJECTED"
               class="erp-filter-tab ${currentFilterKey eq 'REJECTED' ? 'is-active' : ''}">
                Rejected
            </a>
        </div>
        <form action="/accountant/archive" method="get" class="erp-filter-form accountant-archive-search-form">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                ${archiveSearchLabel}
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${archiveSearchPlaceholder}"
                       inputmode="numeric"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="accountant-archive-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/accountant/archive?filter=${currentFilterKey}" class="app-btn btn-neutral">Reset</a>
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
            <th>${archivePrimaryColumnLabel}</th>
            <th>Applicant</th>
            <th>Amount</th>
            <th>Decision</th>
            <th>Reviewed At</th>
            <th>Current Status</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${archiveEntries}" var="entry">
            <tr>
                <td>
                    <c:choose>
                        <c:when test="${archiveLoanIdMode}"><c:out value="${empty entry.loan.loanId ? '-' : entry.loan.loanId}" /></c:when>
                        <c:otherwise>${entry.loan.applicationNumber}</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicantNames[entry.loan.applicantMemberId]}">${applicantNames[entry.loan.applicantMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(entry.loan.applicantMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td>${entry.loan.amount}</td>
                <td>
                    <c:choose>
                        <c:when test="${entry.review.decision eq 'ACCEPT'}">Approved for Disbursement</c:when>
                        <c:otherwise>Rejected</c:otherwise>
                    </c:choose>
                </td>
                <td>${fn:replace(fn:substring(entry.review.createdAt, 0, 16), 'T', ' ')}</td>
                <td><spring:message code="loan.status.${entry.loan.status}" text="${entry.loan.status}" /></td>
                <td><a href="/accountant/loan-applications/${entry.loan.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveEntries}">
            <tr>
                <td colspan="7" class="px-3 py-8 text-center text-slate-500">
                    No accountant-reviewed loan applications matched the current filter.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
