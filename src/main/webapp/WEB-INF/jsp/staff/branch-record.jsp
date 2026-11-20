<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="branchSection" value="${section}" />
<c:set var="branchDetailLabel" value="${section eq 'clients' ? 'kyc.details' : 'recording.loanDetails'}" />
<%@ include file="branch-path.jspf" %>
<c:set var="clientRecord" value="${section eq 'clients'}" />
<div class="accounting-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="${clientRecord ? 'kyc.details' : 'recording.loanDetails'}" /></h1></div>
    <section class="erp-resource-section"><h2><spring:message code="table.details" /></h2><div class="erp-panel-body"><dl class="erp-detail-facts">
        <div><dt><spring:message code="${clientRecord ? 'branches.clientNumber' : 'loan.loanId'}" /></dt><dd><c:out value="${record.number}" /></dd></div>
        <div><dt><spring:message code="recording.client" /></dt><dd><c:out value="${clientRecord ? record.name : record.client}" /></dd></div>
        <div><dt><spring:message code="common.status" /></dt><dd><spring:message code="${clientRecord ? 'kyc.state.'.concat(record.state) : 'recording.status.'.concat(record.status)}" text="${clientRecord ? record.state : record.status}" /></dd></div>
        <c:if test="${not clientRecord}">
            <div><dt><spring:message code="recording.product" /></dt><dd><c:out value="${record.product}" /></dd></div>
            <div><dt><spring:message code="recording.principal" /> (TZS)</dt><dd><fmt:formatNumber value="${record.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
            <c:if test="${not empty record.disbursed}"><div><dt><spring:message code="repayment.disbursed" /></dt><dd><c:out value="${appTime:display(record.disbursed)}" /></dd></div></c:if>
        </c:if>
    </dl></div></section>
</div>
<%@ include file="../fragments/footer.jspf" %>
