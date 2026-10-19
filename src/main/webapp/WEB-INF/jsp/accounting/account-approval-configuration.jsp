<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="account-version-shell.jspf" %>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="coav.configuration" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
 <p><spring:message code="coav.policyHelp" /></p>
 <c:url value="/finance/accounts/approval-configuration" var="policyUrl" />
 <section class="accounting-form accounting-create-card">
 <h2><spring:message code="coav.approvalMode" /></h2>
 <form:form method="post" action="${policyUrl}" modelAttribute="policyForm" cssClass="accounting-create-form">
  <form:hidden path="version" />
  <div role="alert"><form:errors path="*" cssClass="coa-error" element="p" /></div>
  <c:forEach items="${['create','update','deactivate','reactivate']}" var="op">
   <label><spring:message code="coav.policy.${op}" /><form:select path="${op}Automatic" cssClass="aws-control"><form:option value="true"><spring:message code="coav.mode.AUTOMATIC" /></form:option><form:option value="false"><spring:message code="coav.mode.INDEPENDENT" /></form:option></form:select></label>
  </c:forEach>
  <label><spring:message code="coav.reason" /><form:textarea path="reason" cssClass="aws-control" maxlength="500" rows="2" required="required" /></label>
  <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNT_RULES_CONFIGURE')"><div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch"><spring:message code="coav.saveConfiguration" /></button></div></sec:authorize>
 </form:form>
 </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
