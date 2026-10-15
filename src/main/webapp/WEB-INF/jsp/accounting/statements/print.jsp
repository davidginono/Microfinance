<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<spring:message code="fs.title.POSITION" var="positionTitle" />
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width, initial-scale=1" /><title><spring:message code="fs.print" /> — <spring:message code="fs.workspace" /></title><link rel="stylesheet" href="<c:url value='/css/financial-statement-print.css' />" /></head>
<body class="financial-statement-print">
<c:forEach items="${documents}" var="document">
 <section class="fs-print-document fs-print-${document.style} ${fn:length(document.columns)>5 or document.style=='equity'?'fs-print-landscape':'fs-print-portrait'}">
  <h1><c:out value="${document.title}" /></h1>
  <header>
   <p><c:out value="${snapshot.institutionName}" /> | <c:choose><c:when test="${snapshot.parameters.institutionWide}"><spring:message code="fs.institutionScope" /></c:when><c:otherwise><c:out value="${snapshot.branchName}" /></c:otherwise></c:choose></p>
   <p><spring:message code="${document.title==positionTitle?'fs.asAt':'fs.period'}" />: <c:if test="${document.title!=positionTitle}">${run.from} — </c:if>${run.through} | TZS</p>
   <p><spring:message code="fs.previous" />: ${snapshot.parameters.previousFrom()} — ${snapshot.parameters.previousThrough()}</p>
   <p><spring:message code="fs.generated" />: <c:out value="${generatedLabel}" /> | <spring:message code="fs.provisional" /></p>
  </header>
  <table>
   <colgroup><c:forEach items="${document.columns}" var="column"><col width="${column.weight*100}%" /></c:forEach></colgroup>
   <thead><tr><c:forEach items="${document.columns}" var="column"><th class="${column.numeric?'fs-print-number':''}"><c:out value="${column.label}" /></th></c:forEach></tr></thead>
   <tbody><c:forEach items="${document.rows}" var="row"><tr class="${row.heading?'fs-print-section':''} ${row.total?'fs-print-total':''}"><c:choose>
    <c:when test="${row.heading}"><td colspan="${fn:length(document.columns)}"><c:forEach items="${row.cells}" var="cell"><c:out value="${cell.text}" /></c:forEach></td></c:when>
    <c:otherwise><c:forEach items="${row.cells}" var="cell"><td class="${not empty cell.amount?'fs-print-number':''}"><c:out value="${cell.text}" /></td></c:forEach></c:otherwise>
   </c:choose></tr></c:forEach></tbody>
  </table>
  <footer><p><spring:message code="fs.coverage" /></p><c:forEach items="${snapshot.checks}" var="check"><c:if test="${check.key!='coverage'}"><p><spring:message code="fs.check.${check.key}" /><c:if test="${not empty check.difference}">: <c:out value="${check.difference}" /></c:if></p></c:if></c:forEach><p><spring:message code="fs.snapshot" />: <c:out value="${run.id}" /></p></footer>
 </section>
</c:forEach>
</body></html>
