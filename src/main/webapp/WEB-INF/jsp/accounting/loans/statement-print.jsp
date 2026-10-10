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
    <c:if test="${empty document.sections}">
        <h1><c:out value="${document.title}" /></h1>
        <dl class="statement-facts"><c:forEach items="${document.client}" var="field"><div><dt><c:out value="${field.label}" /></dt><dd><c:out value="${field.value}" /></dd></div></c:forEach></dl>
        <p><c:out value="${document['empty']}" /></p>
    </c:if>
    <c:forEach items="${document.sections}" var="section">
        <section class="statement-loan">
            <h1><c:out value="${document.title}" /></h1>
            <dl class="statement-facts"><c:forEach items="${document.client}" var="field"><div><dt><c:out value="${field.label}" /></dt><dd><c:out value="${field.value}" /></dd></div></c:forEach></dl>
            <dl class="statement-facts"><c:forEach items="${section.fields}" var="field"><div><dt><c:out value="${field.label}" /></dt><dd><c:out value="${field.value}" /></dd></div></c:forEach></dl>
            <div class="statement-transactions">
                <table>
                    <colgroup><col /><col /><col /><col /><col /><col /><col /><col /><col /><col /></colgroup>
                    <thead><tr><c:forEach items="${section.columns}" var="column"><th scope="col"><c:out value="${column}" /></th></c:forEach></tr></thead>
                    <tbody>
                    <c:forEach items="${section.rows}" var="row"><tr><c:forEach items="${row}" var="cell" varStatus="position"><td class="${position.index >= 6 ? 'statement-money' : ''}"><c:out value="${cell}" /></td></c:forEach></tr></c:forEach>
                    <c:if test="${empty section.rows}"><tr><td colspan="10"><c:out value="${document['empty']}" /></td></tr></c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </c:forEach>
    <p class="statement-note"><c:out value="${document.note}" /></p>
</main>
<script src="<c:url value='/js/report-print.js' />" defer></script>
</body>
</html>
