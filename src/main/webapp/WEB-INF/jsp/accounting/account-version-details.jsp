<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="account-version-shell.jspf" %>
<c:set var="v" value="${details.version}" />
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${v.proposed.code}" /> · <spring:message code="coav.versionNumber" arguments="${v.number}" /></h1></div>
<div class="accounting-workspace accounting-resource-workspace coav-details">
 <section class="accounting-form">
  <h2><c:out value="${v.proposed.name}" /></h2>
  <dl class="accounting-context">
   <div><dt><spring:message code="accounting.type" /></dt><dd><spring:message code="accounting.option.${v.proposed.type}" /></dd></div>
   <div><dt><spring:message code="accounting.kind" /></dt><dd><c:choose><c:when test="${v.proposed.kind=='HEADING'}"><spring:message code="coa.group" /></c:when><c:otherwise><spring:message code="accounting.option.${v.proposed.kind}" /></c:otherwise></c:choose></dd></div>
   <div><dt><spring:message code="accounting.normalBalance" /></dt><dd><spring:message code="${v.proposed.normalBalance=='DEBIT'?'accounting.debit':'accounting.credit'}" /></dd></div>
   <div><dt><spring:message code="accounting.purpose" /></dt><dd><spring:message code="accounting.option.${v.proposed.purpose}" /></dd></div>
   <c:if test="${not empty details.parentLabel}"><div><dt><spring:message code="coa.parentGroup" /></dt><dd><c:out value="${details.parentLabel}" /></dd></div></c:if>
   <div><dt><spring:message code="coa.status" /></dt><dd><spring:message code="coav.state.${v.state}" /></dd></div>
   <div><dt><spring:message code="coav.column.operation" /></dt><dd><spring:message code="coav.operation.${v.operation}" /></dd></div>
   <div><dt><spring:message code="coav.column.creator" /></dt><dd><c:out value="${v.creatorName}" /></dd></div>
   <div><dt><spring:message code="coav.column.created" /></dt><dd><c:out value="${v.getCreatedLabel()}" /></dd></div>
   <div><dt><spring:message code="coav.approvalMode" /></dt><dd><spring:message code="coav.mode.${v.mode}" /> · <spring:message code="coav.policyVersion" arguments="${v.policyVersion}" /></dd></div>
   <div><dt><spring:message code="coav.reason" /></dt><dd><c:out value="${v.reason}" /></dd></div>
   <c:if test="${not empty v.reviewer}"><div><dt><spring:message code="coav.reviewer" /></dt><dd><c:out value="${v.reviewerName}" /></dd></div></c:if>
   <c:if test="${not empty v.decidedAt}"><div><dt><spring:message code="coav.decisionAt" /></dt><dd><c:out value="${v.getDecidedLabel()}" /></dd></div></c:if>
   <c:if test="${not empty v.decisionReason}"><div><dt><spring:message code="coav.decisionReason" /></dt><dd><c:out value="${v.decisionReason}" /></dd></div></c:if>
  </dl>
  <c:if test="${v.mode=='AUTOMATIC'}"><p><spring:message code="coav.automaticEvidence" /></p></c:if>
  <c:if test="${v.state=='PENDING' or v.state=='DRAFT'}"><p><spring:message code="coav.pendingHelp" /></p></c:if>
  <div class="accounting-actions">
   <c:if test="${details.actions.contains('EDIT')}"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/versions/${v.id}/edit' />"><spring:message code="coav.editDraft" /></a></c:if>
   <c:if test="${v.state=='APPROVED' or v.state=='BASELINE'}"><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/${v.accountId}/versions/new' />"><spring:message code="coav.newVersion" /></a></sec:authorize></c:if>
   <c:if test="${details.actions.contains('REVISE')}"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/versions/${v.id}/revise' />"><spring:message code="coav.newVersion" /></a></c:if>
  </div>
  <c:if test="${details.actions.contains('APPROVE') or details.actions.contains('REJECT') or details.actions.contains('WITHDRAW')}">
   <form method="post" action="<c:url value='/finance/accounts/versions/${v.id}/action' />" class="accounting-create-form">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="revision" value="${v.revision}" />
    <label><spring:message code="coav.decisionReason" /><textarea class="aws-control" name="reason" rows="2" maxlength="500" required></textarea></label>
    <div class="accounting-actions"><c:forEach items="${['WITHDRAW','REJECT','APPROVE']}" var="action"><c:if test="${details.actions.contains(action)}"><button class="app-btn btn-neutral" name="action" value="${action}"><spring:message code="coav.action.${action}" /></button></c:if></c:forEach></div>
   </form>
  </c:if>
 </section>
 <section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="coav.changes" /></h2></div><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="coav.field" /></th><th><spring:message code="coav.before" /></th><th><spring:message code="coav.proposed" /></th></tr></thead><tbody>
  <c:forEach items="${['name','description','active']}" var="field"><tr><td><spring:message code="coav.field.${field}" /></td><td><c:choose><c:when test="${empty v.before}">—</c:when><c:when test="${field=='active'}"><spring:message code="accounting.active.${v.before.active}" /></c:when><c:otherwise><c:out value="${v.before[field]}" /></c:otherwise></c:choose></td><td><c:choose><c:when test="${field=='active'}"><spring:message code="accounting.active.${v.proposed.active}" /></c:when><c:otherwise><c:out value="${v.proposed[field]}" /></c:otherwise></c:choose></td></tr></c:forEach>
 </tbody></table></div></section>
 <section class="erp-table-wrap" data-resource-table="account-version-history"><div class="app-table-titlebar"><h2><spring:message code="coav.history" /></h2></div><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="coav.column.number" /></th><th><spring:message code="coa.status" /></th><th><spring:message code="coav.column.name" /></th><th><spring:message code="coav.column.creator" /></th></tr></thead><tbody>
  <c:forEach items="${details.history}" var="h"><tr><td><a class="resource-table-id-link" href="<c:url value='/finance/accounts/versions/${h.id}' />"><c:out value="${h.number}" /></a></td><td><spring:message code="coav.state.${h.state}" /><c:if test="${h.id==details.currentId}"> · <spring:message code="coav.current" /></c:if></td><td><c:out value="${h.proposed.name}" /></td><td><c:out value="${h.creatorName}" /></td></tr></c:forEach>
 </tbody></table></div><div data-table-pagination data-page="${details.historyPage}" data-table-for="account-version-history"><c:if test="${details.historyPage>0}"><a data-page-direction="previous" href="?historyPage=${details.historyPage-1}&amp;eventPage=${details.eventPage}"><spring:message code="common.previous" /></a></c:if><c:if test="${details.historyHasNext}"><a data-page-direction="next" href="?historyPage=${details.historyPage+1}&amp;eventPage=${details.eventPage}"><spring:message code="common.next" /></a></c:if></div></section>
 <section class="erp-table-wrap" data-resource-table="account-version-events"><div class="app-table-titlebar"><h2><spring:message code="coav.events" /></h2></div><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="coav.event" /></th><th><spring:message code="coav.actor" /></th><th><spring:message code="coav.column.created" /></th><th><spring:message code="coav.reason" /></th></tr></thead><tbody>
  <c:forEach items="${details.events}" var="e"><tr><td><spring:message code="coav.event.${e.action}" /></td><td><c:out value="${e.actorName}" /></td><td><c:out value="${e.getRecordedLabel()}" /></td><td><c:out value="${e.reason}" /></td></tr></c:forEach><c:if test="${empty details.events}"><tr><td colspan="4"><spring:message code="coav.baselineHelp" /></td></tr></c:if>
 </tbody></table></div><div data-table-pagination data-page="${details.eventPage}" data-table-for="account-version-events"><c:if test="${details.eventPage>0}"><a data-page-direction="previous" href="?eventPage=${details.eventPage-1}&amp;historyPage=${details.historyPage}"><spring:message code="common.previous" /></a></c:if><c:if test="${details.eventHasNext}"><a data-page-direction="next" href="?eventPage=${details.eventPage+1}&amp;historyPage=${details.historyPage}"><spring:message code="common.next" /></a></c:if></div></section>
</div>
<%@ include file="../fragments/footer.jspf" %>
