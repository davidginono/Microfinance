<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SACCOs</p>
    <h1 class="erp-page-title">SACCOs</h1>
    <p class="erp-page-subtitle">Portfolio cards for every registered SACCO.</p>
</div>

<section class="erp-panel">
    <div class="erp-panel-header">
        <p class="erp-panel-title">SACCO Portfolio</p>
    </div>
    <div class="erp-panel-body space-y-4">
        <c:set var="portfolioSummaries" value="${platformDashboard.saccos}" />
        <%@ include file="../fragments/platform-sacco-portfolio.jspf" %>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
