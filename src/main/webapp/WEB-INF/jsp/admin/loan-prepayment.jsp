<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:url value="/admin/settings-controls/loan-products/${product.id}/edit" var="productEditUrl" />
<spring:message code="prepayment.product.title" var="prepaymentTitle" />
<div class="accounting-workspace accounting-create-workspace">
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
        <a class="erp-page-path__item erp-page-path__link" href="${productEditUrl}"><c:out value="${product.displayName}" /></a>
        <span class="erp-page-path__separator" aria-hidden="true">›</span>
        <span class="erp-page-path__item" aria-current="page">${prepaymentTitle}</span>
    </nav>
    <div class="erp-page-header"><h1 class="erp-page-title">${prepaymentTitle}</h1></div>
    <c:if test="${prepaymentSaved}">
        <p data-message-type="success" role="status"><spring:message code="prepayment.product.saved" /></p>
    </c:if>
    <form:form method="post" modelAttribute="policyForm" cssClass="accounting-create-form">
        <section class="accounting-section accounting-form" aria-labelledby="prepayment-scope">
            <h2 id="prepayment-scope"><spring:message code="prepayment.product.scope" /></h2>
            <div class="accounting-form-section">
                <div class="accounting-grid">
                    <label><spring:message code="prepayment.schedule" />
                        <form:select path="schedule" cssClass="aws-control">
                            <form:option value="NEXT_INSTALMENTS"><spring:message code="prepayment.product.schedule.NEXT_INSTALMENTS" /></form:option>
                            <form:option value="LAST_INSTALMENTS"><spring:message code="prepayment.product.schedule.LAST_INSTALMENTS" /></form:option>
                            <form:option value="PRO_RATA"><spring:message code="prepayment.product.schedule.PRO_RATA" /></form:option>
                        </form:select>
                    </label>
                    <label><spring:message code="prepayment.partialInterest" />
                        <form:select path="partialInterest" cssClass="aws-control">
                            <form:option value="RECALCULATE"><spring:message code="prepayment.product.partial.RECALCULATE" /></form:option>
                            <form:option value="RETAIN_UNTIL_SETTLEMENT"><spring:message code="prepayment.product.partial.RETAIN_UNTIL_SETTLEMENT" /></form:option>
                        </form:select>
                    </label>
                    <label><spring:message code="prepayment.dayBasis" />
                        <form:select path="dayBasis" cssClass="aws-control">
                            <form:option value="ACTUAL_PERIOD"><spring:message code="prepayment.product.basis.ACTUAL_PERIOD" /></form:option>
                            <form:option value="THIRTY_360"><spring:message code="prepayment.product.basis.THIRTY_360" /></form:option>
                        </form:select>
                    </label>
                    <label><spring:message code="prepayment.policyReason" />
                        <form:textarea path="reason" rows="2" maxlength="500" required="required" cssClass="aws-control" />
                    </label>
                </div>
                <p><spring:message code="prepayment.product.payoff" /></p>
                <details class="accounting-additional">
                    <summary><spring:message code="prepayment.product.flatTitle" /></summary>
                    <p><spring:message code="prepayment.flatHelp" /></p>
                </details>
            </div>
        </section>
        <c:if test="${not empty prepaymentError}">
            <div class="accounting-error-summary" role="alert" tabindex="-1">
                <p class="coa-error"><spring:message code="${prepaymentError eq 'prepayment.error.input' ? 'prepayment.product.error.input' : prepaymentError}" /></p>
            </div>
        </c:if>
        <div class="accounting-actions accounting-form-actions">
            <a class="app-btn btn-neutral" href="${productEditUrl}"><spring:message code="common.cancel" /></a>
            <sec:authorize access="@access.has(principal,'LOAN_PRODUCTS_CONFIGURE')">
                <button type="submit" class="app-btn btn-launch"><spring:message code="common.save" /></button>
            </sec:authorize>
        </div>
    </form:form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
