<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="branch-path.jspf" %>
<div class="accounting-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${document.title}" /></h1></div>
    <section class="erp-resource-section"><h2><spring:message code="branches.section.statements" /></h2><div class="erp-panel-body">
        <form class="voucher-filters" method="get" action="<c:url value='/staff/branches/statements/${run.id}' />">
            <input type="hidden" name="branch" value="<c:out value='${branch.id}' />" />
            <label><spring:message code="office.report" /><select name="kind" class="aws-control">
                <c:forEach var="item" items="${kinds}"><option value="${item}" ${kind eq item ? 'selected' : ''}><spring:message code="fs.title.${item}" /></option></c:forEach>
            </select></label>
            <button class="app-btn btn-neutral"><spring:message code="common.view" /></button>
        </form>
        <p><c:out value="${run.from}" /> – <c:out value="${run.through}" /> · TZS</p>
        <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="financial-statement"><thead><tr>
            <c:forEach var="column" items="${document.columns}"><th><c:out value="${column.label}" /></th></c:forEach>
        </tr></thead><tbody>
            <c:forEach var="row" items="${document.rows}"><tr>
                <c:forEach var="cell" items="${row.cells}"><td><c:choose><c:when test="${not empty cell.amount}"><fmt:formatNumber value="${cell.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><c:out value="${cell.text}" /></c:otherwise></c:choose></td></c:forEach>
            </tr></c:forEach>
        </tbody></table></div>
    </div></section>
</div>
<%@ include file="../fragments/footer.jspf" %>
