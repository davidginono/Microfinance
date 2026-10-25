<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="accountant.archive.breadcrumb" text="Accountant Panel / Archive" /></p>
    <h1 class="erp-page-title" id="accountant-archive-title"><spring:message code="accountant.archive.title" text="Accountant Archive" /></h1>
</div>

<section data-table-sync-query="true" data-resource-table="accountant-archive-1" data-table-title-id="accountant-archive-title" class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." aria-label="Accountant Archive results">
<form action="/accountant/archive" method="get" class="erp-filter-form accountant-archive-search-form aws-filter-toolbar" data-aws-filter-toolbar data-table-filter-for="accountant-archive-1">
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.status" />
                <select name="filter" class="aws-control">
                    <option value="ALL" ${currentFilterKey eq 'ALL' ? 'selected' : ''}><spring:message code="archive.allReviewedLoans" text="All Reviewed Loans" /></option>
                    <option value="APPROVED" ${currentFilterKey eq 'APPROVED' ? 'selected' : ''}><spring:message code="archive.approvedForDisbursement" text="Ready for Disbursement" /></option>
                    <option value="REJECTED" ${currentFilterKey eq 'REJECTED' ? 'selected' : ''}><spring:message code="review.rejected" text="Rejected" /></option>
                </select>
            </label>
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <span data-table-filter-label="searchId">${archiveSearchLabel}</span>
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${archiveSearchPlaceholder}"
                       inputmode="numeric"
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
            <div class="accountant-archive-search-actions">
                <c:if test="${not empty queueSearchValue or not empty fromDate or not empty toDate}">
                    <a href="/accountant/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.applyFilters" text="Apply Filters" /></button>
            </div>
        </form>

    <div class="erp-table-scroll" data-view-position-key="accountant-archive-table">
        <table class="erp-table">
        <thead>
        <tr>
            <th data-table-order="${archiveLoanIdMode?'loan':'number'}">${archivePrimaryColumnLabel}</th>
            <th data-table-order="product"><spring:message code="reports.loanProduct" text="Loan Product" /></th>
            <th data-table-order="applicant"><spring:message code="common.applicant" text="Applicant" /></th>
            <th data-table-order="amount"><spring:message code="common.amount" text="Amount" /></th>
            <th data-table-order="decision"><spring:message code="review.decision" text="Decision" /></th>
            <th data-table-order="date"><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th data-table-order="status"><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th data-table-actions-header><spring:message code="common.action" text="Action" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${archiveEntries}" var="entry">
            <tr>
                <td><c:choose><c:when test="${archiveLoanIdMode and (empty entry.loan.loanId or entry.loan.loanId == '-')}">-</c:when><c:otherwise><a class="resource-table-id-link" href="<c:url value='/accountant/loan-applications/${entry.loan.id}' />">
                    <c:choose>
                        <c:when test="${archiveLoanIdMode}"><c:out value="${empty entry.loan.loanId ? '-' : entry.loan.loanId}" /></c:when>
                        <c:otherwise>${entry.loan.applicationNumber}</c:otherwise>
                    </c:choose>
                </a></c:otherwise></c:choose></td>
                <td>
                    <c:out value="${applicationProductNames[entry.loan.id]}" />
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicantNames[entry.loan.id]}"><c:out value="${applicantNames[entry.loan.id]}" /></c:when>
                        <c:otherwise><spring:message code="common.notAvailable" /></c:otherwise>
                    </c:choose>
                </td>
                <td><fmt:formatNumber value="${entry.loan.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td>
                    <c:choose>
                        <c:when test="${entry.review.decision eq 'ACCEPT'}"><spring:message code="archive.approvedForDisbursement" text="Ready for Disbursement" /></c:when>
                        <c:otherwise><spring:message code="review.rejected" text="Rejected" /></c:otherwise>
                    </c:choose>
                </td>
                <td><c:out value="${appTime:format(entry.review.createdAt)}" /></td>
                <td><span data-resource-status="${entry.loan.status}"><spring:message code="loan.status.${entry.loan.status}" text="${entry.loan.status}" /></span></td>
                <td><a href="/accountant/loan-applications/${entry.loan.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty archiveEntries}">
            <tr>
                <td colspan="8" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="accountant.archive.empty" text="No accountant-reviewed loan applications matched the current filter." />
                </td>
            </tr>
        </c:if>
        </tbody>
        </table>
    </div>
</section>

<c:if test="${archivePage.totalPages gt 1}">
    <div data-table-pagination data-page="${archivePage.number}" data-table-for="accountant-archive-1" class="mt-4 flex items-center justify-between gap-3">
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/accountant/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a data-page-direction="previous" class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/accountant/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number + 1}" />
                </c:url>
                <a data-page-direction="next" class="app-btn btn-primary" href="${archiveNextUrl}"><spring:message code="common.next" text="Next" /></a>
            </c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
