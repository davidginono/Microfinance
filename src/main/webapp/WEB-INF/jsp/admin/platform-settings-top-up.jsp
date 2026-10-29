<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<nav class="erp-page-path"><a href="/admin/platform-settings"><spring:message code="platform.settings.title" /></a><span>/</span><span aria-current="page"><spring:message code="platform.settings.loan-top-up.title" /></span></nav>
<section class="erp-table-wrap" data-resource-table="platform-top-up" data-table-selection="single" data-platform-topup-register data-total-count="${topUpAccess.totalElements}">
<div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="platform.settings.loan-top-up.title" /> <span class="app-table-count">(${topUpAccess.totalElements})</span></h2><div class="app-table-toolbar"><sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><button type="button" class="app-btn btn-neutral" data-table-command data-platform-topup-edit disabled><spring:message code="common.edit" /></button></sec:authorize></div></div>
<form action="/admin/platform-settings/loan-top-up" method="get" data-table-filter-for="platform-top-up" class="aws-filter-toolbar"><label><span class="sr-only"><spring:message code="common.search" /></span><input type="search" name="q" value="${fn:escapeXml(query)}" maxlength="120" class="fcms-control" /></label><input type="hidden" name="size" value="${topUpAccess.size}" /><button type="submit" class="app-btn btn-neutral"><spring:message code="common.search" /></button></form>
<span class="sr-only" role="status" data-platform-topup-selection-status data-empty="<spring:message code='platform.settings.noSelection' />"></span>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><span class="sr-only"><spring:message code="shell.control.select" /></span></th><th><spring:message code="common.sacco" /></th><th><spring:message code="platform.settings.loan-top-up.title" /></th></tr></thead><tbody>
<c:forEach items="${topUpAccess.content}" var="institution">
<c:url value="/admin/platform-settings/loan-top-up/${institution.saccoId}/edit" var="topUpEditUrl"><c:param name="q" value="${query}" /><c:param name="page" value="${topUpAccess.number}" /><c:param name="size" value="${topUpAccess.size}" /></c:url>
<tr data-platform-topup-row data-edit-url="${fn:escapeXml(topUpEditUrl)}"><td><input type="checkbox" name="platform-topup-selection" aria-label="${fn:escapeXml(institution.saccoName)}" /></td><td><c:out value="${institution.saccoName}" /></td><td><c:choose><c:when test="${institution.loanTopUpEnabled}"><spring:message code="platform.settings.enabled" /></c:when><c:otherwise><spring:message code="platform.settings.disabled" /></c:otherwise></c:choose></td></tr>
</c:forEach>
<c:if test="${empty topUpAccess.content}"><tr><td colspan="3"><spring:message code="platform.settings.noInstitutions" /></td></tr></c:if>
</tbody></table></div>
<c:if test="${topUpAccess.hasPrevious()}"><c:url value="/admin/platform-settings/loan-top-up" var="topUpPrevious"><c:param name="q" value="${query}" /><c:param name="size" value="${topUpAccess.size}" /><c:param name="page" value="${topUpAccess.number - 1}" /></c:url></c:if>
<c:if test="${topUpAccess.hasNext()}"><c:url value="/admin/platform-settings/loan-top-up" var="topUpNext"><c:param name="q" value="${query}" /><c:param name="size" value="${topUpAccess.size}" /><c:param name="page" value="${topUpAccess.number + 1}" /></c:url></c:if>
<div class="erp-pagination" data-table-pagination data-table-for="platform-top-up" data-page="${topUpAccess.number}" data-pages="${topUpAccess.totalPages}" data-total="${topUpAccess.totalElements}"><c:if test="${not empty topUpPrevious}"><a href="${fn:escapeXml(topUpPrevious)}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if><c:if test="${not empty topUpNext}"><a href="${fn:escapeXml(topUpNext)}" data-page-direction="next"><spring:message code="common.next" /></a></c:if></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
