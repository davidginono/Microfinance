<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.accounts" /></h1></div>
<c:url value="/finance/accounts/defaults" var="defaultsUrl" />
<form action="${defaultsUrl}" method="post" data-coa-defaults>
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <input type="hidden" name="returnTo" value="<c:out value='${setupReturnTo}' />" />
    <span class="app-loading-spinner" role="status"><span class="sr-only"><spring:message code="common.loading" /></span></span>
    <noscript><button type="submit" class="app-btn btn-neutral"><spring:message code="common.continue" /></button></noscript>
</form>
<script src="<c:url value='/js/accounting-defaults.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
