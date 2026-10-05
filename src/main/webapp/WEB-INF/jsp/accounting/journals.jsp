<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="ledger-workspace">
 <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.ledger.journals" /></h1></div>
 <section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="accounting.ledger.journals" /></h2><span>(<c:out value="${rows.totalElements}" />)</span></div>
  <div class="erp-table-scroll" data-view-position-key="accounting-journals"><table class="erp-table"><thead><tr><th><spring:message code="accounting.ledger.effectiveDate" /></th><th><spring:message code="accounting.ledger.description" /></th><th class="money"><spring:message code="accounting.ledger.debit" /> (TZS)</th><th><spring:message code="common.status" /></th><th><spring:message code="common.actions" /></th></tr></thead>
   <tbody><c:forEach items="${rows.content}" var="row"><tr><td><c:out value="${row.effectiveDate}" /></td><td><c:out value="${row.description}" /></td><td class="money"><fmt:formatNumber value="${row.totalDebit}" minFractionDigits="2" maxFractionDigits="2" /></td><td><spring:message code="accounting.ledger.state.${row.state}" /></td><td><a class="app-btn btn-neutral" href="<c:url value='/accounting/journals/${row.id}' />"><spring:message code="common.view" /></a></td></tr></c:forEach><c:if test="${empty rows.content}"><tr><td colspan="5" class="erp-table-empty"><spring:message code="accounting.ledger.empty" /></td></tr></c:if></tbody></table></div>
  <c:set var="ledgerPagePath" value="/accounting/journals" /><%@ include file="pagination.jspf" %>
 </section>
 <sec:authorize access="@access.has(principal, 'ACCOUNTING_JOURNAL_DRAFT')"><section><h2><spring:message code="accounting.ledger.newJournal" /></h2>
  <form method="post" action="<c:url value='/accounting/journals' />" class="ledger-form">
   <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${requestKey}" />
   <label><spring:message code="accounting.ledger.effectiveDate" /><input class="aws-control" type="date" name="effectiveDate" required /></label>
   <label><spring:message code="accounting.ledger.evidence" /><input class="aws-control" name="evidenceReference" maxlength="500" required /></label>
   <label class="ledger-wide"><spring:message code="accounting.ledger.description" /><input class="aws-control" name="description" maxlength="500" required /></label>
   <label class="ledger-wide"><spring:message code="accounting.ledger.lines" /><small><spring:message code="accounting.ledger.linesHelp" /></small><textarea class="aws-control" name="lines" rows="5" maxlength="64000" required></textarea></label>
   <button type="submit" class="app-btn btn-primary"><spring:message code="accounting.ledger.saveDraft" /></button>
  </form>
 </section></sec:authorize>
 <sec:authorize access="@access.has(principal, 'ACCOUNTING_PERIOD_MANAGE')"><section><h2><spring:message code="accounting.ledger.openPeriod" /></h2>
  <form method="post" action="<c:url value='/accounting/periods' />" class="ledger-form"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
   <label><spring:message code="accounting.ledger.startsOn" /><input class="aws-control" type="date" name="startsOn" required /></label><label><spring:message code="accounting.ledger.endsOn" /><input class="aws-control" type="date" name="endsOn" required /></label>
   <label><spring:message code="accounting.ledger.initialPeriodCutoff" /><input class="aws-control" type="date" name="openingCutoff" /></label>
   <label><spring:message code="accounting.ledger.evidence" /><input class="aws-control" name="evidenceReference" maxlength="500" required /></label><button type="submit" class="app-btn btn-neutral"><spring:message code="accounting.ledger.openPeriod" /></button>
  </form>
 </section></sec:authorize>
</div>
<%@ include file="../fragments/footer.jspf" %>
