<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>

<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<spring:htmlEscape defaultHtmlEscape="true" />

<%@ include file="../../fragments/header.jspf" %>

<%@ include file="../../fragments/sidebar.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />

<link rel="stylesheet" href="<c:url value='/css/voucher-approval.css' />" />

<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="approval.configuration" /></h1></div><div class="accounting-workspace approval-workspace">

<form method="get" class="approval-filters"><label><spring:message code="approval.voucherType" /><select name="type"><c:forEach items="${['RECEIPT','PAYMENT','JOURNAL']}" var="t"><option value="${t}" ${ruleForm.type==t?'selected':''}><spring:message code="voucher.register.${t}" /></option></c:forEach></select></label><button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button></form>

<c:if test="${configurationSaved}"><p role="status"><spring:message code="approval.saved" /></p></c:if><c:url value="/finance/approval/configuration" var="saveUrl" />

<form:form modelAttribute="ruleForm" action="${saveUrl}" method="post" cssClass="approval-form accounting-create-form" data-voucher-form="true" data-approval-rules="true"><form:hidden path="type" /><form:hidden path="version" /><div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" element="p" cssClass="coa-error" /></div>

<sec:authorize access="@access.has(principal, 'VOUCHER_RULE_CONFIGURE')" var="canConfigure" />

<fieldset ${canConfigure?'':'disabled'}><legend><spring:message code="approval.appliesTo" /></legend><div class="approval-checks"><label><form:checkbox path="individuals" /><spring:message code="approval.scope.SINGLE" /></label><label><form:checkbox path="batches" /><spring:message code="approval.scope.BATCH" /></label><label><form:checkbox path="forceAllowed" /><spring:message code="approval.allowForce" /></label></div><p><spring:message code="approval.configHelp" /></p>

<div class="app-table-titlebar"><h2><spring:message code="approval.rules" /></h2><c:if test="${canConfigure}"><button class="app-btn btn-neutral" type="button" data-rule-add><spring:message code="approval.addRule" /></button></c:if></div><p><spring:message code="approval.amountBasis.${ruleForm.type}" /></p><div data-rule-list>

<c:forEach items="${ruleForm.rules}" var="row" varStatus="n"><details class="approval-rule" data-rule open><summary><spring:message code="approval.rule" /> <span data-rule-number>${n.index+1}</span> · <c:out value="${row.name}" /></summary><form:hidden path="rules[${n.index}].firstMember" /><form:hidden path="rules[${n.index}].secondMember" /><div class="approval-fields"><label><spring:message code="approval.ruleName" /><form:input path="rules[${n.index}].name" maxlength="120"  /></label></div><div class="approval-rule-condition"><strong><spring:message code="approval.if" /></strong><label><spring:message code="approval.minimum" /><form:input path="rules[${n.index}].minimum" type="number" min="0" step="0.01"  /></label><label><spring:message code="approval.maximum" /><form:input path="rules[${n.index}].maximum" type="number" min="0" step="0.01" /></label></div><div class="approval-rule-condition"><strong><spring:message code="approval.then" /></strong><label class="approval-inline-check"><form:checkbox path="rules[${n.index}].automatic" data-auto-rule="true" /><spring:message code="approval.automatic" /></label><label><spring:message code="approval.firstReviewer" /><form:select path="rules[${n.index}].firstClaim" data-native-select="true"><form:option value="VOUCHER_APPROVAL_APPROVE"><spring:message code="approval.reviewer.standard" /></form:option><form:option value="VOUCHER_APPROVAL_ESCALATED_APPROVE"><spring:message code="approval.reviewer.escalated" /></form:option></form:select></label><label><spring:message code="approval.secondReviewer" /><form:select path="rules[${n.index}].secondClaim" data-native-select="true"><form:option value=""><spring:message code="approval.none" /></form:option><form:option value="VOUCHER_APPROVAL_APPROVE"><spring:message code="approval.reviewer.standard" /></form:option><form:option value="VOUCHER_APPROVAL_ESCALATED_APPROVE"><spring:message code="approval.reviewer.escalated" /></form:option></form:select></label><c:if test="${not empty row.firstMember or not empty row.secondMember}"><p><spring:message code="approval.namedReviewer" /></p></c:if></div><c:if test="${canConfigure}"><button class="app-btn btn-neutral" type="button" data-rule-remove><spring:message code="approval.removeRule" /></button></c:if></details></c:forEach></div><c:if test="${canConfigure}"><div class="accounting-actions approval-footer"><button class="app-btn btn-neutral"><spring:message code="approval.saveConfiguration" /></button></div></c:if></fieldset></form:form><section class="approval-overview"><h2><spring:message code="approval.testSaved" /></h2><form method="get" class="approval-filters"><input type="hidden" name="type" value="${ruleForm.type}" /><label><spring:message code="voucher.total" /> (TZS)<input type="number" step="0.01" min="0" name="example" value="<c:out value='${example}' />" required /></label><button class="app-btn btn-neutral"><spring:message code="approval.test" /></button></form><c:if test="${not empty exampleError}"><p role="alert"><spring:message code="${exampleError}" /></p></c:if><c:if test="${not empty matchedRule}"><p role="status"><c:out value="${matchedRule.name}" /> · <spring:message code="${matchedRule.automatic?'approval.automatic':'approval.firstReviewer'}" /></p></c:if></section></div><%@ include file="../accounting-form-support.jspf" %>

<script src="<c:url value='/js/voucher-approval.js' />" defer></script>

<%@ include file="../../fragments/footer.jspf" %>