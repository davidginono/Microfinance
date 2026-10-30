<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8" />
    <script>document.documentElement.classList.add("registration-initializing");</script>
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title><spring:message code="register.member.title" /></title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png?v=20260826-clean' />" />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Rg.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Bd.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/amazon-ember.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-components.css?v=20260809-loan-detail-action-v33' />" />
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20261030-registration-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/form-validation.css?v=20261030-registration' />" />
    <script defer src="<c:url value='/js/form-validation.js' />"></script>
    <script defer src="<c:url value='/js/password-visibility.js?v=20261022' />"></script>
<%@ include file="fragments/otp-ui-styles.jspf" %>
<%@ include file="fragments/select-enhancer.jspf" %>
</head>
<body class="auth-shell aws-auth-shell auth-registration-page min-h-screen font-sans text-slate-900 antialiased">
<div id="authToastContainer" class="auth-notification-rail" aria-live="polite"></div>
<span id="form-validation-labels" hidden
      data-field="<spring:message code='validation.field' htmlEscape='true' />"
      data-required="<spring:message code='validation.required' htmlEscape='true' />"
      data-invalid="<spring:message code='validation.invalid' htmlEscape='true' />"
      data-minimum="<spring:message code='validation.minimum' htmlEscape='true' />"
      data-maximum="<spring:message code='validation.maximum' htmlEscape='true' />"
      data-short="<spring:message code='validation.short' htmlEscape='true' />"
      data-long="<spring:message code='validation.long' htmlEscape='true' />"></span>
