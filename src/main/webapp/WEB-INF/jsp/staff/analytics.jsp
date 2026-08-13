<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="staff.analytics.breadcrumb" text="Staff Workspace / Staff Reports and Analytics" /></p>
    <h1 class="erp-page-title">${staffAnalyticsTitle}</h1>
</div>

<section class="erp-panel staff-analytics-command-panel" aria-labelledby="staffAnalyticsControlsTitle">
    <c:url var="staffPdfExportUrl" value="/documents/reports/staff-loan-analytics.pdf">
        <c:param name="fromDate" value="${fromDate}" />
        <c:param name="toDate" value="${toDate}" />
        <c:param name="loanProductId" value="${loanProductId}" />
        <c:param name="loanType" value="${loanType}" />
        <c:param name="viewAs" value="${viewAs}" />
    </c:url>
    <c:url var="staffExcelExportUrl" value="/documents/reports/staff-loan-analytics.xlsx">
        <c:param name="fromDate" value="${fromDate}" />
        <c:param name="toDate" value="${toDate}" />
        <c:param name="loanProductId" value="${loanProductId}" />
        <c:param name="loanType" value="${loanType}" />
        <c:param name="viewAs" value="${viewAs}" />
    </c:url>
    <c:url var="staffAnalyticsRefreshUrl" value="/staff/analytics">
        <c:param name="fromDate" value="${fromDate}" />
        <c:param name="toDate" value="${toDate}" />
        <c:param name="loanProductId" value="${loanProductId}" />
        <c:param name="loanType" value="${loanType}" />
        <c:param name="viewAs" value="${viewAs}" />
    </c:url>
    <div class="app-table-titlebar">
        <div class="app-table-heading">
            <h2 id="staffAnalyticsControlsTitle"><spring:message code="staff.analytics.reportControls" text="Report controls" /></h2>
        </div>
        <div class="app-table-toolbar staff-analytics-actions" data-aws-action-pin="true" aria-label="<spring:message code='staff.analytics.reportActions' text='Report actions' />">
            <a href="${staffPdfExportUrl}" class="app-btn btn-neutral staff-filter-action" data-download-action="true">
                <span class="staff-action-icon is-pdf" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15h6"/><path d="M9 18h4"/></svg>
                </span>
                <spring:message code="reports.exportPdf" text="Export PDF" />
            </a>
            <a href="${staffExcelExportUrl}" class="app-btn btn-neutral staff-filter-action" data-download-action="true">
                <span class="staff-action-icon is-excel" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 15h8"/><path d="M8 18h8"/></svg>
                </span>
                <spring:message code="reports.exportExcel" text="Export Excel" />
            </a>
            <a href="${staffAnalyticsRefreshUrl}" class="app-icon-button btn-neutral" aria-label="<spring:message code='common.refresh' text='Refresh' />" title="<spring:message code='common.refresh' text='Refresh' />">
                <span class="staff-action-icon is-refresh" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 0 1-15.6 6.1"/><path d="M3 12A9 9 0 0 1 18.6 5.9"/><path d="M3 18h5v-5"/><path d="M21 6h-5v5"/></svg>
                </span>
            </a>
        </div>
    </div>
    <form action="/staff/analytics" method="get" class="staff-analytics-filter aws-filter-toolbar" data-page-preloader="true" data-aws-filter-toolbar data-aws-filter-pin="true" data-view-position-key="staff-analytics-filters">
        <label class="staff-analytics-control is-date fcms-label">
            <spring:message code="reports.startDate" text="Start Date" />
            <input name="fromDate" type="date" autocomplete="off" value="${fromDateInput}" class="fcms-control" />
        </label>
        <label class="staff-analytics-control is-date fcms-label">
            <spring:message code="reports.endDate" text="End Date" />
            <input name="toDate" type="date" autocomplete="off" value="${toDateInput}" class="fcms-control" />
        </label>
        <label class="staff-analytics-control is-product fcms-label">
            <spring:message code="reports.loanProduct" text="Loan Product" />
            <select name="loanProductId" class="fcms-control">
                <option value=""><spring:message code="reports.allProducts" text="All Products" /></option>
                <c:forEach items="${loanProducts}" var="product">
                    <option value="${product.id}" ${loanProductId eq product.id ? 'selected' : ''}>
                        <c:out value="${product.displayName}" />
                    </option>
                </c:forEach>
            </select>
        </label>
        <div class="staff-analytics-control is-view fcms-label">
            <span><spring:message code="staff.analytics.viewAs" text="Report scope" /></span>
            <input id="staffAnalyticsViewAs" type="hidden" name="viewAs" value="${viewAs}" />
            <div class="staff-view-switch" role="group" aria-label="<spring:message code='staff.analytics.viewAs' text='Report scope' />">
                <button type="button" data-report-view="member" aria-pressed="${viewAs eq 'member'}" title="<spring:message code='staff.analytics.staffView' text='Staff report' />" class="staff-view-option ${viewAs eq 'member' ? 'is-active' : ''}">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>
                    <span><spring:message code="staff.analytics.staffView" text="Staff report" /></span>
                </button>
                <button type="button" data-report-view="staff" aria-pressed="${viewAs eq 'staff'}" title="<spring:message code='staff.analytics.stationView' text='Station report' />" class="staff-view-option ${viewAs eq 'staff' ? 'is-active' : ''}" ${canViewStationAnalytics ? '' : 'disabled'}>
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21a6 6 0 0 0-12 0"/><circle cx="11" cy="7" r="4"/><path d="M19 8v6"/><path d="M22 11h-6"/></svg>
                    <span><spring:message code="staff.analytics.stationView" text="Station report" /></span>
                </button>
            </div>
        </div>
        <div class="staff-filter-actions">
            <button type="submit" class="app-btn btn-primary staff-filter-action"><spring:message code="common.applyFilters" text="Apply Filter" /></button>
            <a href="/staff/analytics" class="app-btn btn-neutral staff-filter-action"><spring:message code="common.reset" text="Reset" /></a>
        </div>
    </form>
