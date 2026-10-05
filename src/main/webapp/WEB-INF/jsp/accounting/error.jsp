<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="ledger-workspace"><div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.ledger.failed" /></h1></div>
 <p class="ledger-notice ledger-error" role="alert"><spring:message code="${ledgerError}" /></p>
 <c:if test="${not empty retained}"><section><h2><spring:message code="accounting.ledger.retained" /></h2><dl class="ledger-facts"><c:forEach items="${retained}" var="field"><div><dt><spring:message code="accounting.ledger.field.${field.key}" /></dt><dd><c:out value="${field.value}" /></dd></div></c:forEach></dl></section></c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
