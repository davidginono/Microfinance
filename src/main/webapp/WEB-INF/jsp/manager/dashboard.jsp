<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<%@ include file="../fragments/dashboard-support.jspf" %>

<spring:message code="manager.dashboard.breadcrumb" text="Manager Panel / Dashboard" var="dashboardDefaultBreadcrumb" />
<spring:message code="manager.dashboard.title" text="Manager Dashboard" var="dashboardDefaultTitle" />
<spring:message code="loan.status.READY_FOR_MANAGER" text="On Review By Manager" var="dashboardDefaultQueueLabel" />
<spring:message code="manager.dashboard.queueWaiting" text="Queue waiting" var="dashboardDefaultQueueFooter" />
<spring:message code="manager.dashboard.statusPieTitle" text="Application Status Pie Chart" var="dashboardDefaultChartTitle" />
<c:set var="dashboardBreadcrumbValue" value="${empty dashboardBreadcrumb ? dashboardDefaultBreadcrumb : dashboardBreadcrumb}" />
<c:set var="dashboardPageTitleValue" value="${empty dashboardPageTitle ? dashboardDefaultTitle : dashboardPageTitle}" />
<c:set var="dashboardQueueLabelValue" value="${empty dashboardQueueLabel ? dashboardDefaultQueueLabel : dashboardQueueLabel}" />
<c:set var="dashboardQueueValueValue" value="${empty dashboardQueueValue ? dashboardOnReviewByManagerLoans : dashboardQueueValue}" />
<c:set var="dashboardQueueFooterLabelValue" value="${empty dashboardQueueFooterLabel ? dashboardDefaultQueueFooter : dashboardQueueFooterLabel}" />
<c:set var="dashboardDetailBasePathValue" value="${empty dashboardDetailBasePath ? '/manager/loan-applications' : dashboardDetailBasePath}" />
<c:set var="dashboardChartTitleValue" value="${empty dashboardChartTitle ? dashboardDefaultChartTitle : dashboardChartTitle}" />
<c:set var="dashboardQueueHrefValue" value="${dashboardDetailBasePathValue}" />
<c:choose>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/board/')}">
        <c:set var="dashboardQueueHrefValue" value="/board/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/chairperson/')}">
        <c:set var="dashboardQueueHrefValue" value="/chairperson/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/credit-committee/')}">
        <c:set var="dashboardQueueHrefValue" value="/credit-committee/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/loan-officer/')}">
        <c:set var="dashboardQueueHrefValue" value="/loan-officer/queue" />
    </c:when>
</c:choose>
<c:set var="dashboardTotalDisbursedHrefValue" value="#recentDisbursements" />
<c:set var="dashboardActiveDisbursedHrefValue" value="#recentDisbursements" />
<c:set var="dashboardDefaultedHrefValue" value="#StationLoanStatusChart" />
<c:choose>
    <c:when test="${dashboardDetailBasePathValue eq '/manager/loan-applications'}">
        <c:set var="dashboardTotalDisbursedHrefValue" value="/manager/archive?filter=DISBURSED" />
        <c:set var="dashboardActiveDisbursedHrefValue" value="/manager/archive?filter=DISBURSED" />
    </c:when>
    <c:when test="${dashboardDetailBasePathValue eq '/disbursement/loan-applications'}">
        <c:set var="dashboardTotalDisbursedHrefValue" value="/disbursement/archive" />
        <c:set var="dashboardActiveDisbursedHrefValue" value="/disbursement/archive?filter=DISBURSED" />
        <c:set var="dashboardDefaultedHrefValue" value="/disbursement/archive?filter=DEFAULTED" />
    </c:when>
</c:choose>
<fmt:formatNumber value="${dashboardDisbursementYear}" groupingUsed="false" var="dashboardDisbursementYearLabel" />

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">${dashboardBreadcrumbValue}</p>
    <h1 class="erp-page-title">${dashboardPageTitleValue}</h1>
</div>

