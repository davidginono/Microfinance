<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/operational-reports.css' />" />
<fmt:setLocale value="${result.definition.language eq 'SW' ? 'sw' : 'en'}" scope="page" />
<div class="opreport-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${empty result.definition.title ? result.labels.title : result.definition.title}" /></h1></div>
    <p><c:out value="${result.labels.version}" /> <c:out value="${result.templateVersion}" /> · <c:out value="${result.definition.dateFrom}" /> — <c:out value="${result.definition.dateTo}" /> · <c:out value="${result.labels.cutoff}" /> <c:out value="${result.cutoff}" /></p>
    <c:if test="${result.definition.showInstitutionBranding}"><div class="opreport-flex"><c:if test="${not empty activeSaccoLogoUrl}"><img class="opreport-logo" src="<c:out value='${activeSaccoLogoUrl}' />" alt="<c:out value='${activesaccoName}' />" /></c:if><p><c:out value="${activesaccoName}" /></p></div></c:if>
    <p class="opreport-muted"><c:out value="${result.labels.coverage}" /> <c:out value="${result.labels.tracked}" />: <c:out value="${result.coverage.trackedLoans}" />; <c:out value="${result.labels.untracked}" />: <c:out value="${result.coverage.untrackedLoans}" />; <c:out value="${result.labels.unknownDates}" />: <c:out value="${result.coverage.unknownDateLoans}" /></p>
    <section class="erp-table-wrap"><div class="app-table-titlebar"><h2><c:out value="${result.labels.filteredRows}" /></h2><span class="app-table-count">(<c:out value="${result.rowCount}" />)</span></div>
        <div class="erp-table-scroll" data-view-position-key="operational-report-run"><table class="erp-table"><thead><tr><c:forEach items="${result.columns}" var="column"><th style="min-width:${column.width}px" class="${column.format eq 'MONEY' ? 'money' : ''}"><c:out value="${column.label}" /></th></c:forEach></tr></thead>
        <tbody><c:forEach items="${result.rows}" var="row"><tr><c:forEach items="${result.columns}" var="column"><td class="${column.format eq 'MONEY' ? 'money' : ''}"><c:choose>
            <c:when test="${row[column.key] eq null}"><c:out value="${result.labels.unavailable}" /></c:when>
            <c:when test="${column.format eq 'MONEY'}"><fmt:formatNumber value="${row[column.key]}" minFractionDigits="2" maxFractionDigits="2" /></c:when>
            <c:when test="${column.key eq 'status' or column.key eq 'channel' or column.key eq 'kind' or column.key eq 'coverage'}"><c:out value="${result.labels['value.'.concat(row[column.key])]}" default="${row[column.key]}" /></c:when>
            <c:otherwise><c:out value="${row[column.key]}" /></c:otherwise>
        </c:choose></td></c:forEach></tr></c:forEach><c:if test="${empty result.rows}"><tr><td colspan="${result.columns.size()}" class="erp-table-empty"><spring:message code="opreport.emptyRows" /></td></tr></c:if></tbody></table></div>
        <div class="opreport-flex opreport-pagination">
            <c:if test="${result.hasPrevious}"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational/${result.templateId}/run'><c:param name='page' value='${result.page-1}' /></c:url>"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${result.page+1}" /></span>
            <c:if test="${result.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational/${result.templateId}/run'><c:param name='page' value='${result.page+1}' /></c:url>"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
    <section class="opreport-panel"><h2><c:out value="${result.labels.totals}" /></h2><p class="opreport-muted"><c:out value="${result.labels.fullTotals}" /></p><div class="opreport-flex">
        <c:forEach items="${result.totals}" var="total"><div><strong><c:out value="${result.labels['field.'.concat(total.key)]}" />: </strong><c:choose><c:when test="${total.value.value eq null}"><c:out value="${result.labels.unavailable}" /></c:when><c:otherwise><fmt:formatNumber value="${total.value.value}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose><span> · <c:out value="${result.labels.missingRows}" />: <c:out value="${total.value.unavailableRows}" /></span></div></c:forEach>
    </div></section>
    <c:if test="${not empty result.groups}"><section class="erp-table-wrap"><div class="app-table-titlebar"><h2><c:out value="${result.labels.groups}" /></h2></div><div class="erp-table-scroll"><table class="erp-table"><thead><tr><c:forEach items="${result.groups[0]}" var="cell"><th><c:choose>
        <c:when test="${cell.key eq 'row_count'}"><c:out value="${result.labels.filteredRows}" /></c:when>
        <c:when test="${cell.key.endsWith('_unavailable')}"><c:out value="${result.labels['field.'.concat(cell.key.substring(0,cell.key.length()-12))]}" /> · <c:out value="${result.labels.missingRows}" /></c:when>
        <c:otherwise><c:out value="${result.labels['field.'.concat(cell.key)]}" /></c:otherwise>
    </c:choose></th></c:forEach></tr></thead><tbody><c:forEach items="${result.groups}" var="group"><tr><c:forEach items="${group}" var="cell"><td><c:choose>
        <c:when test="${cell.value eq null}"><c:out value="${result.labels.unavailable}" /></c:when>
        <c:when test="${cell.key eq 'channel' or cell.key eq 'kind' or cell.key eq 'status' or cell.key eq 'coverage'}"><c:out value="${result.labels['value.'.concat(cell.value)]}" default="${cell.value}" /></c:when>
        <c:when test="${not empty result.totals[cell.key]}"><fmt:formatNumber value="${cell.value}" minFractionDigits="2" maxFractionDigits="2" /></c:when>
        <c:otherwise><c:out value="${cell.value}" /></c:otherwise>
    </c:choose></td></c:forEach></tr></c:forEach></tbody></table></div></section></c:if>
    <p><c:out value="${result.definition.footer}" /></p>
</div>
<%@ include file="../../fragments/footer.jspf" %>
