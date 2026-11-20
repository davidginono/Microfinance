<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="branchSection" value="statements" />
<c:set var="branchDetailLabel" value="fs.details" />
<%@ include file="branch-path.jspf" %>
<section class="erp-table-wrap" data-resource-table="branch-statement" data-aws-table-region data-aws-no-refresh="true">
    <div class="app-table-titlebar">
        <h1 class="erp-panel-title" data-workspace-title><c:out value="${document.title}" /></h1>
        <span><c:out value="${appTime:display(run.from)}" /> – <c:out value="${appTime:display(run.through)}" /> · TZS</span>
    </div>
    <form class="aws-filter-toolbar" method="get" action="<c:url value='/staff/branches/statements/${run.id}' />" data-aws-filter-toolbar data-table-filter-for="branch-statement">
        <input type="hidden" name="branch" value="<c:out value='${branch.id}' />" />
        <label><span class="sr-only"><spring:message code="office.report" /></span><select name="kind" class="aws-control">
            <c:forEach var="item" items="${kinds}"><option value="${item}" ${kind eq item ? 'selected' : ''}><spring:message code="fs.title.${item}" /></option></c:forEach>
        </select></label>
        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.view" /></button>
    </form>
    <div class="erp-table-scroll">
        <table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="financial-statement">
            <thead><tr><c:forEach var="column" items="${document.columns}"><th scope="col"><c:out value="${column.label}" /></th></c:forEach></tr></thead>
            <tbody>
                <c:forEach var="row" items="${document.rows}">
                    <tr class="${row.total or row.heading ? 'font-semibold' : ''}"><c:forEach var="cell" items="${row.cells}"><td><c:choose>
                        <c:when test="${not empty cell.amount}"><fmt:formatNumber value="${cell.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:when>
                        <c:otherwise><c:out value="${cell.text}" /></c:otherwise>
                    </c:choose></td></c:forEach></tr>
                </c:forEach>
                <c:if test="${empty document.rows}"><tr><td colspan="${document.columns.size()}"><spring:message code="branches.empty" /></td></tr></c:if>
            </tbody>
        </table>
    </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
