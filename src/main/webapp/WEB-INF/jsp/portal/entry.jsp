<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="head.jspf" %>
<c:set var="publicProfileRegistration" value="${entryForm.intent=='REGISTER'}"/>
<c:set var="portalEntryTitle" value="${publicProfileRegistration?'portal.register':'portal.title'}"/>
<div class="portal-entry ${publicProfileRegistration?'portal-registration':''}" data-resource-form-page>
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>">
        <a class="erp-page-path__link" href="<c:url value='/'/>"><spring:message code="home.back"/></a>
        <span class="erp-page-path__separator" aria-hidden="true">&#8250;</span>
        <c:if test="${publicProfileRegistration}">
            <a class="erp-page-path__link" href="<c:url value='/client-portal'/>"><spring:message code="portal.title"/></a>
            <span class="erp-page-path__separator" aria-hidden="true">&#8250;</span>
        </c:if>
        <span class="erp-page-path__item" aria-current="page"><spring:message code="${portalEntryTitle}"/></span>
    </nav>
    <div class="public-access-card">
    <%@ include file="../fragments/public-access-brand.jspf" %>
    <%@ include file="../onboarding/profile-draft-notice.jspf" %>
    <h1 class="erp-page-title"><spring:message code="${portalEntryTitle}"/></h1>
    <form:form modelAttribute="entryForm" action="${pageContext.request.contextPath}/client-portal/request-code" method="post" cssClass="kyc-client-form" data-accounting-dirty-form="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <c:choose>
            <c:when test="${publicProfileRegistration}">
                <form:hidden path="intent"/>
                <c:set var="identityReadOnly" value="${false}"/><c:set var="profileValues" value="${entryForm.registration.profile}"/>
                <%@ include file="../onboarding/profile-editor.jspf" %>
            </c:when>
            <c:otherwise>
                <section>
                    <h2><spring:message code="portal.existing"/></h2>
                    <div class="kyc-profile-grid">
                        <label class="kyc-field"><span><spring:message code="kyc.field.clientNumber"/></span><form:input path="clientNumber" cssClass="aws-control" maxlength="80" required="true" autocomplete="username"/></label>
                        <label class="kyc-field"><span><spring:message code="portal.task"/></span><form:select path="intent" cssClass="aws-control"><form:option value="PROFILE"><spring:message code="kyc.form.self"/></form:option><form:option value="LOAN"><spring:message code="portal.apply"/></form:option></form:select></label>
                    </div>
                </section>
            </c:otherwise>
        </c:choose>
        <c:choose>
            <c:when test="${publicProfileRegistration}"><form:errors cssClass="field-error" element="p" role="alert" data-workspace-form-error="true"/></c:when>
            <c:otherwise><form:errors path="*" cssClass="field-error" element="p" role="alert"/></c:otherwise>
        </c:choose>
        <c:choose><c:when test="${publicProfileRegistration}">
            <c:url value="/client-portal" var="profileCancelUrl"/><c:url value="/client-portal/register/draft" var="profileDraftUrl"/><c:url value="/client-portal/request-code" var="profileSubmitUrl"/>
            <c:set var="profileSubmitValue" value="REGISTER"/><c:set var="profileSubmitLabel" value="portal.sendCode"/>
            <%@ include file="../onboarding/profile-actions.jspf" %>
        </c:when><c:otherwise><div class="accounting-form-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="portal.sendCode"/></button></div></c:otherwise></c:choose>
    </form:form>
    </div>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<c:if test="${publicProfileRegistration}"><script src="<c:url value='/js/client-profile-wizard.js'/>" defer></script></c:if>
<%@ include file="foot.jspf" %>
