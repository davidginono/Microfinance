<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .accountant-queue-search-form {
        width: 100%;
        display: grid;
        gap: 0.85rem;
        padding: 0.9rem 1rem;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #f8fafc;
    }
    .accountant-queue-search-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.55rem;
    }
    @media (min-width: 768px) {
        .accountant-queue-search-form {
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: end;
        }
        .accountant-queue-search-actions {
            justify-content: flex-end;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="accountant.queue.breadcrumb" text="Accountant Panel / Queue" /></p>
    <h1 class="erp-page-title"><spring:message code="accountant.queue.title" text="Accountant Queue" /></h1>
    <p class="erp-page-subtitle"><spring:message code="accountant.queue.subtitle" text="Review approved loans before they move to the disbursement officer." /></p>
</div>
<div class="erp-toolbar">
    <div class="space-y-3">
        <div>
            <p class="erp-widget-title"><spring:message code="common.currentFilter" text="Current Filter" /></p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
        <div class="erp-filter-row">
            <a href="/accountant/loan-applications?filter=AWAITING_ACCOUNTANT"
               class="erp-filter-tab ${currentFilterKey eq 'AWAITING_ACCOUNTANT' ? 'is-active' : ''}">
                <spring:message code="loan.status.AWAITING_ACCOUNTANT" text="On Review By Accountant" />
            </a>
        </div>
        <form action="/accountant/loan-applications" method="get" class="erp-filter-form accountant-queue-search-form">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                <spring:message code="loan.applicationId" text="Loan Application ID" />
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder='<spring:message code="common.searchLoanApplicationId" text="Search loan application ID" />'
                       inputmode="numeric"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="accountant-queue-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/accountant/loan-applications?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
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
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="common.status" text="Status" /></th>
            <th><spring:message code="loan.date" text="Date" /></th>
            <th><spring:message code="common.action" text="Action" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${apps}" var="app">
            <tr>
                <td class="px-3 py-2">${app.applicationNumber}</td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty applicantNames[app.applicantMemberId]}">${applicantNames[app.applicantMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(app.applicantMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">${app.amount}</td>
                <td class="px-3 py-2"><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty app.disbursementDate}">${app.disbursementDate}</c:when>
                        <c:otherwise>${fn:replace(fn:substring(app.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <a href="/accountant/loan-applications/${app.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty apps}">
            <tr>
                <td colspan="6" class="px-3 py-8 text-center text-slate-500">
                    No loan applications found for the ${fn:toLowerCase(currentFilterLabel)} filter.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
