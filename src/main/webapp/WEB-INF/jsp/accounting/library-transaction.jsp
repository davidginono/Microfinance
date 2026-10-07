<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${transaction.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty transaction.nameSw?transaction.nameSw:transaction.name}" /></h1></div>
    <p><spring:message code="library.activity" />: <c:url value="/finance/library/activities/${transaction.activityId}" var="activityUrl" /><a href="<c:out value='${activityUrl}' />"><c:out value="${transaction.activityCode}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty transaction.activityNameSw?transaction.activityNameSw:transaction.activityName}" /></a> · <spring:message code="accounting.active.${transaction.active}" /></p>
    <p><spring:message code="library.sourceEvent" />: <spring:message code="accounting.policy.event.${transaction.sourceEvent}" /></p>
    <c:if test="${not empty transaction.description}"><p><c:out value="${transaction.description}" /></p></c:if>
    <c:if test="${not empty librarySuccess}"><p class="coa-success" role="status"><spring:message code="${librarySuccess}" /></p></c:if>
    <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><div class="accounting-actions">
        <c:if test="${transaction.active}"><a class="app-btn btn-primary" href="<c:url value='/finance/library/transactions/${transaction.id}/template' />"><spring:message code="library.newVersion" /></a></c:if>
        <form method="post" action="<c:url value='/finance/library/transactions/${transaction.id}/${transaction.active?"deactivate":"reactivate"}' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="${transaction.active?'accounting.deactivate':'coa.reactivate'}" /></button></form>
    </div></sec:authorize>
    <p><spring:message code="library.definitionOnly" /></p>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="library.template" /><c:if test="${not empty template}"> · <spring:message code="library.version" arguments="${template.version}" /></c:if></h2></div>
        <c:if test="${not empty template}"><p class="library-note"><c:out value="${template.reason}" /></p></c:if>
        <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="library-template"><table class="erp-table">
            <thead><tr><th><spring:message code="library.component" /></th><th><spring:message code="library.debitAccount" /></th><th><spring:message code="library.creditAccount" /></th></tr></thead>
            <tbody><c:forEach items="${template.rules}" var="rule"><tr><td><spring:message code="library.component.${rule.component}" /></td><td><c:out value="${rule.debitCode}" /> · <c:out value="${rule.debitName}" /></td><td><c:out value="${rule.creditCode}" /> · <c:out value="${rule.creditName}" /></td></tr></c:forEach><c:if test="${empty template}"><tr><td colspan="3"><spring:message code="library.noTemplate" /></td></tr></c:if></tbody>
        </table></div>
    </section>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="library.history" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="library-history"><table class="erp-table">
            <thead><tr><th><spring:message code="library.template" /></th><th><spring:message code="library.reason" /></th><th><spring:message code="library.savedAt" /></th><th><spring:message code="common.actions" /></th></tr></thead>
            <tbody><c:forEach items="${versions.rows}" var="v"><tr><td><spring:message code="library.version" arguments="${v.version}" /></td><td><c:out value="${v.reason}" /></td><td><c:out value="${v.createdAt}" /></td><td><c:url value="/finance/library/transactions/${transaction.id}" var="viewVersion"><c:param name="version" value="${v.id}" /><c:param name="page" value="${versions.page}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${viewVersion}' />"><spring:message code="library.open" /></a></td></tr></c:forEach><c:if test="${empty versions.rows}"><tr><td colspan="4"><spring:message code="library.noTemplate" /></td></tr></c:if></tbody>
        </table></div>
        <div class="erp-table-footer accounting-actions">
            <c:if test="${versions.page>0}"><c:url value="/finance/library/transactions/${transaction.id}" var="previous"><c:param name="page" value="${versions.page-1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="coa.page" arguments="${versions.page+1}" /></span>
            <c:if test="${versions.hasNext}"><c:url value="/finance/library/transactions/${transaction.id}" var="next"><c:param name="page" value="${versions.page+1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
