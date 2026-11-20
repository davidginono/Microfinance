<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ include file="support.jspf" %>
<c:url value="/finance/loan-records/${cancellation.id}" var="loanUrl" />
<spring:message code="${cancellationAction.available ? cancellationAction.submitLabel : 'cancellation.history'}" var="cancellationTitle" />
<div class="accounting-workspace accounting-create-workspace" data-resource-form-page="true">
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
        <a class="erp-page-path__link" href="${loanUrl}"><spring:message code="recording.loanDetails" /></a>
        <span aria-hidden="true">›</span><span aria-current="page"><c:out value="${cancellationTitle}" /></span>
    </nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${cancellationTitle}" /></h1></div>
    <section class="erp-resource-section">
        <h2><spring:message code="recording.loanDetails" /></h2>
        <div class="erp-panel-body">
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${cancellation.number}" /></dd></div>
                <div><dt><spring:message code="recording.client" /></dt><dd><c:out value="${cancellation.client}" /></dd></div>
                <div><dt><spring:message code="voucher.status" /></dt><dd><span data-resource-status="${cancellation.status}"><spring:message code="recording.status.${cancellation.status}" /></span></dd></div>
                <div><dt><spring:message code="recording.principal" /> (TZS)</dt><dd><fmt:formatNumber value="${cancellation.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <c:if test="${not empty cancellation.cancelledAt}">
                    <div><dt><spring:message code="cancellation.by" /></dt><dd><c:out value="${cancellation.cancelledBy}" /></dd></div>
                    <div><dt><spring:message code="voucher.date" /></dt><dd><c:out value="${appTime:display(cancellation.cancelledAt)}" /></dd></div>
                    <div><dt><spring:message code="repayment.reason" /></dt><dd><c:out value="${cancellation.reason}" /></dd></div>
                </c:if>
            </dl>
            <c:if test="${not empty cancellation.adjustmentId}">
                <p><spring:message code="cancellation.request.${cancellation.adjustmentState}" />
                    <sec:authorize access="@access.has(principal,'LOAN_ADJUSTMENTS_VIEW')">
                        <a class="resource-table-id-link" href="<c:url value='/finance/loan-adjustments/${cancellation.adjustmentId}' />"><spring:message code="common.view" /></a>
                    </sec:authorize>
                </p>
            </c:if>
        </div>
    </section>
    <c:if test="${not empty cancellationAction.blockedReason and cancellation.adjustmentState ne 'PENDING' and cancellation.adjustmentState ne 'APPROVED' and empty cancellation.cancelledAt}">
        <div class="console-message" data-message-type="warning" role="status"><spring:message code="${cancellationAction.blockedReason}" /></div>
    </c:if>
    <c:set var="errorKey" value="${cancellationError}" />
    <c:choose>
        <c:when test="${cancellationAction.available}">
            <c:url value="/finance/loan-records/${cancellation.id}/cancel" var="cancelUrl" />
            <form:form action="${cancelUrl}" method="post" modelAttribute="cancellationForm" class="accounting-create-form grid gap-4" data-resource-form-page="true" data-workspace-submit="recover" data-accounting-dirty-form="true">
                <form:hidden path="requestKey" /><form:hidden path="state" /><form:hidden path="terms" />
                <section class="erp-resource-section">
                    <h2><spring:message code="cancellation.history" /></h2>
                    <div class="erp-panel-body space-y-4">
                        <div class="accounting-grid">
                            <div class="voucher-field">
                                <form:label path="reason"><spring:message code="repayment.reason" /></form:label>
                                <form:textarea path="reason" required="required" maxlength="500" cssClass="aws-control" />
                                <form:errors path="reason" cssClass="coa-error" />
                            </div>
                            <c:if test="${cancellation.funded}">
                                <div class="voucher-field">
                                    <form:label path="effectiveDate"><spring:message code="voucher.date" /></form:label>
                                    <form:input path="effectiveDate" type="date" required="required" cssClass="aws-control" />
                                    <form:errors path="effectiveDate" cssClass="coa-error" />
                                </div>
                                <div class="voucher-field">
                                    <form:label path="evidence"><spring:message code="cancellation.fundsEvidence" /></form:label>
                                    <form:textarea path="evidence" required="required" maxlength="500" cssClass="aws-control" />
                                    <form:errors path="evidence" cssClass="coa-error" />
                                </div>
                            </c:if>
                        </div>
                        <div class="space-y-2">
                            <p><spring:message code="${cancellation.funded ? 'cancellation.fundedEffect' : 'cancellation.effect'}" /></p>
                            <label class="flex items-start gap-2"><form:checkbox path="confirmed" required="required" /><span><spring:message code="cancellation.confirm" /></span></label>
                            <form:errors path="confirmed" cssClass="coa-error" />
                        </div>
                    </div>
                </section>
                <c:if test="${not empty errorKey}">
                <div class="console-message" data-message-type="error" data-workspace-response-error role="alert" tabindex="-1"><spring:message code="${errorKey}" /></div>
                </c:if>
                <div class="accounting-actions accounting-form-actions">
                    <a class="app-btn btn-neutral" href="${loanUrl}"><spring:message code="common.cancel" /></a>
                    <button type="submit" class="app-btn btn-launch"><spring:message code="${cancellationAction.submitLabel}" /></button>
                </div>
            </form:form>
        </c:when>
        <c:otherwise>
            <c:if test="${not empty errorKey}">
                <div class="console-message" data-message-type="error" data-workspace-response-error role="alert" tabindex="-1"><spring:message code="${errorKey}" /></div>
                </c:if>
            <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="${loanUrl}"><spring:message code="common.close" /></a></div>
        </c:otherwise>
    </c:choose>
</div>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="../../fragments/footer.jspf" %>
