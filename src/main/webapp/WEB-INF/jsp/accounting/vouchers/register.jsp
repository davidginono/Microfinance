<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />

<div class="accounting-workspace voucher-register" data-voucher-register data-base="<c:url value='${base}' />">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="voucher.register.${voucherType}" /></h1></div>
<section class="erp-table-wrap" data-aws-table-region>
<div class="app-table-titlebar voucher-toolbar" data-aws-action-pin="true"><h2 class="erp-panel-title"><spring:message code="voucher.records" /></h2><div class="accounting-actions">
<button type="button" class="app-btn btn-neutral" data-aws-table-refresh><spring:message code="common.refresh" /></button>
<button type="button" class="app-btn btn-neutral" data-selection-action="details" disabled><spring:message code="common.view" /></button>
<sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_EXPORT')"><button type="button" class="app-btn btn-neutral" data-selection-action="pdf" disabled><spring:message code="voucher.pdf" /></button><button type="button" class="app-btn btn-neutral" data-selection-action="print" disabled><spring:message code="voucher.print" /></button></sec:authorize>
<sec:authorize access="@access.has(principal, '${createClaim}')"><a class="app-btn btn-primary" href="<c:url value='${base}/new' />"><spring:message code="voucher.create.${voucherType}" /></a></sec:authorize>
</div></div>
<form class="voucher-filters" method="get">
<label><spring:message code="voucher.search" /><input class="aws-control" type="search" name="search" value="<c:out value='${filter.search}' />" maxlength="100" /></label>
<label><spring:message code="voucher.from" /><input class="aws-control" type="date" name="from" value="<c:out value='${filter.from}' />" /></label>
<label><spring:message code="voucher.through" /><input class="aws-control" type="date" name="through" value="<c:out value='${filter.through}' />" /></label>
<label><spring:message code="voucher.status" /><select class="aws-control" name="state"><option value=""><spring:message code="voucher.all" /></option><c:forEach items="${['POSTED','REVERSED','REVERSAL']}" var="s"><option value="${s}" ${filter.status==s?'selected':''}><spring:message code="voucher.status.${s}" /></option></c:forEach></select></label>
<label><spring:message code="voucher.sort" /><select class="aws-control" name="sort"><c:forEach items="${['newest','oldest','amount']}" var="s"><option value="${s}" ${filter.sort==s?'selected':''}><spring:message code="voucher.sort.${s}" /></option></c:forEach></select></label>
<button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='${base}' />"><spring:message code="voucher.clear" /></a>
</form>
<p class="sr-only" role="status" aria-live="polite" data-selection-status data-empty="<spring:message code='voucher.selectHelp' />" data-selected="<spring:message code='voucher.selected' />"><spring:message code="voucher.selectHelp" /></p>
<div class="erp-table-scroll" data-view-position-key="core-vouchers-${voucherKind}"><table class="erp-table"><caption class="sr-only"><spring:message code="voucher.register.${voucherType}" /></caption><thead><tr><th class="voucher-select-col"><spring:message code="voucher.select" /></th><th><spring:message code="voucher.number" /></th><th><spring:message code="voucher.date" /></th><th><spring:message code="voucher.party" /></th><th><spring:message code="voucher.reference" /></th><th><spring:message code="voucher.transactions" /></th><th class="voucher-numeric"><spring:message code="voucher.total" /> (TZS)</th><th><spring:message code="voucher.status" /></th></tr></thead><tbody>
<c:forEach items="${vouchers.rows}" var="v"><tr data-voucher-id="${v.id}"><td><input type="checkbox" class="voucher-select" aria-label="<spring:message code='voucher.select' /> <c:out value='${v.number}' />" /></td><td><a href="<c:url value='${base}/${v.id}' />"><c:out value="${v.number}" /></a></td><td><c:out value="${v.effectiveDate}" /></td><td><c:out value="${v.party}" /></td><td><c:out value="${v.reference}" /></td><td><c:out value="${v.transactionCount}" /></td><td class="voucher-numeric"><fmt:formatNumber value="${v.total}" minFractionDigits="2" maxFractionDigits="2" /></td><td><span class="voucher-status"><spring:message code="voucher.status.${v.status()}" /></span></td></tr></c:forEach>
<c:if test="${empty vouchers.rows}"><tr><td colspan="8" class="voucher-empty"><spring:message code="${empty filter.search and empty filter.from and empty filter.through and empty filter.status?'voucher.empty':'voucher.noMatches'}" /></td></tr></c:if>
</tbody></table></div>
<div class="voucher-pagination"><spring:message code="voucher.page" /> ${vouchers.page+1}<div class="accounting-actions">
<c:url value="${base}" var="prev"><c:param name="page" value="${vouchers.page-1}" /><c:param name="search" value="${filter.search}" /><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="state" value="${filter.status}" /><c:param name="sort" value="${filter.sort}" /></c:url>
<c:url value="${base}" var="next"><c:param name="page" value="${vouchers.page+1}" /><c:param name="search" value="${filter.search}" /><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="state" value="${filter.status}" /><c:param name="sort" value="${filter.sort}" /></c:url>
<c:choose><c:when test="${vouchers.page>0}"><a class="app-btn btn-neutral" href="<c:out value='${prev}' />"><spring:message code="common.previous" /></a></c:when><c:otherwise><button class="app-btn btn-neutral" disabled><spring:message code="common.previous" /></button></c:otherwise></c:choose>
<c:choose><c:when test="${vouchers.hasNext}"><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:when><c:otherwise><button class="app-btn btn-neutral" disabled><spring:message code="common.next" /></button></c:otherwise></c:choose>
</div></div></section></div>
<script src="<c:url value='/js/accounting-vouchers.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
