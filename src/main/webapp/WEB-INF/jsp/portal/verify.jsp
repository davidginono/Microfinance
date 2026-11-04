<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %><%@ include file="head.jspf" %>
<div class="portal-entry public-access-card">
    <%@ include file="../fragments/public-access-brand.jspf" %>
    <h1 class="erp-page-title"><spring:message code="portal.verify"/></h1>
    <p class="console-message" data-message-type="info"><spring:message code="portal.codeSent"/></p>
    <form method="post" action="<c:url value='/client-portal/verify'/>" class="kyc-client-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <label class="kyc-field">
            <span><spring:message code="portal.code"/></span>
            <input class="aws-control" name="code" pattern="[0-9]{6}" maxlength="6" inputmode="numeric" autocomplete="one-time-code" required/>
        </label>
        <c:if test="${not empty portalError}">
            <p class="console-message" data-message-type="error" role="alert"><spring:message code="${portalError}"/></p>
        </c:if>
        <div class="accounting-form-actions">
            <button class="app-btn btn-launch" type="submit"><spring:message code="portal.verify"/></button>
            <a class="app-btn btn-neutral" href="<c:url value='/client-portal'/>"><spring:message code="portal.startAgain"/></a>
        </div>
    </form>
</div>
<%@ include file="foot.jspf" %>
