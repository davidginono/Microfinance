<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title><spring:message code="register.member.title" /></title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-400.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-700.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/open-sans.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-components.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20260806-clean-toast-v4' />" />
<%@ include file="fragments/otp-ui-styles.jspf" %>
<%@ include file="fragments/select-enhancer.jspf" %>
</head>
<body class="auth-shell aws-auth-shell min-h-screen font-sans text-slate-900 antialiased">
<div id="authToastContainer" class="auth-notification-rail" aria-live="polite"></div>
<div class="relative flex min-h-screen items-center justify-center px-4 py-10">
    <div class="auth-frame relative w-full max-w-[28rem] overflow-hidden px-5 py-7 sm:px-8 sm:py-8">
        <div class="mb-6 sm:mb-7">
            <div class="auth-title-stack min-w-0">
                <h1 class="auth-heading"><spring:message code="register.member.title" /></h1>
            </div>
        </div>

                <c:if test="${not empty errors}">
                    <div hidden data-toast-message="${fn:escapeXml(errors)}" data-toast-type="error"></div>
                </c:if>
                <div id="registrationVerifyError" class="hidden" data-auto-scroll-message="true" aria-live="assertive"></div>
                <div id="registrationVerifySuccess" class="hidden" data-auto-scroll-message="true" aria-live="polite"></div>
                <form:form id="memberRegistrationForm" modelAttribute="registrationForm" action="/register/member" method="post" class="space-y-5">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <form:errors path="*" cssClass="mb-4 block border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700" element="div" />

                    <div class="auth-form-card space-y-4">
                    <div class="grid gap-4">
                        <div>
                            <spring:bind path="registrationForm.memberNo">
                                <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.memberNo" /></label>
                            </spring:bind>
                            <form:input path="memberNo" cssClass="registration-field" />
                            <form:errors path="memberNo" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                        <div>
                            <spring:bind path="registrationForm.saccoId">
                                <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.saccoId" /></label>
                            </spring:bind>
                            <form:select path="saccoId" cssClass="registration-select">
                                <option value="" data-placeholder="true">Select SACCO Name</option>
                                <c:forEach items="${registrationSaccos}" var="sacco">
                                    <form:option value="${sacco.saccoId}">${sacco.saccoId} - ${sacco.saccoName}</form:option>
                                </c:forEach>
                            </form:select>
                            <form:errors path="saccoId" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                    </div>

                    <div class="grid gap-4">
                        <div>
                            <spring:bind path="registrationForm.fullName">
                                <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.fullName" /></label>
                            </spring:bind>
                            <form:input path="fullName"
                                        cssClass="registration-field uppercase"
                                        autocapitalize="characters"
                                        spellcheck="false"
                                        data-uppercase-input="true" />
                            <form:errors path="fullName" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                        <div>
                            <spring:bind path="registrationForm.email">
                                <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.email" /></label>
                            </spring:bind>
                            <form:input path="email"
                                        type="email"
                                        autocomplete="email"
                                        inputmode="email"
                                        spellcheck="false"
                                        required="required"
                                        cssClass="registration-field" />
                            <form:errors path="email" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                    </div>

                    <div>
                        <spring:bind path="registrationForm.phone">
                            <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.phone" /></label>
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
                        <p class="mt-1 text-xs text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></p>
                        <form:errors path="phone" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>

                    <div>
                        <spring:bind path="registrationForm.stationId">
                            <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}"><spring:message code="register.member.stationId" /></label>
                        </spring:bind>
                        <form:select path="stationId" id="registrationStationSelect" cssClass="registration-select">
                            <option value="" data-placeholder="true">Select station ID</option>
                        </form:select>
                        <form:errors path="stationId" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>
                    </div>

                    <div class="auth-form-card space-y-4">
                        <div class="grid gap-4 md:grid-cols-2">
                            <div>
                                <spring:bind path="registrationForm.password">
                                    <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}">Password</label>
                                </spring:bind>
                                <form:password path="password"
                                               autocomplete="new-password"
                                               minlength="8"
                                               cssClass="registration-field" />
                                <form:errors path="password" cssClass="mt-1 block text-xs text-rose-600" />
                            </div>
                            <div>
                                <spring:bind path="registrationForm.confirmPassword">
                                    <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}">Confirm Password</label>
                                </spring:bind>
                                <form:password path="confirmPassword"
                                               autocomplete="new-password"
                                               minlength="8"
                                               cssClass="registration-field" />
                                <form:errors path="confirmPassword" cssClass="mt-1 block text-xs text-rose-600" />
                            </div>
                        </div>
                        <p class="registration-note text-sm">Use at least 8 characters. This password will be saved after your details and OTP are verified.</p>
                    </div>

                    <div id="registrationOtpBlock" class="auth-form-card ${not empty registrationForm.otpCode ? '' : 'hidden'}">
                        <spring:bind path="registrationForm.otpCode">
                            <label class="mb-1.5 block registration-section-label ${status.error ? 'text-rose-600' : ''}">OTP Code</label>
                        </spring:bind>
                        <form:input path="otpCode"
                                    inputmode="numeric"
                                    autocomplete="one-time-code"
                                    maxlength="6"
                                    data-otp-hidden="true"
                                    data-otp-label="Registration OTP code"
                                    cssClass="sr-only" />
                        <p class="registration-note mt-2 text-sm">Enter the 6-digit code sent to your email to finish registration.</p>
                        <div id="registrationOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                            <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#2F348D]"></span>
                            <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                            </svg>
                            <span data-otp-text>Checking code...</span>
                        </div>
                        <form:errors path="otpCode" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>

                    <div class="space-y-3 pt-2">
                        <button id="memberRegisterRequestOtp" class="otp-request-button registration-action inline-flex w-full items-center justify-center gap-2" type="button">
                            <span class="otp-button-spinner hidden"></span>
                            <span class="otp-button-label">Submit</span>
                        </button>
                        <button id="memberRegisterSubmit" class="registration-action registration-primary inline-flex w-full items-center justify-center" type="submit" ${empty registrationForm.otpCode ? 'disabled' : ''}>
                            <span id="memberRegisterSubmitText"><spring:message code="register.member.submit" /></span>
                        </button>
                        <a href="/login" class="registration-action inline-flex w-full items-center justify-center text-slate-700">
                            <spring:message code="register.member.back" />
                        </a>
                    </div>
                </form:form>
    </div>
