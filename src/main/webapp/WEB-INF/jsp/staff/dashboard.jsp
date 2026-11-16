<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/dashboard-support.jspf" %>
<nav class="erp-page-path" aria-label="<spring:message code='office.path' />"><span aria-current="page"><spring:message code="menu.dashboard" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="menu.dashboard" /></h1></div>
<c:choose><c:when test="${empty personalDashboard.activities and empty personalDashboard.administration}">
    <section class="erp-panel"><div class="erp-panel-body"><p><spring:message code="office.noWorkAssigned" /></p></div></section>
</c:when><c:otherwise>
<div data-dashboard-board="personal" aria-busy="true">
    <c:forEach items="${personalDashboard.activities}" var="activity">
        <section class="erp-panel" data-dashboard-widget="activity-${activity.key}" data-widget-width="2" data-widget-height="320" data-personal-activity="${activity.key}" data-activity-url="<c:url value='/api/dashboard/activities/${activity.key}' />">
            <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="${activity.labelKey}" /></h2></div>
            <div class="erp-panel-body">
                <dl class="dashboard-data-metrics">
                    <c:forEach items="${activity.destinations}" var="destination">
                        <div><dt class="${activity.destinations.size() eq 1 ? 'sr-only' : ''}"><c:choose><c:when test="${activity.destinations.size() gt 1}"><spring:message code="${destination.labelKey}" /></c:when><c:otherwise><spring:message code="office.items" /></c:otherwise></c:choose></dt>
                            <dd><a class="resource-table-id-link" href="<c:url value='${destination.href}' />" title="<spring:message code='office.summaryLimit' />"><span class="sr-only"><spring:message code="${activity.labelKey}" />: <spring:message code="${destination.labelKey}" /> </span><span data-activity-count="${destination.office}" aria-live="polite" aria-atomic="true">—</span></a></dd></div>
                    </c:forEach>
                </dl>
                <p class="dashboard-data-status" data-activity-status role="status"></p>
            </div>
        </section>
    </c:forEach>
    <c:if test="${not empty personalDashboard.administration}">
        <%@ include file="../fragments/dashboard-voucher-shortcuts.jspf" %>
        <%@ include file="../fragments/admin-dashboard-widgets.jspf" %>
    </c:if>
</div>
<script src="<c:url value='/js/personal-dashboard.js' />" defer></script>
</c:otherwise></c:choose>
<%@ include file="../fragments/footer.jspf" %>
