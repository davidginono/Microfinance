<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<c:url value="${create ? '/admin/branches/new' : '/admin/branches/edit'}" var="actionUrl" />
<spring:message code="${create ? 'branches.create' : 'branches.edit'}" var="title" />
<div class="accounting-workspace accounting-create-workspace" data-resource-form-page>
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/admin/branches' />"><spring:message code="branches.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><c:out value="${title}" /></span></nav>
    <div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${title}" /></h1></div>
    <form method="post" action="${actionUrl}" class="accounting-create-form" data-workspace-submit="recover" data-accounting-dirty-form="true">
        <sec:csrfInput />
        <section class="erp-resource-section"><h2><spring:message code="branches.branch" /></h2><div class="erp-panel-body accounting-grid">
            <label class="voucher-field"><spring:message code="branches.id" />
                <input name="branch" class="aws-control" value="<c:out value='${branch}' />" maxlength="64" required ${create ? '' : 'readonly'} />
            </label>
            <label class="voucher-field"><spring:message code="branches.location" />
                <input name="location" class="aws-control" value="<c:out value='${location}' />" maxlength="255" required />
            </label>
        </div></section>
        <c:if test="${not empty branchError}"><div class="console-message" data-message-type="error" data-workspace-response-error role="alert" tabindex="-1"><spring:message code="${branchError}" /></div></c:if>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/admin/branches' />"><spring:message code="common.cancel" /></a>
            <button type="submit" class="app-btn btn-launch"><spring:message code="${create ? 'branches.create' : 'kyc.saveChanges'}" /></button>
        </div>
    </form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
