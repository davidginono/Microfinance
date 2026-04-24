<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title><spring:message code="app.title" /></title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css' />" />
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
        .auth-shell {
            background:
                radial-gradient(circle at top, rgba(59, 130, 246, 0.08), transparent 34%),
                linear-gradient(180deg, #f8fbfd 0%, #edf3f8 100%);
        }
        .auth-frame {
            border-radius: 1rem;
            border: 1px solid #d7e3ee;
            background: #ffffff;
            box-shadow: 0 12px 34px rgba(15, 23, 42, 0.05);
        }
        .auth-panel-divider {
            border-bottom: 1px solid #e2e8f0;
        }
        .auth-title-stack {
            display: flex;
            flex-direction: column;
            gap: 0;
        }
        .auth-heading {
            font-family: "Sora", ui-sans-serif, system-ui;
            font-size: 2.2rem;
            line-height: 0.98;
            letter-spacing: -0.05em;
            color: #0f172a;
        }
        .auth-subheading {
            color: #64748b;
            font-size: 0.95rem;
            line-height: 1.6;
        }
        .auth-tab-switch {
            display: inline-flex;
            border-radius: 0.5rem;
            border: 1px solid #e2e8f0;
            background: #f8fafc;
            padding: 0.25rem;
        }
        .auth-tab {
            border-radius: 0.375rem;
            color: #334155;
            transition: color 180ms ease, box-shadow 180ms ease;
        }
        .auth-tab[data-active="true"] {
            background: #ffffff;
            color: #0f172a;
            box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
        }
        .auth-tab:hover {
            color: #0f172a;
        }
        .auth-footer-links .text-slate-300,
        .auth-footer-links .text-slate-500 {
            display: none;
        }
        .auth-footer-links::after {
            content: "\00A9 2026";
            color: #64748b;
        }
        .auth-input {
            border-radius: 0.375rem;
            border: 1px solid #d7e1ef;
            background: #f8fbff;
            color: #0f172a;
            box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6);
            transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
        }
        .auth-input:focus {
            outline: none;
            border-color: #c7d4e4;
            box-shadow: 0 0 0 1px #c7d4e4;
            background: #ffffff;
        }
        .auth-card {
            border-radius: 0.75rem;
            border: 1px solid #e1e8f0;
            background: #ffffff;
            box-shadow: 0 8px 26px rgba(15, 23, 42, 0.035);
        }
        .auth-primary-btn {
            border-radius: 0.85rem;
            border: 1px solid #14b8c4;
            background: #14b8c4;
            color: #ffffff;
            box-shadow: none;
            transition: background-color 160ms ease, transform 160ms ease;
        }
        .auth-primary-btn:hover {
            transform: translateY(-1px);
            background: #0ea5b7;
        }
        .auth-secondary-btn {
            border-radius: 0.85rem;
            border: 1px solid #d7e1ef;
            background: #ffffff;
            color: #12304d;
            box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
            transition: background-color 160ms ease, border-color 160ms ease, transform 160ms ease;
        }
        .auth-secondary-btn:hover {
            transform: translateY(-1px);
            border-color: #b8cfdd;
            background: #f9fcff;
        }
        .auth-primary-btn:disabled,
        .auth-secondary-btn:disabled {
            cursor: not-allowed;
            transform: none;
            filter: grayscale(0.15);
            box-shadow: none;
            opacity: 0.7;
        }
        .auth-footer-link {
            color: #516b84;
            transition: color 160ms ease;
        }
        .auth-footer-link:hover {
            color: #0f172a;
        }
        .auth-mode-card {
            border: 0;
            background: transparent;
            padding: 0;
        }
        .auth-mode-title {
            color: #7b8aa0;
            font-size: 0.98rem;
            font-weight: 600;
            letter-spacing: 0.01em;
            text-align: center;
        }
        .auth-or-divider {
            display: flex;
            align-items: center;
            gap: 0.85rem;
            color: #94a3b8;
            font-size: 0.74rem;
            font-weight: 700;
            letter-spacing: 0.18em;
            text-transform: uppercase;
        }
        .auth-or-divider::before,
        .auth-or-divider::after {
            content: "";
            flex: 1 1 auto;
            height: 1px;
            background: #e2e8f0;
        }
        .auth-section-label {
            color: #334155;
            font-size: 0.88rem;
            font-weight: 600;
        }
        .auth-signup-link {
            color: #2563eb;
            font-weight: 700;
            transition: color 160ms ease;
        }
        .auth-signup-link:hover {
            color: #1d4ed8;
        }
        .auth-login-meta .text-slate-300,
        .auth-login-meta .text-slate-500 {
            display: none;
        }
        @media (max-width: 640px) {
            .auth-heading {
                font-size: 1.85rem;
            }
            .auth-frame {
                border-radius: 1rem;
            }
        }
    </style>
