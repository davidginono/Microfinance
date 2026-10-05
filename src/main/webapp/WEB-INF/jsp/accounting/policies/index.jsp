<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
    <div class="erp-page-header policy-heading" data-aws-page-header>
        <h1 class="erp-page-title"><spring:message code="policy.title" /></h1>
        <sec:authorize access="@access.has(principal,'ACCOUNTING_POLICY_CREATE')"><a class="app-btn btn-primary" href="<c:url value='/accounting/policies/new' />"><spring:message code="policy.new" /></a></sec:authorize>
    </div>
    <p class="policy-copy"><spring:message code="policy.scope" /></p>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="policy.versions" /></h2></div>
        <div class="erp-table-scroll"><table class="erp-table">
            <thead><tr><th><spring:message code="policy.version" /></th><th><spring:message code="policy.effectiveFrom" /></th><th><spring:message code="policy.authority" /></th><th><spring:message code="policy.state" /></th><th><spring:message code="common.actions" /></th></tr></thead>
            <tbody><c:forEach items="${policies.content()}" var="row"><tr>
                <td><c:out value="${row.version()}" /></td><td><c:out value="${row.effectiveFrom()}" /></td>
                <td><spring:message code="policy.authority.${row.authority()}" /></td><td><spring:message code="policy.state.${row.state()}" /></td>
                <td><a class="app-btn btn-neutral" href="<c:url value='/accounting/policies/${row.id()}' />"><spring:message code="common.view" /></a></td>
            </tr></c:forEach><c:if test="${empty policies.content()}"><tr><td colspan="5" class="erp-table-empty"><spring:message code="policy.empty" /></td></tr></c:if></tbody>
        </table></div>
        <div class="policy-pagination">
            <c:if test="${policies.hasPrevious()}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/policies?page=${policies.number() - 1}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="policy.page" /> <c:out value="${policies.number() + 1}" /></span>
            <c:if test="${policies.hasNext()}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/policies?page=${policies.number() + 1}' />"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
