<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="loanGroups.rules" /></h1></div>
<div class="accounting-workspace">
<c:if test="${recordingSaved}"><p role="status"><spring:message code="recording.saved" /></p></c:if>
<c:if test="${not empty groupError}"><p class="coa-error" role="alert"><spring:message code="${groupError}" /></p></c:if>
<sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE') and @access.has(principal,'LOAN_PRODUCTS_CREATE')"><form method="post" action="<c:url value='/finance/loan-records/rules/custom'/>"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><button class="app-btn btn-neutral"><spring:message code="loanGroups.customRules"/></button></form></sec:authorize>
<form method="get" class="accounting-section accounting-form-grid" data-aws-filter-toolbar>
    <label><spring:message code="common.search" /><input class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" /></label>
    <label><spring:message code="recording.product" /><select name="productId" class="aws-control"><c:if test="${not empty groupRuleForm.productId}"><option value="${groupRuleForm.productId}" selected><c:out value="${groupRuleForm.productName}" /></option></c:if><c:forEach items="${ruleProducts}" var="p" end="24"><c:if test="${p.id!=groupRuleForm.productId}"><option value="${p.id}"><c:out value="${p.label}" /></option></c:if></c:forEach></select></label>
    <button class="app-btn btn-neutral"><spring:message code="common.open" /></button>
</form>
<div class="accounting-actions"><c:forEach items="${[-1,1]}" var="step"><c:if test="${step==-1?page>0:ruleProducts.size()>25}"><c:url value="/finance/loan-records/rules" var="productPage"><c:param name="page" value="${page+step}"/><c:param name="search" value="${search}"/><c:param name="productId" value="${groupRuleForm.productId}"/></c:url><a class="app-btn btn-neutral" href="<c:out value='${productPage}'/>"><spring:message code="${step==-1?'common.previous':'common.next'}"/></a></c:if></c:forEach></div>
<c:if test="${not empty groupRuleForm.productId}">
<c:url value="/finance/loan-records/rules" var="rulesUrl" />
<form:form modelAttribute="groupRuleForm" action="${rulesUrl}" method="post" cssClass="accounting-create-form" data-resource-form-page="true" data-workspace-submit="recover">
<form:hidden path="productId" /><form:hidden path="expectedRevision" />
<sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE')" var="canConfigure"/>
<section class="accounting-section">
<h2><spring:message code="loanGroups.steps" /></h2>
<div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor"><thead><tr><th>#</th><th><spring:message code="loanGroups.group" /></th><th><spring:message code="loanGroups.count" /></th></tr></thead><tbody>
<c:forEach begin="0" end="9" var="i"><tr><td>${i+1}</td><td>
<select class="aws-control" name="groups[${i}]" ${canConfigure?'':'disabled'} aria-label="<spring:message code='loanGroups.group' /> ${i+1}"><option value="">—</option>
<c:forEach items="${userGroups}" var="g"><option value="${g}" ${groupRuleForm.groups.size()>i and groupRuleForm.groups[i]==g?'selected':''}><spring:message code="userGroup.${g}" /></option></c:forEach></select>
</td><td><input class="aws-control" type="number" name="approvals[${i}]" min="1" max="10" ${canConfigure?'':'disabled'} value="${groupRuleForm.approvals.size()>i?groupRuleForm.approvals[i]:1}" aria-label="<spring:message code='loanGroups.count' /> ${i+1}" /></td></tr></c:forEach>
</tbody></table></div>
</section>
<form:errors path="*" cssClass="accounting-alert error" element="div" />
<div class="accounting-actions"><sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE')"><button class="app-btn btn-primary"><spring:message code="common.save" /></button></sec:authorize></div>
</form:form>
</c:if>
</div>
<%@ include file="../../fragments/footer.jspf" %>
