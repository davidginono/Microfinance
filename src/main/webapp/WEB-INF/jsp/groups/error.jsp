<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="group.title" /></h1></div><div class="accounting-error-summary" role="alert" tabindex="-1"><spring:message code="${groupError}" /></div>
<%@ include file="../fragments/footer.jspf" %>
