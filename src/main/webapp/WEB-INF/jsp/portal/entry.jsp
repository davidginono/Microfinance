<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="head.jspf" %>
<c:set var="publicProfileRegistration" value="${entryForm.intent=='REGISTER'}"/>
<div class="portal-entry" data-resource-form-page ${publicProfileRegistration?'data-resource-details-page':''}>
    <h1 class="erp-page-title"><spring:message code="${publicProfileRegistration?'portal.register':'portal.title'}"/></h1>
    <nav class="portal-entry-links">
        <a href="<c:url value='/'/>"><spring:message code="home.back"/></a>
        <a href="<c:url value='/client-portal/register'/>"><spring:message code="portal.register"/></a>
        <a href="<c:url value='/client-portal'/>"><spring:message code="portal.existing"/></a>
        <a href="<c:url value='/login'/>"><spring:message code="portal.fullAccount"/></a>
    </nav>
    <form:form modelAttribute="entryForm" action="${pageContext.request.contextPath}/client-portal/request-code" method="post" cssClass="kyc-client-form" data-accounting-dirty-form="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <c:choose>
            <c:when test="${publicProfileRegistration}">
                <form:hidden path="intent"/>
                <section class="erp-resource-section">
                    <h2><spring:message code="portal.institution"/></h2>
                    <div class="kyc-profile-grid">
                        <label class="kyc-field"><span><spring:message code="portal.institution"/></span><form:select path="institution" cssClass="aws-control" required="required" data-portal-institution="true"><form:option value=""><spring:message code="applications.select"/></form:option><c:if test="${not empty entryForm.institution}"><form:option value="${entryForm.institution}"><c:out value="${entryForm.institution}"/></form:option></c:if></form:select></label>
                        <label class="kyc-field"><span><spring:message code="portal.branch"/></span><form:select path="branch" cssClass="aws-control" required="required" data-portal-branch="true"><form:option value=""><spring:message code="applications.select"/></form:option><c:if test="${not empty entryForm.branch}"><form:option value="${entryForm.branch}"><c:out value="${entryForm.branch}"/></form:option></c:if></form:select></label>
                    </div>
                </section>
                <c:set var="identityReadOnly" value="${false}"/>
                <c:set var="profileValues" value="${entryForm.registration.profile}"/>
                <div data-profile-tabs>
                    <div id="profile-bio" data-detail-panel>
                        <h2 class="sr-only"><spring:message code="kyc.section.bio"/></h2>
                        <spring:nestedPath path="registration">
                            <%@ include file="../onboarding/profile-fields.jspf" %>
                        </spring:nestedPath>
                    </div>
                    <%@ include file="../onboarding/profile-attachments.jspf" %>
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
        <form:errors path="*" cssClass="field-error" element="p" role="alert"/>
        <div class="accounting-form-actions"><button class="app-btn btn-launch"><spring:message code="portal.sendCode"/></button></div>
    </form:form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<%@ include file="foot.jspf" %>
