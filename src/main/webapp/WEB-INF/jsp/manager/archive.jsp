<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="manager.archive.breadcrumb" text="Manager Panel / Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="manager.archive.title" text="Manager Archive" /></h1>
</div>

<div class="erp-filter-row">
    <a href="/manager/archive?filter=ALL"
       class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
        <spring:message code="archive.allReviewedLoans" text="All Reviewed Loans" />
    </a>
    <a href="/manager/archive?filter=APPROVED"
       class="erp-filter-tab ${currentFilterKey eq 'APPROVED' ? 'is-active' : ''}">
        <spring:message code="archive.approvedLoans" text="Approved Loans" />
    </a>
    <a href="/manager/archive?filter=REJECTED"
       class="erp-filter-tab ${currentFilterKey eq 'REJECTED' ? 'is-active' : ''}">
        <spring:message code="archive.rejectedLoans" text="Rejected Loans" />
    </a>
    <a href="/manager/archive?filter=DISBURSED"
       class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
        <spring:message code="archive.disbursedLoans" text="Disbursed Loans" />
    </a>
</div>
<form action="/manager/archive" method="get" class="erp-filter-form manager-archive-search-form aws-filter-toolbar" data-aws-filter-toolbar>
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                ${archiveSearchLabel}
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${archiveSearchPlaceholder}"
                       inputmode="${currentFilterKey eq 'DISBURSED' ? 'text' : 'numeric'}"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="manager-archive-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/manager/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
            </div>
        </form>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." aria-label="Manager Archive results">
    <div class="erp-table-scroll" data-view-position-key="manager-archive-table">
        <table class="erp-table">
        <thead>
        <tr>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.managerDecision" text="Manager Decision" /></th>
            <th><spring:message code="review.reason" text="Reason" /></th>
            <th><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th><spring:message code="common.action" text="Action" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${archiveRows}" var="row">
            <tr>
                <td>${row.applicationNumber}</td>
                <td><c:out value="${row.loanId}" /></td>
                <td><c:out value="${row.loanProductName}" /></td>
                <td>${row.applicantName}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td>${row.decisionLabel}</td>
                <td><c:out value="${row.reason}" /></td>
                <td>${row.reviewedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/manager/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveRows}">
            <tr>
                <td colspan="10" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="manager.archive.empty" text="No manager-reviewed loan applications matched the current filter." />
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
                <c:url var="archivePreviousUrl" value="/manager/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/manager/archive">
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
