<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/dashboard-support.jspf" %>
<div class="erp-page-header" data-aws-page-header><p class="erp-breadcrumb"><spring:message code="menu.staffDashboard" /></p><h1 class="erp-page-title"><spring:message code="menu.staffDashboard" /></h1></div>
<div data-dashboard-board="staff-overview" aria-busy="true">
    <%@ include file="../fragments/dashboard-voucher-shortcuts.jspf" %>
    <c:if test="${not empty staffDashboard.portfolio}">
        <section class="erp-panel" data-dashboard-widget="portfolio-summary" data-widget-width="4" data-widget-height="280">
            <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="dashboard.widget.overview" /> · <c:out value="${staffDashboard.year}" /></h2></div>
            <dl class="erp-panel-body dashboard-data-metrics">
                <div><dt><spring:message code="manager.dashboard.totalDisbursed" /></dt><dd><c:out value="${staffDashboard.portfolio.totalDisbursedLoans}" /></dd></div>
                <div><dt><spring:message code="manager.dashboard.activeDisbursed" /></dt><dd><c:out value="${staffDashboard.portfolio.activeDisbursedLoans}" /></dd></div>
                <div><dt><spring:message code="archive.defaultedLoans" /></dt><dd><c:out value="${staffDashboard.portfolio.defaultedLoansCurrentYear}" /></dd></div>
                <div><dt><spring:message code="loan.status.PAID" /></dt><dd><c:out value="${staffDashboard.portfolio.paidLoans}" /></dd></div>
            </dl>
        </section>
    </c:if>
    <c:forEach items="${staffDashboard.roles}" var="role">
        <section class="erp-panel" data-dashboard-widget="role-${role.key}" data-widget-width="2" data-widget-height="480">
            <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="${role.labelKey}" /></h2></div>
            <div class="erp-panel-body">
                <dl class="dashboard-role-pending"><dt><spring:message code="staff.dashboard.pending" /></dt><dd><c:out value="${role.pending}" /></dd></dl>
                <div data-dashboard-chart data-chart-type="bar" data-chart-label="<spring:message code='${role.labelKey}' />">
                    <table data-chart-source data-table-sort-mode="none" data-table-sort-reason="chart"><thead><tr><th><spring:message code="common.status" /></th><th><spring:message code="admin.dashboard.applications" /></th></tr></thead><tbody>
                        <c:forEach items="${role.statuses}" var="row"><tr><th><spring:message code="loan.status.${row.status}" /></th><td data-value="${row.count}"><c:out value="${row.count}" /></td></tr></c:forEach>
                    </tbody></table>
                </div>
            </div>
        </section>
    </c:forEach>
    <c:if test="${not empty dashboard}"><%@ include file="../fragments/admin-dashboard-widgets.jspf" %></c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