</head>
<body class="auth-shell min-h-screen font-sans text-slate-900 antialiased">
<c:set var="activeLoginTab" value="${param.tab eq 'staff' ? 'staff' : 'member'}" />
<div id="authToastContainer" class="pointer-events-none fixed right-4 top-4 z-[90] flex w-[min(100vw-1rem,24rem)] max-w-full flex-col gap-3 sm:right-5 sm:top-5"></div>
<div class="relative flex min-h-screen items-center justify-center px-4 py-8 sm:px-6 sm:py-10">
    <div class="auth-frame relative w-full max-w-[28rem] overflow-hidden px-5 py-7 sm:px-8 sm:py-8">
        <div class="mb-6 sm:mb-7">
            <div class="auth-title-stack min-w-0">
                <h1 class="auth-heading">Log in to Loan Management System</h1>
            </div>
        </div>
        <div class="mb-5">
            <div class="mb-5 inline-flex rounded-md border border-slate-200 bg-slate-50 p-1 text-sm font-semibold">
                <button type="button"
                        class="auth-tab rounded px-4 py-2"
                        data-login-tab-toggle="member"
                        data-active="${activeLoginTab eq 'member'}">
                    Members
                </button>
                <button type="button"
                        class="auth-tab rounded px-4 py-2"
                        data-login-tab-toggle="staff"
                        data-active="${activeLoginTab eq 'staff'}">
                    Staff
                </button>
            </div>
        </div>
        <div class="pb-1">

                    <c:if test="${param.error != null}">
                        <div hidden data-toast-message="${fn:escapeXml(not empty errorMessage ? errorMessage : 'Invalid member number or password.')}" data-toast-type="error"></div>
                    </c:if>
                    <c:if test="${not empty errorMessage and param.error == null}">
                        <div hidden data-toast-message="${fn:escapeXml(errorMessage)}" data-toast-type="error"></div>
                    </c:if>
                    <c:if test="${param.logout != null}">
                        <div hidden data-toast-message="Logged out successfully." data-toast-type="success"></div>
                    </c:if>
                    <c:if test="${param.claimed != null and empty loginMessage}">
                        <div hidden data-toast-message="Your account is now active. Sign in as staff to continue." data-toast-type="success"></div>
                    </c:if>
                    <c:if test="${not empty loginMessage}">
                        <div hidden data-toast-message="${fn:escapeXml(loginMessage)}" data-toast-type="success"></div>
                    </c:if>
                    <c:if test="${not empty message}">
                        <div hidden data-toast-message="${fn:escapeXml(message)}" data-toast-type="success"></div>
                    </c:if>

                    <div data-login-tab="member" class="${activeLoginTab eq 'member' ? '' : 'hidden '}space-y-5">
                        <div class="auth-mode-card space-y-4">
                            <div>
                                <p class="auth-mode-title">Member Number &amp; Password</p>
                            </div>
                            <form action="/login" method="post" class="space-y-4">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <div>
                                    <label class="mb-2 block auth-section-label">Member Number</label>
                                    <input name="username" class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <div>
                                    <label class="mb-2 block auth-section-label">Password</label>
                                    <input type="password" name="password" class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <button class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold" type="submit">Log in</button>
                            </form>
                        </div>

                        <div class="auth-or-divider">or</div>

                        <div id="memberLoginError" data-auto-scroll-message="true" class="hidden rounded-2xl border border-sacco-brown/20 bg-[#f7efe9] px-4 py-3 text-sm font-medium text-sacco-brown"></div>
                        <div id="memberLoginSuccess" data-auto-scroll-message="true" class="hidden rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700"></div>
                        <form id="memberLoginForm" class="auth-mode-card space-y-4">
                            <input type="hidden" id="memberLoginCsrfName" value="${_csrf.parameterName}" />
                            <input type="hidden" id="memberLoginCsrfToken" value="${_csrf.token}" />
                            <div>
                                <p class="auth-mode-title">One-Time Pin Code</p>
                            </div>
                            <div>
                                <label class="mb-2 block auth-section-label">Email</label>
                                <input id="memberLoginEmail" name="email" type="email" autocomplete="email" class="auth-input w-full px-4 py-3.5 text-slate-900" required />
                            </div>
                            <div id="memberOtpBlock" class="hidden">
                                <label class="mb-2 block auth-section-label">OTP Code</label>
                                <input id="memberLoginOtpCode" name="otpCode" inputmode="numeric" maxlength="6" class="auth-input w-full px-4 py-3.5 tracking-[0.3em] text-slate-900" />
                                <div id="memberOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-600">
                                    <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#16b7c8]"></span>
                                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                    </svg>
                                    <span data-otp-text>Checking code...</span>
                                </div>
                            </div>
                            <div class="space-y-3">
                                <button id="memberRequestOtpButton" class="auth-secondary-btn inline-flex w-full items-center justify-center gap-2 px-4 py-3.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70" type="button">
                                    <span class="otp-button-spinner hidden h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#16b7c8]"></span>
                                    <span class="otp-button-label">Send Sign-In Code</span>
                                </button>
                                <button id="memberVerifyOtpButton" class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70" type="button" disabled>Verify Code &amp; Log in</button>
                            </div>
                        </form>
                    </div>

                    <div data-login-tab="staff" class="${activeLoginTab eq 'staff' ? '' : 'hidden '}space-y-5">
                        <div class="auth-mode-card space-y-4">
                            <div>
                                <p class="auth-mode-title">Member Number &amp; Password</p>
                                <p class="mt-1 text-xs text-slate-500">Admins and minor admins can sign in here using their staff member number and password.</p>
                            </div>
                            <form action="/login" method="post" class="space-y-4">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="loginType" value="staff-password" />
                                <div>
                                    <label class="mb-2 block auth-section-label">Staff Member Number</label>
                                    <input name="username" class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <div>
                                    <label class="mb-2 block auth-section-label">Password</label>
                                    <input type="password" name="password" class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <button class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold" type="submit">Log in as Staff</button>
                            </form>
                        </div>

                        <div class="auth-or-divider">or</div>

                        <div id="staffLoginError" data-auto-scroll-message="true" class="hidden rounded-2xl border border-sacco-brown/20 bg-[#f7efe9] px-4 py-3 text-sm font-medium text-sacco-brown"></div>
                        <div id="staffLoginSuccess" data-auto-scroll-message="true" class="hidden rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700"></div>
                        <form id="staffLoginForm" class="auth-mode-card space-y-4">
                            <input type="hidden" id="staffLoginCsrfName" value="${_csrf.parameterName}" />
                            <input type="hidden" id="staffLoginCsrfToken" value="${_csrf.token}" />
                            <div>
                                <p class="auth-mode-title">Email Sign-In Code</p>
                                <p class="mt-1 text-xs text-slate-500">Staff sign in with a one-time code sent to their registered email.</p>
                            </div>
                            <div>
                                <label class="mb-2 block auth-section-label">Email</label>
                                <input id="staffLoginEmail" type="email" name="email" class="auth-input w-full px-4 py-3.5 text-slate-900" required />
                            </div>
                            <div id="staffOtpBlock" class="hidden">
                                <label class="mb-2 block auth-section-label">OTP Code</label>
                                <input id="staffLoginOtpCode" name="otpCode" inputmode="numeric" maxlength="6" class="auth-input w-full px-4 py-3.5 tracking-[0.3em] text-slate-900" />
                                <div id="staffOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-600">
                                    <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#16b7c8]"></span>
                                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                    </svg>
                                    <span data-otp-text>Checking code...</span>
                                </div>
                            </div>
                            <div class="space-y-3">
                                <button id="staffRequestOtpButton" class="auth-secondary-btn inline-flex w-full items-center justify-center gap-2 px-4 py-3.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70" type="button">
                                    <span class="otp-button-spinner hidden h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#16b7c8]"></span>
                                    <span class="otp-button-label">Send Sign-In Code</span>
                                </button>
                                <button id="staffVerifyOtpButton" class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70" type="button" disabled>Verify Code &amp; Log in</button>
                            </div>
                        </form>
                    </div>

                    <div class="auth-login-meta mt-8 border-t border-slate-200 pt-6 text-center text-sm text-slate-500">
                        <span>Don't have an account? </span>
                        <a href="/register/member" class="auth-signup-link"><spring:message code="login.registerMember" /></a>
                    </div>
                    </div>
        </div>
    </div>