<main class="auth-form-page">
<div class="auth-form-layout registration-layout">
    <div class="auth-title-stack registration-heading"><h1 class="auth-heading"><spring:message code="register.member.title" /></h1></div>
    <c:if test="${not empty errors}"><div hidden data-toast-message="${fn:escapeXml(errors)}" data-toast-type="error"></div></c:if>
    <form:form id="memberRegistrationForm" modelAttribute="registrationForm" action="/register/member" method="post">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <section class="auth-form-section registration-card" aria-label="<spring:message code='register.member.title' htmlEscape='true' />">
            <div class="registration-fields">
                <fieldset class="registration-names">
                    <legend><spring:message code="register.member.fullName" /></legend>
                    <div class="registration-name-fields">
                        <div>
                            <label for="firstName" class="registration-section-label"><spring:message code="register.member.firstName" /></label>
                            <form:input path="firstName" id="firstName" autocomplete="given-name" autocapitalize="characters" spellcheck="false" required="required" maxlength="80" pattern="\s*\S+\s*" data-uppercase-input="true" cssClass="registration-field uppercase" />
                            <form:errors path="firstName" cssClass="coa-error" />
                        </div>
                        <div>
                            <label for="middleName" class="registration-section-label"><spring:message code="register.member.middleName" /></label>
                            <form:input path="middleName" id="middleName" autocomplete="additional-name" autocapitalize="characters" spellcheck="false" required="required" maxlength="80" pattern="\s*\S+\s*" data-uppercase-input="true" cssClass="registration-field uppercase" />
                            <form:errors path="middleName" cssClass="coa-error" />
                        </div>
                        <div>
                            <label for="lastName" class="registration-section-label"><spring:message code="register.member.lastName" /></label>
                            <form:input path="lastName" id="lastName" autocomplete="family-name" autocapitalize="characters" spellcheck="false" required="required" maxlength="80" pattern="\s*\S+\s*" data-uppercase-input="true" cssClass="registration-field uppercase" />
                            <form:errors path="lastName" cssClass="coa-error" />
                        </div>
                    </div>
                </fieldset>
                <div>
                    <spring:bind path="registrationForm.email">
                        <label for="email" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.email" /></label>
                    </spring:bind>
                    <form:input path="email"
                                type="email"
                                autocomplete="email"
                                inputmode="email"
                                spellcheck="false"
                                required="required"
                                cssClass="registration-field" />
                    <form:errors path="email" cssClass="coa-error" />
                </div>
                <div>
                    <spring:bind path="registrationForm.phone">
                        <label for="phone" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.phone" /></label>
                    </spring:bind>
                    <form:input path="phone"
                                type="tel"
                                autocomplete="tel"
                                inputmode="numeric"
                                pattern="255[0-9]{9}"
                                minlength="12"
                                maxlength="12"
                                required="required"
                                cssClass="registration-field"
                                placeholder="255712345678" />
                    <p class="registration-note mt-1 text-sm"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></p>
                    <form:errors path="phone" cssClass="coa-error" />
                </div>
                <div>
                    <spring:bind path="registrationForm.saccoId">
                        <label for="saccoId" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.saccoId" /></label>
                    </spring:bind>
                    <form:select path="saccoId" required="required" cssClass="registration-select">
                        <option value="" data-placeholder="true"><spring:message code="register.member.chooseInstitution" /></option>
                        <c:forEach items="${registrationSaccos}" var="sacco">
                            <form:option value="${sacco.saccoId}">${sacco.saccoName}</form:option>
                        </c:forEach>
                    </form:select>
                    <form:errors path="saccoId" cssClass="coa-error" />
                </div>
                <div>
                    <spring:bind path="registrationForm.stationId">
                        <label for="registrationStationSelect" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.stationId" /></label>
                    </spring:bind>
                    <form:select path="stationId" id="registrationStationSelect" required="required" cssClass="registration-select">
                        <option value="" data-placeholder="true"><spring:message code="register.member.chooseBranch" /></option>
                    </form:select>
                    <form:errors path="stationId" cssClass="coa-error" />
                </div>
                <div>
                    <label for="registrationEmployeeNumber" class="mb-1.5 block registration-section-label"><spring:message code="payroll.employeeNumber.optional" /></label>
                    <form:input path="employeeNumber" id="registrationEmployeeNumber" maxlength="50" pattern="[A-Za-z0-9/._ -]*" cssClass="registration-field" aria-describedby="employeeNumberHelp registrationEmployeeNumberAvailability employeeNumberErrors" />
                    <p id="employeeNumberHelp" class="mt-1 text-sm"><spring:message code="payroll.employeeNumber.help" /></p>
                    <p id="registrationEmployeeNumberAvailability" class="mt-1 hidden text-xs" role="status" aria-live="polite"
                       data-checking-message="<spring:message code='registration.employeeNumber.checking' />"
                       data-available-message="<spring:message code='registration.employeeNumber.available' />"
                       data-unavailable-message="<spring:message code='registration.employeeNumber.unavailable' />"
                       data-failed-message="<spring:message code='registration.employeeNumber.checkFailed' />"></p>
                    <form:errors path="employeeNumber" id="employeeNumberErrors" cssClass="coa-error" />
                </div>
                <div>
                    <spring:bind path="registrationForm.password">
                        <label for="registrationPassword" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.password" /></label>
                    </spring:bind>
                    <div class="auth-password-control">
                        <form:password path="password" id="registrationPassword"
                                       autocomplete="new-password"
                                       required="required"
                                       minlength="8"
                                       cssClass="registration-field" />
                        <button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="registrationPassword" aria-pressed="false"
                                data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button>
                    </div>
                    <form:errors path="password" cssClass="coa-error" />
                </div>
                <p class="registration-note registration-password-note"><spring:message code="register.member.passwordHelp" /></p>
                <div>
                    <spring:bind path="registrationForm.confirmPassword">
                        <label for="registrationConfirmPassword" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.confirmPassword" /></label>
                    </spring:bind>
                    <div class="auth-password-control">
                        <form:password path="confirmPassword" id="registrationConfirmPassword"
                                       autocomplete="new-password"
                                       required="required"
                                       minlength="8"
                                       cssClass="registration-field" />
                        <button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="registrationConfirmPassword" aria-pressed="false"
                                data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button>
                    </div>
                    <form:errors path="confirmPassword" cssClass="coa-error" />
                </div>
            </div>
            <div id="registrationOtpBlock" class="registration-otp ${not empty registrationForm.otpCode ? '' : 'hidden'}">
                <spring:bind path="registrationForm.otpCode">
                    <label for="otpCode" class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.code" /></label>
                </spring:bind>
                <form:input path="otpCode"
                            inputmode="numeric"
                            autocomplete="one-time-code"
                            maxlength="6"
                            data-otp-hidden="true"
                            data-otp-label="Registration OTP code"
                            cssClass="sr-only" />
                <p class="registration-note mt-2 text-sm"><spring:message code="register.member.otpHelp" /></p>
                <div id="registrationOtpLiveStatus" class="mt-3 hidden items-center gap-2 border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                    <span data-otp-spinner class="otp-status-spinner"></span>
                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                    </svg>
                    <span data-otp-text>Checking code...</span>
                </div>
                <form:errors path="otpCode" cssClass="coa-error" />
            </div>
        </section>
        <form:errors path="" cssClass="registration-form-error" element="div" />
        <div id="registrationVerifyError" class="hidden" data-auto-scroll-message="true" aria-live="assertive"></div>
        <div id="registrationVerifySuccess" class="hidden" data-auto-scroll-message="true" aria-live="polite"></div>
        <div class="auth-form-actions registration-actions">
            <a href="/login" class="registration-action auth-secondary-btn inline-flex items-center justify-center text-slate-700"><spring:message code="register.member.back" /></a>
            <button id="memberRegisterRequestOtp" class="otp-request-button auth-primary-btn registration-action inline-flex items-center justify-center gap-2" type="button"
                data-idle-label="<spring:message code='register.member.sendCode' htmlEscape='true' />"
                data-loading-label="<spring:message code='register.member.sendingCode' htmlEscape='true' />"
                data-sent-label="<spring:message code='register.member.codeSent' htmlEscape='true' />">
                <span class="otp-button-spinner hidden"></span>
                <span class="otp-button-label"><spring:message code="register.member.sendCode" /></span>
            </button>
            <button id="memberRegisterSubmit" class="registration-action registration-primary inline-flex items-center justify-center gap-2" type="submit" ${empty registrationForm.otpCode ? 'disabled' : ''}>
                <span id="memberRegisterSubmitLoader" class="auth-ellipsis-loader hidden" aria-hidden="true"><span></span><span></span><span></span></span>
                <span id="memberRegisterSubmitText"><spring:message code="register.member.submit" /></span>
            </button>
        </div>
    </form:form>
