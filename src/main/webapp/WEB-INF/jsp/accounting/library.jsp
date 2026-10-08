<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:set var="isActivity" value="${not isTransactions}" />

<c:set var="listBase" value="${isActivity?'/finance/library':'/finance/library/transactions'}" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" aria-current="page" href="<c:url value='${listBase}' />"><spring:message code="${isActivity?'library.activities':'library.transactions'}" /></a></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="${isActivity?'library.activities':'library.transactions'}" /></h1></div>
<div class="accounting-workspace accounting-resource-workspace">
    <c:if test="${not empty libraryError}"><p class="coa-error" role="alert"><spring:message code="${libraryError}" /></p></c:if>
    <c:if test="${not empty librarySuccess}"><p class="coa-success" role="status"><spring:message code="${librarySuccess}" /></p></c:if>
    <section class="erp-table-wrap" data-aws-table-region id="library-register">
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="${isActivity?'library.activityRegister':'library.transactionRegister'}" /></h2>
            <div class="accounting-actions">
            <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><form method="post" id="library-status-form" data-url="<c:url value='/finance/library/${isActivity?"activities":"transactions"}/' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button type="submit" class="app-btn btn-neutral" id="library-status" disabled data-deactivate="<spring:message code='accounting.deactivate' />" data-reactivate="<spring:message code='coa.reactivate' />"><spring:message code="accounting.deactivate" /></button></form></sec:authorize>
            <button type="button" class="app-btn btn-neutral" id="library-view" disabled><spring:message code="${isActivity?'library.transactions':'library.open'}" /></button>
            <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')">
                <c:if test="${isActivity}"><button type="button" class="app-btn btn-primary" data-library-open><spring:message code="library.addActivity" /></button></c:if>
                <c:if test="${not isActivity}"><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><button type="button" class="app-btn btn-primary" data-library-open><spring:message code="library.addTransaction" /></button></sec:authorize></c:if>
            </sec:authorize>
            </div>
        </div>
        <form method="get" action="<c:url value='${listBase}' />" class="erp-table-toolbar coa-filters">
            <label class="erp-table-toolbar__search"><spring:message code="coa.search" /><input class="aws-control" name="search" value="<c:out value='${search}' />" maxlength="100" /></label>
            <c:if test="${not isActivity}"><label class="erp-table-toolbar__control"><spring:message code="library.activity" /><select class="aws-control" name="activityId"><option value=""><spring:message code="coa.all" /></option>
                <c:if test="${not empty activity}"><option value="${activity.id}" selected><c:out value="${activity.code}" /> · <c:out value="${activity.name}" /></option></c:if>
                <c:forEach items="${activityFilters.rows}" var="choice"><c:if test="${choice.id ne activity.id}"><option value="${choice.id}"><c:out value="${choice.code}" /> · <c:out value="${choice.name}" /></option></c:if></c:forEach>
            </select></label></c:if>
            <label class="erp-table-toolbar__control"><spring:message code="coa.status" /><select class="aws-control" name="state"><option value=""><spring:message code="coa.all" /></option><option value="ACTIVE" ${state=='ACTIVE'?'selected':''}><spring:message code="accounting.active.true" /></option><option value="INACTIVE" ${state=='INACTIVE'?'selected':''}><spring:message code="accounting.active.false" /></option></select></label>
            <div class="erp-table-toolbar__actions"><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='${listBase}' />"><spring:message code="coa.clear" /></a></div>
        </form>
        <p class="sr-only" id="library-selection-status" role="status" data-empty="<spring:message code='coa.selectHint' />" data-selected="<spring:message code='coa.selected' />"><spring:message code="coa.selectHint" /></p>
        <div class="erp-table-scroll" data-view-position-key="${isActivity?'activity-register':'transaction-register'}"><table class="erp-table">
            <thead><tr><th class="coa-selection-cell"><span class="sr-only"><spring:message code="coa.select" /></span></th><th><spring:message code="library.code" /></th><th><spring:message code="library.name" /></th><c:if test="${not isActivity}"><th><spring:message code="library.activityCode" /></th><th><spring:message code="library.sourceEvent" /></th><th><spring:message code="library.template" /></th></c:if><th><spring:message code="coa.status" /></th></tr></thead>
            <tbody><c:forEach items="${records.rows}" var="r">
                <c:choose><c:when test="${isActivity}"><c:url value="/finance/library/transactions" var="recordUrl"><c:param name="activityId" value="${r.id}" /></c:url></c:when><c:otherwise><c:url value="/finance/library/transactions/${r.id}" var="recordUrl" /></c:otherwise></c:choose>
                <tr data-library-row data-id="${r.id}" data-active="${r.active}" data-url="<c:out value='${recordUrl}' />">
                <td class="coa-selection-cell"><input type="checkbox" name="library-selection" value="${r.id}" aria-label="<spring:message code='coa.select' /> <c:out value='${r.code}' />" /></td>
                <td data-library-code><c:out value="${r.code}" /></td><td data-library-name><c:out value="${r.name}" /><c:if test="${not empty r.description}"><small class="coa-description"><c:out value="${r.description}" /></small></c:if></td>
                <c:if test="${not isActivity}"><td><c:out value="${r.activityCode}" /></td><td><spring:message code="accounting.policy.event.${r.sourceEvent}" /></td><td><c:choose><c:when test="${r.hasTemplate}"><spring:message code="library.configured" /></c:when><c:otherwise><spring:message code="library.noTemplate" /></c:otherwise></c:choose></td></c:if>
                <td><spring:message code="accounting.active.${r.active}" /></td>
            </tr></c:forEach><c:if test="${empty records.rows}"><tr><td colspan="${isActivity?4:7}"><div class="accounting-empty"><spring:message code="${not empty search or not empty state or not empty activity?'coa.noMatches':isActivity?'library.emptyActivities':'library.emptyTransactions'}" /></div></td></tr></c:if></tbody>
        </table></div>
        <div class="erp-table-footer accounting-actions">
            <c:if test="${records.page>0}"><c:url value="${listBase}" var="previous"><c:param name="page" value="${records.page-1}" /><c:param name="search" value="${search}" /><c:param name="state" value="${state}" /><c:if test="${not empty activity}"><c:param name="activityId" value="${activity.id}" /></c:if></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="coa.page" arguments="${records.page+1}" /></span>
            <c:if test="${records.hasNext}"><c:url value="${listBase}" var="next"><c:param name="page" value="${records.page+1}" /><c:param name="search" value="${search}" /><c:param name="state" value="${state}" /><c:if test="${not empty activity}"><c:param name="activityId" value="${activity.id}" /></c:if></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')">
    <c:if test="${isActivity}"><%@ include file="library-activity-modal.jspf" %></c:if>
    <c:if test="${not isActivity}"><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><%@ include file="library-transaction-modal.jspf" %></sec:authorize></c:if>
</sec:authorize>
<%@ include file="accounting-form-support.jspf" %>
<script src="<c:url value='/js/accounting-library.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
