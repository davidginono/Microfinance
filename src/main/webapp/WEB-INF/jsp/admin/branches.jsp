<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:if test="${branchSaved}"><div data-workspace-flashbar><div class="console-message" data-message-type="success" data-app-toast role="status"><spring:message code="branches.saved" /><button type="button" class="erp-icon-btn" data-feedback-dismiss aria-label="<spring:message code='common.close' />">&#215;</button></div></div></c:if>
<section class="erp-table-wrap" data-resource-table="branch-register" data-aws-table-region>
    <div class="app-table-titlebar">
        <h1 class="erp-panel-title" data-workspace-title><spring:message code="branches.title" /></h1>
        <c:if test="${register.canCreate}"><a class="app-btn btn-launch" href="<c:url value='/admin/branches/new' />"><spring:message code="branches.create" /></a></c:if>
    </div>
    <form method="get" action="<c:url value='/admin/branches' />" data-table-filter-for="branch-register"><input type="hidden" name="sort" value="<c:out value='${sort}' />" /></form>
    <div class="erp-table-scroll">
        <table class="erp-table">
            <thead><tr><th data-table-sort-asc="id_asc" data-table-sort-desc="id_desc"><spring:message code="branches.id" /></th><th data-table-sort-asc="location_asc" data-table-sort-desc="location_desc"><spring:message code="branches.location" /></th><th data-table-sort-asc="status_asc" data-table-sort-desc="status_desc"><spring:message code="common.status" /></th></tr></thead>
            <tbody>
                <c:forEach var="branch" items="${register.rows.content}">
                    <c:url value="/admin/branches/edit" var="editUrl"><c:param name="branch" value="${branch.id}" /></c:url>
                    <tr>
                        <td><sec:authorize access="@access.has(principal,'BRANCHES_UPDATE')" var="canEditBranch" />
                            <c:choose><c:when test="${canEditBranch}"><a class="resource-table-id-link" href="<c:out value='${editUrl}' />"><c:out value="${branch.id}" /></a></c:when>
                            <c:otherwise><c:out value="${branch.id}" /></c:otherwise></c:choose>
                        </td>
                        <td><c:out value="${branch.location}" /></td>
                        <td><span data-resource-status="${branch.suspended ? 'STOPPED' : branch.active ? 'ACTIVE' : 'INACTIVE'}"><spring:message code="${branch.suspended ? 'branches.suspended' : branch.active ? 'branches.active' : 'branches.inactive'}" /></span></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty register.rows.content}"><tr><td colspan="3"><spring:message code="branches.empty" /></td></tr></c:if>
            </tbody>
        </table>
    </div>
    <div data-table-pagination data-table-for="branch-register" data-page="${register.rows.number}">
        <c:forEach var="step" items="${[-1,1]}">
            <c:if test="${step eq -1 ? not register.rows.first : not register.rows.last}">
                <c:url value="/admin/branches" var="pageUrl"><c:param name="page" value="${register.rows.number+step}" /><c:param name="sort" value="${sort}" /></c:url>
                <a data-page-direction="${step eq -1 ? 'previous' : 'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a>
            </c:if>
        </c:forEach>
    </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
