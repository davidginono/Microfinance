<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
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
    <p class="erp-breadcrumb"><spring:message code="disbursement.archive.breadcrumb" text="Disbursement Panel / Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="disbursement.archive.title" text="Disbursement Archive" /></h1>
    <p class="erp-page-subtitle"><spring:message code="disbursement.archive.subtitle" text="Open the loans you already released and track where they are now." /></p>
</div>

<div class="erp-toolbar">
    <div class="space-y-3">
        <div>
            <p class="erp-widget-title"><spring:message code="common.currentFilter" text="Current Filter" /></p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
        <div class="erp-filter-row">
            <a href="/disbursement/archive?filter=ALL"
               class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                <spring:message code="archive.allDisbursedLoans" text="All Disbursed Loans" />
            </a>
            <a href="/disbursement/archive?filter=DISBURSED"
               class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
                <spring:message code="archive.activeDisbursedLoans" text="Active Disbursed Loans" />
            </a>
            <a href="/disbursement/archive?filter=PAID"
               class="erp-filter-tab ${currentFilterKey eq 'PAID' ? 'is-active' : ''}">
                <spring:message code="archive.paidLoans" text="Paid Loans" />
            </a>
            <a href="/disbursement/archive?filter=DEFAULTED"
               class="erp-filter-tab ${currentFilterKey eq 'DEFAULTED' ? 'is-active' : ''}">
                <spring:message code="archive.defaultedLoans" text="Defaulted Loans" />
            </a>
        </div>
        <form action="/disbursement/archive" method="get" class="erp-filter-form disbursement-archive-search-form">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="loan.loanId" text="Loan ID" />
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder='<spring:message code="common.searchLoanId" text="Search loan ID" />'
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="disbursement-archive-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/disbursement/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
            </div>
        </form>
    </div>
</div>

<div class="erp-table-wrap erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.disbursedAt" text="Disbursed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th><spring:message code="common.action" text="Action" /></th>
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
                <td><a href="/disbursement/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveRows}">
            <tr>
                <td colspan="7" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="disbursement.archive.empty" text="No disbursed loans matched the current filter." />
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<c:if test="${archivePage.totalPages gt 1}">
    <div class="mt-4 flex items-center justify-between gap-3">
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/disbursement/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/disbursement/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="page" value="${archivePage.number + 1}" />
                </c:url>
                <a class="app-btn btn-primary" href="${archiveNextUrl}"><spring:message code="common.next" text="Next" /></a>
            </c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
