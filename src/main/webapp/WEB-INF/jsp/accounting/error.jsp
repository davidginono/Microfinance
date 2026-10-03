<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="accounting.actionFailed" /></h1></div>
<c:if test="${not empty accountingSuccess}"><p role="status"><spring:message code="${accountingSuccess}" /></p></c:if>
<p role="alert"><spring:message code="${accountingError}" /></p></div>
<%@ include file="../fragments/footer.jspf" %>
