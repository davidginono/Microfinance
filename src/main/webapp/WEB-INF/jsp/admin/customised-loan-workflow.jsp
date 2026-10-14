<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<nav class="erp-page-path"><a href="<c:url value='/admin/settings-controls?section=loan' />"><spring:message code="nav.settingsControls" text="Settings &amp; Controls" /></a><span>&#8250;</span><span aria-current="page"><spring:message code="customised.workflow.title" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="customised.workflow.title" /></h1></div>
<c:url var="workflowUrl" value="/admin/settings-controls/customised-loan" />
<form:form modelAttribute="customisedWorkflowForm" action="${workflowUrl}" method="post" cssClass="erp-form-wrap space-y-4">
    <form:errors path="*" element="div" cssClass="loan-error-summary" role="alert" tabindex="-1" />
    <c:if test="${not empty customisedWorkflowError}"><p role="alert"><spring:message code="${customisedWorkflowError}" text="${customisedWorkflowError}" /></p></c:if>
    <p><spring:message code="customised.workflow.help" /></p>
    <fieldset><legend class="font-semibold"><spring:message code="customised.workflow.reviewers" /></legend>
        <div class="grid gap-3 sm:grid-cols-2">
            <c:forTokens items="manager,loanOfficer,chairperson,board,committee,accountant" delims="," var="stage">
                <div class="flex flex-wrap items-center gap-3"><label class="flex items-center gap-2"><form:checkbox path="${stage}ReviewRequired" /><spring:message code="role.${stage}" /></label>
                    <label><span class="sr-only"><spring:message code="customised.workflow.priority" /></span><form:select path="${stage}Priority" cssClass="aws-control"><c:forEach begin="1" end="6" var="priority"><form:option value="${priority}" label="${priority}" /></c:forEach></form:select></label>
                </div>
            </c:forTokens>
        </div>
    </fieldset>
    <c:forTokens items="board,committee,disbursement" delims="," var="kind">
        <c:set var="options" value="${kind=='board'?boardReviewerOptions:kind=='committee'?creditCommitteeReviewerOptions:disbursementReviewerOptions}" />
        <c:set var="field" value="${kind=='board'?'boardReviewerIds':kind=='committee'?'creditCommitteeReviewerIds':'disbursementOfficerIds'}" />
        <fieldset><legend class="font-semibold"><spring:message code="customised.workflow.${kind}" /></legend>
            <div class="grid gap-2 sm:grid-cols-2"><c:forEach items="${options}" var="officer"><label class="flex items-center gap-2"><form:checkbox path="${field}" value="${officer.id}" /><c:out value="${officer.fullName}" /> (<c:out value="${officer.stationId}" />)</label></c:forEach></div>
            <c:if test="${empty options}"><p class="text-sm"><spring:message code="customised.workflow.noStaff" /></p></c:if>
        </fieldset>
    </c:forTokens>
    <label class="flex items-center gap-2"><form:checkbox path="disbursementProofRequired" /><spring:message code="customised.workflow.proof" /></label>
    <div class="app-modal-actions"><a href="<c:url value='/admin/settings-controls?section=loan' />" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button class="app-btn btn-neutral" type="submit"><spring:message code="common.save" /></button></div>
</form:form>
<%@ include file="../fragments/footer.jspf" %>
