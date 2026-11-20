<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<c:if test="${branchSaved}"><div data-workspace-flashbar><div class="console-message" data-message-type="success" data-app-toast role="status"><spring:message code="branches.saved" /><button type="button" class="erp-icon-btn" data-feedback-dismiss aria-label="<spring:message code='common.close' />">&#215;</button></div></div></c:if>
<c:url value="/admin/saccos/${institution}" var="backUrl"><c:param name="section" value="members" /></c:url>
<div class="accounting-workspace accounting-create-workspace" data-resource-form-page>
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:out value='${backUrl}' />"><spring:message code="menu.admin.saccos" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="branches.delegation" /></span></nav>
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="branches.delegation" /></h1></div>
    <form method="post" action="<c:url value='/admin/branches/delegation' />" data-accounting-dirty-form="true">
        <sec:csrfInput />
        <input type="hidden" name="institution" value="<c:out value='${institution}' />" />
        <input type="hidden" name="user" value="${target.accountId}" />
        <section class="erp-resource-section"><h2><c:out value="${target.fullName}" /></h2><div class="erp-panel-body">
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="branches.branch" /></dt><dd><c:out value="${target.stationId}" /></dd></div>
            </dl>
            <label class="flex items-start gap-2"><input class="admin-cloud-checkbox" type="checkbox" name="enabled" value="true" ${delegated ? 'checked' : ''} /> <span><spring:message code="branches.delegation.enable" /></span></label>
        </div></section>
        <c:if test="${not empty delegationError}"><div class="console-message" data-message-type="error" role="alert"><spring:message code="${delegationError}" /></div></c:if>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:out value='${backUrl}' />"><spring:message code="common.cancel" /></a>
            <button type="submit" class="app-btn btn-launch"><spring:message code="kyc.saveChanges" /></button>
        </div>
    </form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
