<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/operational-reports.css' />" />
<script src="<c:url value='/js/operational-report-designer.js' />" defer></script>
<div class="opreport-workspace" id="opreportDesigner" data-preview-url="<c:url value='/reports/operational/api/preview' />">
    <div hidden><c:forEach items="CASH,BANK,MOBILE_MONEY,PAYMENT,REVERSAL,LOCAL_LEDGER,UNAVAILABLE,DISBURSED,PAR,DEFAULTED,PAID" var="value"><span data-report-value="${value}"><spring:message code="opreport.value.${value}" /></span></c:forEach><c:forEach items="${datasetSpec.fields}" var="field"><span data-report-label="${field.id}"><spring:message code="${field.messageKey}" /><c:if test="${field.format eq 'MONEY'}"> (TZS)</c:if></span></c:forEach></div>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="opreport.designer" /></h1></div>
    <c:if test="${not empty reportError}"><p class="opreport-error" role="alert"><spring:message code="${reportError}" /></p></c:if>
    <p class="opreport-muted"><spring:message code="opreport.scope" /></p>
    <c:if test="${not empty template}">
        <div class="opreport-flex"><strong><c:out value="${template.name}" /></strong><span><spring:message code="opreport.version" /> <c:out value="${template.reportVersion}" /></span><span><spring:message code="opreport.state.${template.state}" /></span>
        <sec:authorize access="@access.has(principal,'REPORT_TEMPLATES_CREATE')"><form method="post" action="<c:url value='/reports/operational/${template.id}/clone' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="opreport.newVersion" /></button></form></sec:authorize>
        <c:if test="${template.state eq 'DRAFT'}"><sec:authorize access="@access.has(principal,'REPORT_TEMPLATES_PUBLISH')"><form method="post" action="<c:url value='/reports/operational/${template.id}/publish' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="expectedVersion" value="${template.lockVersion}" /><button class="app-btn btn-primary"><spring:message code="opreport.publish" /></button></form></sec:authorize></c:if>
        <c:if test="${template.state eq 'PUBLISHED'}"><sec:authorize access="@access.has(principal,'REPORTS_RUN')"><a class="app-btn btn-neutral" href="<c:url value='/reports/operational/${template.id}/run' />"><spring:message code="opreport.run" /></a></sec:authorize>
        <sec:authorize access="@access.has(principal,'REPORT_TEMPLATES_PUBLISH')"><form method="post" action="<c:url value='/reports/operational/${template.id}/retire' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="expectedVersion" value="${template.lockVersion}" /><button class="app-btn btn-neutral"><spring:message code="opreport.retire" /></button></form></sec:authorize></c:if></div>
    </c:if>
    <c:if test="${not empty template}"><div class="opreport-flex"><span><spring:message code="opreport.visibility.${template.visibility}" /></span><sec:authorize access="@access.has(principal,'REPORT_TEMPLATES_SHARE')"><form method="post" action="<c:url value='/reports/operational/${template.id}/share' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="expectedVersion" value="${template.lockVersion}" /><input type="hidden" name="institutionVisible" value="${template.visibility eq 'PRIVATE'}" /><button class="app-btn btn-neutral"><spring:message code="${template.visibility eq 'PRIVATE' ? 'opreport.share' : 'opreport.unshare'}" /></button></form></sec:authorize></div></c:if>
    <section class="opreport-panel"><h2><spring:message code="${datasetSpec.messageKey}" /></h2>
        <p><spring:message code="opreport.coverage.${definition.dataset}" /></p>
        <details><summary><spring:message code="opreport.lineage" /></summary><p><c:out value="${datasetSpec.source}" /></p><p><spring:message code="opreport.semantics.${definition.dataset}" /></p><p><spring:message code="opreport.catalogVersion" /> <c:out value="${datasetSpec.metricVersion}" /></p></details>
    </section>
    <form id="reportDefinitionForm" method="post" action="<c:url value='/reports/operational/draft' />" class="opreport-panel">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" data-csrf-header="${_csrf.headerName}" id="opreportCsrf" />
        <input type="hidden" name="id" value="<c:out value='${designerForm.id}' />" /><input type="hidden" name="expectedVersion" value="<c:out value='${designerForm.expectedVersion}' />" />
        <textarea hidden name="definitionJson" id="reportDefinitionJson"><c:out value="${designerForm.definitionJson}" /></textarea>
        <fieldset ${template.state eq 'PUBLISHED' or template.state eq 'RETIRED' ? 'disabled' : ''}>
        <div class="opreport-grid">
            <label><spring:message code="opreport.name" /><input class="aws-control" name="name" id="reportName" required maxlength="120" value="<c:out value='${designerForm.name}' />" /></label>
            <label><spring:message code="opreport.reportTitle" /><input class="aws-control" id="reportTitle" maxlength="120" /></label>
            <c:choose><c:when test="${definition.dataset eq 'LOAN_PORTFOLIO'}"><input type="hidden" id="reportFrom" /></c:when><c:otherwise><label><spring:message code="opreport.from" /><input class="aws-control" type="date" id="reportFrom" required /></label></c:otherwise></c:choose>
            <label><spring:message code="opreport.to" /><input class="aws-control" type="date" id="reportTo" required /></label>
            <label><spring:message code="loan.loanId" /><input class="aws-control" id="reportLoan" maxlength="20" pattern="[0-9]{4,20}" /></label>
            <c:if test="${definition.dataset eq 'COLLECTIONS'}"><label><spring:message code="opreport.field.CHANNEL" /><select class="aws-control" id="reportChannel"><option value=""><spring:message code="opreport.all" /></option><option value="CASH"><spring:message code="opreport.value.CASH" /></option><option value="BANK"><spring:message code="opreport.value.BANK" /></option><option value="MOBILE_MONEY"><spring:message code="opreport.value.MOBILE_MONEY" /></option></select></label></c:if>
            <c:if test="${definition.dataset eq 'LOAN_PORTFOLIO'}"><label><spring:message code="common.status" /><select class="aws-control" id="reportStatus"><option value=""><spring:message code="opreport.all" /></option><c:forEach items="DISBURSED,PAR,DEFAULTED,PAID" var="status"><option value="${status}"><spring:message code="opreport.value.${status}" /></option></c:forEach></select></label></c:if>
        </div>
        <h2><spring:message code="opreport.columns" /></h2><p class="opreport-muted"><spring:message code="opreport.columnHelp" /></p>
        <ol id="reportColumns" class="opreport-columns"><c:forEach items="${datasetSpec.fields}" var="field"><li data-field="${field.id}" data-format="${field.format}">
            <label class="opreport-column-choice"><input type="checkbox" data-column-selected /><spring:message code="${field.messageKey}" /><c:if test="${field.format eq 'MONEY'}"> (TZS)</c:if></label>
            <label><span class="opreport-small"><spring:message code="opreport.extraLabel" /></span><input class="aws-control" data-column-label maxlength="60" /></label>
            <label><span class="opreport-small"><spring:message code="opreport.width" /></span><input class="aws-control" data-column-width type="number" min="70" max="400" value="140" /></label>
            <span class="opreport-flex"><button type="button" class="erp-icon-btn" data-move="up" aria-label="<spring:message code='opreport.moveUp' />" title="<spring:message code='opreport.moveUp' />">↑</button><button type="button" class="erp-icon-btn" data-move="down" aria-label="<spring:message code='opreport.moveDown' />" title="<spring:message code='opreport.moveDown' />">↓</button></span>
        </li></c:forEach></ol>
        <div class="opreport-grid">
            <label><spring:message code="opreport.sort" /><select class="aws-control" id="reportSort"><c:forEach items="${datasetSpec.fields}" var="field"><option value="${field.id}"><spring:message code="${field.messageKey}" /></option></c:forEach></select></label>
            <label><spring:message code="opreport.direction" /><select class="aws-control" id="reportDirection"><option value="ASC"><spring:message code="opreport.ascending" /></option><option value="DESC"><spring:message code="opreport.descending" /></option></select></label>
            <label><spring:message code="opreport.language" /><select class="aws-control" id="reportLanguage"><option value="EN">English</option><option value="SW">Kiswahili</option></select></label>
            <label><spring:message code="opreport.orientation" /><select class="aws-control" id="reportOrientation"><option value="PORTRAIT"><spring:message code="opreport.portrait" /></option><option value="LANDSCAPE"><spring:message code="opreport.landscape" /></option></select></label>
            <label><spring:message code="opreport.paper" /><select class="aws-control" id="reportPaper"><option value="A4">A4</option><option value="LETTER">Letter</option></select></label>
            <label><spring:message code="opreport.footer" /><input class="aws-control" id="reportFooter" maxlength="300" /></label>
        </div>
        <fieldset><legend><spring:message code="opreport.groups" /></legend><div class="opreport-flex"><c:forEach items="${datasetSpec.fields}" var="field"><c:if test="${field.groupable}"><label><input type="checkbox" data-group="${field.id}" /><spring:message code="${field.messageKey}" /></label></c:if></c:forEach></div></fieldset>
        <fieldset><legend><spring:message code="opreport.totals" /></legend><div class="opreport-flex"><c:forEach items="${datasetSpec.fields}" var="field"><c:if test="${field.summable}"><label><input type="checkbox" data-total="${field.id}" /><spring:message code="${field.messageKey}" /> (TZS)</label></c:if></c:forEach></div></fieldset>
        <label><input type="checkbox" id="reportBranding" /><spring:message code="opreport.branding" /></label>
        <div class="opreport-flex opreport-actions"><sec:authorize access="@access.hasAny(principal,'REPORT_TEMPLATES_CREATE','REPORT_TEMPLATES_UPDATE')"><button class="app-btn btn-primary" type="submit"><spring:message code="opreport.saveDraft" /></button></sec:authorize></div>
        </fieldset>
    </form>
    <section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="opreport.preview" /></h2><sec:authorize access="@access.has(principal,'REPORTS_RUN')"><button type="button" class="app-btn btn-neutral" id="reportPreview"><spring:message code="opreport.preview" /></button></sec:authorize></div>
        <p id="reportPreviewFeedback" role="status" aria-live="polite" class="opreport-muted" data-loading="<spring:message code='opreport.loading' />" data-failure="<spring:message code='opreport.error.definition' />" data-empty="<spring:message code='opreport.emptyRows' />" data-unavailable="<spring:message code='opreport.unavailable' />" data-count="<spring:message code='opreport.filteredRows' />"><spring:message code="opreport.previewHelp" /></p>
        <div class="erp-table-scroll" data-view-position-key="operational-report-preview"><table class="erp-table" id="reportPreviewTable"></table></div>
        <div id="reportPreviewTotals" class="opreport-flex opreport-pagination"></div>
        <div class="erp-table-scroll erp-table-scroll-sm" id="reportPreviewGroups" hidden></div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
