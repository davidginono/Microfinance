<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="finance.business.navigation" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.gate" /></p>
<c:if test="${not empty sourceError}"><div class="business-error" role="alert"><spring:message code="${sourceError}" /></div></c:if>
<section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="finance.business.suppliers"/></h2></div><div class="erp-table-scroll" data-view-position-key="accounting-suppliers"><table class="erp-table"><thead><tr><th><spring:message code="finance.business.name"/></th><th><spring:message code="finance.business.reference"/></th><th><spring:message code="finance.business.evidenceReference"/></th></tr></thead><tbody><c:forEach items="${suppliers.rows}" var="s"><tr><td><c:out value="${s.name}"/></td><td><c:out value="${s.id}"/></td><td><c:out value="${s.evidenceReference}"/></td></tr></c:forEach></tbody></table></div></section><sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')"><form method="post" action="<c:url value='/finance/business/suppliers'/>" class="business-form"><input type="hidden" name="${_csrf.parameterName}" value="<c:out value='${_csrf.token}' />" /><div class="business-grid"><label><spring:message code="finance.business.name"/><input name="name" maxlength="160" required/></label><label><spring:message code="finance.business.evidenceReference"/><input name="evidence" maxlength="500" required/></label></div><button class="app-btn btn-primary" type="submit"><spring:message code="finance.business.registerSupplier"/></button></form></sec:authorize>
</div>
<%@ include file="../../fragments/footer.jspf" %>

