<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="finance.business.navigation" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.gate" /></p>
<c:if test="${not empty sourceError}"><div class="business-error" role="alert"><spring:message code="${sourceError}" /></div></c:if>
<section class="erp-table-wrap" data-aws-table-region><div class="app-table-titlebar"><h2><spring:message code="finance.business.assets"/></h2></div><div class="erp-table-scroll" data-view-position-key="accounting-assets"><table class="erp-table"><thead><tr><th><spring:message code="finance.business.description"/></th><th><spring:message code="finance.business.reference"/></th><th><spring:message code="finance.business.date"/></th><th>TZS</th><th><spring:message code="finance.business.depreciation"/> (TZS)</th><th><spring:message code="finance.business.disposed"/></th></tr></thead><tbody><c:forEach items="${assets.rows}" var="a"><tr><td><c:out value="${a.description}"/></td><td><c:out value="${a.id}"/></td><td><c:out value="${a.acquiredOn}"/></td><td class="business-money"><c:out value="${a.cost}"/></td><td class="business-money"><c:out value="${a.depreciation}"/></td><td><c:choose><c:when test="${a.disposed}"><spring:message code="common.yes"/></c:when><c:otherwise><spring:message code="common.no"/></c:otherwise></c:choose></td></tr></c:forEach></tbody></table></div></section>
</div>
<%@ include file="../../fragments/footer.jspf" %>

