<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<!DOCTYPE html><html lang="${pageContext.response.locale.language}"><head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width,initial-scale=1" /><title><spring:message code="reconciliation.title" /></title><link rel="stylesheet" href="<c:url value='/css/sl-gl-reconciliation.css' />" /></head>
<body class="reconciliation-print"><button class="reconciliation-print-command" type="button" data-management-print><spring:message code="management.print" /></button>
<main><%@ include file="document.jspf" %><div class="reconciliation-basis"><p><spring:message code="reconciliation.coverage" /></p><p><spring:message code="reconciliation.interestBasis" /></p><p><spring:message code="reconciliation.liveBasis" /></p></div></main><script src="<c:url value='/js/management-reports.js' />" defer></script></body></html>
