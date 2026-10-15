<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" /><link rel="stylesheet" href="<c:url value='/css/receipt-batches.css' />" />
<meta name="_csrf" content="${_csrf.token}" /><meta name="_csrf_header" content="${_csrf.headerName}" />
<sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE') and @access.has(principal,'LOAN_RECORDING_POST')" var="canPostBatch" />
<div class="accounting-workspace batch-workspace" data-receipt-batch="${summary.batch.id}" data-today="${today}" data-can-edit="${canPostBatch and summary.batch.owner==pageContext.request.userPrincipal.principal.memberId}">
<nav class="erp-page-path"><a href="<c:url value='/finance/batches/receipts' />"><spring:message code="batch.receipts" /></a></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${summary.batch.title}" /></h1><span data-batch-state></span></div>
<p class="batch-note"><spring:message code="batch.recordedHelp" /></p>
<p class="batch-note"><spring:message code="batch.activity" /> · <spring:message code="batch.standardTransactions" /></p>
<div class="batch-summary" data-batch-summary aria-live="polite"></div>
<div class="batch-alert" role="alert" tabindex="-1" data-batch-error hidden></div>
<section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="batch.receiptRows" /></h2><div class="accounting-actions">
<button type="button" class="app-btn btn-neutral" data-batch-refresh><spring:message code="common.refresh" /></button>
<button type="button" class="app-btn btn-neutral" data-batch-save><spring:message code="batch.saveDraft" /></button>
<button type="button" class="app-btn btn-launch" data-batch-add><spring:message code="batch.addReceipt" /></button></div></div>
<div class="erp-table-toolbar batch-defaults"><label><spring:message code="batch.defaultDate" /><input type="date" class="aws-control" data-default-date value="${today}" max="${today}" /></label><label><spring:message code="batch.defaultAccount" /><select class="aws-control" data-default-account data-batch-choice="money" data-searchable="true"><option value=""><spring:message code="batch.choose" /></option></select></label>
<label><spring:message code="batch.show" /><select class="aws-control" data-batch-filter><option value=""><spring:message code="voucher.all" /></option><c:forEach items="${['DRAFT','READY','FAILED','POSTED']}" var="state"><option value="${state}"><spring:message code="batch.state.${state}" /></option></c:forEach></select></label>
<span role="status" aria-live="polite" data-save-status></span></div>
<div class="erp-table-scroll batch-grid-scroll"><table class="erp-table batch-grid"><caption class="sr-only"><spring:message code="batch.receiptRows" /></caption><thead><tr><c:forEach items="${['client','date','loan','balances','amount','reference','status']}" var="column"><th><spring:message code="batch.${column}" /><c:if test="${column=='amount'}"> (TZS)</c:if></th></c:forEach></tr></thead><tbody data-batch-rows></tbody></table></div>
<div class="erp-table-footer accounting-actions"><button type="button" class="app-btn btn-neutral" data-batch-prev><spring:message code="common.previous" /></button><span data-batch-page></span><button type="button" class="app-btn btn-neutral" data-batch-next><spring:message code="common.next" /></button></div>
</section>
<div class="batch-footer"><span data-batch-progress role="status" aria-live="polite"></span><div class="accounting-actions"><button type="button" class="app-btn btn-neutral" data-batch-stop hidden><spring:message code="batch.stop" /></button><button type="button" class="app-btn btn-neutral" data-batch-validate><spring:message code="batch.validate" /></button><button type="button" class="app-btn btn-neutral" data-batch-post disabled><spring:message code="batch.post" /></button></div></div>
<div class="app-modal-overlay batch-post-modal" data-batch-confirm hidden><div class="app-modal-panel app-modal-panel--compact" role="dialog" aria-modal="true" aria-labelledby="batch-confirm-title" tabindex="-1"><div class="app-modal-header"><h2 id="batch-confirm-title"><spring:message code="batch.postTitle" /></h2><button type="button" class="app-modal-close" data-confirm-cancel aria-label="<spring:message code='common.close' />">×</button></div><div class="app-modal-body"><p data-confirm-message></p></div><div class="app-modal-actions"><button type="button" class="app-btn btn-neutral" data-confirm-cancel><spring:message code="common.cancel" /></button><button type="button" class="app-btn btn-neutral" data-confirm-post><spring:message code="batch.postConfirm" /></button></div></div></div>
<%@ include file="messages.jspf" %></div><script src="<c:url value='/js/receipt-batches.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