</section>

<section class="staff-metric-grid ${stationWideStaffView ? 'is-station' : 'is-staff'}" aria-label="${staffAnalyticsTitle}">
    <c:forEach items="${metricCards}" var="card">
        <div class="erp-panel staff-metric-card" data-spark-key="${card.sparkName}" data-spark-tone="${card.tone}" title="${card.label}: ${card.value}">
            <div class="staff-metric-main">
                <span class="staff-metric-icon tone-${card.tone}">
                    <c:choose>
                        <c:when test="${card.key eq 'applied'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15h6"/><circle cx="17" cy="17" r="4"/><path d="M15.8 17l.8.8 1.6-1.8"/></svg></c:when>
                        <c:when test="${card.key eq 'reviewed'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M4 4h16v16H4z"/><path d="M8 9h8"/><path d="M8 13h5"/><path d="m15 16 1.5 1.5L20 14"/></svg></c:when>
                        <c:when test="${card.key eq 'approved'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></c:when>
                        <c:when test="${card.key eq 'active'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6 2h12"/><path d="M6 22h12"/><path d="M8 2c0 5 8 5 8 10s-8 5-8 10"/><path d="M16 2c0 5-8 5-8 10s8 5 8 10"/><path d="M9 12h6"/></svg></c:when>
                        <c:when test="${card.key eq 'disbursed'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M3 10h18"/><path d="M5 10l7-5 7 5"/><path d="M6 10v8"/><path d="M10 10v8"/><path d="M14 10v8"/><path d="M18 10v8"/><path d="M4 18h16"/><path d="M3 22h18"/></svg></c:when>
                        <c:when test="${card.key eq 'paid'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></c:when>
                        <c:when test="${card.key eq 'pending'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg></c:when>
                        <c:when test="${card.key eq 'defaulted'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></c:when>
                        <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12h8"/></svg></c:otherwise>
                    </c:choose>
                </span>
                <div class="min-w-0">
                    <p class="staff-metric-title">${card.label}</p>
                    <p class="staff-metric-value"><fmt:formatNumber value="${card.value}" /></p>
                </div>
            </div>
            <div class="staff-spark-row">
                <div class="staff-sparkline" aria-hidden="true"></div>
            </div>
        </div>
    </c:forEach>
    <c:if test="${stationWideStaffView and stationParticipation ne null}">
        <div class="erp-panel staff-metric-card" title="<spring:message code='staff.analytics.totalMembers' text='Total Members' />">
            <div class="staff-metric-main">
                <span class="staff-metric-icon tone-blue">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M16 21v-2a4 4 0 0 0-8 0v2"/><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a4 4 0 0 1 3-3.87"/><path d="M20 21v-2a4 4 0 0 0-3-3.87"/></svg>
                </span>
                <div class="min-w-0">
                    <p class="staff-metric-title"><spring:message code="staff.analytics.totalMembers" text="Total Members" /></p>
                    <p class="staff-metric-value"><fmt:formatNumber value="${stationTotalMembers}" /></p>
                    <p class="staff-metric-trend">
                        <strong><spring:message code="staff.analytics.activeMembers" text="Active" /></strong>
                        <span><spring:message code="staff.analytics.stationMembers" text="station members" /></span>
                    </p>
                </div>
            </div>
            <div class="staff-spark-row">
                <p class="staff-metric-meta">
                    <spring:message code="staff.analytics.stationScope" text="Current station scope" />
                </p>
            </div>
        </div>
        <div class="erp-panel staff-metric-card" title="<spring:message code='staff.analytics.totalApplicants' text='Total Applicants' />">
            <div class="staff-metric-main">
                <span class="staff-metric-icon tone-emerald">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M16 21v-2a4 4 0 0 0-8 0v2"/><circle cx="12" cy="7" r="4"/><path d="M12 11v6"/><path d="M9 14h6"/></svg>
                </span>
                <div class="min-w-0">
                    <p class="staff-metric-title"><spring:message code="staff.analytics.totalApplicants" text="Total Applicants" /></p>
                    <p class="staff-metric-value"><fmt:formatNumber value="${stationTotalApplicants}" /></p>
                    <p class="staff-metric-trend">
                        <strong>${stationParticipation.participationRateLabel}</strong>
                        <span><spring:message code="staff.analytics.ofMembers" text="of members" /></span>
                    </p>
                </div>
            </div>
            <div class="staff-spark-row">
                <p class="staff-metric-meta">
                    <spring:message code="staff.analytics.repeatApplicants" text="Repeat Applicants" />:
                    <fmt:formatNumber value="${stationParticipation.repeatApplicants}" />
                </p>
                <span class="staff-metric-meta-value" aria-label="<spring:message code='staff.analytics.applicationsPerApplicant' text='Applications per Applicant' />: ${stationParticipation.applicationsPerApplicantLabel}">
                    ${stationParticipation.applicationsPerApplicantLabel} <spring:message code="staff.analytics.applicationsAverageShort" text="avg. applications" />
                </span>
            </div>
        </div>
    </c:if>
