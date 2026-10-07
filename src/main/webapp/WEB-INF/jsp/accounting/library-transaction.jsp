<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions' />"><spring:message code="library.transactions" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="library.transactionTemplates" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="library.transactionTemplates" /></h1></div>
<div class="accounting-workspace accounting-resource-workspace">
    <section class="erp-table-wrap accounting-filter-section">
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="coa.filterBy" /></h2>
            <c:if test="${not empty transaction}"><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><form method="post" action="<c:url value='/finance/library/transactions/${transaction.id}/${transaction.active?"deactivate":"reactivate"}' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="${transaction.active?'accounting.deactivate':'coa.reactivate'}" /></button></form></sec:authorize></c:if>
        </div>
    <form method="get" action="<c:url value='/finance/library/templates' />" class="erp-table-toolbar coa-filters library-template-filters">
        <label class="erp-table-toolbar__control"><spring:message code="library.activity" /><select class="aws-control" name="activityId"><option value=""><spring:message code="coa.all" /></option>
            <c:if test="${not empty activity}"><option value="${activity.id}" selected><c:out value="${activity.code}" /> · <c:out value="${activity.name}" /></option></c:if>
            <c:forEach items="${activityFilters.rows}" var="choice"><c:if test="${choice.id ne activity.id}"><option value="${choice.id}"><c:out value="${choice.code}" /> · <c:out value="${choice.name}" /></option></c:if></c:forEach>
        </select></label>
        <label class="erp-table-toolbar__control"><spring:message code="library.transaction" /><select class="aws-control" name="transactionId">
            <c:if test="${empty transaction}"><option value=""><spring:message code="library.noMatchingTransactions" /></option></c:if>
            <c:if test="${not empty transaction}"><option value="${transaction.id}" selected><c:out value="${transaction.code}" /> · <c:out value="${transaction.name}" /></option></c:if>
            <c:forEach items="${transactionChoices.rows}" var="choice"><c:if test="${choice.id ne transaction.id}"><option value="${choice.id}"><c:out value="${choice.code}" /> · <c:out value="${choice.name}" /></option></c:if></c:forEach>
        </select></label>
        <label class="erp-table-toolbar__control"><spring:message code="library.sourceEvent" /><select class="aws-control" name="sourceEvent"><option value=""><spring:message code="coa.all" /></option><c:forEach items="${sourceEvents}" var="event"><option value="${event}" ${sourceEvent==event?'selected':''}><spring:message code="accounting.policy.event.${event}" /></option></c:forEach></select></label>
        <label class="erp-table-toolbar__control"><spring:message code="coa.status" /><select class="aws-control" name="state"><option value=""><spring:message code="coa.all" /></option><option value="ACTIVE" ${state=='ACTIVE'?'selected':''}><spring:message code="accounting.active.true" /></option><option value="INACTIVE" ${state=='INACTIVE'?'selected':''}><spring:message code="accounting.active.false" /></option></select></label>
        <div class="erp-table-toolbar__actions"><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/library/templates' />"><spring:message code="coa.clear" /></a></div>
    </form>
    </section>
    <c:if test="${empty transaction}"><p role="status"><spring:message code="library.noMatchingTransactions" /></p></c:if>
    <c:if test="${not empty transaction}">
    <c:if test="${not empty transaction.description}"><p><c:out value="${transaction.description}" /></p></c:if>
    <c:if test="${not empty librarySuccess}"><p class="coa-success" role="status"><spring:message code="${librarySuccess}" /></p></c:if>
    <p class="accounting-definition-note"><spring:message code="library.definitionOnly" /></p>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="library.template" /></h2><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><c:if test="${transaction.active}"><a class="app-btn btn-primary" href="<c:url value='/finance/library/transactions/${transaction.id}/template' />"><spring:message code="library.newVersion" /></a></c:if></sec:authorize></div>
        <form method="get" action="<c:url value='/finance/library/templates' />" class="erp-table-toolbar coa-filters library-template-filters">
            <input type="hidden" name="transactionId" value="${transaction.id}" /><input type="hidden" name="activityId" value="${activity.id}" /><input type="hidden" name="sourceEvent" value="<c:out value='${sourceEvent}' />" /><input type="hidden" name="state" value="<c:out value='${state}' />" /><input type="hidden" name="page" value="${versions.page}" />
            <label class="erp-table-toolbar__control"><spring:message code="library.templateVersion" /><select class="aws-control" name="version"><option value=""><spring:message code="library.currentVersion" /></option>
                <c:if test="${not empty selectedVersion}"><option value="${template.id}" selected><spring:message code="library.version" arguments="${template.version}" /></option></c:if>
                <c:forEach items="${versions.rows}" var="v"><c:if test="${v.id ne selectedVersion}"><option value="${v.id}"><spring:message code="library.version" arguments="${v.version}" /></option></c:if></c:forEach>
            </select></label><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button>
        </form>
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
            <tbody><c:forEach items="${versions.rows}" var="v"><tr><td><spring:message code="library.version" arguments="${v.version}" /></td><td><c:out value="${v.reason}" /></td><td><c:out value="${v.createdAt}" /></td><td><c:url value="/finance/library/templates" var="viewVersion"><c:param name="transactionId" value="${transaction.id}" /><c:param name="activityId" value="${activity.id}" /><c:param name="sourceEvent" value="${sourceEvent}" /><c:param name="state" value="${state}" /><c:param name="version" value="${v.id}" /><c:param name="page" value="${versions.page}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${viewVersion}' />"><spring:message code="library.open" /></a></td></tr></c:forEach><c:if test="${empty versions.rows}"><tr><td colspan="4"><spring:message code="library.noTemplate" /></td></tr></c:if></tbody>
        </table></div>
        <div class="erp-table-footer accounting-actions">
            <c:if test="${versions.page>0}"><c:url value="/finance/library/templates" var="previous"><c:param name="transactionId" value="${transaction.id}" /><c:param name="activityId" value="${activity.id}" /><c:param name="sourceEvent" value="${sourceEvent}" /><c:param name="state" value="${state}" /><c:param name="version" value="${selectedVersion}" /><c:param name="page" value="${versions.page-1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="coa.page" arguments="${versions.page+1}" /></span>
            <c:if test="${versions.hasNext}"><c:url value="/finance/library/templates" var="next"><c:param name="transactionId" value="${transaction.id}" /><c:param name="activityId" value="${activity.id}" /><c:param name="sourceEvent" value="${sourceEvent}" /><c:param name="state" value="${state}" /><c:param name="version" value="${selectedVersion}" /><c:param name="page" value="${versions.page+1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
    </c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
