<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Platform Dashboard</p>
    <h1 class="erp-page-title">Platform Dashboard</h1>
</div>

<section class="erp-stat-grid">
    <div class="erp-stat-card erp-stat-blue">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Registered Institutions</p>
                <p class="erp-stat-value">${platformDashboard.totalSaccos}</p>
                <p class="erp-stat-meta">New: ${platformDashboard.newSaccos} | Healthy: ${platformDashboard.healthySaccos}</p>
            </div>
            <span class="erp-stat-icon">S</span>
        </div>
        <div class="erp-stat-footer"><span>Portfolio coverage</span><span>${platformDashboard.totalSaccos}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-green">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Clients</p>
                <p class="erp-stat-value">${platformDashboard.totalMembers}</p>
                <p class="erp-stat-meta">Across all active institution workspaces</p>
            </div>
            <span class="erp-stat-icon">M</span>
        </div>
        <div class="erp-stat-footer"><span>Platform-wide client base</span><span>${platformDashboard.totalMembers}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-amber">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Disbursed Portfolio</p>
                <p class="erp-stat-value">${platformDashboard.totalDisbursedPrincipalLabel}</p>
                <p class="erp-stat-meta">Final approved and paid principal totals</p>
            </div>
            <span class="erp-stat-icon">L</span>
        </div>
        <div class="erp-stat-footer"><span>Loan exposure</span><span>${platformDashboard.totalDisbursedPrincipalLabel}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-red">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">At Risk Institutions</p>
                <p class="erp-stat-value">${platformDashboard.atRiskSaccos}</p>
                <p class="erp-stat-meta">Failed outbox events: ${platformDashboard.failedOutboxCount}</p>
            </div>
            <span class="erp-stat-icon">R</span>
        </div>
        <div class="erp-stat-footer"><span>Needs review</span><span>${platformDashboard.atRiskSaccos}</span></div>
    </div>
</section>

<c:set var="usageShowSaccoColumn" value="true" />
<%@ include file="../fragments/admin-usage-analytics.jspf" %>

<section class="space-y-4">
    <div class="erp-panel">
        <div class="erp-panel-header">
            <div>
                <p class="erp-panel-title">Institution Portfolio</p>
                <p class="mt-1 text-sm text-slate-500">Portfolio cards for every registered institution.</p>
            </div>
        </div>
        <div class="erp-panel-body space-y-4">
            <c:set var="portfolioSummaries" value="${platformDashboard.saccos}" />
            <%@ include file="../fragments/platform-sacco-portfolio.jspf" %>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
