<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.saccos.breadcrumb" text="Admin Tools / Institutions" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.saccos.title" text="Institutions" /></h1>
</div>

<section class="erp-panel">
    <div class="erp-panel-header">
        <p class="erp-panel-title"><spring:message code="admin.saccos.portfolio" text="Institution Portfolio" /></p>
    </div>
    <div class="erp-panel-body space-y-4">
        <c:set var="portfolioSummaries" value="${platformDashboard.saccos}" />
        <%@ include file="../fragments/platform-sacco-portfolio.jspf" %>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
