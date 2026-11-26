<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:url value="/finance/loan-records/${interestTermsLoan}" var="loanUrl" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a href="${loanUrl}"><spring:message code="recording.loanDetails" /></a><span aria-hidden="true">›</span>
    <span aria-current="page"><spring:message code="prepayment.amendTitle" /></span>
</nav>
<div class="accounting-workspace accounting-servicing-workspace" data-resource-form-page>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="prepayment.amendTitle" /></h1></div>
    <c:if test="${not empty interestTermsError}"><p class="console-message" data-message-type="error" role="alert"><spring:message code="${interestTermsError}" /></p></c:if>
    <c:choose><c:when test="${not empty interestTermsDate}">
        <form action="${loanUrl}/interest-terms" method="post" enctype="multipart/form-data">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input type="hidden" name="requestKey" value="${interestTermsKey}" />
            <input type="hidden" name="effectiveDate" value="${interestTermsDate}" />
            <section class="erp-resource-section erp-form-wrap">
                <h2><spring:message code="prepayment.amendEvidence" /></h2>
                <div class="waiver-fields">
                    <label><spring:message code="prepayment.amendDate" /><input class="aws-control" type="date" value="${interestTermsDate}" readonly /></label>
                    <label><spring:message code="prepayment.payoffRule" />
                        <select name="payoffInterest" class="aws-control" required>
                            <option value="EXCLUDE_OPEN_PERIOD" ${interestTermsRule ne 'ELAPSED_DAYS'?'selected':''}><spring:message code="prepayment.payoffRule.exclude" /></option>
                            <option value="ELAPSED_DAYS" ${interestTermsRule eq 'ELAPSED_DAYS'?'selected':''}><spring:message code="prepayment.payoffRule.elapsed" /></option>
                        </select>
                    </label>
                    <label><spring:message code="prepayment.amendApproval" /><input name="approvalReference" class="aws-control" maxlength="160" required value="<c:out value='${interestTermsApproval}' />" /></label>
                    <label><spring:message code="prepayment.amendPdf" /><input name="signedAmendment" type="file" accept="application/pdf,.pdf" required /></label>
                </div>
            </section>
            <div class="accounting-actions waiver-actions"><a href="${loanUrl}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="prepayment.amendRecord" /></button></div>
        </form>
    </c:when><c:otherwise><p class="console-message" role="status"><spring:message code="prepayment.amendUnavailable" /></p></c:otherwise></c:choose>
</div>
<%@ include file="../fragments/footer.jspf" %>