</section>

<section class="staff-analytics-layout">
    <section class="erp-panel staff-portfolio-panel">
            <div class="erp-panel-header">
                <h2 class="erp-panel-title">
                    <c:choose>
                        <c:when test="${viewAs eq 'member'}"><spring:message code="staff.analytics.staffPortfolioSummary" text="Staff Portfolio Summary" /></c:when>
                        <c:when test="${stationWideStaffView}"><spring:message code="staff.analytics.stationPortfolioSummary" text="Station Portfolio Summary" /></c:when>
                        <c:otherwise><spring:message code="staff.analytics.staffPortfolioSummary" text="Staff Portfolio Summary" /></c:otherwise>
                    </c:choose>
                </h2>
            </div>
            <div class="erp-panel-body staff-portfolio-body">
                <c:if test="${staffReviewView}">
                <div class="staff-summary-grid">
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M16 21v-2a4 4 0 0 0-8 0v2"/><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a4 4 0 0 1 3-3.87"/><path d="M20 21v-2a4 4 0 0 0-3-3.87"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansHandled" text="Loans Handled" /></p><p class="staff-metric-value"><fmt:formatNumber value="${staffPortfolio.handledLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansApproved" text="Loans Approved" /></p><p class="staff-metric-value"><fmt:formatNumber value="${staffPortfolio.approvedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-rose"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansRejected" text="Loans Rejected" /></p><p class="staff-metric-value"><fmt:formatNumber value="${staffPortfolio.rejectedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-violet"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M3 10h18"/><path d="M5 10l7-5 7 5"/><path d="M6 10v8"/><path d="M18 10v8"/><path d="M4 18h16"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansDisbursed" text="Loans Disbursed" /></p><p class="staff-metric-value"><fmt:formatNumber value="${staffPortfolio.disbursedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-orange"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.defaultedAfterApproval" text="Defaulted" /></p><p class="staff-metric-value"><fmt:formatNumber value="${staffPortfolio.defaultedAfterApproval}" /></p></div>
                    </div>
                </div>
                </c:if>
                <c:if test="${stationWideStaffView}">
                    <div class="staff-financial-breakdown-grid">
                        <div class="staff-interest-summary">
                            <span class="staff-summary-icon tone-emerald"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 3v18"/><path d="M17 7H9.5a3.5 3.5 0 0 0 0 7H14a3.5 3.5 0 0 1 0 7H6"/></svg></span>
                            <div class="min-w-0">
                                <p class="erp-widget-title"><spring:message code="staff.analytics.totalInterestAccumulated" text="Total Paid Interest Accumulated" /></p>
                                <p class="staff-metric-value is-financial">${totalInterestAccumulatedLabel}</p>
                                <p class="staff-interest-meta">
                                    <spring:message code="staff.analytics.totalInterestScope" arguments="${selectedLoanProductLabel},${fromDate},${toDate}" text="Based on {0} from {1} to {2}." />
                                </p>
                            </div>
                        </div>
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalInterestUnpaid" text="Total Interest Unpaid Yet" /></p>
                            <p class="staff-metric-value is-financial">${totalInterestUnpaidLabel}</p>
                        </div>
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalLoanAmountPaid" text="Total Loan Amount Paid" /></p>
                            <p class="staff-metric-value is-financial">${totalLoanAmountPaidLabel}</p>
                        </div>
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalLoanAmountUnpaid" text="Total Loan Amount Unpaid Yet" /></p>
                            <p class="staff-metric-value is-financial">${totalLoanAmountUnpaidLabel}</p>
                        </div>
                    </div>
                    <c:if test="${empty loanType}">
                        <div class="erp-table-wrap staff-financial-table-wrap" data-aws-table-region data-loading-label="Loading results..." aria-label="Loan product financial breakdown">
                            <div class="erp-table-scroll" data-view-position-key="staff-analytics-financial-breakdown">
                            <table class="erp-table staff-financial-table">
                                <thead>
                                    <tr>
                                        <th><spring:message code="reports.loanProductName" text="Loan Product" /></th>
                                        <th><spring:message code="staff.analytics.totalInterestPaid" text="Total Interest Paid" /></th>
                                        <th><spring:message code="staff.analytics.totalInterestUnpaid" text="Total Interest Unpaid Yet" /></th>
                                        <th><spring:message code="staff.analytics.totalLoanAmountPaid" text="Total Loan Amount Paid" /></th>
                                        <th><spring:message code="staff.analytics.totalLoanAmountUnpaid" text="Total Loan Amount Unpaid Yet" /></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach items="${productFinancialRows}" var="row">
                                        <tr>
                                            <td class="staff-financial-product">${row.loanProduct}</td>
                                            <td>${row.totalInterestPaidLabel}</td>
                                            <td>${row.totalInterestUnpaidLabel}</td>
                                            <td>${row.totalLoanAmountPaidLabel}</td>
                                            <td>${row.totalLoanAmountUnpaidLabel}</td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty productFinancialRows}">
                                        <tr>
                                            <td colspan="5" class="staff-table-empty"><spring:message code="staff.analytics.financialBreakdownEmpty" text="No financial breakdown is available for the selected filters." /></td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                            </div>
                        </div>
                    </c:if>
                </c:if>
                <div class="staff-risk-row">
                    <div class="staff-risk-summary">
                        <span class="staff-summary-icon tone-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"/><path d="M9 12l2 2 4-5"/></svg></span>
                        <div><p class="staff-metric-title"><spring:message code="staff.analytics.portfolioRiskLevel" text="Portfolio Risk Level" /></p><p class="staff-risk-status">${staffPortfolio.riskLevel}</p></div>
                    </div>
                    <div>
                        <p class="staff-risk-rate"><spring:message code="staff.analytics.defaultedAfterApprovalRate" arguments="${staffPortfolio.defaultedAfterApprovalRate}" text="Defaulted-after-approval rate is {0}%" /></p>
                        <p class="staff-risk-help"><spring:message code="staff.analytics.defaultedAfterApprovalHelp" text="Calculated from approved loans in the selected period." /></p>
                    </div>
                </div>
            </div>
        </section>

    <section class="erp-panel staff-trend-panel">
        <div class="erp-panel-header staff-trend-header">
            <h2 class="erp-panel-title">
                <c:choose>
                    <c:when test="${staffReviewView}"><spring:message code="staff.analytics.reviewTrendOverTime" text="Review Trend Over Time" /></c:when>
                    <c:otherwise><spring:message code="reports.loanTrendOverTime" text="Loan Trend Over Time" /></c:otherwise>
                </c:choose>
            </h2>
            <div class="staff-trend-controls" role="group" aria-label="<spring:message code='staff.analytics.trendInterval' text='Trend interval' />">
                <button type="button" class="staff-trend-period-button is-active" data-trend-interval="monthly" aria-pressed="true">
                    <spring:message code="reports.interval.monthly" text="Monthly" />
                </button>
                <button type="button" class="staff-trend-period-button" data-trend-interval="quarterly" aria-pressed="false">
                    <spring:message code="reports.interval.quarterly" text="Quarterly" />
                </button>
                <button type="button" class="staff-trend-period-button" data-trend-interval="yearly" aria-pressed="false">
                    <spring:message code="reports.interval.yearly" text="Yearly" />
                </button>
            </div>
        </div>
        <div class="erp-panel-body">
            <div id="staffTrendChart" class="staff-chart-box" role="img" aria-label="<spring:message code='staff.analytics.trendChartLabel' text='Loan status trend chart' />" data-empty-message="<spring:message code='staff.analytics.chartEmpty' text='No results match the selected filters.' />"></div>
        </div>
    </section>

    <section class="erp-panel staff-product-panel">
            <div class="erp-panel-header">
                <h2 class="erp-panel-title">
                    <c:choose>
                        <c:when test="${staffReviewView}"><spring:message code="staff.analytics.loanProductReviewBreakdown" text="Loan Product Review Breakdown" /></c:when>
                        <c:otherwise><spring:message code="staff.analytics.loanProductPerformance" text="Loan Product Performance" /></c:otherwise>
                    </c:choose>
                </h2>
            </div>
            <div class="erp-panel-body">
                <div id="staffProductChart" class="staff-chart-box" role="img" aria-label="<spring:message code='staff.analytics.productChartLabel' text='Loan product performance chart' />" data-empty-message="<spring:message code='staff.analytics.chartEmpty' text='No results match the selected filters.' />"></div>
            </div>
    </section>
</section>

<%@ include file="../fragments/analytics-chart-utils.jspf" %>
<script>
window.addEventListener('load', function () {
    const productSeries = ${productPerformanceJson};
    const trendSeries = ${trendSeriesJson};
    const trendPeriodButtons = Array.from(document.querySelectorAll('[data-trend-interval]'));
    const dateFilterControls = Array.from(document.querySelectorAll('.staff-analytics-control.is-date'));
    const viewAsInput = document.getElementById('staffAnalyticsViewAs');
    const viewOptions = Array.from(document.querySelectorAll('[data-report-view]'));
    const reportScopeForm = viewAsInput ? viewAsInput.closest('form') : null;
    let reportScopeSubmitting = false;

    function openDateFilterPicker(dateInput) {
        if (!dateInput || dateInput.disabled || dateInput.readOnly) return;
        dateInput.focus({ preventScroll: true });
        if (typeof dateInput.showPicker !== 'function') return;
        try {
            dateInput.showPicker();
        } catch (error) {
            // Some browsers only allow showPicker during direct user activation.
        }
    }

    dateFilterControls.forEach(function (control) {
        const dateInput = control.querySelector('input[type="date"]');
        control.addEventListener('click', function () {
            openDateFilterPicker(dateInput);
        });
    });

    viewOptions.forEach(function (option) {
        option.addEventListener('click', function () {
            if (!viewAsInput || option.disabled || reportScopeSubmitting) return;
            const nextView = option.getAttribute('data-report-view') || 'member';
            if (viewAsInput.value === nextView) return;
            viewAsInput.value = nextView;
            viewOptions.forEach(function (candidate) {
                const selected = candidate === option;
                candidate.classList.toggle('is-active', selected);
                candidate.classList.toggle('is-switching', selected);
                candidate.setAttribute('aria-pressed', selected ? 'true' : 'false');
            });
            reportScopeSubmitting = true;
            const scopeSwitch = option.closest('.staff-view-switch');
            scopeSwitch?.classList.add('is-submitting');
            if (reportScopeForm) {
                reportScopeForm.setAttribute('aria-busy', 'true');
                window.setTimeout(function () {
                    const canSubmit = typeof reportScopeForm.reportValidity !== 'function' || reportScopeForm.reportValidity();
                    if (!canSubmit) {
                        reportScopeSubmitting = false;
                        reportScopeForm.removeAttribute('aria-busy');
                        scopeSwitch?.classList.remove('is-submitting');
                        option.classList.remove('is-switching');
                        return;
                    }
                    if (typeof reportScopeForm.requestSubmit === 'function') {
                        reportScopeForm.requestSubmit();
                    } else {
                        reportScopeForm.submit();
                    }
                }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 140);
            }
        });
        option.addEventListener('animationend', function () {
            option.classList.remove('is-switching');
        });
    });

    renderMetricSparklines(trendSeries);
    renderGroupedBarChart('staffProductChart', productSeries);
    renderTrendChart('monthly');
    trendPeriodButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            const interval = button.getAttribute('data-trend-interval') || 'monthly';
            trendPeriodButtons.forEach(function (candidate) {
                const selected = candidate === button;
                candidate.classList.toggle('is-active', selected);
                candidate.setAttribute('aria-pressed', selected ? 'true' : 'false');
            });
            renderTrendChart(interval);
        });
    });

    function renderTrendChart(interval) {
        renderLineChart('staffTrendChart', trendSeries, interval, trendRangeLimit(interval));
    }

});

