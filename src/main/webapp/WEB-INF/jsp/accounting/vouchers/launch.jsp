<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<%@ include file="../../fragments/alerts.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><span class="erp-page-path__item"><spring:message code="finance.nav.vouchers" /></span><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="voucher.createHub" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="voucher.createHub" /></h1></div>
<main class="accounting-workspace accounting-create-workspace">
    <section class="accounting-form accounting-create-card voucher-launch-panel">
        <h2><spring:message code="voucher.createHub" /></h2>
        <p class="voucher-help"><spring:message code="voucher.createHubHelp" /></p>
        <div class="voucher-launch-grid">
            <c:forEach items="${creatableVoucherTypes}" var="type">
                <c:choose>
                    <c:when test="${type.name() eq 'RECEIPT'}"><c:set var="voucherPath" value="receipts" /></c:when>
                    <c:when test="${type.name() eq 'PAYMENT'}"><c:set var="voucherPath" value="payments" /></c:when>
                    <c:otherwise><c:set var="voucherPath" value="journals" /></c:otherwise>
                </c:choose>
                <a class="voucher-launch-card" href="<c:url value='/finance/vouchers/${voucherPath}/new' />">
                    <span class="voucher-launch-card__title"><spring:message code="voucher.create.${type.name()}" /></span>
                    <span class="voucher-launch-card__arrow" aria-hidden="true">›</span>
                </a>
            </c:forEach>
        </div>
    </section>
</main>
<%@ include file="../../fragments/footer.jspf" %>
