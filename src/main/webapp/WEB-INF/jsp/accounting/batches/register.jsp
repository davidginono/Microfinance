<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<div class="accounting-workspace" data-voucher-register data-base="<c:url value='/finance/batches/receipts' />"><div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="batch.receipts" /></h1></div>
<section class="erp-table-wrap" data-resource-table="receipt-batches" data-table-selection="single">
<div class="app-table-titlebar"><h2><spring:message code="batch.saved" /></h2><div class="accounting-actions">
<button type="button" class="app-btn btn-neutral" data-selection-action="details" disabled><spring:message code="common.view" /></button>
<sec:authorize access="@access.has(principal,'ACCOUNTING_ACCOUNTS_VIEW')"><a class="app-btn btn-neutral" href="<c:url value='/finance/batches/receipts/settings' />"><spring:message code="batch.settings" /></a></sec:authorize>
<sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE') and @access.has(principal,'LOAN_RECORDING_POST')"><a class="app-btn btn-launch" href="<c:url value='/finance/batches/receipts/new' />"><spring:message code="batch.create" /></a></sec:authorize>
</div></div>
<form method="get" class="erp-table-toolbar voucher-filters"><label><spring:message code="common.search" /><input type="search" name="search" maxlength="100" class="aws-control" value="<c:out value='${search}' />" /></label><button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button></form>
<p class="sr-only" role="status" aria-live="polite" data-selection-status data-empty="<spring:message code='voucher.selectHelp' />" data-selected="<spring:message code='voucher.selected' />"></p>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th class="voucher-select-col"><spring:message code="voucher.select" /></th><th><spring:message code="batch.title" /></th><th><spring:message code="batch.created" /></th><th><spring:message code="voucher.status" /></th></tr></thead><tbody>
<c:forEach items="${batches.rows}" var="b"><tr data-voucher-id="${b.id}"><td><input type="checkbox" class="voucher-select" aria-label="<spring:message code='voucher.select' /> <c:out value='${b.title}' />" /></td><td><a class="resource-table-id-link" href="<c:url value='/finance/batches/receipts/${b.id}' />"><c:out value="${b.title}" /></a></td><td><c:out value="${appTime:display(b.createdAt)}" /></td><td><spring:message code="batch.state.${b.state}" /></td></tr></c:forEach>
<c:if test="${empty batches.rows}"><tr><td colspan="4"><spring:message code="${empty search?'batch.empty':'batch.noMatches'}" /></td></tr></c:if>
</tbody></table></div>
<div class="erp-table-footer accounting-actions" data-table-pagination data-page="${batches.page}" data-table-for="receipt-batches">
<c:if test="${batches.page>0}"><c:url value="/finance/batches/receipts" var="prev"><c:param name="page" value="${batches.page-1}" /><c:param name="search" value="${search}" /></c:url><a class="app-btn btn-neutral" data-page-direction="previous" href="<c:out value='${prev}' />"><spring:message code="common.previous" /></a></c:if>
<span><spring:message code="voucher.page" /> ${batches.page+1}</span>
<c:if test="${batches.hasNext}"><c:url value="/finance/batches/receipts" var="next"><c:param name="page" value="${batches.page+1}" /><c:param name="search" value="${search}" /></c:url><a class="app-btn btn-neutral" data-page-direction="next" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
</div></section></div><script src="<c:url value='/js/accounting-vouchers.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
