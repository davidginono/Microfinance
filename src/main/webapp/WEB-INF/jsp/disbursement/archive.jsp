<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="disbursement.archive.breadcrumb" text="Disbursement Panel / Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="disbursement.archive.title" text="Disbursement Archive" /></h1>
</div>

<section class="aws-current-filter-toolbar" aria-labelledby="disbursementArchiveCurrentFilterLabel">
        <div class="aws-current-filter-summary">
            <div class="aws-current-filter-copy">
                <p id="disbursementArchiveCurrentFilterLabel" class="aws-current-filter-kicker"><spring:message code="common.currentFilter" text="Current Filter" /></p>
                <p class="aws-current-filter-value"><c:out value="${currentFilterLabel}" /></p>
            </div>
        </div>
        <c:url var="disbursementArchiveAllUrl" value="/disbursement/archive"><c:param name="filter" value="ALL" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
        <c:url var="disbursementArchiveActiveUrl" value="/disbursement/archive"><c:param name="filter" value="DISBURSED" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
        <c:url var="disbursementArchivePaidUrl" value="/disbursement/archive"><c:param name="filter" value="PAID" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
        <c:url var="disbursementArchiveDefaultedUrl" value="/disbursement/archive"><c:param name="filter" value="DEFAULTED" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
        <div class="erp-filter-row aws-current-filter-tabs">
            <a href="${disbursementArchiveAllUrl}"
               class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                <spring:message code="archive.allDisbursedLoans" text="All Disbursed Loans" />
            </a>
            <a href="${disbursementArchiveActiveUrl}"
               class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
                <spring:message code="archive.activeDisbursedLoans" text="Active Disbursed Loans" />
            </a>
            <a href="${disbursementArchivePaidUrl}"
               class="erp-filter-tab ${currentFilterKey eq 'PAID' ? 'is-active' : ''}">
                <spring:message code="archive.paidLoans" text="Paid Loans" />
            </a>
            <a href="${disbursementArchiveDefaultedUrl}"
               class="erp-filter-tab ${currentFilterKey eq 'DEFAULTED' ? 'is-active' : ''}">
                <spring:message code="archive.defaultedLoans" text="Defaulted Loans" />
            </a>
        </div>
        <form action="/disbursement/archive" method="get" class="erp-filter-form disbursement-archive-search-form aws-filter-toolbar" data-aws-filter-toolbar>
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="loan.loanId" text="Loan ID" />
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder='<spring:message code="common.searchLoanId" text="Search loan ID" />'
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.fromDate" text="From Date" />
                <input type="date" name="fromDate" value="${fromDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.toDate" text="To Date" />
                <input type="date" name="toDate" value="${toDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="disbursement-archive-search-actions">
                <c:if test="${not empty queueSearchValue or not empty fromDate or not empty toDate}">
                    <a href="/disbursement/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.applyFilters" text="Apply Filters" /></button>
            </div>
        </form>
</section>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." aria-label="Disbursement Archive results">
    <div class="erp-table-scroll" data-view-position-key="disbursement-archive-table">
        <table class="erp-table">
        <thead>
        <tr>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
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
                <td><c:out value="${row.loanProductName}" /></td>
                <td>${row.applicantName}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td>${row.disbursedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/disbursement/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveRows}">
            <tr>
                <td colspan="8" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="disbursement.archive.empty" text="No disbursed loans matched the current filter." />
                </td>
            </tr>
        </c:if>
        </tbody>
        </table>
    </div>
</section>

<c:if test="${archivePage.totalPages gt 1}">
    <div class="mt-4 flex items-center justify-between gap-3">
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/disbursement/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/disbursement/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number + 1}" />
                </c:url>
                <a class="app-btn btn-primary" href="${archiveNextUrl}"><spring:message code="common.next" text="Next" /></a>
            </c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
