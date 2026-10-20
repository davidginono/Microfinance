<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="manager.archive.breadcrumb" text="Manager Panel / Archive" /></p>
    <h1 class="erp-page-title" id="manager-archive-title"><spring:message code="manager.archive.title" text="Manager Archive" /></h1>
</div>

<c:url var="managerArchiveAllUrl" value="/manager/archive"><c:param name="filter" value="ALL" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
<c:url var="managerArchiveApprovedUrl" value="/manager/archive"><c:param name="filter" value="APPROVED" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
<c:url var="managerArchiveRejectedUrl" value="/manager/archive"><c:param name="filter" value="REJECTED" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
<c:url var="managerArchiveDisbursedUrl" value="/manager/archive"><c:param name="filter" value="DISBURSED" /><c:param name="searchId" value="${queueSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
<div class="erp-filter-row">
    <a href="${managerArchiveAllUrl}"
       class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
        <spring:message code="archive.allReviewedLoans" text="All Reviewed Loans" />
    </a>
    <a href="${managerArchiveApprovedUrl}"
       class="erp-filter-tab ${currentFilterKey eq 'APPROVED' ? 'is-active' : ''}">
        <spring:message code="archive.approvedLoans" text="Approved Loans" />
    </a>
    <a href="${managerArchiveRejectedUrl}"
       class="erp-filter-tab ${currentFilterKey eq 'REJECTED' ? 'is-active' : ''}">
        <spring:message code="archive.rejectedLoans" text="Rejected Loans" />
    </a>
    <a href="${managerArchiveDisbursedUrl}"
       class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
        <spring:message code="archive.disbursedLoans" text="Disbursed Loans" />
    </a>
</div>
<form action="/manager/archive" method="get" class="erp-filter-form manager-archive-search-form aws-filter-toolbar" data-aws-filter-toolbar data-table-filter-for="manager-archive-1">
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
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.fromDate" text="From Date" />
                <input type="date" name="fromDate" value="${fromDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.toDate" text="To Date" />
                <input type="date" name="toDate" value="${toDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="manager-archive-search-actions">
                <c:if test="${not empty queueSearchValue or not empty fromDate or not empty toDate}">
                    <a href="/manager/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.applyFilters" text="Apply Filters" /></button>
            </div>
        </form>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." aria-label="Manager Archive results" data-resource-table="manager-archive-1" data-resource-split data-table-title-id="manager-archive-title">
    <div class="erp-table-scroll" data-view-position-key="manager-archive-table">
        <table class="erp-table">
        <thead>
        <tr>
            <th data-table-order="number"><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th data-table-order="loan"><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th data-table-order="product"><spring:message code="reports.loanProduct" text="Loan Product" /></th>
            <th data-table-order="applicant"><spring:message code="common.applicant" text="Applicant" /></th>
            <th data-table-order="amount"><spring:message code="common.amount" text="Amount" /></th>
            <th data-table-order="decision"><spring:message code="review.managerDecision" text="Manager Decision" /></th>
            <th data-table-order="reason"><spring:message code="review.reason" text="Reason" /></th>
            <th data-table-order="date"><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th data-table-order="status"><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th data-table-actions-header><spring:message code="common.action" text="Action" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${archiveRows}" var="row">
            <tr>
                <td><a class="resource-table-id-link" href="<c:url value='/manager/loan-applications/${row.id}' />">${row.applicationNumber}</a></td>
                <td><c:choose><c:when test="${not empty row.loanId and row.loanId != '-'}"><a class="resource-table-id-link" href="<c:url value='/manager/loan-applications/${row.id}' />"><c:out value="${row.loanId}" /></a></c:when><c:otherwise><c:out value="${row.loanId}" /></c:otherwise></c:choose></td>
                <td><c:out value="${row.loanProductName}" /></td>
                <td>${row.applicantName}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td><span data-resource-status='<c:out value="${row.decisionLabel}" />'>${row.decisionLabel}</span></td>
                <td><c:out value="${row.reason}" /></td>
                <td>${appTime:display(row.reviewedAt)}</td>
                <td><span data-resource-status='<c:out value="${row.currentStatusLabel}" />'>${row.currentStatusLabel}</span></td>
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
    <div class="mt-4 flex items-center justify-between gap-3" data-table-pagination-shell>
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex gap-2" data-table-pagination data-table-for="manager-archive-1" data-page="${archivePage.number}">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/manager/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}" data-page-direction="previous"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/manager/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number + 1}" />
                </c:url>
                <a class="app-btn btn-primary" href="${archiveNextUrl}" data-page-direction="next"><spring:message code="common.next" text="Next" /></a>
            </c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
