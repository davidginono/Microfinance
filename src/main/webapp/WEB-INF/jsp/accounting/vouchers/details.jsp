<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />

<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='${base}' />"><spring:message code="voucher.register.${voucherType}" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><c:out value="${voucher.number}" /></span></nav>
<div class="erp-page-header voucher-toolbar" data-aws-page-header><h1 class="erp-page-title"><c:out value="${voucher.number}" /></h1><div class="accounting-actions"><sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_EXPORT')"><a class="app-btn btn-neutral" href="<c:url value='${base}/${voucher.id}/pdf' />"><spring:message code="voucher.pdf" /></a><a class="app-btn btn-neutral" target="_blank" rel="noopener" href="<c:url value='${base}/${voucher.id}/print' />"><spring:message code="voucher.print" /></a></sec:authorize></div></div>
<div class="accounting-workspace"><c:if test="${voucherPosted}"><p role="status" class="voucher-success"><spring:message code="voucher.posted" /></p></c:if>
<%@ include file="document.jspf" %>
<c:if test="${empty voucher.reversesId and empty voucher.reversedBy}"><sec:authorize access="@access.has(principal, '${reverseClaim}')"><section class="accounting-form"><details><summary><spring:message code="voucher.reverse" /></summary><p class="voucher-help"><spring:message code="voucher.reverseHelp" /></p><c:if test="${not empty reverseError}"><p class="coa-error" role="alert"><spring:message code="${reverseError}" /></p></c:if><form method="post" action="<c:url value='${base}/${voucher.id}/reverse' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${reverseKey}" /><div class="accounting-grid"><label><spring:message code="voucher.date" /><input type="date" class="aws-control" name="effectiveDate" value="<c:out value='${today}' />" min="${voucher.effectiveDate}" required /></label><label><spring:message code="voucher.reason" /><input class="aws-control" name="reason" maxlength="500" value="<c:out value='${reason}' />" required /></label></div><div class="accounting-actions"><button class="app-btn btn-primary"><spring:message code="voucher.reverse" /></button></div></form></details></section></sec:authorize></c:if>
</div><%@ include file="../../fragments/footer.jspf" %>
