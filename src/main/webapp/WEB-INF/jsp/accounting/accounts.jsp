<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
<c:if test="${not empty accountingError}"><p role="alert"><spring:message code="${accountingError}" /></p></c:if>
<c:if test="${not empty accountingSuccess}"><p role="status"><spring:message code="${accountingSuccess}" /></p></c:if>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.accounts" /></h1></div>
<p><spring:message code="accounting.gated" /></p>
<section class="erp-table-wrap"><div class="erp-table-scroll" data-view-position-key="gl-accounts"><table class="erp-table"><thead><tr>
<c:forEach items="${['code','name','type','kind','purpose','active']}" var="label"><th><spring:message code="accounting.${label}" /></th></c:forEach><th><spring:message code="common.actions" /></th></tr></thead><tbody>
<c:forEach items="${accounts.rows}" var="a"><tr><td><c:out value="${a.code}" /></td><td><c:out value="${a.name}" /></td><td><spring:message code="accounting.option.${a.type}" /></td><td><spring:message code="accounting.option.${a.kind}" /></td><td><spring:message code="accounting.option.${a.purpose}" /></td><td><spring:message code="accounting.active.${a.active}" /></td><td>
<sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><c:if test="${a.active}"><form method="post" action="<c:url value='/finance/accounts/${a.id}/deactivate' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="accounting.deactivate" /></button></form></c:if></sec:authorize>
</td></tr></c:forEach><c:if test="${empty accounts.rows}"><tr><td colspan="7"><spring:message code="accounting.empty" /></td></tr></c:if></tbody></table></div>
<div class="accounting-actions"><c:if test="${accounts.page > 0}"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts?page=${accounts.page-1}' />"><spring:message code="common.previous" /></a></c:if><c:if test="${accounts.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts?page=${accounts.page+1}' />"><spring:message code="common.next" /></a></c:if></div></section>
<sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')"><section class="accounting-form"><h2><spring:message code="accounting.createAccount" /></h2><form method="post" action="<c:url value='/finance/accounts' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><div class="accounting-grid">
<label><spring:message code="accounting.code" /><input class="aws-control" name="code" maxlength="40" pattern="[A-Z0-9][A-Z0-9_.-]{0,39}" required /></label>
<label><spring:message code="accounting.name" /><input class="aws-control" name="name" maxlength="160" required /></label>
<label><spring:message code="accounting.type" /><select class="aws-control" name="type"><c:forEach items="${accountTypes}" var="o"><option value="${o}"><spring:message code="accounting.option.${o}" /></option></c:forEach></select></label>
<label><spring:message code="accounting.normalBalance" /><select class="aws-control" name="normalBalance"><option value="DEBIT"><spring:message code="accounting.debit" /></option><option value="CREDIT"><spring:message code="accounting.credit" /></option></select></label>
<label><spring:message code="accounting.kind" /><select class="aws-control" name="kind"><c:forEach items="${accountKinds}" var="o"><option value="${o}"><spring:message code="accounting.option.${o}" /></option></c:forEach></select></label>
<label><spring:message code="accounting.purpose" /><select class="aws-control" name="purpose"><c:forEach items="${accountPurposes}" var="o"><option value="${o}"><spring:message code="accounting.option.${o}" /></option></c:forEach></select></label>
<label><spring:message code="accounting.parent" /><select class="aws-control" name="parentId"><option value=""><spring:message code="accounting.none" /></option><c:forEach items="${accounts.rows}" var="a"><c:if test="${a.kind=='HEADING' and a.active}"><option value="${a.id}"><c:out value="${a.code} ${a.name}" /></option></c:if></c:forEach></select></label>
</div><button class="app-btn btn-primary"><spring:message code="accounting.saveDraft" /></button></form></section></sec:authorize>
<sec:authorize access="@access.has(principal, 'ACCOUNTING_PERIODS_CREATE')"><section class="accounting-form"><h2><spring:message code="accounting.createPeriod" /></h2><form method="post" action="<c:url value='/finance/periods' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><div class="accounting-grid"><label><spring:message code="accounting.start" /><input name="start" type="date" class="aws-control" required /></label><label><spring:message code="accounting.end" /><input name="end" type="date" class="aws-control" required /></label></div><button class="app-btn btn-primary"><spring:message code="accounting.createPeriod" /></button></form></section></sec:authorize>
</div>
<%@ include file="../fragments/footer.jspf" %>