function trendRangeLimit(interval) {
    if (interval === 'yearly') return 5;
    if (interval === 'quarterly') return 8;
    return 13;
}

function chartSvg(width, height) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    return svg;
}

function svgEl(name, attrs) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', name);
    Object.keys(attrs || {}).forEach(function (key) {
        node.setAttribute(key, attrs[key]);
    });
    return node;
}

function addText(svg, text, x, y, attrs) {
    const node = svgEl('text', Object.assign({ x: x, y: y, fill: '#16191f', 'font-size': 12, 'font-weight': 700 }, attrs || {}));
    node.textContent = text;
    svg.appendChild(node);
    return node;
}

function renderLegend(svg, series, startX, y) {
    let x = startX;
    series.forEach(function (item) {
        svg.appendChild(svgEl('rect', { x: x, y: y - 9, width: 11, height: 11, rx: 1, fill: item.color || '#0972d3' }));
        addText(svg, item.name, x + 17, y, { fill: '#414d5c', 'font-size': 12, 'font-weight': 700 });
        x += Math.max(105, String(item.name).length * 8 + 36);
    });
}

function renderMetricSparklines(series) {
    const byName = {};
    series.forEach(function (item) {
        byName[item.name] = item;
    });
    document.querySelectorAll('.staff-metric-card[data-spark-key]').forEach(function (card) {
        const target = card.querySelector('.staff-sparkline');
        const item = byName[card.getAttribute('data-spark-key')];
        if (!target || !item) return;
        const tone = card.getAttribute('data-spark-tone') || 'blue';
        const color = item.color || toneColor(tone);
        const points = item.dataPoints || [];
        target.innerHTML = '';
        if (!points.length) return;
        const width = 150;
        const height = 28;
        const pad = 3;
        const svg = chartSvg(width, height);
        const maxY = Math.max(1, Math.max.apply(null, points.map(function (point) { return point.y || 0; }).concat([0])));
        const step = points.length > 1 ? (width - pad * 2) / (points.length - 1) : width - pad * 2;
        const coords = points.map(function (point, index) {
            const x = pad + step * index;
            const y = height - pad - ((point.y || 0) / maxY * (height - pad * 2));
            return { x: x, y: y };
        });
        const d = exactLinePath(coords);
        const area = d + ' L ' + (coords[coords.length - 1] ? coords[coords.length - 1].x : width - pad) + ' ' + (height - pad)
            + ' L ' + (coords[0] ? coords[0].x : pad) + ' ' + (height - pad) + ' Z';
        svg.appendChild(svgEl('path', { d: area, fill: color, opacity: 0.08 }));
        svg.appendChild(svgEl('path', { d: d, fill: 'none', stroke: color, 'stroke-width': 2.2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
        target.appendChild(svg);
    });
}

function exactLinePath(coords) {
    if (!coords.length) return '';
    if (coords.length === 1) {
        return 'M' + coords[0].x + ' ' + coords[0].y + ' L' + (coords[0].x + 1) + ' ' + coords[0].y;
    }
    return coords.map(function (point, index) {
        return (index === 0 ? 'M' : 'L') + point.x + ' ' + point.y;
    }).join(' ');
}

function toneColor(tone) {
    return {
        blue: '#0972d3',
        emerald: '#037f0c',
        teal: '#0073bb',
        violet: '#6b3fa0',
        green: '#037f0c',
        orange: '#ff9900',
        red: '#d13212',
        rose: '#d13212',
        slate: '#5f6b7a'
    }[tone] || '#0972d3';
}

function createChartTooltip(target) {
    const tooltip = document.createElement('div');
    tooltip.className = 'staff-chart-tooltip';
    target.appendChild(tooltip);
    return tooltip;
}

function showChartTooltip(target, tooltip, event, html) {
    const rect = target.getBoundingClientRect();
    tooltip.innerHTML = html;
    tooltip.style.display = 'block';
    const left = Math.min(rect.width - tooltip.offsetWidth - 10, Math.max(10, event.clientX - rect.left + 12));
    const top = Math.min(rect.height - tooltip.offsetHeight - 10, Math.max(10, event.clientY - rect.top + 12));
    tooltip.style.left = left + 'px';
    tooltip.style.top = top + 'px';
}

function hideChartTooltip(tooltip) {
    tooltip.style.display = 'none';
}

function renderChartEmpty(target, message) {
    const empty = document.createElement('div');
    empty.className = 'staff-chart-empty';
    empty.textContent = message || 'No results match the selected filters.';
    target.appendChild(empty);
}

function sharedTooltipHtml(title, rows, includeTotal) {
    const total = rows.reduce(function (sum, row) { return sum + row.value; }, 0);
    const body = rows.map(function (row) {
        return '<span class="aws-chart-series-label">' + row.name + '</span>: <strong>' + row.value + '</strong>';
    }).join('<br>');
    return '<span class="aws-chart-title"><strong>' + title + '</strong></span><br>' + body
        + (includeTotal ? '<br><span class="aws-chart-total">Total: </span><strong>' + total + '</strong>' : '');
}

function renderGroupedBarChart(targetId, series) {
    const target = document.getElementById(targetId);
    if (!target) return;
    target.innerHTML = '';
    if (!series.length || !series.some(function (item) { return (item.dataPoints || []).length > 0; })) {
        renderChartEmpty(target, target.dataset.emptyMessage);
        return;
    }
    const width = 980;
    const height = 340;
    const margin = { top: 42, right: 28, bottom: 58, left: 42 };
    const tooltip = createChartTooltip(target);
    const svg = chartSvg(width, height);
    const points = (series[0] && series[0].dataPoints) || [];
    const axis = integerChartAxis(Math.max.apply(null, series.flatMap(function (item) {
        return item.dataPoints.map(function (point) { return point.y || 0; });
    }).concat([0])), 4);
    const maxY = axis.max;
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
    const hoverLayer = svgEl('g', {});
    renderLegend(svg, series, Math.max(margin.left, (width - series.length * 135) / 2), 18);
    axis.ticks.forEach(function (tick) {
        const y = margin.top + plotH - (plotH * tick / maxY);
        svg.appendChild(svgEl('line', { x1: margin.left, y1: y, x2: width - margin.right, y2: y, stroke: '#d5dbdb', 'stroke-width': 1 }));
        addText(svg, tick, 8, y + 4, { fill: '#414d5c', 'font-size': 11, 'font-weight': 700 });
    });
    const groupW = plotW / Math.max(points.length, 1);
    const barW = Math.max(5, Math.min(18, groupW / (series.length + 1.6)));
    points.forEach(function (point, pointIndex) {
        const groupX = margin.left + groupW * pointIndex + groupW / 2;
        const rows = series.map(function (item) {
            const value = (item.dataPoints[pointIndex] && item.dataPoints[pointIndex].y) || 0;
            return { name: item.name, value: value, color: item.color || '#0972d3' };
        });
        series.forEach(function (item, seriesIndex) {
            const value = (item.dataPoints[pointIndex] && item.dataPoints[pointIndex].y) || 0;
            const barH = value / maxY * plotH;
            const x = groupX - (series.length * barW + (series.length - 1) * 4) / 2 + seriesIndex * (barW + 4);
            const y = margin.top + plotH - barH;
            const bar = svgEl('rect', { x: x, y: y, width: barW, height: Math.max(1, barH), rx: 1, fill: item.color || '#0972d3', cursor: 'pointer' });
            bar.addEventListener('mouseenter', function (event) {
                bar.setAttribute('opacity', '0.78');
                showChartTooltip(target, tooltip, event, sharedTooltipHtml(point.label || '', rows, true));
            });
            bar.addEventListener('mousemove', function (event) {
                showChartTooltip(target, tooltip, event, sharedTooltipHtml(point.label || '', rows, true));
            });
            bar.addEventListener('mouseleave', function () {
                bar.removeAttribute('opacity');
                hideChartTooltip(tooltip);
            });
            svg.appendChild(bar);
        });
        const hitZone = svgEl('rect', {
            x: margin.left + groupW * pointIndex,
            y: margin.top,
            width: groupW,
            height: plotH,
            fill: 'transparent',
            cursor: 'pointer'
        });
        hitZone.addEventListener('mouseenter', function (event) {
            showChartTooltip(target, tooltip, event, sharedTooltipHtml(point.label || '', rows, true));
        });
        hitZone.addEventListener('mousemove', function (event) {
            showChartTooltip(target, tooltip, event, sharedTooltipHtml(point.label || '', rows, true));
        });
        hitZone.addEventListener('mouseleave', function () {
            hideChartTooltip(tooltip);
        });
        hoverLayer.appendChild(hitZone);
        addText(svg, point.label || '', groupX, height - 18, { 'text-anchor': 'middle', fill: '#414d5c', 'font-size': 11, 'font-weight': 700 });
    });
    svg.appendChild(svgEl('line', { x1: margin.left, y1: margin.top + plotH, x2: width - margin.right, y2: margin.top + plotH, stroke: '#879596' }));
    svg.appendChild(hoverLayer);
    target.appendChild(svg);
}

function aggregateTrendSeries(series, interval) {
    if (interval === 'monthly') return series;
    return series.map(function (item) {
        const buckets = {};
        item.dataPoints.forEach(function (point) {
            const date = new Date(point.x);
            const key = interval === 'yearly'
                ? String(date.getFullYear())
                : date.getFullYear() + ' Q' + (Math.floor(date.getMonth() / 3) + 1);
            if (!buckets[key]) {
                buckets[key] = { label: key, x: date, y: 0 };
            }
            buckets[key].y += point.y || 0;
        });
        return Object.assign({}, item, { dataPoints: Object.keys(buckets).map(function (key) { return buckets[key]; }) });
    });
}

function bucketCount(series, interval) {
    const scoped = aggregateTrendSeries(visibleTrendSeries(series), interval || 'monthly');
    return (scoped[0] && scoped[0].dataPoints && scoped[0].dataPoints.length) || 1;
}

function visibleTrendSeries(series) {
    const staffNames = ['Reviewed', 'Approved', 'Rejected', 'Pending', 'Disbursed', 'Defaulted'];
    const stationNames = ['Applied', 'Active', 'Disbursed', 'Paid', 'Defaulted', 'Rejected'];
    const staffOnlyNames = ['Reviewed', 'Approved', 'Pending'];
    const hasStaffSeries = series.some(function (item) { return staffOnlyNames.indexOf(item.name) >= 0; });
    const names = hasStaffSeries ? staffNames : stationNames;
    return series.filter(function (item) {
        return names.indexOf(item.name) >= 0;
    });
}

function limitTrendRange(series, rangeCount) {
    const count = Math.max(1, Number(rangeCount) || ((series[0] && series[0].dataPoints.length) || 1));
    return series.map(function (item) {
        return Object.assign({}, item, {
            dataPoints: (item.dataPoints || []).slice(-count)
        });
    });
}

function renderLineChart(targetId, series, interval, rangeCount) {
    const target = document.getElementById(targetId);
    if (!target) return;
    target.innerHTML = '';
    series = aggregateTrendSeries(visibleTrendSeries(series), interval || 'monthly');
    series = limitTrendRange(series, rangeCount);
    if (!series.length || !series.some(function (item) { return (item.dataPoints || []).length > 0; })) {
        renderChartEmpty(target, target.dataset.emptyMessage);
        return;
    }
    const width = 980;
    const height = 340;
    const margin = { top: 42, right: 28, bottom: 58, left: 42 };
    const tooltip = createChartTooltip(target);
    const svg = chartSvg(width, height);
    const firstPoints = (series[0] && series[0].dataPoints) || [];
    const axis = integerChartAxis(Math.max.apply(null, series.flatMap(function (item) {
        return item.dataPoints.map(function (point) { return point.y || 0; });
    }).concat([0])), 4);
    const maxY = axis.max;
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
    const hoverLayer = svgEl('g', {});
    const guide = svgEl('line', { x1: margin.left, y1: margin.top, x2: margin.left, y2: margin.top + plotH, stroke: '#687078', 'stroke-width': 1, 'stroke-dasharray': '4 4', opacity: 0 });
    renderLegend(svg, series, Math.max(margin.left, (width - series.length * 115) / 2), 18);
    axis.ticks.forEach(function (tick) {
        const y = margin.top + plotH - (plotH * tick / maxY);
        svg.appendChild(svgEl('line', { x1: margin.left, y1: y, x2: width - margin.right, y2: y, stroke: '#d5dbdb', 'stroke-width': 1 }));
        addText(svg, tick, 8, y + 4, { fill: '#414d5c', 'font-size': 11, 'font-weight': 700 });
    });
    const step = firstPoints.length > 1 ? plotW / (firstPoints.length - 1) : plotW;
    const labelEvery = Math.max(1, Math.ceil(firstPoints.length / 8));
    firstPoints.forEach(function (point, index) {
        if (index % labelEvery !== 0 && index !== firstPoints.length - 1) return;
        const label = point.label || new Date(point.x).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        addText(svg, label, margin.left + step * index, height - 18, { 'text-anchor': 'middle', fill: '#414d5c', 'font-size': 11, 'font-weight': 700 });
    });
    svg.appendChild(guide);
    series.forEach(function (item) {
        const path = item.dataPoints.map(function (point, index) {
            const x = margin.left + step * index;
            const y = margin.top + plotH - ((point.y || 0) / maxY * plotH);
            return (index === 0 ? 'M' : 'L') + x + ' ' + y;
        }).join(' ');
        svg.appendChild(svgEl('path', { d: path, fill: 'none', stroke: item.color || '#0972d3', 'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
        item.dataPoints.forEach(function (point, index) {
            const x = margin.left + step * index;
            const y = margin.top + plotH - ((point.y || 0) / maxY * plotH);
            const dot = svgEl('circle', { cx: x, cy: y, r: 3, fill: item.color || '#0972d3', cursor: 'pointer' });
            const label = point.label || new Date(point.x).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
            dot.addEventListener('mouseenter', function (event) {
                dot.setAttribute('r', '6');
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            dot.addEventListener('mousemove', function (event) {
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            dot.addEventListener('mouseleave', function () {
                dot.setAttribute('r', '3');
                hideChartTooltip(tooltip);
            });
            svg.appendChild(dot);
            const hitDot = svgEl('circle', { cx: x, cy: y, r: 12, fill: 'transparent', cursor: 'pointer' });
            hitDot.addEventListener('mouseenter', function (event) {
                dot.setAttribute('r', '6');
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            hitDot.addEventListener('mousemove', function (event) {
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            hitDot.addEventListener('mouseleave', function () {
                dot.setAttribute('r', '3');
                hideChartTooltip(tooltip);
            });
            svg.appendChild(hitDot);
        });
    });
    firstPoints.forEach(function (point, index) {
        const x = margin.left + step * index;
        const label = point.label || new Date(point.x).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        const rows = series.map(function (item) {
            const value = (item.dataPoints[index] && item.dataPoints[index].y) || 0;
            return { name: item.name, value: value, color: item.color || '#0972d3' };
        });
        const zoneX = index === 0 ? margin.left : x - step / 2;
        const zoneW = index === firstPoints.length - 1 ? step / 2 + margin.right : step;
        const hitZone = svgEl('rect', {
            x: zoneX,
            y: margin.top,
            width: Math.max(24, zoneW),
            height: plotH,
            fill: 'transparent',
            cursor: 'pointer'
        });
        hitZone.addEventListener('mouseenter', function (event) {
            guide.setAttribute('x1', x);
            guide.setAttribute('x2', x);
            guide.setAttribute('opacity', '1');
            showChartTooltip(target, tooltip, event, sharedTooltipHtml(label, rows, false));
        });
        hitZone.addEventListener('mousemove', function (event) {
            showChartTooltip(target, tooltip, event, sharedTooltipHtml(label, rows, false));
        });
        hitZone.addEventListener('mouseleave', function () {
            guide.setAttribute('opacity', '0');
            hideChartTooltip(tooltip);
        });
        hoverLayer.appendChild(hitZone);
    });
    svg.appendChild(svgEl('line', { x1: margin.left, y1: margin.top + plotH, x2: width - margin.right, y2: margin.top + plotH, stroke: '#879596' }));
    svg.appendChild(hoverLayer);
    target.appendChild(svg);
}
</script>

<%@ include file="../fragments/footer.jspf" %>
