<%@ taglib prefix="report" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<template data-resource-details-page></template>
<link rel="stylesheet" href="<c:url value='/css/client-loan-statement.css' />" />
<c:url value="/finance/client-ledgers/${clientId}" var="statementUrl" />
<div class="accounting-workspace client-statement-workspace">
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/finance/client-ledgers' />"><spring:message code="recording.clientLedgers" /></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><c:out value="${statementTitle}" /></span></nav>
    <div class="erp-page-header erp-detail-header" data-aws-page-header>
        <h1 class="erp-page-title"><c:out value="${statementTitle}" /></h1>
        <sec:authorize access="@access.has(principal,'LOAN_RECORDING_EXPORT')">
            <div class="report-page-actions" data-aws-action-pin="true">
                <a class="app-btn btn-neutral" data-print-action="true" href="<c:url value='/finance/client-ledgers/${clientId}/print' />"><report:reportExport format="print" /></a>
                <a class="app-btn btn-neutral" href="<c:url value='/finance/client-ledgers/${clientId}/pdf' />"><report:reportExport format="pdf" /></a>
            </div>
        </sec:authorize>
    </div>
    <section class="erp-panel">
        <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="resource.details.summary" /></h2></div>
        <div class="erp-panel-body">
        <dl class="erp-detail-facts">
            <div><dt><spring:message code="recording.statement.clientNumber" /></dt><dd><c:out value="${statement.client.number}" /></dd></div>
            <div><dt><spring:message code="recording.outstandingPrincipal" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.principal}" minFractionDigits="0" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="recording.dueInterest" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.interest}" minFractionDigits="0" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="charges.outstanding" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.charges}" minFractionDigits="0" maxFractionDigits="2" /></dd></div><div><dt><spring:message code="recording.arrears" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.arrears}" minFractionDigits="0" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="recording.futureInterest" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.futureInterest}" minFractionDigits="0" maxFractionDigits="2" /></dd></div>
        </dl>
        </div>
    </section>
    <section class="erp-table-wrap" data-resource-table="client-statement-loans" data-table-preserve-page-query="true" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="recording.register.LOAN" /></h2></div>
        <div class="erp-table-scroll" tabindex="0" role="region" aria-label="<spring:message code='recording.register.LOAN' />">
            <table class="erp-table">
                <thead><tr>
                    <th scope="col" data-table-order="loan"><spring:message code="recording.statement.loan" /></th><th scope="col" data-table-order="product"><spring:message code="recording.product" /></th><th scope="col" data-table-order="status"><spring:message code="voucher.status" /></th>
                    <th scope="col" data-table-order="principal"><spring:message code="recording.outstandingPrincipal" /></th><th scope="col" data-table-order="interest"><spring:message code="recording.dueInterest" /></th><th scope="col" data-table-order="charges"><spring:message code="charges.outstanding" /></th><th scope="col" data-table-order="arrears"><spring:message code="recording.arrears" /></th>
                </tr></thead>
                <tbody>
                    <c:forEach items="${statement.page.rows}" var="l"><tr>
                        <td><a class="resource-table-id-link" href="<c:url value='/finance/client-ledgers/${clientId}/loans/${l.id}/statement' />"><c:out value="${l.number}" /></a></td><td><c:out value="${l.product}" /></td><td><span data-resource-status="${l.status}"><spring:message code="recording.status.${l.status}" /></span></td>
                        <td class="statement-money"><fmt:formatNumber value="${l.outstandingPrincipal}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${l.dueInterest}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${l.charges}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${l.arrears}" pattern="#,##0.##;(#,##0.##)" /></td>
                    </tr></c:forEach>
                </tbody>
            </table>
        </div>
        <div class="voucher-pagination" data-table-pagination data-page="${statement.page.page}" data-table-for="client-statement-loans">
            <c:forEach items="${['previous','next']}" var="direction">
                <c:if test="${direction=='previous'?statement.page.page>0:statement.page.hasNext}">
                    <c:url value="/finance/client-ledgers/${clientId}" var="statementPageUrl">
                        <c:param name="page" value="${statement.page.page+(direction=='previous'?-1:1)}" />
                        <c:param name="movementPage" value="${movements.page}" />
                        <c:param name="from" value="${movementFilter.from}" /><c:param name="through" value="${movementFilter.through}" />
                        <c:param name="loanId" value="${movementFilter.loanId}" /><c:param name="productId" value="${movementFilter.productId}" />
                    </c:url>
                    <a data-page-direction="${direction}" href="<c:out value='${statementPageUrl}' />"><spring:message code="common.${direction}" /></a>
                </c:if>
            </c:forEach>
        </div>
    </section>
    <section class="erp-table-wrap statement-movements" data-resource-table="client-statement-movements"
             data-table-page-param="movementPage" data-table-preserve-page-query="true" data-table-sync-query="true"
             data-loan-statement-base="<c:out value='${statementUrl}' />" data-aws-table-region>
        <div class="app-table-titlebar">
            <h2><spring:message code="recording.history" /></h2>
            <sec:authorize access="@access.has(principal,'LOAN_RECORDING_EXPORT')">
                <div class="accounting-actions">
                    <c:forEach items="${['print','pdf']}" var="format">
                        <a class="app-btn btn-neutral" data-table-command data-loan-statement-format="${format}"
                            data-print-action="${format=='print'}"
                            <c:choose><c:when test="${not empty movementSelection.loan}">
                                href="<c:url value='/finance/client-ledgers/${clientId}/loans/${movementSelection.loan.id}/statement/${format}' />"
                            </c:when><c:otherwise>aria-disabled="true"</c:otherwise></c:choose>>
                            <report:reportExport format="${format}" />
                        </a>
                    </c:forEach>
                </div>
            </sec:authorize>
        </div>
        <form class="voucher-filters" method="get" action="<c:out value='${statementUrl}' />">
            <input type="hidden" name="page" value="${statement.page.page}" />
            <input type="hidden" name="detailTab" value="detail-section-client-statement-movements" />
            <label><spring:message code="voucher.from" />
                <input class="aws-control" type="date" name="from" value="${movementFilter.from}" />
            </label>
            <label><spring:message code="voucher.through" />
                <input class="aws-control" type="date" name="through" value="${movementFilter.through}" data-range-error="<spring:message code='recording.statement.dateRange' />" />
            </label>
            <label data-loan-choice data-lazy-choice data-choice-url="<c:url value='/finance/client-ledgers/${clientId}/choices/loans' />">
                <spring:message code="recording.statement.loan" />
                <select class="aws-control" name="loanId" data-choice-select data-searchable="true">
                    <option value="" data-empty-option="true"><spring:message code="voucher.all" /></option>
                    <c:if test="${not empty movementSelection.loan}">
                        <option value="${movementSelection.loan.id}" selected><c:out value="${movementSelection.loan.label}" /></option>
                    </c:if>
                </select>
                <%@ include file="choice-search.jspf" %>
            </label>
            <label data-loan-choice data-lazy-choice data-choice-url="<c:url value='/finance/client-ledgers/${clientId}/choices/products' />">
                <spring:message code="recording.product" />
                <select class="aws-control" name="productId" data-choice-select data-searchable="true">
                    <option value="" data-empty-option="true"><spring:message code="voucher.all" /></option>
                    <c:if test="${not empty movementSelection.product}">
                        <option value="${movementSelection.product.id}" selected><c:out value="${movementSelection.product.label}" /></option>
                    </c:if>
                </select>
                <%@ include file="choice-search.jspf" %>
            </label>
            <button class="app-btn btn-neutral" type="submit"><spring:message code="voucher.filter" /></button>
            <c:url value="/finance/client-ledgers/${clientId}" var="clearMovementsUrl"><c:param name="page" value="${statement.page.page}" /><c:param name="detailTab" value="detail-section-client-statement-movements" /></c:url>
            <a class="app-btn btn-neutral" data-table-query-for="client-statement-movements" href="<c:out value='${clearMovementsUrl}' />">
                <spring:message code="voucher.clear" />
            </a>
        </form>
        <div class="erp-table-scroll" tabindex="0" role="region" aria-label="<spring:message code='recording.history' />">
            <table class="erp-table">
                <thead><tr>
                    <th scope="col" data-table-order="date"><spring:message code="voucher.date" /></th><th scope="col" data-table-order="loan"><spring:message code="recording.statement.loan" /></th><th scope="col" data-table-order="product"><spring:message code="recording.product" /></th><th scope="col" data-table-order="reference"><spring:message code="voucher.reference" /></th><th scope="col" data-table-order="kind"><spring:message code="voucher.type" /></th>
                    <th scope="col" data-table-order="amount"><spring:message code="voucher.amount" /> (TZS)</th><th scope="col" data-table-order="principal"><spring:message code="recording.principal" /></th><th scope="col" data-table-order="interest"><spring:message code="recording.interest" /></th><th scope="col" data-table-order="balance"><spring:message code="recording.balanceAfter" /></th>
                </tr></thead>
                <tbody>
                    <c:forEach items="${movements.rows}" var="p"><tr>
                        <td>${appTime:display(p.date)}</td><td><a class="resource-table-id-link" href="<c:url value='/finance/client-ledgers/${clientId}/loans/${p.loanId}/statement' />"><c:out value="${p.loanNumber}" /></a></td><td><c:out value="${p.product}" /></td><td><a class="resource-table-id-link" href="<c:url value='/finance/loan-postings/${p.id}' />"><c:out value="${p.reference}" /></a></td><td><spring:message code="recording.document.${p.kind}" /></td>
                        <td class="statement-money"><fmt:formatNumber value="${p.kind=='REVERSAL'?-p.amount:p.amount}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${p.principal}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${p.interest}" pattern="#,##0.##;(#,##0.##)" /></td><td class="statement-money"><fmt:formatNumber value="${p.principalBalance}" pattern="#,##0.##;(#,##0.##)" /></td>
                    </tr></c:forEach>
                    <c:if test="${empty movements.rows}"><tr><td colspan="9"><spring:message code="recording.statement.empty" /></td></tr></c:if>
                </tbody>
            </table>
        </div>
        <div class="voucher-pagination" data-table-pagination data-page="${movements.page}" data-table-for="client-statement-movements">
            <c:forEach items="${['previous','next']}" var="direction">
                <c:if test="${direction=='previous'?movements.page>0:movements.hasNext}">
                    <c:url value="/finance/client-ledgers/${clientId}" var="statementPageUrl">
                        <c:param name="movementPage" value="${movements.page+(direction=='previous'?-1:1)}" />
                        <c:param name="page" value="${statement.page.page}" />
                        <c:param name="from" value="${movementFilter.from}" /><c:param name="through" value="${movementFilter.through}" />
                        <c:param name="loanId" value="${movementFilter.loanId}" /><c:param name="productId" value="${movementFilter.productId}" />
                    </c:url>
                    <a data-page-direction="${direction}" href="<c:out value='${statementPageUrl}' />"><spring:message code="common.${direction}" /></a>
                </c:if>
            </c:forEach>
        </div>
    </section>
</div>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