<div data-dashboard-board="staff" aria-busy="true">
<%@ include file="../fragments/dashboard-voucher-shortcuts.jspf" %>
<section class="erp-stat-grid" data-dashboard-widget="overview" data-widget-width="4" data-widget-height="360" data-widget-title="<spring:message code='dashboard.widget.overview' />">
    <a href="${dashboardTotalDisbursedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-green block no-underline" aria-label="Open total disbursed loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="manager.dashboard.totalDisbursed" text="Total Loans Disbursed" /></p>
                <p class="erp-stat-value">${dashboardTotalDisbursedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span>${dashboardDisbursementYearLabel}</span></div>
    </a>
    <a href="${dashboardActiveDisbursedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-blue block no-underline" aria-label="Open active disbursed loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="manager.dashboard.activeDisbursed" text="Active Disbursed" /></p>
                <p class="erp-stat-value">${dashboardActiveDisbursedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span><spring:message code="manager.dashboard.currentlyOngoing" text="Currently ongoing" /></span></div>
    </a>
    <a href="${dashboardQueueHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-amber block no-underline" aria-label="Open ${dashboardQueueLabelValue} queue">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">${dashboardQueueLabelValue}</p>
                <p class="erp-stat-value">${dashboardQueueValueValue}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span>${dashboardQueueFooterLabelValue}</span></div>
    </a>
    <a href="${dashboardDefaultedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-red block no-underline" aria-label="Open defaulted loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="archive.defaultedLoans" text="Defaulted Loans" /></p>
                <p class="erp-stat-value">${dashboardDefaultedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span><spring:message code="manager.dashboard.currentYear" text="Current year" /></span></div>
    </a>
</section>

<section id="StationLoanStatusChart" class="erp-panel" data-dashboard-widget="application-status" data-widget-width="2" data-widget-height="480">
 <div class="erp-panel-header"><h2 class="erp-panel-title"><c:out value="${dashboardChartTitleValue}" /></h2></div>
 <div class="erp-panel-body"><div data-dashboard-chart data-chart-type="bar" data-chart-label="<c:out value='${dashboardChartTitleValue}' />">
  <table data-chart-source data-table-sort-mode="none" data-table-sort-reason="chart"><thead><tr><th><spring:message code="common.status" /></th><th><spring:message code="admin.dashboard.applications" /></th></tr></thead><tbody>
  <c:forEach items="${dashboardStatusChartRows}" var="statusRow"><tr><th><c:out value="${statusRow.label}" /></th><td data-value="<c:out value='${statusRow.count}' />"><c:out value="${statusRow.count}" /></td></tr></c:forEach>
  </tbody></table>
 </div></div>
</section>

<section data-dashboard-widget="disbursements" data-widget-width="2" data-widget-height="480" id="recentDisbursements" class="erp-panel staff-recent-disbursements mt-4" aria-labelledby="recentDisbursementsTitle">
    <div class="erp-panel-header">
        <h2 id="recentDisbursementsTitle" class="erp-panel-title"><spring:message code="manager.dashboard.recentDisbursements" /></h2>
    </div>
    <div class="erp-table-scroll erp-table-scroll-sm staff-recent-disbursements-scroll" tabindex="0" role="region" aria-labelledby="recentDisbursementsTitle">
        <table class="erp-table staff-recent-disbursements-table" data-table-sort-mode="local">
            <thead><tr>
                <th scope="col"><spring:message code="loan.applicationId" /></th>
                <th scope="col"><spring:message code="loan.loanId" /></th>
                <th scope="col"><spring:message code="recording.client" /></th>
                <th scope="col"><spring:message code="recording.product" /></th>
                <th scope="col"><spring:message code="common.amount" /> (TZS)</th>
                <th scope="col"><spring:message code="analytics.disbursed" /></th>
                <th scope="col"><spring:message code="common.status" /></th>
            </tr></thead>
            <tbody>
            <c:forEach items="${dashboardDisbursementRows}" var="loan">
                <tr>
                    <td><c:choose><c:when test="${not empty loan.shortId}"><a class="resource-table-id-link" href="<c:url value='${dashboardDetailBasePathValue}/${loan.id}' />"><c:out value="${loan.shortId}" /></a></c:when><c:otherwise>—</c:otherwise></c:choose></td>
                    <td><c:out value="${empty loan.loanId ? '—' : loan.loanId}" /></td>
                    <td><c:out value="${loan.applicantName}" /></td>
                    <td><c:out value="${loan.loanTypeLabel}" /></td>
                    <td class="staff-recent-disbursements-number"><c:choose><c:when test="${empty loan.amount or loan.amount eq '-'}"><spring:message code="recording.unavailable" /></c:when><c:otherwise><fmt:formatNumber value="${loan.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose></td>
                    <td class="staff-recent-disbursements-date"><c:out value="${appTime:display(loan.disbursementDate)}" /></td>
                    <td><span class="staff-recent-disbursements-status"><span data-resource-status='<c:out value="${loan.statusLabel}" />'><c:out value="${loan.statusLabel}" /></span></span></td>
                </tr>
            </c:forEach>
            <c:if test="${empty dashboardDisbursementRows}">
                <tr><td colspan="7"><spring:message code="manager.dashboard.noRecentDisbursements" arguments="${dashboardRecentDisbursementDays}" /></td></tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

</div>
<%@ include file="../fragments/footer.jspf" %>
