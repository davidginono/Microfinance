<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/client-loan-statement.css' />" />
<link rel="stylesheet" href="<c:url value='/css/statement-reference.css' />" />
<div class="accounting-workspace">
 <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="recording.statement" /></h1>
 <sec:authorize access="@access.has(principal,'LOAN_RECORDING_EXPORT')"><div class="accounting-actions">
 <a class="app-btn btn-neutral" href="<c:url value='${statementBase}/pdf' />"><spring:message code="voucher.pdf" /></a>
 <a class="app-btn btn-neutral" target="_blank" href="<c:url value='${statementBase}/print' />"><spring:message code="voucher.print" /></a>
 </div></sec:authorize></div>
 <article class="reference-report client-loan-statement">
 <%@ include file="statement-document.jspf" %>
 </article>
 <nav class="reference-pagination" aria-label="<spring:message code='voucher.page' />"><spring:message code="voucher.page" /> ${statementPage.page+1}
 <c:if test="${statementPage.page>0}"><a class="app-btn btn-neutral" href="?page=${statementPage.page-1}"><spring:message code="common.previous" /></a></c:if>
 <c:if test="${statementPage.hasNext}"><a class="app-btn btn-neutral" href="?page=${statementPage.page+1}"><spring:message code="common.next" /></a></c:if></nav>
</div><%@ include file="../../fragments/footer.jspf" %>
