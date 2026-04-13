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
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Sora:wght@600;700&family=Great+Vibes&display=swap" rel="stylesheet" />
    <script>
        tailwind.config = {
            theme: {
                extend: {
                    fontFamily: {
                        sans: ["Manrope", "ui-sans-serif", "system-ui"],
                        display: ["Sora", "ui-sans-serif", "system-ui"]
                    }
                }
            }
        };
    </script>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        @keyframes otp-pop {
            0% { transform: translateY(4px) scale(0.82); opacity: 0; }
            100% { transform: translateY(0) scale(1); opacity: 1; }
        }
        @keyframes toast-in {
            0% { opacity: 0; transform: translateY(-14px) scale(0.96); }
            100% { opacity: 1; transform: translateY(0) scale(1); }
        }
        @keyframes toast-out {
            0% { opacity: 1; transform: translateY(0) scale(1); }
            100% { opacity: 0; transform: translateY(-10px) scale(0.96); }
        }

        .otp-checkmark-pop {
            animation: otp-pop 180ms ease-out;
        }
        .app-toast-enter {
            animation: toast-in 220ms ease-out;
        }
        .app-toast-exit {
            animation: toast-out 180ms ease-in forwards;
        }
        .auth-decor {
            background: linear-gradient(180deg, #dff1fb 0%, #cfe8f8 100%);
        }
        .auth-decor::before,
        .auth-decor::after {
            content: "";
            position: absolute;
            inset: 0;
            pointer-events: none;
        }
        .auth-decor::before {
            background:
                linear-gradient(145deg,
                    rgba(255,255,255,0.34) 0%,
                    rgba(255,255,255,0.34) 20%,
                    transparent 20%,
                    transparent 44%,
                    rgba(255,255,255,0.16) 44%,
                    rgba(255,255,255,0.16) 58%,
                    transparent 58%,
                    transparent 100%);
        }
        .auth-decor::after {
            background:
                linear-gradient(25deg,
                    rgba(92, 180, 235, 0.22) 0%,
                    rgba(92, 180, 235, 0.22) 18%,
                    transparent 18%,
                    transparent 40%,
                    rgba(92, 180, 235, 0.13) 40%,
                    rgba(92, 180, 235, 0.13) 60%,
                    transparent 60%,
                    transparent 100%);
        }
        .auth-decor-glow {
            position: absolute;
            left: -28%;
            bottom: -10%;
            width: 120%;
            height: 44%;
            background: radial-gradient(circle at center, rgba(113, 196, 245, 0.42) 0%, rgba(113, 196, 245, 0.18) 42%, rgba(113, 196, 245, 0) 78%);
            pointer-events: none;
        }
    </style>
<%@ include file="fragments/otp-ui-styles.jspf" %>
</head>
<body class="min-h-screen bg-[#eef2f5] font-sans text-slate-900 antialiased">
<div id="authToastContainer" class="pointer-events-none fixed right-4 top-4 z-[90] flex w-[min(100vw-1rem,24rem)] max-w-full flex-col gap-3 sm:right-5 sm:top-5"></div>
<div class="relative flex min-h-screen items-center justify-center px-4 py-10">
    <div class="relative w-full max-w-6xl overflow-hidden border border-slate-200 bg-white shadow-sm">
        <div class="h-12 w-full border-b border-[#10a8b6]" style="background:linear-gradient(180deg,#19c6d8 0%,#11b2c3 100%);"></div>
        <div class="grid lg:grid-cols-[0.95fr_1.15fr]">
            <div class="auth-decor relative hidden overflow-hidden border-r border-slate-200 lg:block">
                <div class="auth-decor-glow"></div>
            </div>

            <div class="bg-white p-7 sm:p-10">
                <div class="mb-6">
                    <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Loan Management System</p>
                    <h3 class="mt-2 font-display text-3xl font-semibold tracking-tight text-slate-900"><spring:message code="register.member.title" /></h3>
                    <p class="mt-2 text-sm text-slate-500"><spring:message code="register.member.subtitle" /></p>
                </div>

                <c:if test="${not empty errors}">
                    <div hidden data-toast-message="${fn:escapeXml(errors)}" data-toast-type="error"></div>
                </c:if>
                <div id="registrationVerifyError" class="hidden"></div>
                <div id="registrationVerifySuccess" class="hidden"></div>
                <form:form id="memberRegistrationForm" modelAttribute="registrationForm" action="/register/member" method="post" class="space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <form:errors path="*" cssClass="mb-4 block border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700" element="div" />

                    <div class="grid gap-4 md:grid-cols-2">
                        <div>
                            <spring:bind path="registrationForm.memberNo">
                                <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}"><spring:message code="register.member.memberNo" /></label>
                            </spring:bind>
                            <form:input path="memberNo" cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10" />
                            <form:errors path="memberNo" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                        <div>
                            <spring:bind path="registrationForm.saccoId">
                                <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}"><spring:message code="register.member.saccoId" /></label>
                            </spring:bind>
                            <form:select path="saccoId" cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10">
                                <form:option value="">Select SACCO Name</form:option>
                                <c:forEach items="${registrationSaccos}" var="sacco">
                                    <form:option value="${sacco.saccoId}">${sacco.saccoId} - ${sacco.saccoName}</form:option>
                                </c:forEach>
                            </form:select>
                            <form:errors path="saccoId" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                    </div>

                    <div class="grid gap-4 md:grid-cols-2">
                        <div>
                            <spring:bind path="registrationForm.fullName">
                                <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}"><spring:message code="register.member.fullName" /></label>
                            </spring:bind>
                            <form:input path="fullName" cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10" />
                            <form:errors path="fullName" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                        <div>
                            <spring:bind path="registrationForm.email">
                                <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}"><spring:message code="register.member.email" /></label>
                            </spring:bind>
                            <form:input path="email"
                                        type="email"
                                        autocomplete="email"
                                        inputmode="email"
                                        spellcheck="false"
                                        required="required"
                                        cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10" />
                            <form:errors path="email" cssClass="mt-1 block text-xs text-rose-600" />
                        </div>
                    </div>

                    <div>
                        <spring:bind path="registrationForm.stationId">
                            <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}"><spring:message code="register.member.stationId" /></label>
                        </spring:bind>
                        <form:select path="stationId" id="registrationStationSelect" cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10">
                            <form:option value="">Select station ID</form:option>
                        </form:select>
                        <form:errors path="stationId" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>

                    <div>
                        <spring:bind path="registrationForm.signatureText">
                            <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}">Signature</label>
                        </spring:bind>
                        <form:input path="signatureText"
                                    id="registrationSignatureInput"
                                    cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10"
                                    placeholder="Example: James M Juma" />
                        <p class="mt-1 text-xs text-slate-500">Type your signature in a simple name style like <strong>James M Juma</strong>.</p>
                        <form:errors path="signatureText" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>

                    <div id="registrationOtpBlock" class="${not empty registrationForm.otpCode ? '' : 'hidden'}">
                        <spring:bind path="registrationForm.otpCode">
                            <label class="mb-1.5 block text-sm font-semibold ${status.error ? 'text-rose-600' : 'text-slate-700'}">OTP Code</label>
                        </spring:bind>
                        <form:input path="otpCode" inputmode="numeric" maxlength="6" cssClass="w-full border border-slate-300 bg-white px-3.5 py-3 tracking-[0.3em] text-slate-900 focus:border-[#2F348D] focus:outline-none focus:ring-2 focus:ring-[#2F348D]/10" />
                        <p class="mt-1 text-xs text-slate-500">Enter the 6-digit code sent to your email to finish registration.</p>
                        <div id="registrationOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                            <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#2F348D]"></span>
                            <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                            </svg>
                            <span data-otp-text>Checking code...</span>
                        </div>
                        <form:errors path="otpCode" cssClass="mt-1 block text-xs text-rose-600" />
                    </div>

                    <div class="flex flex-col gap-3 pt-2 sm:flex-row">
                        <button id="memberRegisterRequestOtp" class="otp-request-button inline-flex items-center justify-center gap-2 border border-slate-300 bg-white px-4 py-3 text-sm font-semibold text-slate-800 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70" type="button">
                            <span class="otp-button-spinner hidden"></span>
                            <span class="otp-button-label">Send OTP Code</span>
                        </button>
                        <button id="memberRegisterSubmit" class="inline-flex items-center justify-center border border-slate-300 bg-white px-4 py-3 text-sm font-semibold text-slate-800 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70" type="submit" ${empty registrationForm.otpCode ? 'disabled' : ''}>
                            <span id="memberRegisterSubmitText"><spring:message code="register.member.submit" /></span>
                        </button>
                        <a href="/login" class="inline-flex items-center justify-center border border-slate-300 bg-white px-4 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50">
                            <spring:message code="register.member.back" />
                        </a>
                    </div>
                </form:form>
            </div>
        </div>
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
            const duration = Number.isFinite(settings.duration) ? settings.duration : (variant === 'error' ? 5200 : 3600);
            const palette = variant === 'success'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                : variant === 'error'
                    ? 'border-rose-200 bg-rose-50 text-rose-700'
                    : 'border-blue-200 bg-blue-50 text-blue-700';
            const icon = variant === 'success'
                ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/></svg>'
                : variant === 'error'
                    ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-11.5a.75.75 0 00-1.5 0v4.25a.75.75 0 001.5 0V6.5zm0 7a.75.75 0 00-1.5 0v.25a.75.75 0 001.5 0v-.25z" clip-rule="evenodd"/></svg>'
                    : '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M18 10A8 8 0 112 10a8 8 0 0116 0zm-7.25-3.75a.75.75 0 10-1.5 0v.25a.75.75 0 001.5 0V6.25zm0 2.5a.75.75 0 00-1.5 0v5a.75.75 0 001.5 0v-5z" clip-rule="evenodd"/></svg>';
            const toast = document.createElement('div');
            toast.className = 'app-toast-enter pointer-events-auto relative overflow-hidden rounded-xl border px-4 py-3 shadow-lg ' + palette;
            toast.setAttribute('role', variant === 'error' ? 'alert' : 'status');
            toast.innerHTML =
                '<div class="flex items-start gap-3">' +
                    '<div class="mt-0.5 shrink-0">' + icon + '</div>' +
                    '<div class="min-w-0 flex-1 pr-6"><p class="text-sm font-semibold leading-5">' + String(message) + '</p></div>' +
                    '<button type="button" class="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-lg text-current/70 transition hover:bg-black/5 hover:text-current" aria-label="Dismiss notification">' +
                        '<svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M4.22 4.22a.75.75 0 011.06 0L10 8.94l4.72-4.72a.75.75 0 011.06 1.06L11.06 10l4.72 4.72a.75.75 0 11-1.06 1.06L10 11.06l-4.72 4.72a.75.75 0 11-1.06-1.06L8.94 10 4.22 5.28a.75.75 0 010-1.06z" clip-rule="evenodd"/></svg>' +
                    '</button>' +
                '</div>';
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
            if (duration > 0) {
                window.setTimeout(dismiss, duration);
            }
            return { dismiss, element: toast };
        };

        const scrollToFeedback = (element) => {
            if (!element || !element.textContent || !element.textContent.trim() || element.classList.contains('hidden')) {
                return;
            }
            window.requestAnimationFrame(() => {
                const top = Math.max(window.scrollY + element.getBoundingClientRect().top - 18, 0);
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
            const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message]')).find((element) =>
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
        const signatureInput = document.getElementById("registrationSignatureInput");
        const saccoSelect = form.querySelector('[name="saccoId"]');
        const stationSelect = document.getElementById("registrationStationSelect");
        const registrationSaccosData = document.getElementById("registrationSaccosData");
        if (!form || !requestOtpButton || !submitButton || !submitText || !errorBox || !successBox || !otpBlock || !otpInput || !otpLiveStatus || !signatureInput || !saccoSelect || !stationSelect || !registrationSaccosData) {
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
                label.textContent = loading ? "Sending..." : (sent ? "OTP Sent" : "Send OTP Code");
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
            stationSelect.innerHTML = '<option value="">Select station ID</option>';
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
                    return;
                }
                otpBlock.classList.remove("hidden");
                otpRequested = true;
                otpVerificationComplete = false;
                syncOtpLiveStatus();
                otpInput.focus();
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
                otpInput.focus();
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
