<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/staff-office.css' />" />
<%@ include file="../fragments/alerts.jspf" %>
<c:set var="office" value="${officeView.office}" />
<spring:message code="${office.labelKey}" var="officeName" />
<nav class="erp-page-path" aria-label="<spring:message code='office.path' />"><a href="<c:url value='/staff/dashboard' />"><spring:message code="menu.dashboard" /></a><span aria-hidden="true">›</span><span><c:out value="${officeName}" /></span><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="office.section.${officeView.section}" /></span></nav>
<c:choose>
    <c:when test="${officeView.section eq 'queue'}">
        <c:url value="${office.base}/queue" var="queueUrl" />
        <section class="erp-table-wrap" data-resource-table="staff-office-queue" data-group-register data-table-selection="single" data-aws-table-region>
            <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="office.queueTitle" arguments="${officeName}" /></h2>
                <div class="accounting-actions" data-table-toolbar-for="staff-office-queue"><button type="button" class="app-btn btn-neutral" data-table-command data-group-command="view" disabled><spring:message code="common.view" /></button><span class="sr-only" data-group-selection-status aria-live="polite"></span></div>
            </div>
            <c:if test="${not empty officeView.sources}">
                <form method="get" action="${queueUrl}" data-table-filter-for="staff-office-queue">
                    <label><span class="sr-only"><spring:message code="office.workType" /></span><select class="aws-control" name="source" aria-label="<spring:message code='office.workType' />"><c:forEach items="${officeView.sources}" var="source"><option value="${source.key}" data-chronological="${source.chronological}" ${source.key eq officeView.source.key ? 'selected' : ''}><spring:message code="${source.labelKey}" /></option></c:forEach></select></label>
                    <label><span class="sr-only"><spring:message code="common.search" /></span><input class="aws-control" type="search" name="search" maxlength="100" value="<c:out value='${search}' />" placeholder="<spring:message code='common.search' />" /></label>
                    <label><span class="sr-only"><spring:message code="voucher.sort" /></span><select class="aws-control" name="sort"><c:forEach items="${['number','number_desc']}" var="order"><option value="${order}" ${sort eq order ? 'selected' : ''}><spring:message code="office.sort.${order}" /></option></c:forEach><c:forEach items="${['newest','oldest']}" var="order"><option value="${order}" ${sort eq order ? 'selected' : ''}><spring:message code="voucher.sort.${order}" /></option></c:forEach></select></label>
                    <button class="app-btn btn-neutral"><spring:message code="voucher.filter" /></button><a class="app-btn btn-neutral" href="${queueUrl}"><spring:message code="voucher.clear" /></a>
                </form>
            </c:if>
            <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><span class="sr-only"><spring:message code="voucher.select" /></span></th><th data-table-sort-asc="number" data-table-sort-desc="number_desc"><spring:message code="office.reference" /></th><th><spring:message code="office.name" /></th><th><spring:message code="voucher.status" /></th><th><spring:message code="voucher.amount" /> (TZS)</th></tr></thead><tbody>
                <c:forEach items="${officeView.work.rows}" var="task" varStatus="i"><tr <c:if test="${not empty task.href}">data-group-row="task-${i.index}" data-target="<c:url value='${task.href}' />"</c:if>>
                    <td><input type="checkbox" ${empty task.href ? 'disabled' : ''} aria-label="<spring:message code='voucher.select' /> <c:out value='${task.number}' />" /></td>
                    <td data-group-identity><c:choose><c:when test="${not empty task.href}"><a class="resource-table-id-link" href="<c:url value='${task.href}' />"><c:out value="${task.number}" /></a></c:when><c:otherwise><c:out value="${task.number}" /></c:otherwise></c:choose></td>
                    <td><c:out value="${task.name}" /></td><td><span data-resource-status="${task.stateKey}"><spring:message code="${task.stateKey}" /></span></td><td class="office-amount"><c:choose><c:when test="${not empty task.amount}"><fmt:formatNumber value="${task.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise>—</c:otherwise></c:choose></td>
                </tr></c:forEach>
                <c:if test="${empty officeView.work.rows}"><tr><td colspan="5"><spring:message code="${empty officeView.sources ? 'office.noWorkAssigned' : not empty search ? 'credit.noMatches' : officeView.source.key eq 'loan-reviews' ? 'office.noLoanReviews' : 'office.noWork'}" /></td></tr></c:if>
            </tbody></table></div>
            <div data-table-pagination data-page="${officeView.work.page}" data-table-for="staff-office-queue">
                <c:forEach items="${[-1,1]}" var="step"><c:if test="${step eq -1 ? officeView.work.page gt 0 : officeView.work.hasNext}"><c:url value="${office.base}/queue" var="pageUrl"><c:param name="page" value="${officeView.work.page+step}" /><c:param name="source" value="${officeView.source.key}" /><c:param name="search" value="${search}" /><c:param name="sort" value="${sort}" /></c:url><a data-page-direction="${step eq -1 ? 'previous' : 'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${step eq -1 ? 'common.previous' : 'common.next'}" /></a></c:if></c:forEach>
            </div>
        </section>
        <script src="<c:url value='/js/group-lending.js' />" defer></script>
        <script src="<c:url value='/js/staff-office.js' />" defer></script>
    </c:when>
    <c:otherwise>
        <section class="erp-table-wrap" data-resource-table="staff-office-reports" data-group-register data-table-selection="single" data-aws-table-region>
            <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="office.section.reports" /></h2><div class="accounting-actions"><button type="button" class="app-btn btn-neutral" data-table-command data-group-command="view" disabled><spring:message code="common.view" /></button><span class="sr-only" data-group-selection-status aria-live="polite"></span></div></div>
            <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="local"><thead><tr><th><span class="sr-only"><spring:message code="voucher.select" /></span></th><th><spring:message code="office.report" /></th></tr></thead><tbody>
                <c:forEach items="${officeView.reports}" var="report" varStatus="i"><tr data-group-row="report-${i.index}" data-target="<c:url value='${report.href}' />"><td><input type="checkbox" aria-label="<spring:message code='voucher.select' /> <spring:message code='${report.labelKey}' />" /></td><td data-group-identity><a class="resource-table-id-link" href="<c:url value='${report.href}' />"><spring:message code="${report.labelKey}" /></a></td></tr></c:forEach>
                <c:if test="${empty officeView.reports}"><tr><td colspan="2"><spring:message code="office.noReports" /></td></tr></c:if>
            </tbody></table></div>
        </section>
        <script src="<c:url value='/js/group-lending.js' />" defer></script>
    </c:otherwise>
</c:choose>
<%@ include file="../fragments/footer.jspf" %>
