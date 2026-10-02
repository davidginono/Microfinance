<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.policy.title" /></h1></div>
    <p><spring:message code="accounting.policy.gate" /></p>
    <section class="erp-table-wrap">
        <div class="app-table-titlebar"><h2><spring:message code="accounting.policy.versions" /></h2>
            <sec:authorize access="@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')"><a class="app-btn btn-primary" href="<c:url value='/finance/policies/new' />"><spring:message code="accounting.policy.propose" /></a></sec:authorize>
        </div>
        <div class="erp-table-scroll" data-view-position-key="accounting-policies"><table class="erp-table">
            <thead><tr><th><spring:message code="accounting.policy.version" /></th><th><spring:message code="accounting.policy.effective" /></th><th><spring:message code="accounting.policy.books" /></th><th><spring:message code="accounting.policy.status" /></th><th><spring:message code="common.actions" /></th></tr></thead>
            <tbody><c:forEach items="${policies.content}" var="row"><tr>
                <td><c:out value="${row.version}" /></td><td><c:out value="${row.effectiveFrom}" /></td>
                <td><spring:message code="accounting.policy.books.${row.authoritativeLedger}" /></td>
                <td><c:choose><c:when test="${empty row.approvalDecision}"><spring:message code="accounting.policy.pending" /></c:when><c:otherwise><spring:message code="accounting.policy.status.${row.approvalDecision}" /></c:otherwise></c:choose></td>
                <td><a class="app-btn btn-neutral" href="<c:url value='/finance/policies/${row.id}' />"><spring:message code="common.view" /></a></td>
            </tr></c:forEach><c:if test="${empty policies.content}"><tr><td colspan="5" class="erp-table-empty"><spring:message code="accounting.policy.empty" /></td></tr></c:if></tbody>
        </table></div>
        <div class="policy-actions"><c:if test="${policies.hasPrevious()}"><a class="app-btn btn-neutral" href="<c:url value='/finance/policies?page=${policies.number - 1}' />"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${policies.number + 1}" /></span>
            <c:if test="${policies.hasNext()}"><a class="app-btn btn-neutral" href="<c:url value='/finance/policies?page=${policies.number + 1}' />"><spring:message code="common.next" /></a></c:if></div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
