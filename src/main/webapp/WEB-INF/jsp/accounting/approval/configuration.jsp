<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/voucher-approval.css' />" />
<div class="accounting-workspace approval-workspace approval-simple-rules">
<nav class="erp-page-path"><span aria-current="page"><spring:message code="approval.configuration" /></span></nav>
<h1 class="erp-page-title"><spring:message code="approval.configuration" /></h1>
<sec:authorize access="@access.has(principal, 'VOUCHER_RULE_CONFIGURE')" var="canConfigure" />
<c:if test="${configurationSaved}"><p role="status" class="console-message" data-message-type="success"><spring:message code="approval.saved" /></p></c:if>
<form method="get" class="erp-resource-section approval-type-filter"><h2><spring:message code="approval.voucherType" /></h2><div class="accounting-actions"><label><span class="sr-only"><spring:message code="approval.voucherType" /></span><select name="type" data-native-select="true" class="aws-control"><c:forEach items="${['RECEIPT','PAYMENT','JOURNAL']}" var="t"><option value="${t}" ${ruleForm.type==t?'selected':''}><spring:message code="approval.type.${t}" /></option></c:forEach></select></label><button class="app-btn btn-neutral"><spring:message code="approval.show" /></button></div></form>
<spring:message code="approval.noLimit" var="noLimit" /><c:url value="/finance/approval/configuration" var="saveUrl" />
<form:form modelAttribute="ruleForm" action="${saveUrl}" method="post" id="approval-rule-form" cssClass="accounting-create-form" data-voucher-form="true" data-approval-rules="true">
<form:hidden path="type" /><form:hidden path="version" />
<div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" element="p" cssClass="coa-error" /></div>
<fieldset ${canConfigure?'':'disabled'}><legend class="sr-only"><spring:message code="approval.rules" /></legend>
<div data-rule-list><c:forEach items="${ruleForm.rules}" var="row" varStatus="n">
<section class="erp-resource-section" data-rule><h2><spring:message code="approval.rule" /> <span data-rule-number>${n.index+1}</span><button type="button" class="erp-icon-btn" data-rule-remove aria-label="<spring:message code='approval.removeRule' />">×</button></h2>
<div class="approval-rule-fields">
<label><spring:message code="approval.level" /><form:select path="rules[${n.index}].scope" cssClass="aws-control" data-native-select="true"><form:option value="SINGLE"><spring:message code="approval.scope.SINGLE" /></form:option><form:option value="BATCH"><spring:message code="approval.scope.BATCH" /></form:option></form:select></label>
<label><spring:message code="approval.fromAmount" /><form:input path="rules[${n.index}].minimum" type="number" min="0" step="0.01" required="required" cssClass="aws-control" /></label>
<label><spring:message code="approval.belowAmount" /><form:input path="rules[${n.index}].maximum" type="number" min="0.01" step="0.01" cssClass="aws-control" placeholder="${noLimit}" /></label>
</div>
<div class="approval-steps" data-steps><c:forEach items="${row.stages}" var="step" varStatus="j"><div class="approval-step" data-step>
<span data-step-number>${j.index+1}</span><label><spring:message code="approval.role" /><form:select path="rules[${n.index}].stages[${j.index}].role" cssClass="aws-control" data-native-select="true"><c:forEach items="${roles}" var="role"><form:option value="${role}"><spring:message code="admin.role.${role}" /></form:option></c:forEach></form:select></label>
<label><spring:message code="approval.people" /><form:input path="rules[${n.index}].stages[${j.index}].approvals" type="number" min="1" max="10" required="required" cssClass="aws-control" /></label><button type="button" class="erp-icon-btn" data-step-remove aria-label="<spring:message code='approval.removeStep' />">×</button>
</div></c:forEach></div>
<div class="approval-rule-bottom"><button type="button" class="app-btn btn-neutral" data-step-add><spring:message code="approval.addStep" /></button><label><spring:message code="approval.afterApproval" /><form:select path="rules[${n.index}].autoPost" cssClass="aws-control" data-native-select="true"><form:option value="true"><spring:message code="approval.autoPost" /></form:option><form:option value="false"><spring:message code="approval.makerPost" /></form:option></form:select></label></div>
</section></c:forEach></div>
<c:if test="${canConfigure}"><button type="button" class="app-btn btn-neutral" data-rule-add><spring:message code="approval.addRule" /></button></c:if>
</fieldset>
<c:if test="${canConfigure}"><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/approval/configuration?type=${ruleForm.type}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="approval.save" /></button></div></c:if>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %>
<script src="<c:url value='/js/voucher-approval.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
