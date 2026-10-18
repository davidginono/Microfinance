<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>

<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<spring:htmlEscape defaultHtmlEscape="true" />

<%@ include file="../../fragments/header.jspf" %>

<%@ include file="../../fragments/sidebar.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />

<link rel="stylesheet" href="<c:url value='/css/voucher-approval.css' />" />

<div class="accounting-workspace approval-workspace" data-approval-register>

<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="approval.workspace" /></h1></div>

<div class="approval-tiles">

<c:forEach items="${['validation','approval','posting']}" var="group">

<c:set var="categories" value="${group=='validation'?['INCOMPLETE','NOT_VALIDATED','ERROR']:group=='approval'?['INITIATED','OTHERS','REJECTED']:['VALIDATED','FAILED','POSTED']}" />

<section class="approval-tile ${categories.contains(category)?'is-active':''}"><h2><spring:message code="approval.group.${group}" /></h2><c:forEach items="${categories}" var="state"><c:url value="/finance/approval" var="tileUrl"><c:param name="type" value="${type}" /><c:param name="category" value="${state}" /></c:url><a href="<c:out value='${tileUrl}' />" ${category==state?'aria-current="page"':''}><span><spring:message code="approval.status.${state}" /></span><strong>${empty records.counts[state]?0:records.counts[state]}</strong></a></c:forEach></section>

</c:forEach></div>

<section class="erp-table-wrap" data-resource-table="voucher-approval-register" data-table-selection="single" data-table-page-size="${records.size}">

<div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="approval.records" /> (${empty records.counts['ALL']?0:records.counts['ALL']})</h2><div class="accounting-actions"><button type="button" class="app-btn btn-neutral" data-aws-table-refresh><spring:message code="common.refresh" /></button><button type="button" class="app-btn btn-neutral" data-approval-open disabled><spring:message code="common.view" /></button><sec:authorize access="@access.has(principal, 'VOUCHER_WORKSPACE_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/finance/approval/new?type=${type}' />"><spring:message code="approval.create" /></a></sec:authorize></div></div>

<form method="get" class="approval-filters"><label><spring:message code="approval.voucherType" /><select name="type" class="aws-control"><c:forEach items="${['RECEIPT','PAYMENT','JOURNAL']}" var="t"><option value="${t}" ${type==t?'selected':''}><spring:message code="voucher.register.${t}" /></option></c:forEach></select></label><label><spring:message code="voucher.search" /><input name="search" type="search" class="aws-control" value="<c:out value='${search}' />" maxlength="100" /></label><label><spring:message code="voucher.status" /><select name="category" class="aws-control"><option value=""><spring:message code="voucher.all" /></option><c:forEach items="${['INCOMPLETE','NOT_VALIDATED','VALIDATED','ERROR','REQUIRED','INITIATED','WITHDRAWN','HELD_FROM_APPROVAL','REJECTED','RESUBMIT_FOR_APPROVAL','WORKFLOW_APPROVED','MANUALLY_APPROVED','NOT_REQUIRED','NOT_POSTED','FAILED','POSTED','OTHERS']}" var="s"><option value="${s}" ${category==s?'selected':''}><spring:message code="approval.status.${s}" /></option></c:forEach></select></label><input type="hidden" name="sort" value="<c:out value='${sort}' />" /><input type="hidden" name="size" value="${records.size}" /><button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/approval?type=${type}' />"><spring:message code="voucher.clear" /></a></form>

<p class="sr-only" role="status" aria-live="polite" data-approval-selection data-empty="<spring:message code='voucher.selectHelp' />" data-selected="<spring:message code='voucher.selected' />"></p>

<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="voucher.select" /></th><th data-table-sort-asc="number_asc" data-table-sort-desc="number_desc"><spring:message code="voucher.number" /></th><th data-table-sort-asc="title_asc" data-table-sort-desc="title_desc"><spring:message code="approval.title" /></th><th data-table-sort-asc="scope_asc" data-table-sort-desc="scope_desc"><spring:message code="approval.scope" /></th><th data-table-sort-asc="count_asc" data-table-sort-desc="count_desc"><spring:message code="approval.vouchers" /></th><th data-table-sort-asc="total_asc" data-table-sort-desc="total_desc"><spring:message code="voucher.total" /></th><th data-table-sort-asc="validation_asc" data-table-sort-desc="validation_desc"><spring:message code="approval.group.validation" /></th><th data-table-sort-asc="approval_asc" data-table-sort-desc="approval_desc"><spring:message code="approval.group.approval" /></th><th data-table-sort-asc="posting_asc" data-table-sort-desc="posting_desc"><spring:message code="approval.group.posting" /></th><th data-table-sort-asc="updated_asc" data-table-sort-desc="updated_desc"><spring:message code="approval.updated" /></th></tr></thead><tbody><c:forEach items="${records.rows}" var="w"><tr data-approval-url="<c:url value='/finance/approval/${w.id}' />"><td><input type="checkbox" data-approval-select aria-label="<spring:message code='voucher.select' /> <c:out value='${w.number}' />" /></td><td><a href="<c:url value='/finance/approval/${w.id}' />"><c:out value="${w.number}" /></a></td><td><c:out value="${w.title}" /></td><td><spring:message code="approval.scope.${w.scope}" /></td><td>${w.itemCount}</td><td><c:choose><c:when test="${w.validation=='VALIDATED'}"><fmt:formatNumber value="${w.total}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise>—</c:otherwise></c:choose> TZS</td><td><span class="approval-status" data-state="${w.validation}"><spring:message code="approval.status.${w.validation}" /></span></td><td><span class="approval-status" data-state="${w.approval}"><spring:message code="approval.status.${w.approval}" /></span></td><td><span class="approval-status" data-state="${w.posting}"><spring:message code="approval.status.${w.posting}" /></span></td><td><c:out value="${w.updatedAt.toLocalDate()}" /></td></tr></c:forEach><c:if test="${empty records.rows}"><tr><td colspan="10"><spring:message code="voucher.noMatches" /></td></tr></c:if></tbody></table></div>

<div data-table-pagination data-page="${records.page}" data-table-for="voucher-approval-register"><c:forEach items="${['previous','next']}" var="direction"><c:url value="/finance/approval" var="pageUrl"><c:param name="type" value="${type}" /><c:param name="search" value="${search}" /><c:param name="category" value="${category}" /><c:param name="sort" value="${sort}" /><c:param name="size" value="${records.size}" /><c:param name="page" value="${records.page+(direction=='next'?1:-1)}" /></c:url><c:if test="${direction=='next'?records.hasNext:records.page>0}"><a data-page-direction="${direction}" href="<c:out value='${pageUrl}' />"><spring:message code="common.${direction}" /></a></c:if></c:forEach></div>

</section></div><script src="<c:url value='/js/voucher-approval.js' />" defer></script>

<%@ include file="../../fragments/footer.jspf" %>