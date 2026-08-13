<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">${boardListBreadcrumb}</p>
    <h1 class="erp-page-title">${boardListTitle}</h1>
</div>

<c:if test="${archiveView}">
    <section class="aws-current-filter-toolbar" aria-labelledby="boardArchiveCurrentFilterLabel">
            <div class="aws-current-filter-summary">
                <div class="aws-current-filter-copy">
                    <p id="boardArchiveCurrentFilterLabel" class="aws-current-filter-kicker"><spring:message code="common.currentFilter" text="Current Filter" /></p>
                    <p class="aws-current-filter-value"><c:out value="${currentFilterLabel}" /></p>
                </div>
            </div>
            <c:url var="boardArchiveAllUrl" value="${boardListRoute}"><c:param name="filter" value="ALL" /><c:param name="searchId" value="${boardSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
            <c:url var="boardArchiveApprovedUrl" value="${boardListRoute}"><c:param name="filter" value="APPROVED" /><c:param name="searchId" value="${boardSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
            <c:url var="boardArchiveRejectedUrl" value="${boardListRoute}"><c:param name="filter" value="REJECTED" /><c:param name="searchId" value="${boardSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
            <c:url var="boardArchiveDisbursedUrl" value="${boardListRoute}"><c:param name="filter" value="DISBURSED" /><c:param name="searchId" value="${boardSearchValue}" /><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /></c:url>
            <div class="erp-filter-row aws-current-filter-tabs">
                <a href="${boardArchiveAllUrl}" class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                    <spring:message code="archive.allReviewedLoans" text="All Reviewed Loans" />
                </a>
                <a href="${boardArchiveApprovedUrl}" class="erp-filter-tab ${currentFilterKey eq 'APPROVED' ? 'is-active' : ''}">
                    <spring:message code="archive.approvedLoans" text="Approved Loans" />
                </a>
                <a href="${boardArchiveRejectedUrl}" class="erp-filter-tab ${currentFilterKey eq 'REJECTED' ? 'is-active' : ''}">
                    <spring:message code="archive.rejectedLoans" text="Rejected Loans" />
                </a>
                <a href="${boardArchiveDisbursedUrl}" class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
                    <spring:message code="archive.disbursedLoans" text="Disbursed Loans" />
                </a>
            </div>
    </section>
</c:if>

<div class="erp-panel overflow-hidden">
<form action="${boardListRoute}" method="get" class="erp-filter-form board-queue-search-form aws-filter-toolbar" data-aws-filter-toolbar>
        <c:if test="${archiveView}">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
        </c:if>
        <label class="board-queue-search-label text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="loan.applicationId" text="Loan Application ID" />
            <input type="search"
                   name="searchId"
                   value="${fn:escapeXml(boardSearchValue)}"
                   placeholder='<spring:message code="common.searchLoanApplicationId" text="Search loan application ID" />'
                   inputmode="numeric"
                   class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <c:if test="${archiveView}">
            <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.fromDate" text="From Date" />
                <input type="date" name="fromDate" value="${fromDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="common.toDate" text="To Date" />
                <input type="date" name="toDate" value="${toDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
        </c:if>
        <div class="board-queue-search-actions">
            <c:if test="${not empty boardSearchValue or (archiveView and (not empty fromDate or not empty toDate))}">
                <c:url var="boardArchiveResetUrl" value="${boardListRoute}"><c:if test="${archiveView}"><c:param name="filter" value="${currentFilterKey}" /></c:if></c:url>
                <a href="${boardArchiveResetUrl}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
            </c:if>
            <button type="submit" class="app-btn btn-primary">
                <c:choose>
                    <c:when test="${archiveView}"><spring:message code="common.applyFilters" text="Apply Filters" /></c:when>
                    <c:otherwise><spring:message code="common.search" text="Search" /></c:otherwise>
                </c:choose>
            </button>
        </div>
    </form>
<div class="erp-table-wrap border-0 shadow-none" data-aws-table-region data-loading-label="Loading results...">
        <div class="erp-table-scroll">
        <table class="erp-table">
            <thead>
            <tr>
                <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
                <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
                <th><spring:message code="common.applicant" text="Applicant" /></th>
                <th><spring:message code="common.amount" text="Amount" /></th>
                <th><spring:message code="common.applicationStatus" text="Application Status" /></th>
                <th><spring:message code="board.myReview" text="My Review" /></th>
                <c:if test="${archiveView}">
                    <th><spring:message code="review.reason" text="Reason" /></th>
                </c:if>
                <th><spring:message code="loan.date" text="Date" /></th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${apps}" var="app">
                <tr>
                    <td class="px-3 py-2">${app.applicationNumber}</td>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty loanProductNames[app.loanType]}"><c:out value="${loanProductNames[app.loanType]}" /></c:when>
                            <c:otherwise><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty applicantNames[app.applicantMemberId]}">${applicantNames[app.applicantMemberId]}</c:when>
                            <c:otherwise>#${fn:substring(app.applicantMemberId, 0, 8)}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2"><fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                    <td class="px-3 py-2"><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
                    <td class="px-3 py-2">${myDecisions[app.id]}</td>
                    <c:if test="${archiveView}">
                        <td class="px-3 py-2"><c:out value="${myDecisionReasons[app.id]}" /></td>
                    </c:if>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty myDecisionDates[app.id]}">${fn:replace(fn:substring(myDecisionDates[app.id], 0, 16), 'T', ' ')}</c:when>
                            <c:otherwise>${fn:replace(fn:substring(app.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">
                        <a class="app-btn ${myDecisions[app.id] eq 'PENDING' ? 'btn-primary' : 'btn-neutral'}" href="${reviewBasePath}/loan-applications/${app.id}">
                            <c:choose>
                                <c:when test="${myDecisions[app.id] eq 'PENDING'}"><spring:message code="common.review" text="Review" /></c:when>
                                <c:otherwise><spring:message code="common.view" text="View" /></c:otherwise>
                            </c:choose>
                        </a>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty apps}">
                <tr>
                    <td colspan="${archiveView ? 9 : 8}" class="px-4 py-5 text-sm text-slate-500">
                        <c:choose>
                            <c:when test="${not empty boardSearchValue}">
                                No applications found for loan application ID
                                <span class="font-semibold text-slate-700"><c:out value="${boardSearchValue}" /></span>
                                in this view.
                            </c:when>
                            <c:otherwise>${boardListEmptyState}</c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</div>
        </div>

<c:if test="${archiveView and archivePage.totalPages gt 1}">
    <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex flex-wrap gap-2">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="${boardListRoute}">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${boardSearchValue}" />
                    <c:param name="fromDate" value="${fromDate}" />
                    <c:param name="toDate" value="${toDate}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="${boardListRoute}">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${boardSearchValue}" />
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
