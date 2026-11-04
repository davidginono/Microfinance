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
    <h1 class="erp-page-title"><spring:message code="${portalEntryTitle}"/></h1>
    <form:form modelAttribute="entryForm" action="${pageContext.request.contextPath}/client-portal/request-code" method="post" cssClass="kyc-client-form" data-accounting-dirty-form="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <c:choose>
            <c:when test="${publicProfileRegistration}">
                <form:hidden path="intent"/>
                <%@ include file="../onboarding/profile-wizard-labels.jspf" %>
                <div data-profile-wizard data-profile-registration>
                    <section class="erp-resource-section" data-profile-section="personal">
                        <h2><spring:message code="portal.institution"/></h2>
                        <div class="kyc-profile-grid">
                            <label class="kyc-field"><span><spring:message code="portal.institution"/></span><form:select path="institution" cssClass="aws-control" required="required" data-portal-institution="true"><form:option value=""><spring:message code="applications.select"/></form:option><c:if test="${not empty entryForm.institution}"><form:option value="${entryForm.institution}"><c:out value="${entryForm.institution}"/></form:option></c:if></form:select><form:errors path="institution" cssClass="field-error"/></label>
                            <label class="kyc-field"><span><spring:message code="portal.branch"/></span><form:select path="branch" cssClass="aws-control" required="required" data-portal-branch="true"><form:option value=""><spring:message code="applications.select"/></form:option><c:if test="${not empty entryForm.branch}"><form:option value="${entryForm.branch}"><c:out value="${entryForm.branch}"/></form:option></c:if></form:select><form:errors path="branch" cssClass="field-error"/></label>
                        </div>
                    </section>
                    <c:set var="identityReadOnly" value="${false}"/>
                    <c:set var="profileValues" value="${entryForm.registration.profile}"/>
                    <spring:nestedPath path="registration">
                        <%@ include file="../onboarding/profile-fields.jspf" %>
                    </spring:nestedPath>
                    <p class="kyc-field-hint" data-profile-section="review"><spring:message code="portal.verifyBeforeUpload"/></p>
                </div>
            </c:when>
            <c:otherwise>
                <section class="erp-resource-section">
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
        <div class="accounting-form-actions">
            <c:if test="${publicProfileRegistration}">
                <button class="app-btn btn-neutral" type="button" data-profile-previous hidden><spring:message code="common.previous"/></button>
                <button class="app-btn btn-launch" type="button" data-profile-next hidden><spring:message code="common.next"/></button>
            </c:if>
            <button class="app-btn btn-launch" type="submit" data-profile-send><spring:message code="portal.sendCode"/></button>
        </div>
    </form:form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<c:if test="${publicProfileRegistration}"><script src="<c:url value='/js/client-profile-wizard.js'/>" defer></script></c:if>
<%@ include file="foot.jspf" %>
