<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="branchSection" value="${section}" />
<%@ include file="branch-path.jspf" %>
<c:url value="/staff/branches/${section}" var="registryUrl" />
<c:set var="clientList" value="${section eq 'clients'}" />
<section class="erp-table-wrap" data-resource-table="other-branch-records" data-aws-table-region>
    <div class="app-table-titlebar"><h1 class="erp-panel-title"><spring:message code="branches.section.${section}" /></h1></div>
    <form method="get" action="${registryUrl}" class="aws-filter-toolbar" data-aws-filter-toolbar data-table-filter-for="other-branch-records">
        <input type="hidden" name="branch" value="<c:out value='${branch.id}' />" />
        <input type="hidden" name="sort" value="<c:out value='${sort}' />" />
        <label><span class="sr-only"><spring:message code="common.search" /></span><input name="search" type="search" class="aws-control" value="<c:out value='${search}' />" maxlength="100" placeholder="<spring:message code='common.search' />" /></label>
        <button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button>
        <c:url value="${registryUrl}" var="clearUrl"><c:param name="branch" value="${branch.id}" /></c:url>
        <a class="app-btn btn-neutral" href="<c:out value='${clearUrl}' />"><spring:message code="voucher.clear" /></a>
    </form>
    <div class="erp-table-scroll"><table class="erp-table">
        <thead><tr>
            <th data-table-sort-asc="number_asc" data-table-sort-desc="number_desc"><spring:message code="${clientList ? 'branches.clientNumber' : 'loan.loanId'}" /></th>
            <th data-table-sort-asc="name_asc" data-table-sort-desc="name_desc"><spring:message code="recording.client" /></th>
            <th data-table-sort-asc="status_asc" data-table-sort-desc="status_desc"><spring:message code="common.status" /></th>
            <c:if test="${not clientList}"><th data-table-sort-asc="amount_asc" data-table-sort-desc="amount_desc"><spring:message code="recording.principal" /> (TZS)</th></c:if>
        </tr></thead>
        <tbody>
            <c:forEach var="row" items="${rows}">
                <c:url value="/staff/branches/${section}/${row.id}" var="recordUrl"><c:param name="branch" value="${branch.id}" /></c:url>
                <tr>
                    <td><a class="resource-table-id-link" href="<c:out value='${recordUrl}' />"><c:out value="${row.number}" /></a></td>
                    <td><c:out value="${clientList ? row.name : row.client}" /></td>
                    <td><span data-resource-status="${clientList ? row.state : row.status}"><spring:message code="${clientList ? 'kyc.state.'.concat(row.state) : 'recording.status.'.concat(row.status)}" text="${clientList ? row.state : row.status}" /></span></td>
                    <c:if test="${not clientList}"><td><fmt:formatNumber value="${row.principal}" minFractionDigits="2" maxFractionDigits="2" /></td></c:if>
                </tr>
            </c:forEach>
            <c:if test="${empty rows}"><tr><td colspan="${clientList ? 3 : 4}"><spring:message code="${empty search ? 'branches.empty' : 'credit.noMatches'}" /></td></tr></c:if>
        </tbody>
    </table></div>
    <div data-table-pagination data-table-for="other-branch-records" data-page="${page}">
        <c:forEach var="step" items="${[-1,1]}"><c:if test="${step eq -1 ? page gt 0 : hasNext}">
            <c:url value="${registryUrl}" var="pageUrl"><c:param name="branch" value="${branch.id}" /><c:param name="search" value="${search}" /><c:param name="sort" value="${sort}" /><c:param name="page" value="${page+step}" /></c:url>
            <a data-page-direction="${step eq -1 ? 'previous' : 'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a>
        </c:if></c:forEach>
    </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
