<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="accounting.accounts" /></h1></div>
    <p><spring:message code="coa.intro" /></p>
    <c:if test="${not empty accountingError}"><p class="coa-error" role="alert"><spring:message code="${accountingError}" /></p></c:if>
    <c:if test="${not empty accountingSuccess}"><p class="coa-success" role="status"><spring:message code="${accountingSuccess}" /></p></c:if>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar coa-titlebar">
            <h2><spring:message code="coa.registry" /></h2>
            <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')">
                <div class="accounting-actions">
                    <form method="post" action="<c:url value='/finance/accounts/initialize' />">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <button class="app-btn btn-neutral"><spring:message code="coa.initialize" /></button>
                    </form>
                    <a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/groups/new' />"><spring:message code="coa.newGroup" /></a>
                    <a class="app-btn btn-primary" href="<c:url value='/finance/accounts/posting/new' />"><spring:message code="coa.newAccount" /></a>
                </div>
            </sec:authorize>
        </div>
        <form method="get" action="<c:url value='/finance/accounts' />" class="erp-table-toolbar coa-filters">
            <label class="erp-table-toolbar__search"><spring:message code="coa.search" /><input class="aws-control" name="search" value="<c:out value='${filter.search}' />" maxlength="100" /></label>
            <label class="erp-table-toolbar__control"><spring:message code="accounting.type" /><select class="aws-control" name="type"><option value=""><spring:message code="coa.all" /></option><c:forEach items="${accountTypes}" var="t"><option value="${t}" ${filter.type==t?'selected':''}><spring:message code="accounting.option.${t}" /></option></c:forEach></select></label>
            <label class="erp-table-toolbar__control"><spring:message code="accounting.kind" /><select class="aws-control" name="kind"><option value=""><spring:message code="coa.all" /></option><c:forEach items="${accountKinds}" var="k"><option value="${k}" ${filter.kind==k?'selected':''}><c:choose><c:when test="${k=='HEADING'}"><spring:message code="coa.group" /></c:when><c:otherwise><spring:message code="accounting.option.${k}" /></c:otherwise></c:choose></option></c:forEach></select></label>
            <label class="erp-table-toolbar__control"><spring:message code="coa.status" /><select class="aws-control" name="state"><option value=""><spring:message code="coa.all" /></option><option value="ACTIVE" ${filter.state=='ACTIVE'?'selected':''}><spring:message code="accounting.active.true" /></option><option value="INACTIVE" ${filter.state=='INACTIVE'?'selected':''}><spring:message code="accounting.active.false" /></option></select></label>
            <div class="erp-table-toolbar__actions"><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts' />"><spring:message code="coa.clear" /></a></div>
        </form>
        <div class="erp-table-scroll" data-view-position-key="gl-accounts"><table class="erp-table">
            <thead><tr><th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th><th><spring:message code="coa.parentGroup" /></th><th><spring:message code="accounting.type" /></th><th><spring:message code="accounting.kind" /></th><th><spring:message code="accounting.normalBalance" /></th><th><spring:message code="coa.status" /></th><th><spring:message code="common.actions" /></th></tr></thead>
            <tbody><c:forEach items="${accounts.rows}" var="row"><c:set var="a" value="${row.account}" /><tr>
                <td><c:out value="${a.code}" /></td>
                <td><c:out value="${pageContext.response.locale.language=='sw' and not empty a.nameSw?a.nameSw:a.name}" /><c:if test="${not empty a.description}"><small class="coa-description"><c:out value="${a.description}" /></small></c:if></td>
                <td><c:choose><c:when test="${not empty row.parentCode}"><c:out value="${row.parentCode}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty row.parentNameSw?row.parentNameSw:row.parentName}" /></c:when><c:when test="${a.kind=='HEADING' and a.code.matches('[1-5]00000')}"><spring:message code="coa.mainGroup" /></c:when><c:otherwise><spring:message code="coa.noParent" /></c:otherwise></c:choose></td>
                <td><spring:message code="accounting.option.${a.type}" /></td>
                <td><c:choose><c:when test="${a.kind=='HEADING'}"><spring:message code="coa.group" /></c:when><c:otherwise><spring:message code="accounting.option.${a.kind}" /></c:otherwise></c:choose></td>
                <td><spring:message code="${a.normalBalance=='DEBIT'?'accounting.debit':'accounting.credit'}" /></td>
                <td><spring:message code="accounting.active.${a.active}" /></td>
                <td><div class="accounting-actions">
                    <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')"><c:if test="${a.active and a.kind=='HEADING'}">
                        <c:choose><c:when test="${a.code.matches('[1-5][0-9]0000')}"><c:url value="/finance/accounts/groups/new" var="addChild"><c:param name="parentId" value="${a.id}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${addChild}' />"><spring:message code="coa.addGroup" /></a></c:when><c:when test="${a.code.matches('[1-5][1-9][1-9]000')}"><c:url value="/finance/accounts/posting/new" var="addChild"><c:param name="parentId" value="${a.id}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${addChild}' />"><spring:message code="coa.addAccount" /></a></c:when></c:choose>
                    </c:if></sec:authorize>
                    <sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><c:if test="${not (empty a.parentId and a.kind=='HEADING' and a.code.matches('[1-5]00000'))}">
                        <c:url value="/finance/accounts/${a.id}/${a.active?'deactivate':'reactivate'}" var="statusUrl" /><form method="post" action="${statusUrl}"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="${a.active?'accounting.deactivate':'coa.reactivate'}" /></button></form>
                    </c:if></sec:authorize>
                </div></td>
            </tr></c:forEach><c:if test="${empty accounts.rows}"><tr><td colspan="8"><spring:message code="coa.empty" /></td></tr></c:if></tbody>
        </table></div>
        <div class="erp-table-footer accounting-actions">
            <c:if test="${accounts.page>0}"><c:url value="/finance/accounts" var="previous"><c:param name="page" value="${accounts.page-1}" /><c:param name="search" value="${filter.search}" /><c:param name="type" value="${filter.type}" /><c:param name="kind" value="${filter.kind}" /><c:param name="state" value="${filter.state}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="coa.page" arguments="${accounts.page+1}" /></span>
            <c:if test="${accounts.hasNext}"><c:url value="/finance/accounts" var="next"><c:param name="page" value="${accounts.page+1}" /><c:param name="search" value="${filter.search}" /><c:param name="type" value="${filter.type}" /><c:param name="kind" value="${filter.kind}" /><c:param name="state" value="${filter.state}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
