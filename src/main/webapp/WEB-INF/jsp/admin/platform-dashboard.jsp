<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/dashboard-support.jspf" %>
<div class="erp-page-header" data-aws-page-header><p class="erp-breadcrumb"><spring:message code="dashboard.platform.breadcrumb" /></p><h1 class="erp-page-title"><spring:message code="dashboard.platform.title" /></h1></div>
<div data-dashboard-board="platform">
 <section class="erp-panel" data-dashboard-widget="overview" data-widget-width="2" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.overview" /></h2></div>
  <div class="erp-panel-body"><dl class="dashboard-facts">
   <div><dt><spring:message code="dashboard.metric.institutions" /></dt><dd><c:out value="${platformDashboard.totalSaccos}" /></dd></div>
   <div><dt><spring:message code="admin.dashboard.members" /></dt><dd><c:out value="${platformDashboard.totalMembers}" /></dd></div>
   <div><dt><spring:message code="dashboard.metric.disbursedPrincipal" /> (TZS)</dt><dd><c:out value="${platformDashboard.totalDisbursedPrincipalLabel}" /></dd></div>
  </dl></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="institutions" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.institutions" /></h2></div>
  <div class="erp-panel-body"><div data-dashboard-chart data-chart-type="bar" data-chart-label="<spring:message code='dashboard.widget.institutions' />"><table data-chart-source><thead><tr><th><spring:message code="common.status" /></th><th><spring:message code="dashboard.metric.institutions" /></th></tr></thead><tbody>
   <tr><th><spring:message code="dashboard.metric.healthy" /></th><td data-value="${platformDashboard.healthySaccos}"><c:out value="${platformDashboard.healthySaccos}" /></td></tr>
   <tr><th><spring:message code="dashboard.metric.new" /></th><td data-value="${platformDashboard.newSaccos}"><c:out value="${platformDashboard.newSaccos}" /></td></tr>
   <tr><th><spring:message code="dashboard.metric.review" /></th><td data-value="${platformDashboard.atRiskSaccos}"><c:out value="${platformDashboard.atRiskSaccos}" /></td></tr>
  </tbody></table></div></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="operations" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.operations" /></h2></div>
  <div class="erp-panel-body"><dl class="dashboard-facts"><div><dt><spring:message code="admin.dashboard.failedOutboxEvents" /></dt><dd><c:out value="${platformDashboard.failedOutboxCount}" /></dd></div><div><dt><spring:message code="dashboard.metric.review" /></dt><dd><c:out value="${platformDashboard.atRiskSaccos}" /></dd></div></dl></div>
 </section>
 <%@ include file="../fragments/dashboard-usage-widget.jspf" %>
 <section class="erp-panel" data-dashboard-widget="events" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.dashboard.recentEvents" /></h2></div>
  <div class="erp-panel-body"><ul class="dashboard-event-list"><c:forEach items="${platformDashboard.recentAuditEntries}" var="entry" end="4"><li><c:out value="${entry.actionLabel}" /><small><c:out value="${entry.sourceLabel}" /></small><time><c:out value="${appTime:format(entry.createdAtLabel)}" /></time></li></c:forEach></ul><c:if test="${empty platformDashboard.recentAuditEntries}"><p class="dashboard-data-status"><spring:message code="admin.dashboard.noRecentEvents" /></p></c:if></div>
 </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
