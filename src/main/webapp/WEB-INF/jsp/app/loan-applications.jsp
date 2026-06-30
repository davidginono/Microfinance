<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="apps.breadcrumb" text="Member Workspace / Applications" /></p>
    <h1 class="erp-page-title"><spring:message code="apps.title" /></h1>
    <p class="erp-page-subtitle"><spring:message code="apps.subtitle" text="Review every loan application, its current workflow stage, and any manager feedback." /></p>
</div>
<c:set var="hasAwaitingGuarantors" value="false" />
<c:forEach items="${apps}" var="a">
    <c:if test="${a.status eq 'AWAITING_GUARANTORS'}">
        <c:set var="hasAwaitingGuarantors" value="true" />
    </c:if>
</c:forEach>
<c:if test="${hasAwaitingGuarantors}">
    <div class="erp-section mb-3 border-sky-200 bg-sky-50 text-sm text-sky-800">
        <spring:message code="loan.view.awaitingGuarantors" text="Waiting for guarantor approval. This page auto-refreshes every 1 hour." />
    </div>
    <script>
        setTimeout(function () { window.location.reload(); }, 3600000);
    </script>
</c:if>
<div class="erp-table-wrap erp-table-scroll">
    <table class="erp-table">
        <thead>
            <tr>
                <th class="px-3 py-2 text-left"><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
                <th class="px-3 py-2 text-left">
                    <spring:message code="loan.type" />
                </th>
                <th class="px-3 py-2 text-left">
                    <spring:message code="loan.amount" />
                </th>
                <th class="px-3 py-2 text-left">
                    <spring:message code="loan.status" />
                </th>
                <th class="px-3 py-2 text-left"><spring:message code="common.reason" text="Reason" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="common.actions" text="Actions" /></th>
            </tr>
        </thead>
        <tbody>
            <c:forEach items="${apps}" var="app">
                <tr>
                    <td class="px-3 py-2">${app.applicationNumber}</td>
                    <td class="px-3 py-2">
                        <spring:message code="loan.type.${app.loanType}" />
                    </td>
                    <td class="px-3 py-2"><fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                    <td class="px-3 py-2">
                        <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        <c:if test="${app.status eq 'FINAL_APPROVED' and empty app.applicantDisbursementAcknowledgedAt}">
                            <div class="mt-1 text-xs font-semibold text-emerald-700"><spring:message code="loan.disbursement.awaitingAcknowledgement" text="Awaiting your acknowledgement" /></div>
                        </c:if>
                    </td>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty managerReasons[app.id]}">${managerReasons[app.id]}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">${fn:replace(fn:substring(app.createdAt, 0, 16), 'T', ' ')}</td>
                    <td class="px-3 py-2">
                        <div class="flex flex-wrap gap-2">
                            <a href="${pageContext.request.contextPath}/app/loan-applications/${app.id}"
                               class="app-btn btn-primary">
                                <spring:message code="common.view" />
                            </a>
                            <c:if test="${app.status eq 'FINAL_APPROVED' and empty app.applicantDisbursementAcknowledgedAt}">
                                <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${app.id}/acknowledge-disbursement" class="m-0">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                    <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
                                </form>
                            </c:if>
                        </div>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
