<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:url value="${create ? '/admin/branches/new' : '/admin/branches/edit'}" var="actionUrl" />
<spring:message code="${create ? 'branches.create' : 'branches.edit'}" var="title" />
<div class="accounting-workspace" data-resource-form-page>
    <nav class="erp-page-path"><a href="<c:url value='/admin/branches' />"><spring:message code="branches.title" /></a><span>›</span><span aria-current="page"><c:out value="${title}" /></span></nav>
    <div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${title}" /></h1></div>
    <form method="post" action="${actionUrl}" data-workspace-submit="recover">
        <sec:csrfInput />
        <section class="erp-resource-section"><h2><spring:message code="branches.branch" /></h2><div class="erp-panel-body accounting-grid">
            <label><spring:message code="branches.id" />
                <input name="branch" class="aws-control" value="<c:out value='${branch}' />" maxlength="64" required ${create ? '' : 'readonly'} />
            </label>
            <label><spring:message code="branches.location" />
                <input name="location" class="aws-control" value="<c:out value='${location}' />" maxlength="255" required />
            </label>
        </div></section>
        <c:if test="${not empty branchError}"><div class="console-message" data-message-type="error" role="alert"><spring:message code="${branchError}" /></div></c:if>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/admin/branches' />"><spring:message code="common.cancel" /></a>
            <button type="submit" class="app-btn btn-launch"><spring:message code="${create ? 'branches.create' : 'kyc.saveChanges'}" /></button>
        </div>
    </form>
</div>
<%@ include file="../fragments/footer.jspf" %>
