<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:url value="/staff/offices/loan-review-archive" var="archiveUrl" />
<section class="erp-table-wrap" data-resource-table="staff-loan-review-archive" data-aws-table-region>
    <div class="app-table-titlebar">
        <h1 class="erp-panel-title"><spring:message code="office.archive" /></h1>
        <p class="erp-table-total"><spring:message code="office.archive.reviewed" /> <fmt:formatNumber value="${archive.count}" /></p>
    </div>
    <form method="get" action="${archiveUrl}" data-table-filter-for="staff-loan-review-archive">
        <label><span class="sr-only"><spring:message code="common.search" /></span><input type="search" class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" placeholder="<spring:message code='common.search' />" /></label>
        <input type="hidden" name="sort" value="<c:out value='${sort}' />" />
        <button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button>
        <a class="app-btn btn-neutral" href="${archiveUrl}"><spring:message code="voucher.clear" /></a>
    </form>
    <div class="erp-table-scroll">
        <table class="erp-table">
            <thead><tr>
                <th data-table-sort-asc="number" data-table-sort-desc="number_desc"><spring:message code="loan.loanId" /></th>
                <th data-table-sort-asc="client" data-table-sort-desc="client_desc"><spring:message code="recording.client" /></th>
                <th data-table-sort-asc="oldest" data-table-sort-desc="newest"><spring:message code="repayment.disbursed" /></th>
                <th data-table-sort-asc="status" data-table-sort-desc="status_desc"><spring:message code="voucher.status" /></th>
            </tr></thead>
            <tbody>
                <c:forEach var="loan" items="${archive.rows}">
                    <tr>
                        <td><a class="resource-table-id-link" href="<c:url value='/finance/loan-records/${loan.id}' />"><c:out value="${loan.number}" /></a></td>
                        <td><c:out value="${loan.client}" /></td>
                        <td><c:out value="${loan.disbursed}" /></td>
                        <td><spring:message code="recording.status.${loan.status}" /></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty archive.rows}"><tr><td colspan="4"><spring:message code="${empty search ? 'office.archive.empty' : 'credit.noMatches'}" /></td></tr></c:if>
            </tbody>
        </table>
    </div>
    <div data-table-pagination data-table-for="staff-loan-review-archive" data-page="${archive.page}">
        <c:forEach var="step" items="${[-1,1]}">
            <c:if test="${step eq -1 ? archive.page gt 0 : archive.hasNext}">
                <c:url value="${archiveUrl}" var="pageUrl"><c:param name="page" value="${archive.page + step}" /><c:param name="search" value="${search}" /><c:param name="sort" value="${sort}" /></c:url>
                <a data-page-direction="${step eq -1 ? 'previous' : 'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a>
            </c:if>
        </c:forEach>
    </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
