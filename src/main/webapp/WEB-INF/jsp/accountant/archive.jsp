<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
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
    <p class="erp-breadcrumb"><spring:message code="accountant.archive.breadcrumb" text="Accountant Panel / Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="accountant.archive.title" text="Accountant Archive" /></h1>
    <p class="erp-page-subtitle"><spring:message code="accountant.archive.subtitle" text="Open the loan applications you have already reviewed and track where they are now." /></p>
</div>

<div class="erp-toolbar">
    <div class="space-y-3">
        <div>
            <p class="erp-widget-title"><spring:message code="common.currentFilter" text="Current Filter" /></p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
        <div class="erp-filter-row">
            <a href="/accountant/archive?filter=ALL"
               class="erp-filter-tab ${currentFilterKey eq 'ALL' ? 'is-active' : ''}">
                <spring:message code="archive.allReviewedLoans" text="All Reviewed Loans" />
            </a>
            <a href="/accountant/archive?filter=APPROVED"
               class="erp-filter-tab ${currentFilterKey eq 'APPROVED' ? 'is-active' : ''}">
                <spring:message code="archive.approvedForDisbursement" text="Ready for Disbursement" />
            </a>
            <a href="/accountant/archive?filter=REJECTED"
               class="erp-filter-tab ${currentFilterKey eq 'REJECTED' ? 'is-active' : ''}">
                <spring:message code="review.rejected" text="Rejected" />
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
                    <a href="/accountant/archive?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
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
            <th>${archivePrimaryColumnLabel}</th>
            <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.decision" text="Decision" /></th>
            <th><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th><spring:message code="common.action" text="Action" /></th>
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
                        <c:when test="${not empty loanProductNames[entry.loan.loanType]}"><c:out value="${loanProductNames[entry.loan.loanType]}" /></c:when>
                        <c:otherwise><spring:message code="loan.type.${entry.loan.loanType}" text="${entry.loan.loanType}" /></c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicantNames[entry.loan.applicantMemberId]}">${applicantNames[entry.loan.applicantMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(entry.loan.applicantMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td><fmt:formatNumber value="${entry.loan.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td>
                    <c:choose>
                        <c:when test="${entry.review.decision eq 'ACCEPT'}"><spring:message code="archive.approvedForDisbursement" text="Ready for Disbursement" /></c:when>
                        <c:otherwise><spring:message code="review.rejected" text="Rejected" /></c:otherwise>
                    </c:choose>
                </td>
                <td>${fn:replace(fn:substring(entry.review.createdAt, 0, 16), 'T', ' ')}</td>
                <td><spring:message code="loan.status.${entry.loan.status}" text="${entry.loan.status}" /></td>
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

<c:if test="${archivePage.totalPages gt 1}">
    <div class="mt-4 flex items-center justify-between gap-3">
        <span class="text-sm text-slate-500">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/accountant/archive">
                    <c:param name="filter" value="${currentFilterKey}" />
                    <c:param name="searchId" value="${queueSearchValue}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}"><spring:message code="common.previous" text="Previous" /></a>
            </c:if>
            <c:if test="${not archivePage.last}">
                <c:url var="archiveNextUrl" value="/accountant/archive">
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
