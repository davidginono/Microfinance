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
<form:form modelAttribute="customisedWorkflowForm" action="${workflowUrl}" method="post" cssClass="erp-form-wrap space-y-4 accounting-create-form">
    <form:errors path="*" element="div" cssClass="loan-error-summary" role="alert" tabindex="-1" />
    <c:if test="${not empty customisedWorkflowError}"><p class="loan-error-summary" role="alert" tabindex="-1"><spring:message code="${customisedWorkflowError}" text="${customisedWorkflowError}" /></p></c:if>
    <p><spring:message code="customised.workflow.help" /></p>
    <section class="product-builder-section">
        <div class="product-builder-section-header"><h2 class="erp-panel-title"><spring:message code="admin.settings.workflow.title" /></h2></div>
        <div class="product-builder-section-body">
        <div class="workflow-subsection">
            <p class="workflow-subsection-title" id="custom-review-stages"><spring:message code="admin.settings.workflow.reviewStages" /></p>
            <div class="workflow-table" role="table" aria-labelledby="custom-review-stages">
                <div class="workflow-table-head" role="row">
                    <div role="columnheader" id="custom-stage-column"><spring:message code="admin.settings.workflow.stage" /></div>
                    <div role="columnheader" id="custom-required-column"><spring:message code="admin.settings.workflow.required" /></div>
                    <div role="columnheader" id="custom-priority-column"><spring:message code="admin.settings.workflow.priority" /></div>
                    <div role="columnheader"><spring:message code="admin.settings.workflow.notes" /></div>
                </div>
            <c:forTokens items="manager,loanOfficer,chairperson,board,committee,accountant" delims="," var="stage">
                <div class="workflow-table-row" role="row">
                    <div class="workflow-table-cell workflow-table-stage" role="cell" id="custom-stage-${stage}" data-label="<spring:message code='admin.settings.workflow.stage' />"><spring:message code="role.${stage}" /></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.required' />"><label class="workflow-checkbox-lock"><form:checkbox path="${stage}ReviewRequired" aria-labelledby="custom-stage-${stage} custom-required-column" /></label></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.priority' />"><form:select path="${stage}Priority" cssClass="workflow-priority-select" aria-labelledby="custom-stage-${stage} custom-priority-column"><c:forEach begin="1" end="6" var="priority"><form:option value="${priority}" label="${priority}" /></c:forEach></form:select></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.notes' />"><spring:message code="${stage=='accountant'?'admin.settings.workflow.accountantNote':stage=='committee'?'admin.settings.workflow.committeeNote':'admin.settings.workflow.openPriorityNote'}" /></div>
                </div>
            </c:forTokens>
                <div class="workflow-table-row" role="row">
                    <div class="workflow-table-cell workflow-table-stage" role="cell" id="custom-stage-disbursement" data-label="<spring:message code='admin.settings.workflow.stage' />"><spring:message code="role.disbursementOfficer" /></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.required' />"><span class="workflow-checkbox-lock"><input type="checkbox" checked disabled aria-labelledby="custom-stage-disbursement custom-required-column" /></span></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.priority' />"><select class="workflow-priority-select" disabled aria-labelledby="custom-stage-disbursement custom-priority-column"><option selected>7</option></select></div>
                    <div class="workflow-table-cell" role="cell" data-label="<spring:message code='admin.settings.workflow.notes' />"><spring:message code="admin.settings.workflow.disbursementOfficerNote" /></div>
                </div>
            </div>
        </div>
    <c:forTokens items="board,committee,disbursement" delims="," var="kind">
        <c:set var="options" value="${kind=='board'?boardReviewerOptions:kind=='committee'?creditCommitteeReviewerOptions:disbursementReviewerOptions}" />
        <c:set var="field" value="${kind=='board'?'boardReviewerIds':kind=='committee'?'creditCommitteeReviewerIds':'disbursementOfficerIds'}" />
        <fieldset class="workflow-subsection"><legend class="workflow-subsection-title"><spring:message code="customised.workflow.${kind}" /></legend>
            <div class="grid gap-2 sm:grid-cols-2"><c:forEach items="${options}" var="officer"><label class="flex items-center gap-2"><form:checkbox path="${field}" value="${officer.id}" /><c:out value="${officer.fullName}" /> (<c:out value="${officer.stationId}" />)</label></c:forEach></div>
            <c:if test="${empty options}"><p class="text-sm"><spring:message code="customised.workflow.noStaff" /></p></c:if>
        </fieldset>
    </c:forTokens>
    <label class="flex items-center gap-2"><form:checkbox path="disbursementProofRequired" /><spring:message code="customised.workflow.proof" /></label>
        </div>
    </section>
    <div class="app-modal-actions"><a href="<c:url value='/admin/settings-controls?section=loan' />" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="common.save" /></button></div>
</form:form>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
