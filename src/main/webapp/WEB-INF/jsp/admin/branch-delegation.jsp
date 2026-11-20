<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:if test="${branchSaved}"><div data-workspace-flashbar><div class="console-message" data-message-type="success" data-app-toast role="status"><spring:message code="branches.saved" /><button type="button" class="erp-icon-btn" data-feedback-dismiss aria-label="<spring:message code='common.close' />">&#215;</button></div></div></c:if>
<c:url value="/admin/saccos/${institution}" var="backUrl"><c:param name="section" value="members" /></c:url>
<div class="accounting-workspace" data-resource-form-page>
    <nav class="erp-page-path"><a href="<c:out value='${backUrl}' />"><spring:message code="menu.admin.saccos" /></a><span>›</span><span aria-current="page"><spring:message code="branches.delegation" /></span></nav>
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="branches.delegation" /></h1></div>
    <form method="post" action="<c:url value='/admin/branches/delegation' />">
        <sec:csrfInput />
        <input type="hidden" name="institution" value="<c:out value='${institution}' />" />
        <input type="hidden" name="user" value="${target.accountId}" />
        <section class="erp-resource-section"><h2><c:out value="${target.fullName}" /></h2><div class="erp-panel-body">
            <p><spring:message code="branches.branch" /> <c:out value="${target.stationId}" /></p>
            <label><input type="checkbox" name="enabled" value="true" ${delegated ? 'checked' : ''} /> <spring:message code="branches.delegation.enable" /></label>
        </div></section>
        <c:if test="${not empty delegationError}"><p class="field-error" role="alert"><spring:message code="${delegationError}" /></p></c:if>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:out value='${backUrl}' />"><spring:message code="common.cancel" /></a>
            <button type="submit" class="app-btn btn-launch"><spring:message code="kyc.saveChanges" /></button>
        </div>
    </form>
</div>
<%@ include file="../fragments/footer.jspf" %>
