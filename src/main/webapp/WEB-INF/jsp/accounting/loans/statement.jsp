<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<template data-resource-details-page></template>
<link rel="stylesheet" href="<c:url value='/css/client-loan-statement.css' />" />
<div class="accounting-workspace client-statement-workspace">
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/finance/client-ledgers' />"><spring:message code="recording.clientLedgers" /></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><c:out value="${statement.client}" /></span></nav>
    <div class="erp-page-header erp-detail-header" data-aws-page-header>
        <h1 class="erp-page-title"><spring:message code="recording.statement" /></h1>
        <sec:authorize access="@access.has(principal,'LOAN_RECORDING_EXPORT')">
            <div class="accounting-actions" data-aws-action-pin="true">
                <a class="app-btn btn-neutral" href="<c:url value='/finance/client-ledgers/${clientId}/pdf' />"><spring:message code="voucher.pdf" /></a>
                <a class="app-btn btn-neutral" data-print-action="true" href="<c:url value='/finance/client-ledgers/${clientId}/print' />"><spring:message code="voucher.print" /></a>
            </div>
        </sec:authorize>
    </div>
    <section class="erp-panel">
        <div class="erp-panel-header"><h2 class="erp-panel-title"><c:out value="${statement.client}" /></h2></div>
        <div class="erp-panel-body">
        <dl class="erp-detail-facts">
            <div><dt><spring:message code="recording.outstandingPrincipal" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="recording.dueInterest" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.interest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="charges.outstanding" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.charges}" minFractionDigits="2" maxFractionDigits="2" /></dd></div><div><dt><spring:message code="recording.arrears" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.arrears}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
            <div><dt><spring:message code="recording.futureInterest" /> (TZS)</dt><dd><fmt:formatNumber value="${statement.futureInterest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        </dl>
        </div>
    </section>
    <section class="erp-table-wrap" data-resource-table="client-statement-loans" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="recording.register.LOAN" /></h2></div>
        <div class="erp-table-scroll" tabindex="0" role="region" aria-label="<spring:message code='recording.register.LOAN' />">
            <table class="erp-table">
                <thead><tr>
                    <th scope="col" data-table-order="loan"><spring:message code="recording.loan" /></th><th scope="col" data-table-order="product"><spring:message code="recording.product" /></th><th scope="col" data-table-order="status"><spring:message code="voucher.status" /></th>
                    <th scope="col" data-table-order="principal"><spring:message code="recording.outstandingPrincipal" /></th><th scope="col" data-table-order="interest"><spring:message code="recording.dueInterest" /></th><th scope="col" data-table-order="charges"><spring:message code="charges.outstanding" /></th><th scope="col" data-table-order="arrears"><spring:message code="recording.arrears" /></th>
                </tr></thead>
                <tbody>
                    <c:forEach items="${statement.page.rows}" var="l"><tr>
                        <td><a class="resource-table-id-link" href="<c:url value='/finance/client-ledgers/${clientId}/loans/${l.id}/statement' />"><c:out value="${l.number}" /></a></td><td><c:out value="${l.product}" /></td><td><span data-resource-status="${l.status}"><spring:message code="recording.status.${l.status}" /></span></td>
                        <td class="statement-money"><fmt:formatNumber value="${l.outstandingPrincipal}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${l.dueInterest}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${l.charges}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${l.arrears}" pattern="#,##0.00;(#,##0.00)" /></td>
                    </tr></c:forEach>
                </tbody>
            </table>
        </div>
        <div class="voucher-pagination" data-table-pagination data-page="${statement.page.page}" data-table-for="client-statement-loans">
            <spring:message code="voucher.page" /> ${statement.page.page+1}
            <div class="accounting-actions">
                <c:if test="${statement.page.page>0}"><a data-page-direction="previous" class="app-btn btn-neutral" href="?page=${statement.page.page-1}&amp;movementPage=${movements.page}"><spring:message code="common.previous" /></a></c:if>
                <c:if test="${statement.page.hasNext}"><a data-page-direction="next" class="app-btn btn-neutral" href="?page=${statement.page.page+1}&amp;movementPage=${movements.page}"><spring:message code="common.next" /></a></c:if>
            </div>
        </div>
    </section>
    <section class="erp-table-wrap statement-movements" data-resource-table="client-statement-movements" data-table-page-param="movementPage" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="recording.history" /></h2></div>
        <div class="erp-table-scroll" tabindex="0" role="region" aria-label="<spring:message code='recording.history' />">
            <table class="erp-table">
                <thead><tr>
                    <th scope="col" data-table-order="date"><spring:message code="voucher.date" /></th><th scope="col" data-table-order="loan"><spring:message code="recording.loan" /></th><th scope="col" data-table-order="product"><spring:message code="recording.product" /></th><th scope="col" data-table-order="reference"><spring:message code="voucher.reference" /></th><th scope="col" data-table-order="kind"><spring:message code="voucher.type" /></th>
                    <th scope="col" data-table-order="amount"><spring:message code="voucher.amount" /> (TZS)</th><th scope="col" data-table-order="principal"><spring:message code="recording.principal" /></th><th scope="col" data-table-order="interest"><spring:message code="recording.interest" /></th><th scope="col" data-table-order="balance"><spring:message code="recording.balanceAfter" /></th>
                </tr></thead>
                <tbody>
                    <c:forEach items="${movements.rows}" var="p"><tr>
                        <td>${appTime:display(p.date)}</td><td><c:out value="${p.loanNumber}" /></td><td><c:out value="${p.product}" /></td><td><a class="resource-table-id-link" href="<c:url value='/finance/loan-postings/${p.id}' />"><c:out value="${p.reference}" /></a></td><td><spring:message code="recording.document.${p.kind}" /></td>
                        <td class="statement-money"><fmt:formatNumber value="${p.kind=='REVERSAL'?-p.amount:p.amount}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${p.principal}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${p.interest}" pattern="#,##0.00;(#,##0.00)" /></td><td class="statement-money"><fmt:formatNumber value="${p.principalBalance}" pattern="#,##0.00;(#,##0.00)" /></td>
                    </tr></c:forEach>
                    <c:if test="${empty movements.rows}"><tr><td colspan="9"><spring:message code="recording.statement.empty" /></td></tr></c:if>
                </tbody>
            </table>
        </div>
        <div class="voucher-pagination" data-table-pagination data-page="${movements.page}" data-table-for="client-statement-movements">
            <spring:message code="voucher.page" /> ${movements.page+1}
            <div class="accounting-actions">
                <c:if test="${movements.page>0}"><a data-page-direction="previous" class="app-btn btn-neutral" href="?movementPage=${movements.page-1}&amp;page=${statement.page.page}"><spring:message code="common.previous" /></a></c:if>
                <c:if test="${movements.hasNext}"><a data-page-direction="next" class="app-btn btn-neutral" href="?movementPage=${movements.page+1}&amp;page=${statement.page.page}"><spring:message code="common.next" /></a></c:if>
            </div>
        </div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
