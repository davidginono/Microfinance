<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .loan-analytics-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.65rem;
    }
    .loan-analytics-filter {
        display: flex;
        flex-wrap: nowrap;
        align-items: end;
        gap: 0.8rem;
    }
    .loan-analytics-filter-field {
        flex: 0.95 1 8.75rem;
        min-width: 0;
    }
    .loan-analytics-filter-field.is-wide {
        flex: 1.08 1 10.5rem;
        min-width: 0;
    }
    .loan-analytics-filter input,
    .loan-analytics-filter select {
        height: 2.875rem;
        min-height: 2.875rem;
        box-sizing: border-box;
        line-height: 1.25rem;
    }
    .loan-analytics-filter-actions {
        display: flex;
        flex: 0 0 auto;
        gap: 0.6rem;
    }
    .loan-analytics-filter-actions .app-btn {
        min-height: 2.875rem;
        height: 2.875rem;
        align-items: center;
        box-sizing: border-box;
        padding-top: 0;
        padding-bottom: 0;
    }
    .loan-metrics-grid {
        display: grid;
        grid-template-columns: minmax(0, 1fr);
        align-items: stretch;
        grid-auto-rows: 1fr;
    }
    .loan-metrics-grid > .loan-metric-card {
        grid-column: span 1;
    }
    .loan-metric-card {
        min-height: 8.75rem;
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        gap: 0.7rem;
        padding: 0.85rem;
        box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
    }
    .loan-metric-main {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: start;
        gap: 0.7rem;
    }
    .loan-metric-icon {
        width: 2.55rem;
        height: 2.55rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 0.7rem;
        font-size: 1rem;
        font-weight: 800;
        letter-spacing: 0.04em;
    }
    .loan-metric-icon svg {
        width: 1.45rem;
        height: 1.45rem;
        stroke-width: 1.9;
    }
    .loan-metric-title {
        color: #0f172a;
        font-size: 0.68rem;
        font-weight: 800;
        line-height: 1.25;
    }
    .loan-metric-value {
        margin-top: 0.25rem;
        color: #0f172a;
        font-size: 1.45rem;
        font-weight: 800;
        line-height: 1;
    }
    .loan-metric-trend {
        margin-top: 0.4rem;
        display: flex;
        align-items: center;
        gap: 0.35rem;
        flex-wrap: wrap;
        color: #64748b;
        font-size: 0.58rem;
        line-height: 1.2;
    }
    .loan-metric-trend strong {
        display: inline-flex;
        align-items: center;
        gap: 0.16rem;
        color: #047857;
        font-weight: 800;
    }
    .loan-metric-trend.is-negative strong {
        color: #dc2626;
    }
    .loan-spark-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.45rem;
    }
    .loan-sparkline {
        width: 100%;
        height: 1.55rem;
    }
    .loan-sparkline svg {
        display: block;
        width: 100%;
        height: 100%;
        overflow: visible;
    }
    .loan-card-info {
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
    .tone-blue { background: #eff6ff; color: #2563eb; }
    .tone-emerald { background: #ecfdf5; color: #059669; }
    .tone-violet { background: #f5f3ff; color: #7c3aed; }
    .tone-green { background: #f0fdf4; color: #15803d; }
    .tone-orange { background: #fff7ed; color: #f97316; }
    .tone-rose { background: #fff1f2; color: #e11d48; }
    .tone-slate { background: #f1f5f9; color: #334155; }
    .loan-risk-card {
        min-height: 6.7rem;
        padding: 1.05rem 1.15rem;
        box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
    }
    .loan-risk-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
    }
    .loan-risk-badge {
        border-radius: 999px;
        background: #dcfce7;
        color: #166534;
        padding: 0.18rem 0.65rem;
        font-size: 0.72rem;
        font-weight: 800;
        white-space: nowrap;
    }
    .loan-risk-grid {
        margin-top: 0.95rem;
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        overflow: hidden;
    }
    .loan-risk-cell {
        padding: 0.75rem;
        border-right: 1px solid #e2e8f0;
        border-bottom: 1px solid #e2e8f0;
    }
    .loan-risk-cell:nth-child(3n) {
        border-right: 0;
    }
    .loan-risk-cell:nth-last-child(-n + 3) {
        border-bottom: 0;
    }
    .loan-risk-value {
        margin-top: 0.35rem;
        color: #0f172a;
        font-size: 1rem;
        font-weight: 800;
        line-height: 1.25;
    }
    .loan-risk-status {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        color: #047857;
    }
    .loan-risk-status.is-review {
        color: #be123c;
    }
    .loan-risk-status svg {
        width: 1rem;
        height: 1rem;
        stroke-width: 2.2;
    }
    .loan-risk-note {
        margin-top: 0;
        border-radius: 0 0 0.5rem 0.5rem;
        background: #f0fdf4;
        padding: 0.75rem;
        color: #334155;
        font-size: 0.78rem;
        line-height: 1.35;
    }
    .loan-chart-box {
        position: relative;
        height: 370px;
        width: 100%;
    }
    .loan-chart-box svg {
        display: block;
        width: 100%;
        height: 100%;
        overflow: visible;
    }
    .loan-chart-tooltip {
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
    @media (max-width: 900px) {
        .loan-analytics-filter {
            flex-wrap: wrap;
        }
        .loan-analytics-filter-field,
        .loan-analytics-filter-field.is-wide {
            flex: 1 1 13rem;
        }
    }
    @media (min-width: 640px) {
        .loan-metrics-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
    }
    @media (min-width: 1280px) {
        .loan-metrics-grid {
            grid-template-columns: repeat(12, minmax(0, 1fr));
        }
        .loan-metrics-grid > .loan-metric-card {
            grid-column: span 3;
        }
        .loan-metrics-grid > .loan-metric-card:nth-child(n + 5) {
            grid-column: span 4;
        }
    }
    @media (max-width: 760px) {
        .loan-analytics-filter {
            align-items: stretch;
        }
        .loan-analytics-filter-field,
        .loan-analytics-filter-field.is-wide,
        .loan-analytics-filter-actions {
            flex: 1 1 100%;
        }
        .loan-analytics-filter-actions {
            display: grid;
            grid-template-columns: minmax(0, 1fr);
        }
        .loan-risk-grid {
            grid-template-columns: minmax(0, 1fr);
        }
        .loan-risk-cell,
        .loan-risk-cell:nth-child(3n),
        .loan-risk-cell:nth-last-child(-n + 3) {
            border-right: 0;
            border-bottom: 1px solid #e2e8f0;
        }
        .loan-risk-cell:last-child {
            border-bottom: 0;
        }
    }
</style>

<c:set var="currentQuery" value="fromDate=${fromDate}&toDate=${toDate}&loanType=${loanType}" />

<div class="erp-page-header">
    <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
            <p class="erp-breadcrumb"><spring:message code="reports.member.breadcrumb" text="Member Workspace / Reports & Analytics" /></p>
            <h1 class="erp-page-title"><spring:message code="reports.member.title" text="Loan Reports and Analytics" /></h1>
            <p class="erp-page-subtitle"><spring:message code="reports.member.subtitle" text="View loan performance, status breakdown, and risk indicators within a selected period." /></p>
        </div>
        <div class="loan-analytics-actions md:justify-end">
            <a href="/documents/reports/member-loans.pdf?${currentQuery}" class="app-btn btn-neutral"><spring:message code="reports.exportPdf" text="Export PDF" /></a>
            <a href="/documents/reports/member-loans.csv?${currentQuery}" class="app-btn btn-neutral"><spring:message code="reports.exportCsv" text="Export CSV" /></a>
            <a href="/app/reports" class="app-btn btn-neutral"><spring:message code="common.refresh" text="Refresh" /></a>
        </div>
    </div>
</div>

<section class="erp-panel overflow-hidden">
    <form action="/app/reports" method="get" class="erp-panel-body loan-analytics-filter">
        <label class="loan-analytics-filter-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.startDate" text="Start Date" />
            <input name="fromDate" type="date" value="${fromDate}" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <label class="loan-analytics-filter-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.endDate" text="End Date" />
            <input name="toDate" type="date" value="${toDate}" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <label class="loan-analytics-filter-field is-wide block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.loanProduct" text="Loan Product" />
            <select name="loanType" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800">
                <option value=""><spring:message code="reports.allProducts" text="All Products" /></option>
                <c:forEach items="${loanTypes}" var="type">
                    <option value="${type}" ${loanType eq type ? 'selected' : ''}>${type.displayLabel}</option>
                </c:forEach>
            </select>
        </label>
        <div class="loan-analytics-filter-actions">
            <button type="submit" class="app-btn btn-primary justify-center px-6"><spring:message code="common.applyFilters" text="Apply Filter" /></button>
            <a href="/app/reports" class="app-btn btn-neutral justify-center px-6"><spring:message code="common.reset" text="Reset" /></a>
        </div>
    </form>
</section>

<section class="loan-metrics-grid gap-4">
    <c:forEach items="${metricCards}" var="card">
        <div class="erp-panel loan-metric-card" data-spark-key="${card.sparkName}" data-spark-tone="${card.tone}" title="${card.label}: ${card.value}">
            <div class="loan-metric-main">
                <span class="loan-metric-icon tone-${card.tone}">
                    <c:choose>
                        <c:when test="${card.key eq 'applied'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7z"/><path d="M14 2v5h5"/><path d="M9 14h5"/><path d="M9 18h3"/><circle cx="17" cy="17" r="4"/><path d="M15.8 17l.8.8 1.6-1.8"/></svg></c:when>
                        <c:when test="${card.key eq 'active'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 2h12"/><path d="M6 22h12"/><path d="M8 2c0 5 8 5 8 10s-8 5-8 10"/><path d="M16 2c0 5-8 5-8 10s8 5 8 10"/><path d="M9 12h6"/></svg></c:when>
                        <c:when test="${card.key eq 'disbursed'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 10h18"/><path d="M5 10l7-5 7 5"/><path d="M6 10v8"/><path d="M10 10v8"/><path d="M14 10v8"/><path d="M18 10v8"/><path d="M4 18h16"/><path d="M3 22h18"/></svg></c:when>
                        <c:when test="${card.key eq 'paid'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.6 2.6L16.5 9"/></svg></c:when>
                        <c:when test="${card.key eq 'defaulted'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></c:when>
                        <c:when test="${card.key eq 'forfeited'}"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg></c:when>
                        <c:otherwise><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8 12h8"/></svg></c:otherwise>
                    </c:choose>
                </span>
                <div class="min-w-0">
                    <p class="loan-metric-title">${card.label}</p>
                    <p class="loan-metric-value"><fmt:formatNumber value="${card.value}" /></p>
                    <p class="loan-metric-trend ${card.positive ? '' : 'is-negative'}">
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
            <div class="loan-spark-row">
                <div class="loan-sparkline" aria-hidden="true"></div>
                <span class="loan-card-info" title="${card.label} trend">i</span>
            </div>
        </div>
    </c:forEach>
</section>

<section class="erp-panel loan-risk-card">
    <div class="loan-risk-head">
        <p class="loan-metric-title"><spring:message code="reports.riskEligibilitySummary" text="Risk / Eligibility Summary" /></p>
        <span class="loan-risk-badge"><spring:message code="staff.analytics.memberView" text="Member View" /></span>
    </div>
    <div class="loan-risk-grid text-sm">
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="reports.currentActiveLoanAmount" text="Current Active Loan Amount" /></p>
            <p class="loan-risk-value"><fmt:formatNumber value="${analytics.activeLoanAmount}" minFractionDigits="2" maxFractionDigits="2" /></p>
        </div>
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="analytics.defaultedLoans" text="Defaulted Loans" /></p>
            <p class="loan-risk-value">${analytics.defaultedLoans}</p>
        </div>
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="analytics.forfeitedLoans" text="Forfeited Loan Applications" /></p>
            <p class="loan-risk-value">${analytics.forfeitedLoans}</p>
        </div>
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="reports.totalGuaranteedAmount" text="Total Guaranteed Amount" /></p>
            <p class="loan-risk-value"><fmt:formatNumber value="${eligibilitySummary.totalGuaranteedAmount}" minFractionDigits="2" maxFractionDigits="2" /></p>
        </div>
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="reports.canApply" text="Can Apply" /></p>
            <p class="loan-risk-value loan-risk-status ${canApply ? '' : 'is-review'}">
                <c:choose>
                    <c:when test="${canApply}">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.5 2.5L16 9"/></svg>
                        <spring:message code="common.yes" text="Yes" />
                    </c:when>
                    <c:otherwise>
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg>
                        <spring:message code="common.notAllowed" text="Not Allowed" />
                    </c:otherwise>
                </c:choose>
            </p>
        </div>
        <div class="loan-risk-cell">
            <p class="erp-widget-title"><spring:message code="reports.canGuarantee" text="Can Guarantee" /></p>
            <p class="loan-risk-value loan-risk-status ${canGuarantee ? '' : 'is-review'}">
                <c:choose>
                    <c:when test="${canGuarantee}">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.5 2.5L16 9"/></svg>
                        <spring:message code="common.yes" text="Yes" />
                    </c:when>
                    <c:otherwise>
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6"/><path d="M15 9l-6 6"/></svg>
                        <spring:message code="common.notAllowed" text="Not Allowed" />
                    </c:otherwise>
                </c:choose>
            </p>
        </div>
    </div>
    <div class="loan-risk-note">
        <p class="erp-widget-title"><spring:message code="reports.eligibilityReason" text="Eligibility Reason" /></p>
        <p class="mt-1">${riskNote}</p>
    </div>
</section>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="reports.loanTrendOverTime" text="Loan Trend Over Time" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="reports.multiMetricStatusTrend" text="Multi-Metric Status Trend" /></h2>
    </div>
    <div class="erp-panel-body">
        <div id="loanAnalyticsChart" class="loan-chart-box"></div>
    </div>
</section>

<script>
window.addEventListener('load', function () {
    const series = ${trendSeriesJson};
    renderMetricSparklines(series);
    renderLineChart('loanAnalyticsChart', series);
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
    document.querySelectorAll('.loan-metric-card[data-spark-key]').forEach(function (card) {
        const target = card.querySelector('.loan-sparkline');
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
    tooltip.className = 'loan-chart-tooltip';
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

function renderLineChart(targetId, series) {
    const target = document.getElementById(targetId);
    if (!target) return;
    target.innerHTML = '';
    const width = 980;
    const height = 350;
    const margin = { top: 42, right: 28, bottom: 58, left: 42 };
    const tooltip = createChartTooltip(target);
    const svg = chartSvg(width, height);
    const firstPoints = (series[0] && series[0].dataPoints) || [];
    const maxY = niceMax(Math.max.apply(null, series.flatMap(function (item) {
        return item.dataPoints.map(function (point) { return point.y || 0; });
    }).concat([0])));
    const plotW = width - margin.left - margin.right;
    const plotH = height - margin.top - margin.bottom;
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
        const date = new Date(point.x);
        const label = date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        addText(svg, label, margin.left + step * index, height - 18, { 'text-anchor': 'middle', fill: '#334155', 'font-size': 11, 'font-weight': 700 });
    });
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
            const date = new Date(point.x);
            const label = date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
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
        });
    });
    svg.appendChild(svgEl('line', { x1: margin.left, y1: margin.top + plotH, x2: width - margin.right, y2: margin.top + plotH, stroke: '#cbd5e1' }));
    target.appendChild(svg);
}
</script>

<%@ include file="../fragments/footer.jspf" %>
