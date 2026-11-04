<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="head.jspf" %>
<div class="portal-entry public-access-card">
    <%@ include file="../fragments/public-access-brand.jspf" %>
    <h1 class="erp-page-title"><spring:message code="portal.title"/></h1>
    <p class="console-message" data-message-type="warning" role="alert"><spring:message code="portal.error.session"/></p>
    <a class="app-btn btn-launch" href="<c:url value='/client-portal'/>"><spring:message code="portal.startAgain"/></a>
</div>
<%@ include file="foot.jspf" %>
