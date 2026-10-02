<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="statement.title" /></h1></div>
<p role="alert"><spring:message code="${statementError}" /></p>
<button class="app-btn btn-neutral" type="button" id="statement-back"><spring:message code="statement.back" /></button>
<script src="<c:url value='/js/statement-designer.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
