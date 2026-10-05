<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/operational-reports.css' />" />
<div class="opreport-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="opreport.title" /></h1></div>
    <c:if test="${not empty reportError}"><p class="opreport-error" role="alert"><spring:message code="${reportError}" /></p></c:if>
    <p class="opreport-muted"><spring:message code="opreport.scope" /></p>
    <section class="opreport-panel">
        <h2><spring:message code="opreport.systemTemplates" /></h2>
        <div class="opreport-flex">
            <c:forEach items="${catalog}" var="dataset"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational/design'><c:param name='dataset' value='${dataset.id}' /></c:url>"><spring:message code="${dataset.messageKey}" /></a></c:forEach>
        </div>
        <c:if test="${empty catalog}"><p><spring:message code="opreport.noDatasets" /></p></c:if>
    </section>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="opreport.templates" /></h2><span class="app-table-count">(<c:out value="${templates.totalElements}" />)</span></div>
        <div class="erp-table-scroll" data-view-position-key="operational-report-templates"><table class="erp-table">
            <thead><tr><th><spring:message code="opreport.name" /></th><th><spring:message code="opreport.version" /></th><th><spring:message code="common.status" /></th><th><spring:message code="common.actions" /></th></tr></thead>
            <tbody><c:forEach items="${templates.content}" var="template"><tr>
                <td><c:out value="${template.name}" /></td><td><c:out value="${template.reportVersion}" /></td><td><spring:message code="opreport.state.${template.state}" /></td>
                <td><a class="app-btn btn-neutral" href="<c:url value='/reports/operational/design'><c:param name='id' value='${template.id}' /></c:url>"><spring:message code="common.view" /></a></td>
            </tr></c:forEach><c:if test="${empty templates.content}"><tr><td colspan="4" class="erp-table-empty"><spring:message code="opreport.emptyTemplates" /></td></tr></c:if></tbody>
        </table></div>
        <div class="opreport-flex opreport-pagination">
            <c:if test="${templates.hasPrevious()}"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational'><c:param name='page' value='${templates.number-1}' /></c:url>"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${templates.number+1}" /></span>
            <c:if test="${templates.hasNext()}"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational'><c:param name='page' value='${templates.number+1}' /></c:url>"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
