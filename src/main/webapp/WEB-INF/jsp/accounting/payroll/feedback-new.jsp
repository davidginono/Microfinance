<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<%@ include file="feedback-path.jspf" %>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.feedback.import" /></h1></div>
<div class="accounting-workspace accounting-create-workspace"><form method="post" enctype="multipart/form-data" class="accounting-create-form" action="<c:url value='/finance/payroll/${batch.id}/feedback/new' />">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><%@ include file="error.jspf" %>
<c:if test="${feedbackErrorLine!=null}"><p role="status"><spring:message code="payroll.feedback.errorLine" arguments="${feedbackErrorLine}" /></p></c:if>
<section class="erp-resource-section"><h2><spring:message code="payroll.feedback.spreadsheet" /></h2><div class="grid gap-4">
<p><c:out value="${batch.reference}" /> · <c:out value="${batch.employer}" /></p>
<p><spring:message code="payroll.feedback.format" /></p>
<ol><c:forEach items="${columns}" var="column"><li><code><c:out value="${column}" /></code></li></c:forEach></ol>
<p><spring:message code="payroll.feedback.values" /></p>
<p><spring:message code="payroll.feedback.noMoney" /></p>
<label class="grid gap-2"><spring:message code="payroll.feedback.file" /><input class="aws-control" type="file" name="file" accept=".xlsx" required /></label>
<label class="grid gap-2"><spring:message code="payroll.feedback.source" /><textarea name="evidence" class="aws-control" maxlength="500" required rows="3"><c:out value="${evidence}" /></textarea></label>
</div></section><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/payroll/${batch.id}/feedback' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="payroll.feedback.import" /></button></div>
</form><%@ include file="../accounting-form-support.jspf" %></div><%@ include file="../../fragments/footer.jspf" %>