</div>
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

        window.loginPageScrollToFeedback = function (element) {
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
            if (document.activeElement && typeof document.activeElement.blur === 'function' && document.activeElement !== document.body) {
                document.activeElement.blur();
            }
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
                window.loginPageScrollToFeedback(initialAlert);
            }
        });

        const toggles = document.querySelectorAll('[data-login-tab-toggle]');
        const tabs = document.querySelectorAll('[data-login-tab]');
        if (!toggles.length || !tabs.length) {
            return;
        }
        function activate(key) {
            toggles.forEach((button) => {
                button.dataset.active = String(button.getAttribute('data-login-tab-toggle') === key);
            });
            tabs.forEach((panel) => {
                panel.classList.toggle('hidden', panel.getAttribute('data-login-tab') !== key);
            });
        }
        toggles.forEach((button) => {
            button.addEventListener('click', () => activate(button.getAttribute('data-login-tab-toggle')));
        });
    })();

    (() => {
        function setOtpButtonLoading(button, loading, idleLabel, loadingLabel) {
            if (!button) {
                return;
            }
            const spinner = button.querySelector('.otp-button-spinner');
            const label = button.querySelector('.otp-button-label');
            if (spinner) {
                spinner.classList.toggle('hidden', !loading);
            }
            if (label) {
                label.textContent = loading ? loadingLabel : idleLabel;
            }
            button.disabled = loading;
        }

        function bindOtpLiveStatus(config) {
            const otpInput = config.otpInput;
            const verifyButton = config.verifyButton;
            const status = config.status;
            const spinner = status?.querySelector('[data-otp-spinner]');
            const tick = status?.querySelector('[data-otp-tick]');
            const text = status?.querySelector('[data-otp-text]');
            let otpRequested = false;
            let lastReady = false;
            let verificationComplete = false;
            let verificationTimer = null;

            function clearVerification() {
                if (verificationTimer) {
                    window.clearTimeout(verificationTimer);
                    verificationTimer = null;
                }
            }

            function setStatus(mode) {
                if (!status || !spinner || !tick || !text) {
                    return;
                }
                if (mode === 'hidden') {
                    status.classList.add('hidden');
                    status.classList.remove('flex', 'border-emerald-200', 'bg-emerald-50', 'text-emerald-700');
                    status.classList.add('border-slate-200', 'bg-slate-50', 'text-slate-600');
                    spinner.classList.remove('hidden');
                    tick.classList.add('hidden');
                    text.textContent = 'Checking code...';
                    return;
                }
                status.classList.remove('hidden');
                status.classList.add('flex');
                if (mode === 'ready') {
                    status.classList.remove('border-slate-200', 'bg-slate-50', 'text-slate-600');
                    status.classList.add('border-emerald-200', 'bg-emerald-50', 'text-emerald-700');
                    spinner.classList.add('hidden');
                    tick.classList.remove('hidden');
                    tick.classList.remove('otp-checkmark-pop');
                    void tick.offsetWidth;
                    tick.classList.add('otp-checkmark-pop');
                    text.textContent = 'Verified';
                } else {
                    status.classList.remove('border-emerald-200', 'bg-emerald-50', 'text-emerald-700');
                    status.classList.add('border-slate-200', 'bg-slate-50', 'text-slate-600');
                    spinner.classList.remove('hidden');
                    tick.classList.add('hidden');
                    text.textContent = 'Checking code...';
                }
            }

            function sync() {
                if (!otpInput || !verifyButton) {
                    return;
                }
                otpInput.value = (otpInput.value || '').replace(/\D/g, '').slice(0, 6);
                const ready = /^\d{6}$/.test(otpInput.value);
                verifyButton.disabled = !otpRequested || !ready;
                if (!otpRequested || !otpInput.value) {
                    clearVerification();
                    verificationComplete = false;
                    lastReady = false;
                    setStatus('hidden');
                    return;
                }
                if (ready && !verificationComplete) {
                    if (!verificationTimer) {
                        verificationTimer = window.setTimeout(() => {
                            verificationTimer = null;
                            verificationComplete = true;
                            sync();
                        }, 240);
                    }
                    setStatus('typing');
                    if (text) {
                        text.textContent = 'Verifying code...';
                    }
                    return;
                }
                if (ready && !lastReady && config.proceedButton && !config.proceedButton.disabled) {
                    try {
                        config.proceedButton.focus({ preventScroll: true });
                    } catch (ignored) {
                        config.proceedButton.focus();
                    }
                }
                if (!ready) {
                    clearVerification();
                    verificationComplete = false;
                }
                lastReady = ready;
                setStatus(ready ? 'ready' : 'typing');
                if (ready && text) {
                    text.textContent = 'Verified';
                }
            }

            otpInput?.addEventListener('input', sync);
            sync();

            return {
                markRequested() {
                    otpRequested = true;
                    verificationComplete = false;
                    sync();
                },
                reset() {
                    otpRequested = false;
                    clearVerification();
                    verificationComplete = false;
                    sync();
                }
            };
        }

        function bindOtpSignIn(options) {
            const form = document.getElementById(options.formId);
            const emailInput = document.getElementById(options.emailId);
            const otpInput = document.getElementById(options.otpId);
            const otpBlock = document.getElementById(options.otpBlockId);
            const requestButton = document.getElementById(options.requestButtonId);
            const verifyButton = document.getElementById(options.verifyButtonId);
            const errorBox = document.getElementById(options.errorBoxId);
            const successBox = document.getElementById(options.successBoxId);
            const csrfName = document.getElementById(options.csrfNameId)?.value;
            const csrfToken = document.getElementById(options.csrfTokenId)?.value;
            const status = document.getElementById(options.statusId);
            if (!form || !emailInput || !otpInput || !otpBlock || !requestButton || !verifyButton || !csrfName || !csrfToken) {
                return;
            }

            const otpUi = bindOtpLiveStatus({
                otpInput,
                verifyButton,
                status
            });
            let verifying = false;
            let lastAutoSubmittedOtp = '';

            const setError = (message) => {
                successBox.classList.add('hidden');
                successBox.textContent = '';
                errorBox.classList.add('hidden');
                errorBox.textContent = '';
                window.showToast?.('error', message);
            };

            const setSuccess = (message) => {
                errorBox.classList.add('hidden');
                errorBox.textContent = '';
                successBox.classList.add('hidden');
                successBox.textContent = '';
                window.showToast?.('success', message);
            };

            requestButton.addEventListener('click', async () => {
                if (!emailInput.reportValidity()) {
                    return;
                }

                setOtpButtonLoading(requestButton, true, options.requestIdleLabel, 'Sending...');
                verifyButton.disabled = true;
                lastAutoSubmittedOtp = '';
                otpUi.reset();
                try {
                    const body = new URLSearchParams();
                    body.set('email', emailInput.value);
                    body.set(csrfName, csrfToken);
                    const response = await fetch(options.requestUrl, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                            'Accept': 'application/json'
                        },
                        body
                    });
                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok || payload.valid !== true) {
                        setError(payload.message || 'Unable to send the sign-in code right now.');
                        return;
                    }
                    otpBlock.classList.remove('hidden');
                    otpUi.markRequested();
                    otpInput.focus();
                    setSuccess(payload.message || 'We sent a sign-in code to your email.');
                } catch (error) {
                    setError('Unable to send the sign-in code right now.');
                } finally {
                    setOtpButtonLoading(requestButton, false, options.requestIdleLabel, 'Sending...');
                }
            });

            verifyButton.addEventListener('click', async () => {
                if (verifying) {
                    return;
                }
                if (!emailInput.reportValidity()) {
                    return;
                }
                if (!otpInput.value.trim()) {
                    otpInput.reportValidity();
                    otpInput.focus();
                    setError('Enter the OTP code sent to your email.');
                    return;
                }

                setOtpButtonLoading(requestButton, true, options.requestIdleLabel, 'Sending...');
                verifyButton.disabled = true;
                verifying = true;
                try {
                    const body = new URLSearchParams();
                    body.set('email', emailInput.value);
                    body.set('otpCode', otpInput.value);
                    body.set(csrfName, csrfToken);
                    const response = await fetch(options.verifyUrl, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                            'Accept': 'application/json'
                        },
                        body
                    });
                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok || payload.valid !== true) {
                        setError(payload.message || 'Unable to verify the sign-in code.');
                        return;
                    }
                    window.location.href = payload.redirectUrl || options.defaultRedirectUrl;
                } catch (error) {
                    setError('Unable to verify the sign-in code right now.');
                } finally {
                    verifying = false;
                    setOtpButtonLoading(requestButton, false, options.requestIdleLabel, 'Sending...');
                    otpUi.markRequested();
                }
            });

            if (options.autoSubmitOnReady) {
                otpInput.addEventListener('input', () => {
                    const normalized = (otpInput.value || '').replace(/\D/g, '').slice(0, 6);
                    if (!/^\d{6}$/.test(normalized)) {
                        lastAutoSubmittedOtp = '';
                        return;
                    }
                    if (normalized === lastAutoSubmittedOtp) {
                        return;
                    }
                    lastAutoSubmittedOtp = normalized;
                    window.setTimeout(() => {
                        if (!verifying && !verifyButton.disabled && otpInput.value === normalized) {
                            verifyButton.click();
                        }
                    }, 120);
                });
            }
        }

        bindOtpSignIn({
            formId: 'memberLoginForm',
            emailId: 'memberLoginEmail',
            otpId: 'memberLoginOtpCode',
            otpBlockId: 'memberOtpBlock',
            requestButtonId: 'memberRequestOtpButton',
            verifyButtonId: 'memberVerifyOtpButton',
            errorBoxId: 'memberLoginError',
            successBoxId: 'memberLoginSuccess',
            csrfNameId: 'memberLoginCsrfName',
            csrfTokenId: 'memberLoginCsrfToken',
            statusId: 'memberOtpLiveStatus',
            proceedButton: document.getElementById('memberVerifyOtpButton'),
            autoSubmitOnReady: true,
            requestIdleLabel: 'Send Sign-In Code',
            requestUrl: '/login/member/request-otp',
            verifyUrl: '/login/member/verify-otp',
            defaultRedirectUrl: '/app/dashboard'
        });

        bindOtpSignIn({
            formId: 'staffLoginForm',
            emailId: 'staffLoginEmail',
            otpId: 'staffLoginOtpCode',
            otpBlockId: 'staffOtpBlock',
            requestButtonId: 'staffRequestOtpButton',
            verifyButtonId: 'staffVerifyOtpButton',
            errorBoxId: 'staffLoginError',
            successBoxId: 'staffLoginSuccess',
            csrfNameId: 'staffLoginCsrfName',
            csrfTokenId: 'staffLoginCsrfToken',
            statusId: 'staffOtpLiveStatus',
            proceedButton: document.getElementById('staffVerifyOtpButton'),
            autoSubmitOnReady: true,
            requestIdleLabel: 'Send Sign-In Code',
            requestUrl: '/login/staff/request-otp',
            verifyUrl: '/login/staff/verify-otp',
            defaultRedirectUrl: '/login'
        });
    })();
</script>
</body>
</html>
