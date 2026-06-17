<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .staff-analytics-actions,
    .staff-analytics-filter,
    .staff-view-switch {
        display: flex;
        gap: 0.75rem;
    }
    .staff-analytics-actions {
        align-items: center;
    }
    .staff-analytics-actions,
    .staff-analytics-filter,
    .staff-view-switch {
        flex-wrap: wrap;
    }
    .staff-analytics-filter {
        align-items: flex-end;
        flex-wrap: wrap;
        gap: 0.8rem;
    }
    .staff-analytics-control {
        flex: 1 1 11.75rem;
        min-width: 0;
    }
    .staff-analytics-control.is-date {
        flex: 0.95 1 10.75rem;
    }
    .staff-analytics-control.is-view {
        flex: 1.15 1 15rem;
    }
    .staff-analytics-control input,
    .staff-analytics-control select,
    .staff-view-option,
    .staff-filter-action {
        height: 2.875rem;
        min-height: 2.875rem;
        box-sizing: border-box;
    }
    .staff-view-switch {
        align-items: stretch;
        gap: 0;
        flex-wrap: nowrap;
    }
    .staff-view-option {
        display: inline-flex;
        flex: 1 1 0;
        min-width: 0;
        align-items: center;
        justify-content: center;
        gap: 0.45rem;
        border: 1px solid #cbd5e1;
        background: #fff;
        color: #0f172a;
        font-size: 0.86rem;
        font-weight: 700;
        transition: all 160ms ease;
        white-space: nowrap;
    }
    .staff-view-option span {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .staff-view-option:first-child {
        border-radius: 0.375rem 0 0 0.375rem;
    }
    .staff-view-option:last-child {
        border-left: 0;
        border-radius: 0 0.375rem 0.375rem 0;
    }
    .staff-view-option.is-active {
        border-color: #2563eb;
        background: #eff6ff;
        color: #1d4ed8;
        box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.18);
    }
    .staff-filter-actions {
        display: flex;
        flex: 0 0 auto;
        gap: 0.6rem;
    }
    .staff-filter-action {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 6.5rem;
        padding-top: 0;
        padding-bottom: 0;
    }
    .staff-metric-grid {
        display: grid;
        grid-template-columns: repeat(1, minmax(0, 1fr));
        gap: 1rem;
        align-items: stretch;
        grid-auto-rows: 1fr;
    }
    .staff-metric-grid > .staff-metric-card {
        grid-column: span 1;
    }
    .staff-metric-card {
        min-height: 8.75rem;
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        gap: 0.7rem;
        padding: 0.85rem;
        box-shadow: 0 12px 26px rgba(15, 23, 42, 0.05);
        overflow: hidden;
    }
    .staff-metric-main {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: start;
        gap: 0.7rem;
    }
    .staff-metric-icon,
    .staff-summary-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
    }
    .staff-metric-icon {
        width: 2.55rem;
        height: 2.55rem;
        border-radius: 0.7rem;
    }
    .staff-metric-icon svg {
        width: 1.45rem;
        height: 1.45rem;
        stroke-width: 1.9;
    }
    .staff-metric-title {
        color: #0f172a;
        font-size: 0.68rem;
        font-weight: 800;
        line-height: 1.25;
        overflow-wrap: anywhere;
    }
    .staff-metric-value {
        margin-top: 0.25rem;
        color: #0f172a;
        font-size: 1.45rem;
        line-height: 1;
        font-weight: 850;
    }
    .staff-metric-trend {
        margin-top: 0.4rem;
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.35rem;
        color: #475569;
        font-size: 0.58rem;
        line-height: 1.2;
    }
    .staff-metric-trend strong {
        display: inline-flex;
        align-items: center;
        gap: 0.16rem;
        color: #059669;
        font-size: 0.68rem;
        font-weight: 850;
        white-space: nowrap;
    }
    .staff-metric-trend.is-negative strong {
        color: #ef4444;
    }
    .staff-spark-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.45rem;
    }
    .staff-sparkline {
        width: 100%;
        height: 1.55rem;
    }
    .staff-sparkline svg {
        display: block;
        width: 100%;
        height: 100%;
        overflow: visible;
    }
    .staff-card-info {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 0.9rem;
        height: 0.9rem;
        border: 1px solid #cbd5e1;
        border-radius: 999px;
        color: #64748b;
        font-size: 0.56rem;
        font-weight: 800;
        line-height: 1;
    }
    .staff-comparison-bar {
        display: flex;
        align-items: center;
        gap: 0.65rem;
        border: 1px solid #dbeafe;
        background: #eff6ff;
        color: #1e3a8a;
        font-size: 0.8rem;
        line-height: 1.35;
    }
    .staff-summary-grid {
        display: grid;
        grid-template-columns: repeat(1, minmax(0, 1fr));
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        overflow: hidden;
    }
    .staff-summary-item {
        min-height: 5.4rem;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: center;
        gap: 0.85rem;
        padding: 1rem;
        border-bottom: 1px solid #e2e8f0;
    }
    .staff-summary-item:last-child {
        border-bottom: 0;
    }
    .staff-summary-icon {
        width: 2.8rem;
        height: 2.8rem;
        border-radius: 0.85rem;
    }
    .staff-summary-icon svg {
        width: 1.7rem;
        height: 1.7rem;
        stroke-width: 2;
    }
    .staff-risk-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr);
        gap: 1rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        padding: 1rem;
    }
    .staff-risk-status {
        margin-top: 0.35rem;
        color: #047857;
        font-size: 1.15rem;
        line-height: 1.1;
        font-weight: 800;
    }
    .staff-interest-summary {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: center;
        gap: 0.85rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        background: #fff;
        padding: 1rem;
    }
    .staff-interest-meta {
        margin-top: 0.35rem;
        color: #64748b;
        font-size: 0.78rem;
        line-height: 1.35;
    }
    .staff-financial-breakdown-grid {
        display: grid;
        grid-template-columns: repeat(1, minmax(0, 1fr));
        gap: 0.75rem;
    }
    .staff-financial-breakdown-card {
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        background: #fff;
        padding: 0.9rem;
    }
    .staff-financial-table-wrap {
        overflow-x: auto;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
    }
    .staff-financial-table {
        min-width: 44rem;
        width: 100%;
        border-collapse: collapse;
        background: #fff;
    }
    .staff-financial-table th,
    .staff-financial-table td {
        border-bottom: 1px solid #e2e8f0;
        padding: 0.7rem 0.8rem;
        text-align: left;
        font-size: 0.82rem;
    }
    .staff-financial-table th {
        background: #f8fafc;
        color: #475569;
        font-size: 0.7rem;
        font-weight: 800;
        letter-spacing: 0.08em;
        text-transform: uppercase;
    }
    .staff-financial-table tr:last-child td {
        border-bottom: 0;
    }
    .staff-chart-box {
        position: relative;
        width: 100%;
        height: 350px;
    }
    .staff-chart-box svg {
        display: block;
        width: 100%;
        height: 100%;
        overflow: visible;
    }
    .staff-chart-tooltip {
        position: absolute;
        z-index: 5;
        display: none;
        max-width: 15rem;
        padding: 0.45rem 0.6rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        background: #fff;
        color: #0f172a;
        font-size: 0.75rem;
        line-height: 1.3;
        box-shadow: 0 12px 24px rgba(15, 23, 42, 0.13);
        pointer-events: none;
    }
    .staff-trend-controls {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: flex-end;
        gap: 0.55rem;
    }
    .staff-trend-select {
        width: 8.5rem;
    }
    .staff-trend-range {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        height: 2.5rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        background: #fff;
        padding: 0 0.65rem;
        color: #334155;
        font-size: 0.82rem;
        font-weight: 700;
    }
    .staff-trend-range input {
        width: 3.5rem;
        height: 1.9rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.3rem;
        padding: 0 0.35rem;
        text-align: center;
        color: #0f172a;
    }
    .staff-info-bar {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        border: 1px solid #dbeafe;
        background: #eff6ff;
        color: #1e3a8a;
        font-size: 0.82rem;
        line-height: 1.35;
    }
    .tone-blue { background: #dbeafe; color: #2563eb; }
    .tone-emerald { background: #dcfce7; color: #059669; }
    .tone-violet { background: #f3e8ff; color: #7e22ce; }
    .tone-green { background: #dcfce7; color: #15803d; }
    .tone-orange { background: #ffedd5; color: #f97316; }
    .tone-rose { background: #fee2e2; color: #ef4444; }
    .tone-slate { background: #f1f5f9; color: #1e293b; }
    @media (min-width: 640px) {
        .staff-metric-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .staff-summary-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .staff-financial-breakdown-grid {
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }
        .staff-summary-item {
            border-right: 1px solid #e2e8f0;
        }
        .staff-summary-item:nth-child(2n) {
            border-right: 0;
        }
        .staff-summary-item:nth-last-child(-n + 2) {
            border-bottom: 0;
        }
    }
    @media (min-width: 1280px) {
        .staff-analytics-filter {
            flex-wrap: nowrap;
        }
        .staff-analytics-control {
            flex: 1 1 9.75rem;
        }
        .staff-analytics-control.is-date {
            flex: 0.72 1 8.6rem;
        }
        .staff-analytics-control.is-view {
            flex: 0.95 1 12.25rem;
        }
        .staff-filter-actions {
            flex: 0 0 auto;
        }
        .staff-filter-action {
            min-width: 5.75rem;
            padding-left: 0.9rem;
            padding-right: 0.9rem;
        }
        .staff-metric-grid {
            grid-template-columns: repeat(12, minmax(0, 1fr));
            gap: 1rem;
        }
        .staff-metric-grid > .staff-metric-card {
            grid-column: span 3;
        }
        .staff-metric-grid > .staff-metric-card:nth-child(n + 5) {
            grid-column: span 4;
        }
        .staff-summary-grid {
            grid-template-columns: repeat(5, minmax(0, 1fr));
        }
        .staff-summary-item,
        .staff-summary-item:nth-child(2n) {
            border-right: 1px solid #e2e8f0;
            border-bottom: 0;
        }
        .staff-summary-item:last-child {
            border-right: 0;
        }
        .staff-risk-row {
            grid-template-columns: 12rem minmax(0, 1fr);
            align-items: center;
        }
    }
    @media (min-width: 1536px) {
        .staff-analytics-filter {
            flex-wrap: nowrap;
            gap: 0.7rem;
        }
        .staff-analytics-control {
            flex: 1 1 10.5rem;
        }
        .staff-analytics-control.is-date {
            flex: 0 0 11.25rem;
        }
        .staff-analytics-control.is-view {
            flex: 0 1 14.25rem;
        }
        .staff-filter-actions {
            flex: 0 0 auto;
        }
        .staff-metric-grid {
            grid-template-columns: repeat(12, minmax(0, 1fr));
            gap: 1rem;
        }
        .staff-metric-card {
            min-height: 8.75rem;
            gap: 0.7rem;
            padding: 0.85rem;
        }
        .staff-metric-icon {
            width: 2.55rem;
            height: 2.55rem;
        }
        .staff-metric-icon svg {
            width: 1.45rem;
            height: 1.45rem;
        }
        .staff-metric-value {
            font-size: 1.45rem;
        }
        .staff-metric-trend {
            font-size: 0.75rem;
        }
    }
    @media (max-width: 760px) {
        .staff-analytics-control,
        .staff-analytics-control.is-date,
        .staff-analytics-control.is-view {
            flex-basis: calc(50% - 0.375rem);
        }
        .staff-filter-actions {
            flex: 1 1 100%;
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .staff-trend-controls {
            justify-content: flex-start;
        }
    }
    @media (max-width: 520px) {
        .staff-analytics-control,
        .staff-analytics-control.is-date,
        .staff-analytics-control.is-view {
            flex-basis: 100%;
        }
        .staff-filter-actions {
            grid-template-columns: minmax(0, 1fr);
        }
    }
</style>

<c:set var="currentQuery" value="fromDate=${fromDate}&toDate=${toDate}&loanType=${loanType}&viewAs=${viewAs}" />

<div class="erp-page-header">
    <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
            <h1 class="erp-page-title">${staffAnalyticsTitle}</h1>
            <p class="erp-page-subtitle">${staffAnalyticsSubtitle}</p>
        </div>
        <div class="staff-analytics-actions md:justify-end">
            <a href="/documents/reports/staff-loan-analytics.pdf?${currentQuery}" class="app-btn btn-neutral staff-filter-action">
                <span class="text-red-600" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15h6"/><path d="M9 18h4"/></svg>
                </span>
                <spring:message code="reports.exportPdf" text="Export PDF" />
            </a>
            <a href="/documents/reports/staff-loan-analytics.xlsx?${currentQuery}" class="app-btn btn-neutral staff-filter-action">
                <span class="text-emerald-600" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 15h8"/><path d="M8 18h8"/></svg>
                </span>
                <spring:message code="reports.exportExcel" text="Export Excel" />
            </a>
            <a href="/staff/analytics" class="app-btn btn-neutral staff-filter-action">
                <span class="text-blue-600" aria-hidden="true">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 0 1-15.6 6.1"/><path d="M3 12A9 9 0 0 1 18.6 5.9"/><path d="M3 18h5v-5"/><path d="M21 6h-5v5"/></svg>
                </span>
                <spring:message code="common.refresh" text="Refresh" />
            </a>
        </div>
    </div>
</div>

<section class="erp-panel overflow-hidden">
    <form action="/staff/analytics" method="get" class="erp-panel-body staff-analytics-filter">
        <label class="staff-analytics-control is-date block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.startDate" text="Start Date" />
            <input name="fromDate" type="date" value="${fromDate}" class="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-800" />
        </label>
        <label class="staff-analytics-control is-date block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.endDate" text="End Date" />
            <input name="toDate" type="date" value="${toDate}" class="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-800" />
        </label>
        <label class="staff-analytics-control block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.loanProduct" text="Loan Product" />
            <select name="loanType" class="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-800">
                <option value=""><spring:message code="reports.allProducts" text="All Products" /></option>
                <c:forEach items="${loanTypes}" var="type">
                    <option value="${type}" ${loanType eq type ? 'selected' : ''}>${type.displayLabel}</option>
                </c:forEach>
            </select>
        </label>
        <div class="staff-analytics-control is-view block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="staff.analytics.viewAs" text="View As" />
            <div class="staff-view-switch mt-1">
                <button type="submit" name="viewAs" value="member" title="<spring:message code='staff.analytics.memberView' text='Member View' />" aria-label="<spring:message code='staff.analytics.memberView' text='Member View' />" class="staff-view-option ${viewAs eq 'member' ? 'is-active' : ''}" ${canViewMemberAnalytics ? '' : 'disabled'}>
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>
                    <span><spring:message code="staff.analytics.memberView" text="Member View" /></span>
                </button>
                <button type="submit" name="viewAs" value="staff" title="<spring:message code='staff.analytics.staffView' text='Staff View' />" aria-label="<spring:message code='staff.analytics.staffView' text='Staff View' />" class="staff-view-option ${viewAs eq 'staff' ? 'is-active' : ''}">
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21a6 6 0 0 0-12 0"/><circle cx="11" cy="7" r="4"/><path d="M19 8v6"/><path d="M22 11h-6"/></svg>
                    <span>
                        <c:choose>
                            <c:when test="${stationWideStaffView}"><spring:message code="staff.analytics.stationView" text="Station View" /></c:when>
                            <c:otherwise><spring:message code="staff.analytics.staffView" text="Staff View" /></c:otherwise>
                        </c:choose>
                    </span>
                </button>
            </div>
        </div>
        <div class="staff-filter-actions">
            <button type="submit" class="app-btn btn-primary staff-filter-action"><spring:message code="common.applyFilters" text="Apply Filter" /></button>
            <a href="/staff/analytics" class="app-btn btn-neutral staff-filter-action"><spring:message code="common.reset" text="Reset" /></a>
        </div>
    </form>
</section>

<div class="staff-comparison-bar rounded-md px-4 py-3">
    <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">i</span>
    <span>${metricComparisonLabel}</span>
</div>

<section class="staff-metric-grid">
    <c:forEach items="${metricCards}" var="card">
        <div class="erp-panel staff-metric-card" data-spark-key="${card.sparkName}" data-spark-tone="${card.tone}" title="${card.label}: ${card.value}">
            <div class="staff-metric-main">
                <span class="staff-metric-icon tone-${card.tone}">
                    <c:choose>
                        <c:when test="${card.key eq 'applied'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15h6"/><circle cx="17" cy="17" r="4"/><path d="M15.8 17l.8.8 1.6-1.8"/></svg></c:when>
                        <c:when test="${card.key eq 'active'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6 2h12"/><path d="M6 22h12"/><path d="M8 2c0 5 8 5 8 10s-8 5-8 10"/><path d="M16 2c0 5-8 5-8 10s8 5 8 10"/><path d="M9 12h6"/></svg></c:when>
                        <c:when test="${card.key eq 'disbursed'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M3 10h18"/><path d="M5 10l7-5 7 5"/><path d="M6 10v8"/><path d="M10 10v8"/><path d="M14 10v8"/><path d="M18 10v8"/><path d="M4 18h16"/><path d="M3 22h18"/></svg></c:when>
                        <c:when test="${card.key eq 'paid'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></c:when>
                        <c:when test="${card.key eq 'defaulted'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></c:when>
                        <c:when test="${card.key eq 'forfeited'}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg></c:when>
                        <c:otherwise><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12h8"/></svg></c:otherwise>
                    </c:choose>
                </span>
                <div class="min-w-0">
                    <p class="staff-metric-title">${card.label}</p>
                    <p class="staff-metric-value"><fmt:formatNumber value="${card.value}" /></p>
                    <p class="staff-metric-trend ${card.positive ? '' : 'is-negative'}">
                        <strong>
                            <c:choose>
                                <c:when test="${card.positive}">&uarr;</c:when>
                                <c:otherwise>&darr;</c:otherwise>
                            </c:choose>
                            ${card.percentLabel}
                        </strong>
                        <span>vs ${metricPeriodLabel}</span>
                    </p>
                </div>
            </div>
            <div class="staff-spark-row">
                <div class="staff-sparkline" aria-hidden="true"></div>
                <span class="staff-card-info" title="${card.label} trend">i</span>
            </div>
        </div>
    </c:forEach>
</section>

<section class="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
    <div class="space-y-4">
        <section class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
                <h2 class="text-lg font-bold text-sacco-ink">
                    <c:choose>
                        <c:when test="${viewAs eq 'member'}"><spring:message code="staff.analytics.memberPortfolioSummary" text="Member Portfolio Summary" /></c:when>
                        <c:when test="${stationWideStaffView}"><spring:message code="staff.analytics.stationPortfolioSummary" text="Station Portfolio Summary" /></c:when>
                        <c:otherwise><spring:message code="staff.analytics.staffPortfolioSummary" text="Staff Portfolio Summary" /></c:otherwise>
                    </c:choose>
                </h2>
            </div>
            <div class="erp-panel-body space-y-4">
                <div class="staff-summary-grid">
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M16 21v-2a4 4 0 0 0-8 0v2"/><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a4 4 0 0 1 3-3.87"/><path d="M20 21v-2a4 4 0 0 0-3-3.87"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansHandled" text="Loans Handled" /></p><p class="staff-metric-value text-[1.35rem]"><fmt:formatNumber value="${staffPortfolio.handledLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansApproved" text="Loans Approved" /></p><p class="staff-metric-value text-[1.35rem]"><fmt:formatNumber value="${staffPortfolio.approvedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-rose"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansRejected" text="Loans Rejected" /></p><p class="staff-metric-value text-[1.35rem]"><fmt:formatNumber value="${staffPortfolio.rejectedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-violet"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M3 10h18"/><path d="M5 10l7-5 7 5"/><path d="M6 10v8"/><path d="M18 10v8"/><path d="M4 18h16"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.loansDisbursed" text="Loans Disbursed" /></p><p class="staff-metric-value text-[1.35rem]"><fmt:formatNumber value="${staffPortfolio.disbursedLoans}" /></p></div>
                    </div>
                    <div class="staff-summary-item">
                        <span class="staff-summary-icon tone-orange"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></span>
                        <div><p class="erp-widget-title"><spring:message code="staff.analytics.defaultedAfterApproval" text="Defaulted After Approval" /></p><p class="staff-metric-value text-[1.35rem]"><fmt:formatNumber value="${staffPortfolio.defaultedAfterApproval}" /></p></div>
                    </div>
                </div>
                <c:if test="${stationWideStaffView}">
                    <div class="staff-interest-summary">
                        <span class="staff-summary-icon tone-emerald"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 3v18"/><path d="M17 7H9.5a3.5 3.5 0 0 0 0 7H14a3.5 3.5 0 0 1 0 7H6"/></svg></span>
                        <div class="min-w-0">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalInterestAccumulated" text="Total Paid Interest Accumulated" /></p>
                            <p class="staff-metric-value text-[1.35rem]">${totalInterestAccumulatedLabel}</p>
                            <p class="staff-interest-meta">
                                <spring:message code="staff.analytics.totalInterestScope" arguments="${selectedLoanProductLabel},${fromDate},${toDate}" text="Based on {0} from {1} to {2}." />
                            </p>
                        </div>
                    </div>
                    <div class="staff-financial-breakdown-grid">
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalInterestUnpaid" text="Total Interest Unpaid Yet" /></p>
                            <p class="staff-metric-value text-[1.2rem]">${totalInterestUnpaidLabel}</p>
                        </div>
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalLoanAmountPaid" text="Total Loan Amount Paid" /></p>
                            <p class="staff-metric-value text-[1.2rem]">${totalLoanAmountPaidLabel}</p>
                        </div>
                        <div class="staff-financial-breakdown-card">
                            <p class="erp-widget-title"><spring:message code="staff.analytics.totalLoanAmountUnpaid" text="Total Loan Amount Unpaid Yet" /></p>
                            <p class="staff-metric-value text-[1.2rem]">${totalLoanAmountUnpaidLabel}</p>
                        </div>
                    </div>
                    <c:if test="${empty loanType}">
                        <div class="staff-financial-table-wrap">
                            <table class="staff-financial-table">
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
                                            <td class="font-semibold text-slate-800">${row.loanProduct}</td>
                                            <td>${row.totalInterestPaidLabel}</td>
                                            <td>${row.totalInterestUnpaidLabel}</td>
                                            <td>${row.totalLoanAmountPaidLabel}</td>
                                            <td>${row.totalLoanAmountUnpaidLabel}</td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty productFinancialRows}">
                                        <tr>
                                            <td colspan="5" class="text-slate-500"><spring:message code="staff.analytics.financialBreakdownEmpty" text="No financial breakdown is available for the selected filters." /></td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </c:if>
                </c:if>
                <div class="staff-risk-row">
                    <div class="flex items-center gap-3 border-b border-slate-200 pb-4 xl:border-b-0 xl:border-r xl:pb-0 xl:pr-4">
                        <span class="staff-summary-icon tone-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"/><path d="M9 12l2 2 4-5"/></svg></span>
                        <div><p class="staff-metric-title"><spring:message code="staff.analytics.portfolioRiskLevel" text="Portfolio Risk Level" /></p><p class="staff-risk-status">${staffPortfolio.riskLevel}</p></div>
                    </div>
                    <div>
                        <p class="text-base font-semibold text-slate-900"><spring:message code="staff.analytics.defaultedAfterApprovalRate" arguments="${staffPortfolio.defaultedAfterApprovalRate}" text="Defaulted-after-approval rate is {0}%" /></p>
                        <p class="mt-2 text-sm text-slate-600"><spring:message code="staff.analytics.defaultedAfterApprovalHelp" text="This is calculated based on approved loans during the selected period." /></p>
                    </div>
                </div>
            </div>
        </section>

        <section class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
                <h2 class="text-lg font-bold text-sacco-ink"><spring:message code="staff.analytics.loanProductPerformance" text="Loan Product Performance" /></h2>
            </div>
            <div class="erp-panel-body">
                <div id="staffProductChart" class="staff-chart-box"></div>
            </div>
        </section>
    </div>

    <section class="erp-panel overflow-hidden">
        <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <h2 class="text-lg font-bold text-sacco-ink"><spring:message code="reports.loanTrendOverTime" text="Loan Trend Over Time" /></h2>
            <div class="staff-trend-controls">
                <select id="staffTrendInterval" title="<spring:message code='staff.analytics.trendInterval' text='Trend interval' />" aria-label="<spring:message code='staff.analytics.trendInterval' text='Trend interval' />" class="staff-trend-select h-10 rounded-md border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700">
                    <option value="monthly"><spring:message code="reports.interval.monthly" text="Monthly" /></option>
                    <option value="quarterly"><spring:message code="reports.interval.quarterly" text="Quarterly" /></option>
                    <option value="yearly"><spring:message code="reports.interval.yearly" text="Yearly" /></option>
                </select>
                <label class="staff-trend-range" for="staffTrendRange">
                    <span>Last</span>
                    <input id="staffTrendRange" type="number" min="1" step="1" value="12" />
                    <span id="staffTrendRangeUnit">months</span>
                </label>
            </div>
        </div>
        <div class="erp-panel-body">
            <div id="staffTrendChart" class="staff-chart-box"></div>
        </div>
    </section>
</section>

<div class="staff-info-bar rounded-md px-4 py-3">
    <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">i</span>
    <span>
        <c:choose>
            <c:when test="${viewAs eq 'member'}"><spring:message code="staff.analytics.memberViewHelp" text="You are viewing analytics for loans you applied for as a member." /></c:when>
            <c:when test="${stationWideStaffView}"><spring:message code="staff.analytics.stationViewHelp" text="You are viewing station-wide loan status for all applicants and members in your station." /></c:when>
            <c:otherwise><spring:message code="staff.analytics.staffViewHelp" text="You are viewing analytics for loans you handled, reviewed, approved, rejected, disbursed, or managed." /></c:otherwise>
        </c:choose>
    </span>
</div>

<script>
window.addEventListener('load', function () {
    const productSeries = ${productPerformanceJson};
    const trendSeries = ${trendSeriesJson};
    const trendSelect = document.getElementById('staffTrendInterval');
    const trendRange = document.getElementById('staffTrendRange');
    const trendRangeUnit = document.getElementById('staffTrendRangeUnit');

    renderMetricSparklines(trendSeries);
    renderGroupedBarChart('staffProductChart', productSeries);
    syncTrendRangeControl(trendSeries, 'monthly');
    renderLineChart('staffTrendChart', trendSeries, 'monthly', Number(trendRange && trendRange.value));
    if (trendSelect) {
        trendSelect.addEventListener('change', function () {
            syncTrendRangeControl(trendSeries, trendSelect.value);
            renderLineChart('staffTrendChart', trendSeries, trendSelect.value, Number(trendRange && trendRange.value));
        });
    }
    if (trendRange) {
        trendRange.addEventListener('input', function () {
            const interval = trendSelect ? trendSelect.value : 'monthly';
            renderLineChart('staffTrendChart', trendSeries, interval, Number(trendRange.value));
        });
    }

    function syncTrendRangeControl(series, interval) {
        if (!trendRange || !trendRangeUnit) return;
        const buckets = bucketCount(series, interval);
        trendRange.max = String(Math.max(1, buckets));
        trendRange.value = String(Math.max(1, buckets));
        trendRangeUnit.textContent = interval === 'yearly' ? 'years' : interval === 'quarterly' ? 'quarters' : 'months';
    }

});

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
    const node = svgEl('text', Object.assign({ x: x, y: y, fill: '#0f172a', 'font-size': 12, 'font-weight': 700 }, attrs || {}));
    node.textContent = text;
    svg.appendChild(node);
    return node;
}

function renderLegend(svg, series, startX, y) {
    let x = startX;
    series.forEach(function (item) {
        svg.appendChild(svgEl('rect', { x: x, y: y - 9, width: 11, height: 11, rx: 2, fill: item.color || '#2563eb' }));
        addText(svg, item.name, x + 17, y, { fill: '#334155', 'font-size': 12, 'font-weight': 700 });
        x += Math.max(105, String(item.name).length * 8 + 36);
    });
}

function niceMax(value) {
    const safe = Math.max(1, value || 0);
    const magnitude = Math.pow(10, Math.floor(Math.log10(safe)));
    return Math.ceil(safe / magnitude) * magnitude;
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
            const baseY = height - pad - ((point.y || 0) / maxY * (height - pad * 2));
            const wave = Math.sin(index * 1.55 + tone.length) * 2.4;
            const y = Math.max(pad, Math.min(height - pad, baseY + wave));
            return { x: x, y: y };
        });
        const d = smoothPath(coords);
        const area = d + ' L ' + (coords[coords.length - 1] ? coords[coords.length - 1].x : width - pad) + ' ' + (height - pad)
            + ' L ' + (coords[0] ? coords[0].x : pad) + ' ' + (height - pad) + ' Z';
        svg.appendChild(svgEl('path', { d: area, fill: color, opacity: 0.08 }));
        svg.appendChild(svgEl('path', { d: d, fill: 'none', stroke: color, 'stroke-width': 2.2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
        target.appendChild(svg);
    });
}

function smoothPath(coords) {
    if (!coords.length) return '';
    if (coords.length === 1) {
        return 'M' + coords[0].x + ' ' + coords[0].y + ' L' + (coords[0].x + 1) + ' ' + coords[0].y;
    }
    let d = 'M' + coords[0].x + ' ' + coords[0].y;
    for (let i = 0; i < coords.length - 1; i++) {
        const current = coords[i];
        const next = coords[i + 1];
        const midX = (current.x + next.x) / 2;
        d += ' C ' + midX + ' ' + current.y + ', ' + midX + ' ' + next.y + ', ' + next.x + ' ' + next.y;
    }
    return d;
}

function toneColor(tone) {
    return {
        blue: '#2563eb',
        emerald: '#059669',
        violet: '#7c3aed',
        green: '#15803d',
        orange: '#f97316',
        rose: '#e11d48',
        slate: '#334155'
    }[tone] || '#2563eb';
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

function sharedTooltipHtml(title, rows, includeTotal) {
    const total = rows.reduce(function (sum, row) { return sum + row.value; }, 0);
    const body = rows.map(function (row) {
        return '<span style="color:' + row.color + '">' + row.name + '</span>: <strong>' + row.value + '</strong>';
    }).join('<br>');
    return '<span style="color:#2563eb"><strong>' + title + '</strong></span><br>' + body
        + (includeTotal ? '<br><span style="color:#ef4444">Total: </span><strong>' + total + '</strong>' : '');
}

function renderGroupedBarChart(targetId, series) {
    const target = document.getElementById(targetId);
    if (!target) return;
    target.innerHTML = '';
    const width = 980;
    const height = 340;
    const margin = { top: 42, right: 28, bottom: 58, left: 42 };
    const tooltip = createChartTooltip(target);
    const svg = chartSvg(width, height);
    const points = (series[0] && series[0].dataPoints) || [];
    const maxY = niceMax(Math.max.apply(null, series.flatMap(function (item) {
        return item.dataPoints.map(function (point) { return point.y || 0; });
    }).concat([0])));
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
    const hoverLayer = svgEl('g', {});
    renderLegend(svg, series, Math.max(margin.left, (width - series.length * 135) / 2), 18);
    for (let i = 0; i <= 4; i++) {
        const y = margin.top + plotH - (plotH * i / 4);
        svg.appendChild(svgEl('line', { x1: margin.left, y1: y, x2: width - margin.right, y2: y, stroke: '#e2e8f0', 'stroke-width': 1 }));
        addText(svg, Math.round(maxY * i / 4), 8, y + 4, { fill: '#334155', 'font-size': 11, 'font-weight': 600 });
    }
    const groupW = plotW / Math.max(points.length, 1);
    const barW = Math.max(5, Math.min(18, groupW / (series.length + 1.6)));
    points.forEach(function (point, pointIndex) {
        const groupX = margin.left + groupW * pointIndex + groupW / 2;
        const rows = series.map(function (item) {
            const value = (item.dataPoints[pointIndex] && item.dataPoints[pointIndex].y) || 0;
            return { name: item.name, value: value, color: item.color || '#2563eb' };
        });
        series.forEach(function (item, seriesIndex) {
            const value = (item.dataPoints[pointIndex] && item.dataPoints[pointIndex].y) || 0;
            const barH = value / maxY * plotH;
            const x = groupX - (series.length * barW + (series.length - 1) * 4) / 2 + seriesIndex * (barW + 4);
            const y = margin.top + plotH - barH;
            const bar = svgEl('rect', { x: x, y: y, width: barW, height: Math.max(1, barH), rx: 2, fill: item.color || '#2563eb', cursor: 'pointer' });
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
        addText(svg, point.label || '', groupX, height - 18, { 'text-anchor': 'middle', fill: '#334155', 'font-size': 11, 'font-weight': 700 });
    });
    svg.appendChild(svgEl('line', { x1: margin.left, y1: margin.top + plotH, x2: width - margin.right, y2: margin.top + plotH, stroke: '#cbd5e1' }));
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
    const scoped = aggregateTrendSeries(series.filter(function (item) {
        return ['Applied', 'Disbursed', 'Paid', 'Defaulted'].indexOf(item.name) >= 0;
    }), interval || 'monthly');
    return (scoped[0] && scoped[0].dataPoints && scoped[0].dataPoints.length) || 1;
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
    series = aggregateTrendSeries(series.filter(function (item) {
        return ['Applied', 'Disbursed', 'Paid', 'Defaulted'].indexOf(item.name) >= 0;
    }), interval || 'monthly');
    series = limitTrendRange(series, rangeCount);
    const width = 980;
    const height = 340;
    const margin = { top: 42, right: 28, bottom: 58, left: 42 };
    const tooltip = createChartTooltip(target);
    const svg = chartSvg(width, height);
    const firstPoints = (series[0] && series[0].dataPoints) || [];
    const maxY = niceMax(Math.max.apply(null, series.flatMap(function (item) {
        return item.dataPoints.map(function (point) { return point.y || 0; });
    }).concat([0])));
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
    const hoverLayer = svgEl('g', {});
    const guide = svgEl('line', { x1: margin.left, y1: margin.top, x2: margin.left, y2: margin.top + plotH, stroke: '#94a3b8', 'stroke-width': 1, 'stroke-dasharray': '4 4', opacity: 0 });
    renderLegend(svg, series, Math.max(margin.left, (width - series.length * 115) / 2), 18);
    for (let i = 0; i <= 4; i++) {
        const y = margin.top + plotH - (plotH * i / 4);
        svg.appendChild(svgEl('line', { x1: margin.left, y1: y, x2: width - margin.right, y2: y, stroke: '#e2e8f0', 'stroke-width': 1 }));
        addText(svg, Math.round(maxY * i / 4), 8, y + 4, { fill: '#334155', 'font-size': 11, 'font-weight': 600 });
    }
    const step = firstPoints.length > 1 ? plotW / (firstPoints.length - 1) : plotW;
    const labelEvery = Math.max(1, Math.ceil(firstPoints.length / 8));
    firstPoints.forEach(function (point, index) {
        if (index % labelEvery !== 0 && index !== firstPoints.length - 1) return;
        const label = point.label || new Date(point.x).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        addText(svg, label, margin.left + step * index, height - 18, { 'text-anchor': 'middle', fill: '#334155', 'font-size': 11, 'font-weight': 700 });
    });
    svg.appendChild(guide);
    series.forEach(function (item) {
        const path = item.dataPoints.map(function (point, index) {
            const x = margin.left + step * index;
            const y = margin.top + plotH - ((point.y || 0) / maxY * plotH);
            return (index === 0 ? 'M' : 'L') + x + ' ' + y;
        }).join(' ');
        svg.appendChild(svgEl('path', { d: path, fill: 'none', stroke: item.color || '#2563eb', 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
        item.dataPoints.forEach(function (point, index) {
            const x = margin.left + step * index;
            const y = margin.top + plotH - ((point.y || 0) / maxY * plotH);
            const dot = svgEl('circle', { cx: x, cy: y, r: 4, fill: item.color || '#2563eb', cursor: 'pointer' });
            const label = point.label || new Date(point.x).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
            dot.addEventListener('mouseenter', function (event) {
                dot.setAttribute('r', '6');
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            dot.addEventListener('mousemove', function (event) {
                showChartTooltip(target, tooltip, event, '<strong>' + label + '</strong><br>' + item.name + ': ' + (point.y || 0));
            });
            dot.addEventListener('mouseleave', function () {
                dot.setAttribute('r', '4');
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
                dot.setAttribute('r', '4');
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
            return { name: item.name, value: value, color: item.color || '#2563eb' };
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
    svg.appendChild(svgEl('line', { x1: margin.left, y1: margin.top + plotH, x2: width - margin.right, y2: margin.top + plotH, stroke: '#cbd5e1' }));
    svg.appendChild(hoverLayer);
    target.appendChild(svg);
}
</script>

<%@ include file="../fragments/footer.jspf" %>
