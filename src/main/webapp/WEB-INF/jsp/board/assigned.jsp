<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">${boardListBreadcrumb}</p>
    <h1 class="erp-page-title">${boardListTitle}</h1>
</div>

<div class="erp-panel overflow-hidden">
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
                        <a class="app-btn ${myDecisions[app.id] eq 'PENDING' ? 'btn-primary' : 'btn-neutral'}" href="/board/loan-applications/${app.id}">
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
                    <td colspan="7" class="px-4 py-5 text-sm text-slate-500">${boardListEmptyState}</td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</div>

<%@ include file="../fragments/footer.jspf" %>
