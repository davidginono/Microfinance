<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title><c:out value="${document.title}" /></title>
    <link rel="stylesheet" href="<c:url value='/css/client-loan-statement.css' />" />
</head>
<body class="client-statement-print">
<div class="statement-print-tools"><button type="button" class="report-print-action"><spring:message code="voucher.print" /></button></div>
<main class="client-loan-statement">
<%@ include file="statement-document.jspf" %>
</main>
<script src="<c:url value='/js/report-print.js' />" defer></script>
</body>
</html>
