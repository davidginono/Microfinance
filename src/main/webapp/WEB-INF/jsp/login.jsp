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
        .forgot-password-dialog {
            width: min(100%, 34rem);
            max-height: calc(100vh - 3rem);
            overflow-y: auto;
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
                <h1 class="auth-heading">Log in to the Loan Application Portal</h1>
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
                                <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
                                    <span class="text-slate-500">Having trouble with your password?</span>
                                    <button type="button" class="text-xs font-semibold text-[#2F348D] hover:underline" data-forgot-password-open="member">Forgot password?</button>
                                </div>
                                <button class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold" type="submit">Log in</button>
                            </form>
                        </div>

                        <div class="auth-or-divider">or</div>

                        <div class="auth-mode-card space-y-4">
                            <div>
                                <p class="auth-mode-title">Google SSO</p>
                            </div>
                            <c:choose>
                                <c:when test="${googleSsoEnabled}">
                                    <a href="/oauth2/authorization/google" class="auth-secondary-btn inline-flex w-full items-center justify-center gap-3 px-4 py-3.5 text-sm font-semibold">
                                        <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-white text-sm font-bold text-slate-700">G</span>
                                        Continue with Google
                                    </a>
                                </c:when>
                                <c:otherwise>
                                    <button type="button" class="auth-secondary-btn inline-flex w-full cursor-not-allowed items-center justify-center gap-3 px-4 py-3.5 text-sm font-semibold opacity-70" disabled>
                                        Google SSO not configured
                                    </button>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>

                    <div data-login-tab="staff" class="${activeLoginTab eq 'staff' ? '' : 'hidden '}space-y-5">
                        <div class="auth-mode-card space-y-4">
                            <div>
                                <p class="auth-mode-title">Member Number &amp; Password</p>
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
                                <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
                                    <span class="text-slate-500">Having trouble with your password?</span>
                                    <button type="button" class="text-xs font-semibold text-[#2F348D] hover:underline" data-forgot-password-open="staff">Forgot password?</button>
                                </div>
                                <button class="auth-primary-btn w-full px-4 py-3.5 text-sm font-semibold" type="submit">Log in as Staff</button>
                            </form>
                        </div>

                        <div class="auth-or-divider">or</div>

                        <div class="auth-mode-card space-y-4">
                            <div>
                                <p class="auth-mode-title">Google SSO</p>
                            </div>
                            <c:choose>
                                <c:when test="${googleSsoEnabled}">
                                    <a href="/oauth2/authorization/google" class="auth-secondary-btn inline-flex w-full items-center justify-center gap-3 px-4 py-3.5 text-sm font-semibold">
                                        <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-white text-sm font-bold text-slate-700">G</span>
                                        Continue with Google
                                    </a>
                                </c:when>
                                <c:otherwise>
                                    <button type="button" class="auth-secondary-btn inline-flex w-full cursor-not-allowed items-center justify-center gap-3 px-4 py-3.5 text-sm font-semibold opacity-70" disabled>
                                        Google SSO not configured
                                    </button>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>

                    <div class="auth-login-meta mt-8 border-t border-slate-200 pt-6 text-center text-sm text-slate-500">
                        <span>Don't have an account? </span>
                        <a href="/register/member" class="auth-signup-link"><spring:message code="login.registerMember" /></a>
                    </div>
                    </div>
        </div>
    </div>
</div>
<div id="forgotPasswordModal" class="fixed inset-0 z-50 hidden items-center justify-center bg-slate-900/45 px-4 py-8">
    <div class="forgot-password-dialog rounded-xl border border-slate-200 bg-white shadow-2xl">
        <div class="flex items-start justify-between border-b border-slate-200 px-5 py-4">
            <div>
                <h2 class="text-lg font-bold text-slate-900">Reset password</h2>
                <p id="forgotPasswordSubtitle" class="mt-1 text-sm text-slate-500">Enter your member number. The code goes to the email saved on your account.</p>
            </div>
            <button type="button" class="rounded-md px-2 py-1 text-xl leading-none text-slate-400 hover:bg-slate-100 hover:text-slate-700" data-forgot-password-close aria-label="Close modal">&times;</button>
        </div>
        <form id="forgotPasswordForm" class="space-y-4 px-5 py-5">
            <input type="hidden" id="forgotPasswordCsrfName" value="${_csrf.parameterName}" />
            <input type="hidden" id="forgotPasswordCsrfToken" value="${_csrf.token}" />
            <input type="hidden" id="forgotPasswordAccountType" value="member" />
            <div id="forgotPasswordError" class="hidden rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700"></div>
            <div id="forgotPasswordSuccess" class="hidden rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700"></div>
            <div>
                <label id="forgotPasswordUsernameLabel" class="mb-1.5 block auth-section-label">Member Number</label>
                <input id="forgotPasswordUsername" autocomplete="username" required class="auth-input w-full px-4 py-3 text-slate-900" />
                <p class="mt-2 text-xs text-slate-500">We will send the OTP to the registered email for this account.</p>
            </div>
            <div id="forgotPasswordOtpBlock" class="hidden">
                <label class="mb-1.5 block auth-section-label">OTP Code</label>
                <input id="forgotPasswordOtp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" class="auth-input w-full px-4 py-3 tracking-[0.3em] text-slate-900" />
            </div>
            <div id="forgotPasswordNewPasswordBlock" class="hidden space-y-4">
                <div>
                    <label class="mb-1.5 block auth-section-label">New Password</label>
                    <input id="forgotPasswordNewPassword" type="password" autocomplete="new-password" minlength="8" class="auth-input w-full px-4 py-3 text-slate-900" />
                </div>
                <div>
                    <label class="mb-1.5 block auth-section-label">Confirm Password</label>
                    <input id="forgotPasswordConfirmPassword" type="password" autocomplete="new-password" minlength="8" class="auth-input w-full px-4 py-3 text-slate-900" />
                </div>
            </div>
            <div class="flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-4">
                <button type="button" class="auth-secondary-btn px-4 py-2.5 text-sm font-semibold" data-forgot-password-close>Cancel</button>
                <button id="forgotPasswordAction" type="button" class="auth-primary-btn px-4 py-2.5 text-sm font-semibold">Send OTP Code</button>
            </div>
        </form>
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

        function bindForgotPassword() {
            const modal = document.getElementById('forgotPasswordModal');
            const accountTypeInput = document.getElementById('forgotPasswordAccountType');
            const subtitle = document.getElementById('forgotPasswordSubtitle');
            const usernameInput = document.getElementById('forgotPasswordUsername');
            const usernameLabel = document.getElementById('forgotPasswordUsernameLabel');
            const otpBlock = document.getElementById('forgotPasswordOtpBlock');
            const otpInput = document.getElementById('forgotPasswordOtp');
            const newPasswordBlock = document.getElementById('forgotPasswordNewPasswordBlock');
            const passwordInput = document.getElementById('forgotPasswordNewPassword');
            const confirmPasswordInput = document.getElementById('forgotPasswordConfirmPassword');
            const actionButton = document.getElementById('forgotPasswordAction');
            const errorBox = document.getElementById('forgotPasswordError');
            const successBox = document.getElementById('forgotPasswordSuccess');
            const csrfName = document.getElementById('forgotPasswordCsrfName')?.value;
            const csrfToken = document.getElementById('forgotPasswordCsrfToken')?.value;
            if (!modal || !accountTypeInput || !usernameInput || !otpBlock || !otpInput || !newPasswordBlock || !passwordInput || !confirmPasswordInput || !actionButton || !csrfName || !csrfToken) {
                return;
            }

            let step = 'request';
            let busy = false;

            const setError = (message) => {
                successBox?.classList.add('hidden');
                if (errorBox) {
                    errorBox.textContent = message || '';
                    errorBox.classList.toggle('hidden', !message);
                }
            };
            const setSuccess = (message) => {
                errorBox?.classList.add('hidden');
                if (successBox) {
                    successBox.textContent = message || '';
                    successBox.classList.toggle('hidden', !message);
                }
            };
            const setStep = (nextStep) => {
                step = nextStep;
                otpBlock.classList.toggle('hidden', step === 'request');
                newPasswordBlock.classList.toggle('hidden', step !== 'save');
                actionButton.textContent = step === 'request' ? 'Send OTP Code' : (step === 'verify' ? 'Verify Code' : 'Save Password');
            };
            const open = (accountType) => {
                accountTypeInput.value = accountType === 'staff' ? 'staff' : 'member';
                const loginUsername = accountTypeInput.value === 'staff'
                    ? document.querySelector('[data-login-tab="staff"] input[name="username"]')?.value
                    : document.querySelector('[data-login-tab="member"] input[name="username"]')?.value;
                usernameInput.value = (loginUsername || '').trim();
                if (usernameLabel) {
                    usernameLabel.textContent = accountTypeInput.value === 'staff' ? 'Staff Member Number' : 'Member Number';
                }
                if (subtitle) {
                    subtitle.textContent = accountTypeInput.value === 'staff'
                        ? 'Enter your staff member number. The code goes to the email saved on your account.'
                        : 'Enter your member number. The code goes to the email saved on your account.';
                }
                otpInput.value = '';
                passwordInput.value = '';
                confirmPasswordInput.value = '';
                setError('');
                setSuccess('');
                setStep('request');
                modal.classList.remove('hidden');
                modal.classList.add('flex');
                window.setTimeout(() => usernameInput.focus(), 50);
            };
            const close = () => {
                modal.classList.add('hidden');
                modal.classList.remove('flex');
            };
            const post = async (url, extra) => {
                const body = new URLSearchParams();
                body.set('username', usernameInput.value);
                body.set('accountType', accountTypeInput.value);
                body.set(csrfName, csrfToken);
                Object.entries(extra || {}).forEach(([key, value]) => body.set(key, value));
                const response = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'Accept': 'application/json'
                    },
                    body
                });
                const payload = await response.json().catch(() => ({}));
                if (!response.ok || payload.valid !== true) {
                    throw new Error(payload.message || 'Unable to complete password reset right now.');
                }
                return payload;
            };

            document.querySelectorAll('[data-forgot-password-open]').forEach((button) => {
                button.addEventListener('click', () => open(button.getAttribute('data-forgot-password-open')));
            });
            document.querySelectorAll('[data-forgot-password-close]').forEach((button) => {
                button.addEventListener('click', close);
            });
            modal.addEventListener('click', (event) => {
                if (event.target === modal) {
                    close();
                }
            });

            actionButton.addEventListener('click', async () => {
                if (busy) {
                    return;
                }
                if (!usernameInput.reportValidity()) {
                    return;
                }
                if (step !== 'request') {
                    otpInput.value = (otpInput.value || '').replace(/\D/g, '').slice(0, 6);
                    if (!/^\d{6}$/.test(otpInput.value)) {
                        otpInput.reportValidity();
                        otpInput.focus();
                        setError('Enter the 6-digit OTP code sent to your email.');
                        return;
                    }
                }
                if (step === 'save') {
                    if (!passwordInput.reportValidity() || !confirmPasswordInput.reportValidity()) {
                        return;
                    }
                    if (passwordInput.value !== confirmPasswordInput.value) {
                        setError('Passwords do not match.');
                        confirmPasswordInput.focus();
                        return;
                    }
                }

                busy = true;
                actionButton.disabled = true;
                try {
                    const payload = step === 'request'
                        ? await post('/login/password-reset/request-otp')
                        : step === 'verify'
                            ? await post('/login/password-reset/verify-otp', { otpCode: otpInput.value })
                            : await post('/login/password-reset/save', {
                                otpCode: otpInput.value,
                                password: passwordInput.value,
                                confirmPassword: confirmPasswordInput.value
                            });
                    setSuccess(payload.message || 'Done.');
                    if (step === 'request') {
                        setStep('verify');
                        otpInput.focus();
                    } else if (step === 'verify') {
                        setStep('save');
                        passwordInput.focus();
                    } else {
                        window.setTimeout(close, 900);
                    }
                } catch (error) {
                    setError(error.message);
                } finally {
                    busy = false;
                    actionButton.disabled = false;
                }
            });
        }

        bindForgotPassword();
    })();
</script>
</body>
</html>