</div>
</main>
<script>
    document.addEventListener("DOMContentLoaded", () => {
        window.refreshEnhancedSelects?.(document);
        document.documentElement.classList.remove("registration-initializing");
    }, {once:true});
</script>
<script id="registrationSaccosData" type="application/json">${registrationSaccosJson}</script>
<script>
    (() => {
        const toastContainer = document.getElementById('authToastContainer');
        window.showToast = function (type, message, options) {
            if (!toastContainer || !message || !String(message).trim()) {
                return null;
            }
            const variant = type === 'error' ? 'error' : (type === 'success' ? 'success' : 'info');
            const icon = variant === 'success'
                ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/></svg>'
                : variant === 'error'
                    ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-11.5a.75.75 0 00-1.5 0v4.25a.75.75 0 001.5 0V6.5zm0 7a.75.75 0 00-1.5 0v.25a.75.75 0 001.5 0v-.25z" clip-rule="evenodd"/></svg>'
                    : '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M18 10A8 8 0 112 10a8 8 0 0116 0zm-7.25-3.75a.75.75 0 10-1.5 0v.25a.75.75 0 001.5 0V6.25zm0 2.5a.75.75 0 00-1.5 0v5a.75.75 0 001.5 0v-5z" clip-rule="evenodd"/></svg>';
            const toast = document.createElement('div');
            toast.className = 'app-toast-enter auth-notification-bar auth-notification-' + variant;
            toast.setAttribute('role', variant === 'error' ? 'alert' : 'status');
            toast.innerHTML =
                '<div class="flex items-start gap-3">' +
                    '<div class="mt-0.5 shrink-0">' + icon + '</div>' +
                    '<div class="min-w-0 flex-1 pr-6"><p class="text-sm font-semibold leading-5" data-toast-text></p></div>' +
                    '<button type="button" class="app-toast-close" aria-label="Dismiss notification">' +
                        '<svg class="app-toast-close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>' +
                    '</button>' +
                '</div>';
            toast.querySelector('[data-toast-text]').textContent = String(message);
            const dismiss = () => {
                if (!toast.isConnected || toast.classList.contains('app-toast-exit')) {
                    return;
                }
                toast.classList.remove('app-toast-enter');
                toast.classList.add('app-toast-exit');
                window.setTimeout(() => toast.remove(), 190);
            };
            toast.querySelector('button')?.addEventListener('click', dismiss);
            toastContainer.appendChild(toast);
            return { dismiss, element: toast };
        };

        const scrollToFeedback = (element) => {
            if (!element || !element.textContent || !element.textContent.trim() || element.classList.contains('hidden')) {
                return;
            }
            window.requestAnimationFrame(() => {
                const top = Math.max(window.scrollY + element.getBoundingClientRect().top - 24, 0);
                window.scrollTo({ top, behavior: 'smooth' });
                element.setAttribute('tabindex', '-1');
                try {
                    element.focus({ preventScroll: true });
                } catch (ignored) {
                    element.focus();
                }
            });
        };

        window.addEventListener('load', () => {
            document.querySelectorAll('[data-toast-message]').forEach((element) => {
                const message = element.getAttribute('data-toast-message');
                if (message) {
                    window.showToast(element.getAttribute('data-toast-type') || 'info', message);
                }
            });
            const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message], #memberRegistrationForm > .border-rose-200')).find((element) =>
                !element.classList.contains('hidden') && element.textContent && element.textContent.trim()
            );
            if (initialAlert) {
                scrollToFeedback(initialAlert);
            }
        });

        const form = document.getElementById("memberRegistrationForm");
        const requestOtpButton = document.getElementById("memberRegisterRequestOtp");
        const submitButton = document.getElementById("memberRegisterSubmit");
        const submitLoader = document.getElementById("memberRegisterSubmitLoader");
        const submitText = document.getElementById("memberRegisterSubmitText");
        const errorBox = document.getElementById("registrationVerifyError");
        const successBox = document.getElementById("registrationVerifySuccess");
        const otpBlock = document.getElementById("registrationOtpBlock");
        const otpInput = form ? form.querySelector('[name="otpCode"]') : null;
        const otpLiveStatus = document.getElementById("registrationOtpLiveStatus");
        const saccoSelect = form.querySelector('[name="saccoId"]');
        const stationSelect = document.getElementById("registrationStationSelect");
        const stationPlaceholderLabel = stationSelect.options[0].textContent;
        const registrationSaccosData = document.getElementById("registrationSaccosData");
        const employeeNumberInput = document.getElementById("registrationEmployeeNumber");
        const employeeNumberAvailability = document.getElementById("registrationEmployeeNumberAvailability");
        if (!form || !requestOtpButton || !submitButton || !submitLoader || !submitText || !errorBox || !successBox || !otpBlock || !otpInput || !otpLiveStatus || !saccoSelect || !stationSelect || !registrationSaccosData || !employeeNumberInput || !employeeNumberAvailability) {
            return;
        }

        const defaultButtonText = submitText.textContent.trim();
        let otpRequested = !otpBlock.classList.contains("hidden");
        let lastOtpReady = false;
        let otpVerificationComplete = false;
        let otpVerificationTimer = null;
        const registrationSaccos = JSON.parse(registrationSaccosData.textContent || '[]');

        const createAvailabilityChecker = (input, availability, requestUrl, ready) => {
            let timer = null;
            let request = null;
            let version = 0;
            const setAvailability = (state) => {
                const message = availability.dataset[state + "Message"] || "";
                availability.textContent = message;
                availability.className = state === "available"
                    ? "mt-1 text-xs text-emerald-700"
                    : state === "unavailable" || state === "failed"
                        ? "mt-1 text-xs text-rose-700"
                        : "mt-1 text-xs text-slate-600";
            };
            const clearAvailability = () => {
                availability.textContent = "";
                availability.className = "mt-1 hidden text-xs";
            };
            const check = () => {
                version += 1;
                const currentVersion = version;
                const value = input.value.trim();
                if (timer) {
                    window.clearTimeout(timer);
                }
                if (request) {
                    request.abort();
                    request = null;
                }
                if (!value || !ready()) {
                    clearAvailability();
                    return;
                }
                setAvailability("checking");
                timer = window.setTimeout(async () => {
                    const controller = new AbortController();
                    request = controller;
                    try {
                        const response = await fetch(requestUrl(value), {
                        headers: {"Accept": "application/json"},
                        signal: controller.signal
                    });
                    const payload = await response.json();
                    if (currentVersion !== version) {
                        return;
                    }
                    setAvailability(response.ok && payload.available === true ? "available" : "unavailable");
                } catch (error) {
                    if (error.name !== "AbortError" && currentVersion === version) {
                        setAvailability("failed");
                    }
                } finally {
                    if (currentVersion === version) {
                        request = null;
                    }
                }
            }, 350);
        };
            input.addEventListener("input", check);
            return check;
        };

        const checkEmployeeNumberAvailability = createAvailabilityChecker(
            employeeNumberInput,
            employeeNumberAvailability,
            (employeeNumber) => "/register/member/employee-number-availability?employeeNumber=" + encodeURIComponent(employeeNumber)
                + "&saccoId=" + encodeURIComponent(saccoSelect.value)
                + "&stationId=" + encodeURIComponent(stationSelect.value),
            () => employeeNumberInput.validity.valid && !!saccoSelect.value && !!stationSelect.value
        );
        const recheckEmployeeNumberAvailability = () => window.setTimeout(checkEmployeeNumberAvailability);
        saccoSelect.addEventListener("change", recheckEmployeeNumberAvailability);
        stationSelect.addEventListener("change", recheckEmployeeNumberAvailability);

        const clearOtpVerification = () => {
            if (otpVerificationTimer) {
                window.clearTimeout(otpVerificationTimer);
                otpVerificationTimer = null;
            }
        };

        const setError = (message) => {
            successBox.textContent = "";
            successBox.classList.add("hidden");
            errorBox.textContent = message;
            errorBox.className = " border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700";
            scrollToFeedback(errorBox);
        };

        const setSuccess = (message) => {
            errorBox.textContent = "";
            errorBox.classList.add("hidden");
            successBox.textContent = message;
            successBox.className = " border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700";
        };

        const clearMessages = () => {
            errorBox.textContent = "";
            errorBox.classList.add("hidden");
            successBox.textContent = "";
            successBox.classList.add("hidden");
        };

        const setOtpButtonState = (state) => {
            const spinner = requestOtpButton.querySelector(".otp-button-spinner");
            const label = requestOtpButton.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            if (spinner) {
                spinner.classList.toggle("hidden", !loading);
            }
            if (label) {
                label.textContent = loading ? requestOtpButton.dataset.loadingLabel : (sent ? requestOtpButton.dataset.sentLabel : requestOtpButton.dataset.idleLabel);
            }
            requestOtpButton.disabled = loading || sent;
            requestOtpButton.classList.toggle("is-loading", loading);
            requestOtpButton.classList.toggle("is-sent", sent);
        };

        const syncOtpLiveStatus = () => {
            const spinner = otpLiveStatus.querySelector("[data-otp-spinner]");
            const tick = otpLiveStatus.querySelector("[data-otp-tick]");
            const text = otpLiveStatus.querySelector("[data-otp-text]");
            otpInput.value = (otpInput.value || "").replace(/\D/g, "").slice(0, 6);
            const ready = /^\d{6}$/.test(otpInput.value);
            submitButton.disabled = !otpRequested || !ready;

            if (!otpRequested || !otpInput.value) {
                clearOtpVerification();
                otpVerificationComplete = false;
                lastOtpReady = false;
                otpLiveStatus.classList.add("hidden");
                otpLiveStatus.classList.remove("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                otpLiveStatus.classList.add("border-slate-200", "bg-slate-50", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = "Checking code...";
                return;
            }

            otpLiveStatus.classList.remove("hidden");
            otpLiveStatus.classList.add("flex");
            if (ready) {
                if (!otpVerificationComplete) {
                    if (!otpVerificationTimer) {
                        otpVerificationTimer = window.setTimeout(() => {
                            otpVerificationTimer = null;
                            otpVerificationComplete = true;
                            syncOtpLiveStatus();
                        }, 240);
                    }
                    otpLiveStatus.classList.remove("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    otpLiveStatus.classList.add("border-slate-200", "bg-slate-50", "text-slate-600");
                    spinner.classList.remove("hidden");
                    tick.classList.add("hidden");
                    text.textContent = "Verifying code...";
                    return;
                }
                if (!lastOtpReady && !submitButton.disabled) {
                    try {
                        submitButton.focus({ preventScroll: true });
                    } catch (ignored) {
                        submitButton.focus();
                    }
                }
                otpLiveStatus.classList.remove("border-slate-200", "bg-slate-50", "text-slate-600");
                otpLiveStatus.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                spinner.classList.add("hidden");
                tick.classList.remove("hidden");
                tick.classList.remove("otp-checkmark-pop");
                void tick.offsetWidth;
                tick.classList.add("otp-checkmark-pop");
                text.textContent = "Verified";
            } else {
                clearOtpVerification();
                otpVerificationComplete = false;
                otpLiveStatus.classList.remove("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                otpLiveStatus.classList.add("border-slate-200", "bg-slate-50", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = "Checking code...";
            }
            lastOtpReady = ready;
        };

        otpInput.addEventListener("input", syncOtpLiveStatus);
        const syncStationOptions = () => {
            const selectedSacco = saccoSelect.value;
            const sacco = registrationSaccos.find((item) => item.saccoId === selectedSacco);
            const currentValue = stationSelect.value;
            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.dataset.placeholder = 'true';
            placeholder.textContent = stationPlaceholderLabel;
            stationSelect.replaceChildren(placeholder);
            const stationIds = sacco && Array.isArray(sacco.stationIds) ? sacco.stationIds : [];
            stationIds.forEach((stationId) => {
                const option = document.createElement('option');
                option.value = stationId;
                option.textContent = stationId;
                if (stationId === currentValue || stationId === '${registrationForm.stationId}') {
                    option.selected = true;
                }
                stationSelect.appendChild(option);
            });
            if (typeof window.refreshEnhancedSelects === "function") {
                window.refreshEnhancedSelects(stationSelect.parentElement);
            }
        };
        saccoSelect.addEventListener("change", syncStationOptions);
        syncStationOptions();
        syncOtpLiveStatus();
        setOtpButtonState(otpRequested ? "sent" : "idle");

        requestOtpButton.addEventListener("click", async () => {
            if (!form.reportValidity()) {
                return;
            }
            clearMessages();
            submitButton.disabled = true;
            setOtpButtonState("loading");

            try {
                const verifyResponse = await fetch("/register/member/request-otp", {
                    method: "POST",
                    headers: {"Accept":"application/json"
                    },
                    body: new FormData(form)
                });

                let payload = {};
                try {
                    payload = await verifyResponse.json();
                } catch (ignored) {
                    payload = {};
                }

                if (!verifyResponse.ok || payload.valid !== true) {
                    setError(payload.message || "Unable to verify the client details right now.");
                    setOtpButtonState("idle");
                    return;
                }
                otpBlock.classList.remove("hidden");
                otpRequested = true;
                otpVerificationComplete = false;
                syncOtpLiveStatus();
                window.SaccosOtp?.focusBoxes(otpInput);
                setSuccess(payload.message || "We sent an OTP code to your email.");
                setOtpButtonState("sent");
            } catch (error) {
                setError("Unable to verify the client details right now. Please try again.");
                setOtpButtonState("idle");
            }
        });

        form.addEventListener("submit", (event) => {
            if (submitButton.disabled) {
                event.preventDefault();
                setError("Request an OTP code first.");
                return;
            }
            if (!otpInput.value.trim()) {
                event.preventDefault();
                window.SaccosOtp?.focusBoxes(otpInput);
                setError("Enter the OTP code sent to your email.");
                return;
            }
            clearMessages();
            submitButton.disabled = true;
            requestOtpButton.disabled = true;
            submitLoader.classList.remove("hidden");
            submitText.textContent = "Creating account...";
        });
    })();
</script>
</body>
</html>
