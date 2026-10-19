<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace accounting-create-workspace">
<nav class="erp-page-path"><a href="<c:url value='/admin/settings-controls/loan-products/${product.id}/edit' />"><c:out value="${product.displayName}" /></a><span>›</span><span aria-current="page"><spring:message code="prepayment.title" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="prepayment.title" /></h1></div>
<c:if test="${not empty prepaymentError}"><div class="coa-error" role="alert" tabindex="-1"><spring:message code="${prepaymentError}" /></div></c:if>
<c:if test="${prepaymentSaved}"><p role="status"><spring:message code="prepayment.saved" /></p></c:if>
<form:form method="post" modelAttribute="policyForm" cssClass="accounting-create-form">
<section class="accounting-section accounting-form"><h2><c:out value="${product.displayName}" /></h2>
<p><spring:message code="prepayment.productHelp" /></p>
<div class="accounting-grid">
<label><spring:message code="prepayment.schedule" /><form:select path="schedule" cssClass="aws-control">
<form:option value="NEXT_INSTALMENTS"><spring:message code="prepayment.schedule.NEXT_INSTALMENTS" /></form:option>
<form:option value="LAST_INSTALMENTS"><spring:message code="prepayment.schedule.LAST_INSTALMENTS" /></form:option>
<form:option value="PRO_RATA"><spring:message code="prepayment.schedule.PRO_RATA" /></form:option></form:select></label>
<label><spring:message code="prepayment.partialInterest" /><form:select path="partialInterest" cssClass="aws-control">
<form:option value="RECALCULATE"><spring:message code="prepayment.partial.RECALCULATE" /></form:option>
<form:option value="RETAIN_UNTIL_SETTLEMENT"><spring:message code="prepayment.partial.RETAIN_UNTIL_SETTLEMENT" /></form:option></form:select></label>
<label><spring:message code="prepayment.dayBasis" /><form:select path="dayBasis" cssClass="aws-control">
<form:option value="ACTUAL_PERIOD"><spring:message code="prepayment.basis.ACTUAL_PERIOD" /></form:option>
<form:option value="THIRTY_360"><spring:message code="prepayment.basis.THIRTY_360" /></form:option></form:select></label>
<label><spring:message code="prepayment.policyReason" /><form:textarea path="reason" rows="3" maxlength="500" required="required" cssClass="aws-control" /></label>
</div><p><spring:message code="prepayment.mandatory" /></p><p><spring:message code="prepayment.flatHelp" /></p>
</section>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/admin/settings-controls/loan-products/${product.id}/edit' />"><spring:message code="common.cancel" /></a>
<sec:authorize access="@access.has(principal,'LOAN_PRODUCTS_CONFIGURE')"><button class="app-btn btn-launch"><spring:message code="common.save" /></button></sec:authorize></div>
</form:form></div><%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
