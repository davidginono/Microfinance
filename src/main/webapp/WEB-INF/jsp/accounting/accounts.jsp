<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="ledger-workspace">
 <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.ledger.accounts" /></h1></div>
 <section class="erp-table-wrap">
  <div class="app-table-titlebar"><h2><spring:message code="accounting.ledger.accounts" /></h2><span>(<c:out value="${rows.totalElements}" />)</span></div>
  <div class="erp-table-scroll" data-view-position-key="accounting-accounts"><table class="erp-table">
   <thead><tr><th><spring:message code="accounting.ledger.code" /></th><th><spring:message code="accounting.ledger.name" /></th><th><spring:message code="accounting.ledger.kind" /></th><th><spring:message code="accounting.ledger.usage" /></th><th><spring:message code="accounting.ledger.category" /></th><th><spring:message code="common.status" /></th><th><spring:message code="common.actions" /></th></tr></thead>
   <tbody><c:forEach items="${rows.content}" var="row"><tr>
    <td><c:out value="${row.code}" /></td><td><c:out value="${row.name}" /></td><td><spring:message code="accounting.ledger.kind.${row.kind}" /></td><td><spring:message code="accounting.ledger.usage.${row.usage}" /></td><td><spring:message code="accounting.ledger.category.${row.category}" /></td>
    <td><c:choose><c:when test="${row.active}"><spring:message code="accounting.ledger.active" /></c:when><c:otherwise><spring:message code="accounting.ledger.inactive" /></c:otherwise></c:choose></td>
    <td><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_MANAGE')"><c:if test="${row.active}"><form method="post" action="<c:url value='/accounting/accounts/${row.id}/deactivate' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral" type="submit"><spring:message code="accounting.ledger.deactivate" /></button></form></c:if></sec:authorize></td>
   </tr></c:forEach><c:if test="${empty rows.content}"><tr><td colspan="7" class="erp-table-empty"><spring:message code="accounting.ledger.empty" /></td></tr></c:if></tbody>
  </table></div>
  <c:set var="ledgerPagePath" value="/accounting/accounts" /><%@ include file="pagination.jspf" %>
 </section>
 <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_MANAGE')"><section><h2><spring:message code="accounting.ledger.addAccount" /></h2>
  <form method="post" action="<c:url value='/accounting/accounts' />" class="ledger-form">
   <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
   <label><spring:message code="accounting.ledger.code" /><input class="aws-control" name="code" maxlength="40" pattern="[A-Z0-9][A-Z0-9_.-]{0,39}" required /></label>
   <label><spring:message code="accounting.ledger.name" /><input class="aws-control" name="name" maxlength="160" required /></label>
   <label><spring:message code="accounting.ledger.kind" /><select class="aws-control" name="kind"><c:forEach items="${kinds}" var="kind"><option value="${kind}"><spring:message code="accounting.ledger.kind.${kind}" /></option></c:forEach></select></label>
   <label><spring:message code="accounting.ledger.normalBalance" /><select class="aws-control" name="normalBalance"><c:forEach items="${normalBalances}" var="balance"><option value="${balance}"><spring:message code="accounting.ledger.balance.${balance}" /></option></c:forEach></select></label>
   <label><spring:message code="accounting.ledger.usage" /><select class="aws-control" name="usage"><c:forEach items="${usages}" var="usage"><option value="${usage}"><spring:message code="accounting.ledger.usage.${usage}" /></option></c:forEach></select></label>
   <label><spring:message code="accounting.ledger.category" /><select class="aws-control" name="category"><c:forEach items="${categories}" var="category"><option value="${category}"><spring:message code="accounting.ledger.category.${category}" /></option></c:forEach></select></label>
   <label><spring:message code="accounting.ledger.parentCode" /><input class="aws-control" name="parentCode" maxlength="40" /></label>
   <button type="submit" class="app-btn btn-primary"><spring:message code="accounting.ledger.addAccount" /></button>
  </form>
 </section></sec:authorize>
</div>
<%@ include file="../fragments/footer.jspf" %>
