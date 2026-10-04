<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../../fragments/header.jspf" %>
<%@ include file="../../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="finance.business.opening.title" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.opening.gate" /></p>
<section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="finance.business.opening.registers" /></h2><sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')"><a class="app-btn btn-primary" href="<c:url value='/finance/business/openings/new' />"><spring:message code="finance.business.opening.upload" /></a></sec:authorize></div>
<div class="erp-table-scroll" data-view-position-key="business-source-openings"><table class="erp-table"><thead><tr><th><spring:message code="finance.business.type" /></th><th><spring:message code="finance.business.opening.through" /></th><th class="business-money">TZS</th><th><spring:message code="finance.business.status" /></th><th><spring:message code="common.actions" /></th></tr></thead><tbody>
<c:forEach items="${openings.rows}" var="o"><tr><td><spring:message code="finance.business.opening.purpose.${o.purpose}" /></td><td><c:out value="${o.through}" /></td><td class="business-money"><c:out value="${o.signedBalance}" /></td><td><c:choose><c:when test="${empty o.decision}"><spring:message code="finance.business.opening.pending" /></c:when><c:otherwise><spring:message code="finance.business.opening.state.${o.decision}" /></c:otherwise></c:choose></td><td><a class="app-btn btn-neutral" href="<c:url value='/finance/business/openings/${o.id}' />"><spring:message code="common.view" /></a></td></tr></c:forEach>
<c:if test="${empty openings.rows}"><tr><td colspan="5" class="erp-table-empty"><spring:message code="finance.business.opening.empty" /></td></tr></c:if></tbody></table></div>
<div class="business-actions"><c:if test="${openings.page>0}"><a class="app-btn btn-neutral" href="<c:url value='/finance/business/openings?page=${openings.page-1}' />"><spring:message code="common.previous" /></a></c:if><span><spring:message code="repayment.page" /> <c:out value="${openings.page+1}" /></span><c:if test="${openings.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='/finance/business/openings?page=${openings.page+1}' />"><spring:message code="common.next" /></a></c:if></div></section>
</div><%@ include file="../../../fragments/footer.jspf" %>
