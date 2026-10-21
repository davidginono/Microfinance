<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/dashboard-support.jspf" %>
<div class="erp-page-header" data-aws-page-header><p class="erp-breadcrumb"><spring:message code="admin.dashboard.breadcrumb" /></p><h1 class="erp-page-title"><spring:message code="admin.dashboard.title" /></h1></div>
<div data-dashboard-board="admin" aria-busy="true">
 <%@ include file="../fragments/dashboard-voucher-shortcuts.jspf" %>
 <%@ include file="../fragments/admin-dashboard-widgets.jspf" %>
</div>
<%@ include file="../fragments/footer.jspf" %>
