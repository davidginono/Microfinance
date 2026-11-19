<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" /><link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<%@ include file="../../fragments/alerts.jspf" %>
<div class="accounting-workspace" data-petty-cash-workspace data-today="${cashbook.today}" data-starts="${cashbook.fund.startsOn}">
<sec:authorize access="@access.has(principal,'ACCOUNTING_JOURNALS_VIEW')" var="canJournal" />
<c:set var="q" value="${cashbook.query}" /><c:set var="movements" value="${q.view=='movements'}" />
<c:choose><c:when test="${empty cashbook.fund}"><div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="petty.title" /></h1></div><div class="console-message" data-message-type="info" role="status"><spring:message code="petty.error.setup" /></div></c:when><c:otherwise>
<section class="erp-resource-section" data-table-query-state-for="petty-cash"><h2><c:out value="${cashbook.fund.name}" /> (TZS)</h2><span data-table-query-value="todayIso" hidden>${cashbook.today}</span><dl class="erp-detail-facts">
<div><dt><spring:message code="petty.opening" /> · <span data-table-query-value="openingDate"><c:out value="${appTime:display(q.from)}" /></span></dt><dd data-table-query-value="opening"><c:choose><c:when test="${cashbook.balances.opening!=null}"><fmt:formatNumber value="${cashbook.balances.opening}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><spring:message code="petty.unavailable" /></c:otherwise></c:choose></dd></div>
<div><dt><spring:message code="petty.current" /> · <span data-table-query-value="currentDate"><c:out value="${appTime:display(cashbook.today)}" /></span></dt><dd data-table-query-value="current"><fmt:formatNumber value="${cashbook.balances.current}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div data-petty-closing ${q.through lt cashbook.today?'':'hidden'}><dt><spring:message code="petty.closing" /> · <span data-table-query-value="closingDate"><c:out value="${appTime:display(q.through)}" /></span></dt><dd data-table-query-value="closing"><c:choose><c:when test="${cashbook.balances.closing!=null}"><fmt:formatNumber value="${cashbook.balances.closing}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><spring:message code="petty.unavailable" /></c:otherwise></c:choose></dd></div>
<div><dt><spring:message code="petty.refreshed" /></dt><dd data-table-query-value="cutoff"><c:out value="${appTime:display(cashbook.cutoff)}" /></dd></div>
</dl></section>
<div data-petty-coverage ${q.from lt cashbook.fund.startsOn?'':'hidden'} class="console-message" data-message-type="warning" role="status"><spring:message code="petty.coverageNotice" arguments="${appTime:display(cashbook.fund.startsOn)}" /></div>
<section class="erp-table-wrap" data-resource-table="petty-cash" data-table-page-size="${q.size}" data-petty-table data-petty-view="${q.view}" data-table-sync-query="true">
<div class="app-table-titlebar"><h2 data-workspace-title><spring:message code="petty.title" /></h2><div class="accounting-actions"><button type="button" class="app-btn btn-neutral" data-aws-table-refresh><spring:message code="common.refresh" /></button><sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/finance/petty-cash/expense' />"><spring:message code="petty.record" /></a></sec:authorize></div></div>
<form method="get" action="<c:url value='/finance/petty-cash' />" class="voucher-filters" data-petty-query>
<label><spring:message code="petty.view" /><select class="aws-control" name="view"><option value="expenses" ${movements?'':'selected'}><spring:message code="petty.expenses" /></option><option value="movements" ${movements?'selected':''}><spring:message code="petty.movements" /></option></select></label>
<label><spring:message code="voucher.from" /><input class="aws-control" type="date" name="from" value="${q.from}" required /></label><label><spring:message code="voucher.through" /><input class="aws-control" type="date" name="through" value="${q.through}" max="${cashbook.today}" required /></label>
<label><spring:message code="voucher.search" /><input class="aws-control" type="search" name="search" value="<c:out value='${q.search}' />" maxlength="100" /></label><input type="hidden" name="sort" value="<c:out value='${q.sort}' />" /><input type="hidden" name="size" value="${q.size}" /><button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/petty-cash' />"><spring:message code="common.clear" /></a>
</form>
<div class="erp-table-scroll" data-view-position-key="petty-cash"><table class="erp-table"><thead><tr>
<c:forEach items="${movements?['date','number','party','description','in','out','balance','state']:['date','number','party','account','description','paid','state']}" var="column"><th data-table-sort-asc="${column}_asc" data-table-sort-desc="${column}_desc"><spring:message code="petty.column.${movements and column=='number'?'reference':column}" /></th></c:forEach>
</tr></thead><tbody>
<c:forEach items="${cashbook.rows}" var="r"><tr><td><c:out value="${appTime:display(r.date)}" /></td><td><c:choose><c:when test="${not empty r.voucherId and (r.voucherKind!='journals' or canJournal)}"><a class="resource-table-id-link" href="<c:url value='/finance/vouchers/${r.voucherKind}/${r.voucherId}' />"><c:out value="${r.number}" /></a></c:when><c:otherwise><c:out value="${r.number}" /></c:otherwise></c:choose></td><td><c:out value="${r.party}" /></td>
<c:if test="${not movements}"><td><c:out value="${r.account}" /></td></c:if><td><c:out value="${r.description}" /></td>
<c:choose><c:when test="${movements}"><td><fmt:formatNumber value="${r.moneyIn}" minFractionDigits="2" maxFractionDigits="2" /></td><td><fmt:formatNumber value="${r.moneyOut}" minFractionDigits="2" maxFractionDigits="2" /></td><td><c:choose><c:when test="${r.date ge cashbook.fund.startsOn}"><fmt:formatNumber value="${r.balance}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><spring:message code="petty.unavailable" /></c:otherwise></c:choose></td></c:when><c:otherwise><td><fmt:formatNumber value="${r.paid}" minFractionDigits="2" maxFractionDigits="2" /></td></c:otherwise></c:choose><td><span data-resource-status="${r.state}"><spring:message code="voucher.status.${r.state}" /></span></td></tr></c:forEach>
<c:if test="${empty cashbook.rows}"><tr><td colspan="${movements?8:7}"><spring:message code="${empty q.search?'petty.empty':'voucher.noMatches'}" /></td></tr></c:if>
</tbody></table></div>
<div data-table-pagination data-table-for="petty-cash" data-page="${q.page}"><c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?q.page>0:cashbook.hasNext}"><c:url value="/finance/petty-cash" var="pageUrl"><c:param name="page" value="${q.page+(move=='previous'?-1:1)}" /><c:param name="size" value="${q.size}" /><c:param name="from" value="${q.from}" /><c:param name="through" value="${q.through}" /><c:param name="view" value="${q.view}" /><c:param name="search" value="${q.search}" /><c:param name="sort" value="${q.sort}" /></c:url><a data-page-direction="${move}" href="<c:out value='${pageUrl}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach></div>
</section>
<sec:authorize access="@access.has(principal,'VOUCHER_WORKSPACE_VIEW')"><a href="<c:url value='/finance/petty-cash/pending' />"><spring:message code="petty.pending" /></a></sec:authorize>
</c:otherwise></c:choose></div>
<script src="<c:url value='/js/petty-cash.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
