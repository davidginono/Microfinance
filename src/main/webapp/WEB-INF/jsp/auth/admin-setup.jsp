<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><spring:message code="adminSetup.title"/></title>
    <link rel="stylesheet" href="<c:url value='/css/amazon-ember.css'/>"/>
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css'/>"/>
    <link rel="stylesheet" href="<c:url value='/css/console-components.css'/>"/>
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css'/>"/>
    <link rel="stylesheet" href="<c:url value='/css/form-validation.css'/>"/>
    <script defer src="<c:url value='/js/form-validation.js'/>"></script>
</head>
<body class="aws-auth-shell text-slate-800">
<span id="form-validation-labels" hidden
    data-field="<spring:message code='validation.field' htmlEscape='true'/>"
    data-required="<spring:message code='validation.required' htmlEscape='true'/>"
    data-invalid="<spring:message code='validation.invalid' htmlEscape='true'/>"
    data-minimum="<spring:message code='validation.minimum' htmlEscape='true'/>"
    data-maximum="<spring:message code='validation.maximum' htmlEscape='true'/>"
    data-short="<spring:message code='validation.short' htmlEscape='true'/>"
    data-long="<spring:message code='validation.long' htmlEscape='true'/>"></span>
<main class="flex min-h-screen items-center justify-center px-4 py-10">
<div class="w-full max-w-xl">
    <h1 class="mb-6 text-2xl font-bold"><spring:message code="adminSetup.title"/></h1>
    <form:form method="post" modelAttribute="setupForm" htmlEscape="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <section class="border border-slate-200 bg-white p-6">
            <div class="space-y-4">
                <c:forEach items="${['fullName','email','phone']}" var="field">
                    <label class="block text-sm font-semibold"><spring:message code="adminSetup.${field}"/>
                        <form:input path="${field}" type="${field=='email'?'email':field=='phone'?'tel':'text'}"
                            maxlength="${field=='fullName'?255:field=='email'?160:40}" required="required"
                            autocomplete="${field=='fullName'?'name':field=='phone'?'tel':'email'}"
                            cssClass="mt-1 w-full border border-slate-300 px-3 py-2 text-sm"/>
                        <form:errors path="${field}" cssClass="coa-error"/>
                    </label>
                </c:forEach>
                <c:forEach items="${['currentPassword','password','confirmPassword']}" var="field">
                    <label class="block text-sm font-semibold"><spring:message code="adminSetup.${field}"/>
                        <input id="${field}" name="${field}" type="password" required maxlength="72" minlength="${field=='currentPassword'?1:14}"
                            autocomplete="${field=='currentPassword'?'current-password':'new-password'}"
                            class="mt-1 w-full border border-slate-300 px-3 py-2 text-sm"/>
                        <form:errors path="${field}" cssClass="coa-error"/>
                    </label>
                </c:forEach>
                <p class="text-sm"><spring:message code="adminSetup.passwordRule"/></p>
            </div>
        </section>
        <c:if test="${not empty setupError}"><p role="alert" class="mt-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"><spring:message code="${setupError}"/></p></c:if>
        <div class="mt-6 flex justify-end"><button class="auth-primary-btn border px-4 py-2 text-sm font-semibold" type="submit"><spring:message code="adminSetup.save"/></button></div>
    </form:form>
    <form method="post" action="<c:url value='/logout'/>" class="mt-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <button type="submit" class="auth-secondary-btn border border-slate-300 bg-white px-4 py-2 text-sm font-semibold"><spring:message code="adminSetup.logout"/></button>
    </form>
</div>
</main>
</body>
</html>
