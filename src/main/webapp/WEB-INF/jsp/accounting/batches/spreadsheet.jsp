<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:url var="batchUrl" value="/finance/batches/receipts/${batch.id}" />
<c:url var="spreadsheetUrl" value="/finance/batches/receipts/${batch.id}/spreadsheet" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a class="erp-page-path__item erp-page-path__link" href="${batchUrl}"><c:out value="${batch.title}" /></a>
    <span class="erp-page-path__item" aria-hidden="true">›</span>
    <span class="erp-page-path__item" aria-current="page"><spring:message code="batch.sheet.import" /></span>
</nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="batch.sheet.import" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
    <c:if test="${not empty spreadsheetError}">
        <div class="console-message" data-message-type="error" data-download-error-message role="alert">
            <c:if test="${not empty spreadsheetErrorLine}"><spring:message code="batch.sheet.line" arguments="${spreadsheetErrorLine}" /> </c:if>
            <spring:message code="${spreadsheetError}" />
        </div>
    </c:if>
    <section class="erp-resource-section">
        <h2><spring:message code="batch.sheet.template" /></h2>
        <form class="grid gap-3" method="get" action="${spreadsheetUrl}/template.xlsx">
            <label class="grid gap-2"><spring:message code="batch.sheet.search" />
                <input class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" />
            </label>
            <div class="accounting-actions"><button type="submit" class="app-btn btn-neutral" data-download-error-response><spring:message code="batch.sheet.download" /></button></div>
        </form>
    </section>
    <form method="post" enctype="multipart/form-data" class="accounting-create-form" action="${spreadsheetUrl}">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <section class="erp-resource-section">
            <h2><spring:message code="batch.sheet.upload" /></h2>
            <div class="grid gap-3">
                <p><spring:message code="batch.sheet.instructions" /></p>
                <label class="grid gap-2"><spring:message code="batch.receivingAccount" />
                    <select name="moneyAccountId" class="aws-control" data-batch-choice="money" data-searchable="true" required>
                        <option value=""><spring:message code="batch.choose" /></option>
                        <c:if test="${not empty selectedAccount}">
                            <option value="${selectedAccount.id}" selected><c:out value="${selectedAccount.label}" /></option>
                        </c:if>
                    </select>
                </label>
                <label class="grid gap-2"><spring:message code="batch.sheet.file" />
                    <input class="aws-control" type="file" name="file" accept=".xlsx" required />
                </label>
            </div>
        </section>
        <div class="accounting-actions accounting-form-actions">
            <a class="app-btn btn-neutral" href="${batchUrl}"><spring:message code="common.cancel" /></a>
            <button type="submit" class="app-btn btn-launch"><spring:message code="batch.sheet.addDrafts" /></button>
        </div>
    </form>
    <div role="alert" data-batch-error hidden></div>
    <%@ include file="messages.jspf" %>
</div>
<script src="<c:url value='/js/receipt-batches.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
