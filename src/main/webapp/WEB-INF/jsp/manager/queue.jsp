<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Manager Panel / Queue</p>
    <h1 class="erp-page-title">Manager Queue</h1>
    <p class="erp-page-subtitle">Move across workflow stages, inspect applicant details, and finalize the records waiting on manager action.</p>
</div>
<div class="erp-toolbar">
    <div>
        <p class="erp-widget-title">Current Filter</p>
        <h2 class="erp-widget-heading"><spring:message code="loan.status.${status}" text="${status}" /></h2>
    </div>
    <div class="erp-filter-row">
    <a href="/manager/loan-applications?status=READY_FOR_MANAGER"
       class="erp-filter-tab ${status eq 'READY_FOR_MANAGER' ? 'is-active' : ''}">
        On Review By Manager
    </a>
    <a href="/manager/loan-applications?status=AWAITING_BOARD"
       class="erp-filter-tab ${status eq 'AWAITING_BOARD' ? 'is-active' : ''}">
        On Review By Board
    </a>
    <a href="/manager/loan-applications?status=BOARD_APPROVED"
       class="erp-filter-tab ${status eq 'BOARD_APPROVED' ? 'is-active' : ''}">
        Reviewed
    </a>
    <a href="/manager/loan-applications?status=MANAGER_REJECTED"
       class="erp-filter-tab ${status eq 'MANAGER_REJECTED' ? 'is-active' : ''}">
        Rejected
    </a>
    <a href="/manager/loan-applications?status=FINAL_APPROVED"
       class="erp-filter-tab ${status eq 'FINAL_APPROVED' ? 'is-active' : ''}">
        Disbursed Loans
    </a>
    <a href="/manager/loan-applications?status=PAID"
       class="erp-filter-tab ${status eq 'PAID' ? 'is-active' : ''}">
        Paid Loans
    </a>
</div>
</div>
<div class="erp-table-wrap overflow-x-auto">
<table class="erp-table">
    <thead><tr><th>Reference</th><th>Applicant</th><th>Amount</th><th>Status</th><th>Date</th><th>Actions</th></tr></thead>
    <tbody>
    <c:forEach items="${apps}" var="app">
        <tr>
            <td class="px-3 py-2">${fn:substring(app.id, 0, 8)}</td>
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
                    <a href="/manager/loan-applications/${app.id}" class="app-btn btn-primary">Open</a>
                    <c:if test="${app.status eq 'FINAL_APPROVED'}">
                        <form action="/manager/loan-applications/${app.id}/mark-paid"
                              method="post"
                              data-confirm-title="Mark Loan as Paid"
                              data-confirm-message="Mark this disbursed loan as fully paid?"
                              data-confirm-proceed="Mark Paid">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <button type="submit" class="app-btn btn-approve">Mark Paid</button>
                        </form>
                    </c:if>
                </div>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>
</div>

<%@ include file="../fragments/confirm-modal.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
