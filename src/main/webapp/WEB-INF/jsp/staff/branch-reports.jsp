<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="branchSection" value="reports" />
<%@ include file="branch-path.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/management-reports.css' />" />
<div class="accounting-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="branches.section.reports" /></h1></div>
    <section class="erp-resource-section" aria-label="<spring:message code='common.filter' />"><div class="erp-panel-body">
        <form method="get" action="<c:url value='/staff/branches/reports' />" class="aws-filter-toolbar">
            <input type="hidden" name="branch" value="<c:out value='${branch.id}' />" />
            <label><spring:message code="office.report" /><select class="aws-control" name="kind" required>
                <option value=""><spring:message code="branches.chooseReport" /></option>
                <c:forEach var="item" items="${reportKinds}"><option value="${item}" ${kind eq item ? 'selected' : ''}><spring:message code="management.title.${item}" /></option></c:forEach>
            </select></label>
            <label><spring:message code="fs.from" /><input type="date" class="aws-control" name="from" value="${from}" required /></label>
            <label><spring:message code="report.endDate" /><input type="date" class="aws-control" name="through" value="${through}" required /></label>
            <button class="app-btn btn-neutral"><spring:message code="common.view" /></button>
        </form>
    </div></section>
    <c:if test="${not empty report}">
        <section class="erp-table-wrap management-preview">
            <div class="management-canvas ${report.isReferenceLayout() ? 'management-canvas--reference' : ''}" role="region" aria-label="<spring:message code='management.preview' />" tabindex="0">
                <%@ include file="../accounting/management/document.jspf" %>
            </div>
        <nav class="accounting-actions management-pager" aria-label="<spring:message code='branches.pages' />">
            <c:forEach var="step" items="${[-1,1]}"><c:if test="${step eq -1 ? report.parameters.page gt 0 : report.hasNext}">
                <c:url value="/staff/branches/reports" var="pageUrl"><c:param name="branch" value="${branch.id}" /><c:param name="kind" value="${kind}" /><c:param name="from" value="${from}" /><c:param name="through" value="${through}" /><c:param name="cutoff" value="${report.parameters.cutoff}" /><c:param name="page" value="${report.parameters.page+step}" /></c:url>
                <a class="app-btn btn-neutral" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a>
            </c:if></c:forEach>
        </nav>
        </section>
    </c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
