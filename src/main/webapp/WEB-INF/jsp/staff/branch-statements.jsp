<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="branch-path.jspf" %>
<section class="erp-table-wrap" data-resource-table="branch-statements" data-aws-table-region>
    <div class="app-table-titlebar"><h1 class="erp-panel-title"><spring:message code="branches.section.statements" /></h1></div>
    <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="financial-statement"><thead><tr><th><spring:message code="fs.from" /></th><th><spring:message code="fs.through" /></th></tr></thead><tbody>
        <c:forEach var="run" items="${runs}">
            <c:url value="/staff/branches/statements/${run.id}" var="previewUrl"><c:param name="branch" value="${branch.id}" /></c:url>
            <tr><td><c:out value="${run.from}" /></td><td><a class="resource-table-id-link" href="<c:out value='${previewUrl}' />"><c:out value="${run.through}" /></a></td></tr>
        </c:forEach>
        <c:if test="${empty runs}"><tr><td colspan="2"><spring:message code="branches.empty" /></td></tr></c:if>
    </tbody></table></div>
    <div data-table-pagination data-table-for="branch-statements" data-page="${page}">
        <c:forEach var="step" items="${[-1,1]}"><c:if test="${step eq -1 ? page gt 0 : hasNext}">
            <c:url value="/staff/branches/statements" var="pageUrl"><c:param name="branch" value="${branch.id}" /><c:param name="page" value="${page+step}" /></c:url>
            <a data-page-direction="${step eq -1 ? 'previous' : 'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a>
        </c:if></c:forEach>
    </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