</div>
<script id="registrationSaccosData" type="application/json">${registrationSaccosJson}</script>
<script>
    (() => {
        const toastContainer = document.getElementById('authToastContainer');
        window.showToast = function (type, message, options) {
            if (!toastContainer || !message || !String(message).trim()) {
                return null;
            }
            const settings = options || {};
            const variant = type === 'error' ? 'error' : (type === 'success' ? 'success' : 'info');
            const requestedDuration = Number(settings.duration);
            const duration = Number.isFinite(requestedDuration) && requestedDuration > 0 ? Math.min(requestedDuration, 10000) : (variant === 'error' ? 5200 : 3600);
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
            window.setTimeout(dismiss, duration);
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
        const submitText = document.getElementById("memberRegisterSubmitText");
        const errorBox = document.getElementById("registrationVerifyError");
        const successBox = document.getElementById("registrationVerifySuccess");
        const otpBlock = document.getElementById("registrationOtpBlock");
        const otpInput = form ? form.querySelector('[name="otpCode"]') : null;
        const otpLiveStatus = document.getElementById("registrationOtpLiveStatus");
        const saccoSelect = form.querySelector('[name="saccoId"]');
        const stationSelect = document.getElementById("registrationStationSelect");
        const registrationSaccosData = document.getElementById("registrationSaccosData");
        if (!form || !requestOtpButton || !submitButton || !submitText || !errorBox || !successBox || !otpBlock || !otpInput || !otpLiveStatus || !saccoSelect || !stationSelect || !registrationSaccosData) {
            return;
        }

        const defaultButtonText = submitText.textContent.trim();
        let otpRequested = !otpBlock.classList.contains("hidden");
        let lastOtpReady = false;
        let otpVerificationComplete = false;
        let otpVerificationTimer = null;
        const registrationSaccos = JSON.parse(registrationSaccosData.textContent || '[]');

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
            errorBox.className = "rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700";
            scrollToFeedback(errorBox);
        };

        const setSuccess = (message) => {
            errorBox.textContent = "";
            errorBox.classList.add("hidden");
            successBox.textContent = message;
            successBox.className = "rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700";
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
                label.textContent = loading ? "Submitting..." : (sent ? "Submitted" : "Submit");
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
            stationSelect.innerHTML = '<option value="" data-placeholder="true">Select station ID</option>';
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
                    headers: {
                        "Accept": "application/json"
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
                    setError(payload.message || "Unable to verify the member details right now.");
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
                setError("Unable to verify the member details right now. Please try again.");
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
            submitText.textContent = "Creating account...";
        });
    })();
</script>
</body>
</html>
