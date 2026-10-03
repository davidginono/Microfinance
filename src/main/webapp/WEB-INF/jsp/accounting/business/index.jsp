<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="finance.business.navigation" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.gate" /></p>
<c:if test="${not empty sourceError}"><div class="business-error" role="alert"><spring:message code="${sourceError}" /></div></c:if>
<section class="erp-table-wrap" data-aws-table-region><div class="app-table-titlebar"><h2><spring:message code="finance.business.documents" /></h2><sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')"><a class="app-btn btn-primary" href="<c:url value='/finance/business/new' />"><spring:message code="finance.business.create" /></a></sec:authorize></div>
<div class="erp-table-scroll" data-view-position-key="accounting-business"><table class="erp-table"><thead><tr><th><spring:message code="finance.business.type" /></th><th><spring:message code="finance.business.date" /></th><th><spring:message code="finance.business.description" /></th><th class="business-money">TZS</th><th><spring:message code="finance.business.status" /></th><th><spring:message code="common.actions" /></th></tr></thead><tbody>
<c:forEach items="${documents.rows}" var="d"><tr><td><spring:message code="finance.business.kind.${d.command.kind}" /></td><td><c:out value="${d.command.effectiveDate}" /></td><td><c:out value="${d.command.description}" /></td><td class="business-money"><c:out value="${d.command.amount}" /></td><td><spring:message code="finance.business.state.${d.state}" /></td><td><a class="app-btn btn-neutral" href="<c:url value='/finance/business/${d.id}' />"><spring:message code="common.view" /></a></td></tr></c:forEach>
<c:if test="${empty documents.rows}"><tr><td colspan="6" class="erp-table-empty"><spring:message code="finance.business.empty" /></td></tr></c:if></tbody></table></div><div class="business-actions">
<c:if test="${documents.page>0}"><a class="app-btn btn-neutral" href="<c:url value='/finance/business?page=${documents.page-1}' />"><spring:message code="common.previous" /></a></c:if><span><spring:message code="repayment.page" /> <c:out value="${documents.page+1}" /></span><c:if test="${documents.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='/finance/business?page=${documents.page+1}' />"><spring:message code="common.next" /></a></c:if></div></section>
</div>
<%@ include file="../../fragments/footer.jspf" %>

