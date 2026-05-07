<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .board-queue-search-form {
        width: 100%;
        display: grid;
        gap: 0.85rem;
        padding: 0.9rem 1rem;
        border-bottom: 1px solid #d7dde3;
        background: #f8fafc;
    }
    .board-queue-search-label {
        display: block;
        min-width: 0;
    }
    .board-queue-search-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.55rem;
    }
    @media (min-width: 768px) {
        .board-queue-search-form {
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: end;
        }
        .board-queue-search-actions {
            justify-content: flex-end;
        }
    }
    @media (max-width: 640px) {
        .board-queue-search-actions > * {
            flex: 1 1 auto;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">${boardListBreadcrumb}</p>
    <h1 class="erp-page-title">${boardListTitle}</h1>
</div>

<div class="erp-panel overflow-hidden">
    <form action="${boardListRoute}" method="get" class="erp-filter-form board-queue-search-form">
        <label class="board-queue-search-label text-xs font-semibold uppercase tracking-wide text-slate-500">
            Loan Application ID
            <input type="search"
                   name="searchId"
                   value="${fn:escapeXml(boardSearchValue)}"
                   placeholder="Search loan application ID"
                   inputmode="numeric"
                   class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <div class="board-queue-search-actions">
            <c:if test="${not empty boardSearchValue}">
                <a href="${boardListRoute}" class="app-btn btn-neutral">Reset</a>
            </c:if>
            <button type="submit" class="app-btn btn-primary">Search</button>
        </div>
    </form>
    <div class="erp-table-wrap overflow-x-auto border-0 shadow-none">
        <table class="erp-table">
            <thead>
            <tr>
                <th>Loan Application ID</th>
                <th>Applicant</th>
                <th>Amount</th>
                <th>Application Status</th>
                <th>My Review</th>
                <th>Date</th>
                <th></th>
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
                    <td class="px-3 py-2">${myDecisions[app.id]}</td>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty myDecisionDates[app.id]}">${fn:replace(fn:substring(myDecisionDates[app.id], 0, 16), 'T', ' ')}</c:when>
                            <c:otherwise>${fn:replace(fn:substring(app.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">
                        <a class="app-btn ${myDecisions[app.id] eq 'PENDING' ? 'btn-primary' : 'btn-neutral'}" href="${reviewBasePath}/loan-applications/${app.id}">
                            <c:choose>
                                <c:when test="${myDecisions[app.id] eq 'PENDING'}">Review</c:when>
                                <c:otherwise>View</c:otherwise>
                            </c:choose>
                        </a>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty apps}">
                <tr>
                    <td colspan="7" class="px-4 py-5 text-sm text-slate-500">
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

<%@ include file="../fragments/footer.jspf" %>
