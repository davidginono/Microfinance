<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/dashboard-support.jspf" %>
<div class="erp-page-header" data-aws-page-header><p class="erp-breadcrumb"><spring:message code="admin.dashboard.breadcrumb" /></p><h1 class="erp-page-title"><spring:message code="admin.dashboard.title" /></h1></div>
<div data-dashboard-board="admin">
 <section class="erp-panel" data-dashboard-widget="overview" data-widget-width="2" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.overview" /></h2></div>
  <div class="erp-panel-body"><dl class="dashboard-facts">
   <div><dt><spring:message code="admin.dashboard.members" /></dt><dd><c:out value="${dashboard.totalMembers}" /></dd></div>
   <div><dt><spring:message code="admin.dashboard.applications" /></dt><dd><c:out value="${dashboard.totalApplications}" /></dd></div>
   <div><dt><spring:message code="dashboard.metric.managerReview" /></dt><dd><c:out value="${dashboard.onReviewByManagerCount}" /></dd></div>
   <div><dt><spring:message code="dashboard.metric.awaitingBoard" /></dt><dd><c:out value="${dashboard.awaitingBoardCount}" /></dd></div>
  </dl></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="clients" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.clients" /></h2></div>
  <div class="erp-panel-body"><div data-dashboard-chart data-chart-type="bar" data-chart-label="<spring:message code='dashboard.widget.clients' />">
   <table data-chart-source data-table-sort-mode="none" data-table-sort-reason="chart"><thead><tr><th><spring:message code="common.status" /></th><th><spring:message code="admin.dashboard.members" /></th></tr></thead><tbody>
    <tr><th><spring:message code="admin.dashboard.active" /></th><td data-value="${dashboard.activeMemberCount}"><c:out value="${dashboard.activeMemberCount}" /></td></tr>
    <tr><th><spring:message code="admin.dashboard.inactive" /></th><td data-value="${dashboard.inactiveMemberCount}"><c:out value="${dashboard.inactiveMemberCount}" /></td></tr>
   </tbody></table>
  </div></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="operations" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.operations" /></h2></div>
  <div class="erp-panel-body"><dl class="dashboard-facts">
   <div><dt><spring:message code="dashboard.metric.queued" /></dt><dd><c:out value="${dashboard.outboxNewCount}" /></dd></div>
   <div><dt><spring:message code="dashboard.metric.published" /></dt><dd><c:out value="${dashboard.outboxPublishedCount}" /></dd></div>
   <div><dt><spring:message code="admin.dashboard.failedOutboxEvents" /></dt><dd><c:out value="${dashboard.outboxFailedCount}" /></dd></div>
  </dl></div>
 </section>
 <%@ include file="../fragments/dashboard-usage-widget.jspf" %>
 <section class="erp-panel" data-dashboard-widget="sms" data-widget-width="1" data-widget-height="480">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.dashboard.smsBalance" /></h2></div>
  <div class="erp-panel-body"><dl class="dashboard-facts">
   <div><dt><spring:message code="admin.dashboard.station" /></dt><dd><c:out value="${dashboard.smsBalance.stationId}" /></dd></div>
   <div><dt><spring:message code="admin.dashboard.smsUnits" /></dt><dd><c:choose><c:when test="${dashboard.smsBalance.stationId ne '-'}"><c:out value="${dashboard.smsBalance.availableUnits}" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div>
   <div><dt><spring:message code="common.status" /></dt><dd><c:choose><c:when test="${dashboard.smsBalance.stationId ne '-'}"><span data-resource-status="${dashboard.smsBalance.status}"><c:out value="${dashboard.smsBalance.statusLabel}" /></span></c:when><c:otherwise><spring:message code="recording.unavailable" /></c:otherwise></c:choose></dd></div>
  </dl></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="database" data-widget-width="3" data-widget-min-width="2" data-widget-height="560" data-dashboard-source="database" data-dashboard-url="<c:url value='/admin/dashboard/database-utilization' />">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.dashboard.databaseUtilization" /></h2></div>
  <div class="erp-panel-body">
   <div class="dashboard-data-tools"><dl class="dashboard-data-metrics"><div><dt><spring:message code="admin.dashboard.size" /></dt><dd data-dashboard-metric="size">—</dd></div><div><dt><spring:message code="admin.dashboard.connections" /></dt><dd data-dashboard-metric="connections">—</dd></div><div><dt><spring:message code="admin.dashboard.usage" /></dt><dd data-dashboard-metric="usage">—</dd></div></dl><button type="button" class="app-btn btn-neutral" data-dashboard-refresh><spring:message code="common.refresh" /></button></div>
   <div class="dashboard-data-status" data-dashboard-data-status role="status"><spring:message code="common.loading" /></div><button type="button" class="app-btn btn-neutral" data-dashboard-retry hidden><spring:message code="common.refresh" /></button>
   <label class="dashboard-chart-selector"><spring:message code="dashboard.chart.metric" /><select data-dashboard-view><option value="size"><spring:message code="dashboard.metric.databaseSize" /> (MB)</option><option value="connections"><spring:message code="dashboard.metric.connectionUsage" /> (%)</option></select></label>
   <div data-dashboard-chart-panel="size"><div data-live-chart="size" class="dashboard-chart"></div></div><div data-dashboard-chart-panel="connections" hidden><div data-live-chart="connections" class="dashboard-chart"></div></div>
   <p class="dashboard-data-updated"><spring:message code="admin.dashboard.updated" />: <span data-dashboard-metric="updated">—</span></p>
  </div>
 </section>
 <section class="erp-panel" data-dashboard-widget="events" data-widget-width="1" data-widget-height="560">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.dashboard.recentEvents" /></h2></div>
  <div class="erp-panel-body"><ul class="dashboard-event-list"><c:forEach items="${dashboard.recentAuditEntries}" var="entry" end="4"><li><c:out value="${entry.actionLabel}" /><small><c:out value="${entry.sourceLabel}" /></small><time><c:out value="${appTime:format(entry.createdAtLabel)}" /></time></li></c:forEach></ul><c:if test="${empty dashboard.recentAuditEntries}"><p class="dashboard-data-status"><spring:message code="admin.dashboard.noRecentEvents" /></p></c:if></div>
 </section>
 <section class="erp-panel" data-dashboard-widget="incidents" data-widget-width="2" data-widget-height="360">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.dashboard.recentIncidents" /></h2></div>
  <div class="erp-panel-body"><ul class="dashboard-event-list"><c:forEach items="${dashboard.recentIncidents}" var="incident" end="2"><li><a class="resource-table-id-link" href="<c:url value='/admin/incidents/${incident.id}' />"><c:out value="${incident.subject}" /></a><small><span data-resource-status="${incident.status}"><c:out value="${incident.status}" /></span></small></li></c:forEach></ul><c:if test="${empty dashboard.recentIncidents}"><p class="dashboard-data-status"><spring:message code="admin.dashboard.noRecentIncidents" /></p></c:if></div>
 </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
