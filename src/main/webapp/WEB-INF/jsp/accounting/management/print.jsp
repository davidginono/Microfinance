<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html><html lang="${pageContext.response.locale.language}"><head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width,initial-scale=1" /><title><spring:message code="management.title.${report.kind}" /></title><link rel="stylesheet" href="<c:url value='/css/management-reports.css' />" /></head><body class="management-print"><div class="reference-print-controls"><button type="button" data-management-print><spring:message code="management.print" /></button></div><%@ include file="document.jspf" %><script src="<c:url value='/js/management-reports.js' />" defer></script></body></html>
