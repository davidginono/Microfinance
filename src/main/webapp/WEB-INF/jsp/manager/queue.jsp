<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .manager-queue-filter-toolbar {
        display: block;
    }
    .manager-queue-filter-stack {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 0.7rem;
    }
    .manager-queue-filter-stack .erp-filter-row {
        width: 100%;
    }
    .manager-queue-search-form {
        width: 100%;
        display: grid;
        gap: 0.85rem;
        padding: 0.9rem 1rem;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #f8fafc;
    }
    .manager-queue-search-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.55rem;
    }
    @media (min-width: 768px) {
        .manager-queue-search-form {
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: end;
        }
        .manager-queue-search-actions {
            justify-content: flex-end;
        }
    }
    @media (max-width: 640px) {
        .manager-queue-filter-stack .erp-filter-row {
            flex-direction: column;
        }
        .manager-queue-filter-stack .erp-filter-tab {
            width: 100%;
        }
        .manager-queue-search-actions > * {
            flex: 1 1 auto;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="manager.queue.breadcrumb" text="Manager Panel / Queue" /></p>
    <h1 class="erp-page-title"><spring:message code="manager.queue.title" text="Manager Queue" /></h1>
    <p class="erp-page-subtitle"><spring:message code="manager.queue.subtitle" text="Move across workflow stages, inspect applicant details, and finalize the records waiting on manager action." /></p>
</div>
<div class="erp-toolbar manager-queue-filter-toolbar">
    <div class="manager-queue-filter-stack">
        <div>
            <p class="erp-widget-title"><spring:message code="common.currentFilter" text="Current Filter" /></p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
            <c:if test="${currentFilterKey eq 'DISBURSED'}">
                <p class="mt-1 text-sm text-slate-500"><spring:message code="manager.queue.activeLoansHelp" text="Displayed loans are the current active loans." /></p>
            </c:if>
        </div>
        <div class="erp-filter-row">
            <a href="/manager/loan-applications?filter=READY_FOR_MANAGER"
               class="erp-filter-tab ${currentFilterKey eq 'READY_FOR_MANAGER' ? 'is-active' : ''}">
                <spring:message code="loan.status.READY_FOR_MANAGER" text="On Review By Manager" />
            </a>
            <a href="/manager/loan-applications?filter=AWAITING_BOARD"
               class="erp-filter-tab ${currentFilterKey eq 'AWAITING_BOARD' ? 'is-active' : ''}">
                <spring:message code="loan.status.AWAITING_BOARD" text="On Review By Board" />
            </a>
            <a href="/manager/loan-applications?filter=DISBURSED"
               class="erp-filter-tab ${currentFilterKey eq 'DISBURSED' ? 'is-active' : ''}">
                <spring:message code="manager.queue.disbursedLoans" text="Disbursed Loans" />
            </a>
        </div>
        <form action="/manager/loan-applications" method="get" class="erp-filter-form manager-queue-search-form">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                ${queueSearchLabel}
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${queueSearchPlaceholder}"
                       inputmode="numeric"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="manager-queue-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/manager/loan-applications?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
            </div>
        </form>
    </div>
</div>
<div class="erp-table-wrap overflow-x-auto">
<table class="erp-table">
    <thead>
    <tr>
        <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
        <c:if test="${currentFilterKey eq 'DISBURSED'}">
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
        </c:if>
        <th><spring:message code="common.applicant" text="Applicant" /></th>
        <th><spring:message code="common.amount" text="Amount" /></th>
        <th><spring:message code="common.status" text="Status" /></th>
        <th><spring:message code="loan.date" text="Date" /></th>
        <th><spring:message code="common.actions" text="Actions" /></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${apps}" var="app">
        <tr>
            <td class="px-3 py-2">${app.applicationNumber}</td>
            <c:if test="${currentFilterKey eq 'DISBURSED'}">
                <td class="px-3 py-2">${empty app.loanId ? '-' : app.loanId}</td>
            </c:if>
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
                <div class="flex flex-wrap gap-2">
                    <a href="/manager/loan-applications/${app.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a>
                </div>
            </td>
        </tr>
    </c:forEach>
    <c:if test="${empty apps}">
        <tr>
            <td colspan="${currentFilterKey eq 'DISBURSED' ? 7 : 6}" class="px-3 py-8 text-center text-slate-500">
                <c:choose>
                    <c:when test="${not empty queueSearchValue}">
                        No loan applications found for <span class="font-semibold text-slate-700">${fn:toLowerCase(queueSearchLabel)}</span>
                        <span class="font-semibold text-slate-700"><c:out value="${queueSearchValue}" /></span>
                        in the ${fn:toLowerCase(currentFilterLabel)} filter.
                    </c:when>
                    <c:otherwise>
                        No loan applications found for the ${fn:toLowerCase(currentFilterLabel)} filter.
                    </c:otherwise>
                </c:choose>
            </td>
        </tr>
    </c:if>
    </tbody>
</table>
</div>

<%@ include file="../fragments/confirm-modal.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
