<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width, initial-scale=1" /><title><spring:message code="fs.print" /> — <spring:message code="fs.workspace" /></title><link rel="stylesheet" href="<c:url value='/css/financial-statement-print.css' />" /><link rel="stylesheet" href="<c:url value='/css/print-footer.css' />" /></head>
<body data-print-document class="financial-statement-print">
 <section class="fs-print-document fs-print-${document.style} ${fn:length(document.columns)>5 or document.style=='equity'?'fs-print-landscape':'fs-print-portrait'}">
  <c:choose><c:when test="${document.style=='equity'}">
   <h1><c:out value="${document.title}" /></h1>
   <p class="fs-equity-period"><span><spring:message code="fs.equity.forPeriod" /></span><span><c:out value="${equityPeriod}" /></span></p>
  </c:when><c:otherwise>
  <h1><c:out value="${document.title}" /></h1>
  <header>
   <p><c:out value="${snapshot.institutionName}" /> | <c:choose><c:when test="${snapshot.parameters.institutionWide}"><spring:message code="fs.institutionScope" /></c:when><c:otherwise><c:out value="${snapshot.branchName}" /></c:otherwise></c:choose></p>
   <p><spring:message code="${document.asAt?'fs.asAt':'fs.period'}" />: <c:if test="${not document.asAt}">${appTime:display(run.from)} — </c:if>${appTime:display(run.through)} | TZS</p>
   <p><spring:message code="fs.previous" />: ${appTime:display(snapshot.parameters.previousFrom())} — ${appTime:display(snapshot.parameters.previousThrough())}</p>
   <p><spring:message code="fs.generated" />: <c:out value="${generatedLabel}" /></p>
  </header>
  </c:otherwise></c:choose>
  <table data-table-sort-mode="none" data-table-sort-reason="document">
   <colgroup><c:forEach items="${document.columns}" var="column"><col width="${column.weight*100}%" /></c:forEach></colgroup>
   <thead><tr><c:forEach items="${document.columns}" var="column"><th class="${column.numeric?'fs-print-number':''}"><c:out value="${column.label}" /></th></c:forEach></tr></thead>
   <tbody><c:forEach items="${document.rows}" var="row"><tr class="${row.heading?'fs-print-section':''} ${row.total?'fs-print-total':''}"><c:choose>
    <c:when test="${row.heading}"><td colspan="${fn:length(document.columns)}"><c:forEach items="${row.cells}" var="cell"><c:out value="${cell.text}" /></c:forEach></td></c:when>
    <c:otherwise><c:forEach items="${row.cells}" var="cell"><td class="${not empty cell.amount?'fs-print-number':''}"><c:out value="${cell.text}" /></td></c:forEach></c:otherwise>
   </c:choose></tr></c:forEach></tbody>
  </table>
  <c:if test="${document.style=='equity'}"><p class="fs-equity-scope"><c:out value="${snapshot.institutionName}" /> | <c:choose><c:when test="${snapshot.parameters.institutionWide}"><spring:message code="fs.institutionScope" /></c:when><c:otherwise><c:out value="${snapshot.branchName}" /></c:otherwise></c:choose> | TZS</p></c:if>
  <footer><p><spring:message code="fs.provisional" /></p></footer>
 </section>
</body></html>
