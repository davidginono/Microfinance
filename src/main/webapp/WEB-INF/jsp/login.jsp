<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title><spring:message code="app.title" /></title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png?v=20260826-clean' />" />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Rg.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Bd.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/amazon-ember.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-components.css?v=20260809-loan-detail-action-v33' />" />
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20261030-auth-alerts' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-resource-details.css?v=20261030-auth-alerts' />" />
    <link rel="stylesheet" href="<c:url value='/css/public-access.css' />" />
    <script defer src="<c:url value='/js/password-visibility.js?v=20261022' />"></script>
</head>
<body class="auth-shell aws-auth-shell auth-login-page public-access min-h-screen font-sans text-slate-900 antialiased">
<nav class="auth-language" aria-label="<spring:message code='portal.language' />">
    <c:forEach items="${['en','sw']}" var="loginLanguage">
        <c:url value="${systemAdminLogin ? '/system-admin/login' : '/login'}" var="loginLanguageUrl">
            <c:param name="lang" value="${loginLanguage}" />
        </c:url>
        <a href="${loginLanguageUrl}" lang="${loginLanguage}" hreflang="${loginLanguage}" data-login-language ${pageContext.response.locale.language eq loginLanguage ? 'aria-current="true"' : ''}>${loginLanguage eq 'sw' ? 'Kiswahili' : 'English'}</a>
    </c:forEach>
</nav>
<main class="public-access-layout">
    <div class="auth-login-layout">
        <div id="authToastContainer" class="auth-notification-rail" aria-live="polite"></div>
    <div class="auth-frame public-access-card">
        <%@ include file="fragments/public-access-brand.jspf" %>
        <h1 class="auth-heading mb-6"><spring:message code="${systemAdminLogin ? 'login.systemAdminHeading' : 'login.title'}" /></h1>
        <div class="pb-1">

                    <spring:message code="login.invalidCredentials" var="loginInvalidCredentials" />
                    <c:if test="${param.error != null}">
                        <div hidden data-toast-message="${fn:escapeXml(not empty errorMessage ? errorMessage : loginInvalidCredentials)}" data-toast-type="error" data-mfa-email-fallback="${not empty errorMessage and fn:contains(errorMessage, 'SMS OTP')}"></div>
                    </c:if>
                    <c:if test="${not empty errorMessage and param.error == null}">
                        <div hidden data-toast-message="${fn:escapeXml(errorMessage)}" data-toast-type="error" data-mfa-email-fallback="${fn:contains(errorMessage, 'SMS OTP')}"></div>
                    </c:if>
                    <c:if test="${param.logout != null}">
                        <div hidden data-toast-message="Logged out successfully." data-toast-type="success" data-toast-auto-dismiss-ms="4000" data-toast-closeable="false"></div>
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
                    <c:if test="${param.loginPageExpired != null}">
                        <div hidden data-toast-message="Your sign-in page was refreshed for security. Please enter your password again." data-toast-type="info"></div>
                    </c:if>

                    <c:if test="${not empty assignedUsername}">
                        <div class="auth-mode-card">
                            <p id="assignedUsernameLabel" class="auth-section-label"><spring:message code="register.member.username" /></p>
                            <output id="assignedUsername" aria-labelledby="assignedUsernameLabel" class="block mt-2 font-bold"><c:out value="${assignedUsername}" /></output>
                        </div>
                    </c:if>

                    <c:choose>
                    <c:when test="${systemAdminLogin}">
                    <div data-login-tab="system-admin" class="space-y-5">
                        <div class="space-y-4">
                            <div>
                                <p class="auth-mode-title"><spring:message code="home.systemAdmin" /></p>
                            </div>
                            <form action="/login" method="post" class="space-y-4" data-login-password-form>
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="loginType" value="system-admin-password" />
                                <div>
                                    <label class="mb-2 block auth-section-label"><spring:message code="login.systemAdminId" /></label>
                                    <input name="username" autocomplete="username" required autofocus class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <div>
                                    <label class="mb-2 block auth-section-label"><spring:message code="login.password" /></label>
                                    <div class="auth-password-control"><input id="systemAdminLoginPassword" type="password" name="password" autocomplete="current-password" required class="auth-input w-full px-4 py-3.5 text-slate-900" /><button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="systemAdminLoginPassword" aria-pressed="false" data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button></div>
                                </div>
                                <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
                                    <span class="text-slate-500"><spring:message code="login.passwordHelp" /></span>
                                    <a href="#forgotPasswordModal" class="auth-text-link" data-forgot-password-open="system-admin"><spring:message code="login.forgotPassword" /></a>
                                </div>
                                <button class="auth-primary-btn inline-flex w-full items-center justify-center gap-2 px-4 py-3.5 text-sm font-semibold" type="submit" data-login-submit-button>
                                    <span data-login-submit-label><spring:message code="login.asSystemAdmin" /></span>
                                    <span class="hidden items-center gap-2" data-login-submit-loading>
                                        <span class="auth-submit-spinner" aria-hidden="true"></span>
                                        <span><spring:message code="login.signingIn" /></span>
                                    </span>
                                </button>
                            </form>
                        </div>
                        <div class="auth-login-meta border-t border-slate-200 pt-6 text-center text-sm">
                            <a href="/login" class="auth-text-link"><spring:message code="login.otherLogin" /></a>
                        </div>
                    </div>
                    </c:when>
                    <c:otherwise>

                    <div id="staffLoginPanel" data-login-tab="staff" class="space-y-5">
                        <div class="space-y-4">
                            <form action="/login" method="post" class="space-y-4" data-login-password-form>
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="loginType" value="staff-password" />
                                <div>
                                    <label for="staffLoginIdentifier" class="mb-2 block auth-section-label"><spring:message code="login.username" /></label>
                                    <input id="staffLoginIdentifier" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" maxlength="254" required class="auth-input w-full px-4 py-3.5 text-slate-900" />
                                </div>
                                <div>
                                    <label class="mb-2 block auth-section-label"><spring:message code="login.password" /></label>
                                    <div class="auth-password-control"><input id="staffLoginPassword" type="password" name="password" autocomplete="current-password" required class="auth-input w-full px-4 py-3.5 text-slate-900" /><button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="staffLoginPassword" aria-pressed="false" data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button></div>
                                </div>
                                <div class="flex flex-wrap items-center justify-end gap-2 text-xs">
                                    <a href="#forgotPasswordModal" class="auth-text-link" data-forgot-password-open="staff"><spring:message code="login.forgotPassword" /></a>
                                </div>
                                <button class="auth-primary-btn inline-flex w-full items-center justify-center gap-2 px-4 py-3.5 text-sm font-semibold" type="submit" data-login-submit-button>
                                    <span data-login-submit-label><spring:message code="login.submit" /></span>
                                    <span class="hidden items-center gap-2" data-login-submit-loading>
                                        <span class="auth-submit-spinner" aria-hidden="true"></span>
                                        <span><spring:message code="login.signingIn" /></span>
                                    </span>
                                </button>
                            </form>
                        </div>

                    </div>

                    <div class="auth-login-meta mt-8 border-t border-slate-200 pt-6 text-center text-sm text-slate-500">
                        <div class="mt-3">
                            <a href="/system-admin/login" class="auth-text-link"><spring:message code="home.systemAdmin" /></a>
                        </div>
                    </div>
                    </c:otherwise>
                    </c:choose>
                    </div>
        </div>
    </div>
</main>
<div id="forgotPasswordModal" class="fixed inset-0 z-50 hidden items-center justify-center bg-slate-900/45 px-4 py-8">
    <div class="forgot-password-dialog border border-slate-200 bg-white">
        <div class="flex items-start justify-between border-b border-slate-200 px-5 py-4">
            <div>
                <h2 class="text-lg font-bold text-slate-900">Reset password</h2>
                <p id="forgotPasswordSubtitle" class="mt-1 text-sm text-slate-500"><spring:message code="login.clientResetHelp" /></p>
            </div>
            <button type="button" class="px-2 py-1 text-xl leading-none text-slate-400 hover:bg-slate-100 hover:text-slate-700" data-forgot-password-close aria-label="Close modal">&times;</button>
        </div>
        <form id="forgotPasswordForm" class="space-y-4 px-5 py-5">
            <input type="hidden" id="forgotPasswordCsrfName" value="${_csrf.parameterName}" />
            <input type="hidden" id="forgotPasswordCsrfToken" value="${_csrf.token}" />
            <input type="hidden" id="forgotPasswordAccountType" value="staff" />
            <div id="forgotPasswordError" class="hidden border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-700"></div>
            <div id="forgotPasswordSuccess" class="hidden border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700"></div>
            <div>
                <label id="forgotPasswordUsernameLabel" for="forgotPasswordUsername" class="mb-1.5 block auth-section-label"><spring:message code="login.username" /></label>
                <input id="forgotPasswordUsername" autocomplete="username" autocapitalize="none" spellcheck="false" maxlength="254" required class="auth-input w-full px-4 py-3 text-slate-900" />
                <p class="mt-2 text-xs text-slate-500">We will send the OTP to the registered email for this account.</p>
            </div>
            <div id="forgotPasswordOtpBlock" class="hidden">
                <label class="mb-1.5 block auth-section-label">OTP Code</label>
                <input id="forgotPasswordOtp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" class="auth-input w-full px-4 py-3 tracking-[0.3em] text-slate-900" />
            </div>
            <div id="forgotPasswordNewPasswordBlock" class="hidden space-y-4">
                <div>
                    <label class="mb-1.5 block auth-section-label">New Password</label>
                    <div class="auth-password-control"><input id="forgotPasswordNewPassword" type="password" autocomplete="new-password" minlength="8" class="auth-input w-full px-4 py-3 text-slate-900" /><button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="forgotPasswordNewPassword" aria-pressed="false" data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button></div>
                </div>
                <div>
                    <label class="mb-1.5 block auth-section-label">Confirm Password</label>
                    <div class="auth-password-control"><input id="forgotPasswordConfirmPassword" type="password" autocomplete="new-password" minlength="8" class="auth-input w-full px-4 py-3 text-slate-900" /><button type="button" class="password-visibility-toggle" data-password-visibility aria-controls="forgotPasswordConfirmPassword" aria-pressed="false" data-show-label="<spring:message code='password.show' />" data-hide-label="<spring:message code='password.hide' />"><spring:message code="password.show" /></button></div>
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
        const csrfName = '${_csrf.parameterName}';
        const csrfToken = '${_csrf.token}';

        async function sendMfaCodeToEmail() {
            const body = new URLSearchParams();
            if (csrfName && csrfToken) {
                body.set(csrfName, csrfToken);
            }
            const response = await fetch('/login/mfa/send-email', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: body.toString()
            });
            const payload = await response.json().catch(() => ({
                valid: false,
                message: 'We could not send the code to email right now.'
            }));
            return { response, payload };
        }

        window.showToast = function (type, message, options) {
            if (!toastContainer || !message || !String(message).trim()) {
                return null;
            }
            const settings = options || {};
            const offerEmailFallback = settings.mfaEmailFallback === true;
            const autoDismissMs = Number(settings.autoDismissMs || 0);
            const closeable = settings.closeable !== false;
            const variant = type === 'error' ? 'error' : (type === 'success' ? 'success' : 'info');
            const icon = variant === 'success'
                ? '<svg class="console-icon console-message-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><circle cx="8" cy="8" r="7"/><path d="M4.5 7.5L7 10L11 5"/></svg>'
                : variant === 'error'
                    ? '<svg class="console-icon console-message-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><circle cx="8" cy="8" r="7"/><path d="M5.5 5.5L10.5 10.5M10.5 5.5L5.5 10.5"/></svg>'
                    : '<svg class="console-icon console-message-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><circle cx="8" cy="8" r="7"/><path d="M8 12V7M8 6V4"/></svg>';
            const toast = document.createElement('div');
            toast.className = 'app-toast-enter console-message auth-notification-bar auth-notification-' + variant;
            toast.dataset.messageType = variant;
            toast.setAttribute('role', variant === 'error' ? 'alert' : 'status');
            const closeButtonMarkup = closeable
                ? '<button type="button" class="app-toast-close" aria-label="Dismiss notification">' +
                    '<svg class="app-toast-close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>' +
                '</button>'
                : '';
            toast.innerHTML = icon +
                '<div class="flex items-start gap-3">' +
                    '<div class="min-w-0 flex-1' + (closeable ? ' pr-6' : '') + '">' +
                        '<p class="text-sm font-semibold leading-5" data-toast-text></p>' +
                        '<div class="mt-3 hidden" data-toast-email-fallback>' +
                            '<p class="text-xs font-semibold uppercase tracking-wide">Or send to email?</p>' +
                            '<div class="mt-2 flex flex-wrap gap-2">' +
                                '<button type="button" class="border border-rose-200 bg-white px-3 py-1.5 text-xs font-bold text-rose-700 hover:bg-rose-100" data-toast-email-yes>Yes</button>' +
                                '<button type="button" class="border border-rose-200 bg-white px-3 py-1.5 text-xs font-bold text-rose-700 hover:bg-rose-100" data-toast-email-no>No</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    closeButtonMarkup +
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
            const emailFallback = toast.querySelector('[data-toast-email-fallback]');
            const emailYes = toast.querySelector('[data-toast-email-yes]');
            const emailNo = toast.querySelector('[data-toast-email-no]');
            if (offerEmailFallback && emailFallback && emailYes && emailNo) {
                emailFallback.classList.remove('hidden');
                emailNo.addEventListener('click', dismiss);
                emailYes.addEventListener('click', async () => {
                    emailYes.disabled = true;
                    emailNo.disabled = true;
                    emailYes.textContent = 'Sending...';
                    try {
                        const result = await sendMfaCodeToEmail();
                        if (!result.response.ok || !result.payload.valid) {
                            toast.querySelector('[data-toast-text]').textContent = result.payload.message || 'We could not send the code to email right now.';
                            emailYes.disabled = false;
                            emailNo.disabled = false;
                            emailYes.textContent = 'Yes';
                            return;
                        }
                        dismiss();
                        window.showToast('success', result.payload.message || 'We sent an OTP code to your registered email.');
                        window.setTimeout(() => {
                            window.location.href = result.payload.redirectUrl || '/login/mfa';
                        }, 500);
                    } catch (error) {
                        toast.querySelector('[data-toast-text]').textContent = 'We could not send the code to email right now.';
                        emailYes.disabled = false;
                        emailNo.disabled = false;
                        emailYes.textContent = 'Yes';
                    }
                });
            }
            toast.querySelector('.app-toast-close')?.addEventListener('click', dismiss);
            toastContainer.appendChild(toast);
            if (Number.isFinite(autoDismissMs) && autoDismissMs > 0) {
                window.setTimeout(dismiss, autoDismissMs);
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
                    const autoDismissMs = Number.parseInt(element.getAttribute('data-toast-auto-dismiss-ms') || '0', 10);
                    window.showToast(element.getAttribute('data-toast-type') || 'info', message, {
                        mfaEmailFallback: element.getAttribute('data-mfa-email-fallback') === 'true',
                        autoDismissMs: Number.isFinite(autoDismissMs) ? autoDismissMs : 0,
                        closeable: element.getAttribute('data-toast-closeable') !== 'false'
                    });
                }
            });
            const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message]')).find((element) =>
                !element.classList.contains('hidden') && element.textContent && element.textContent.trim()
            );
            if (initialAlert) {
                window.loginPageScrollToFeedback(initialAlert);
            }
        });

        function bindPasswordLoginSubmitGuard() {
            const forms = Array.from(document.querySelectorAll('[data-login-password-form]'));
            if (!forms.length) {
                return;
            }

            let locked = false;
            const loginPageIssuedAtMs = Date.now();
            const loginPageTimeoutSeconds = Number('${pageContext.session.maxInactiveInterval}');
            const loginPageTimeoutMs = Number.isFinite(loginPageTimeoutSeconds) && loginPageTimeoutSeconds > 0
                ? loginPageTimeoutSeconds * 1000
                : Number.POSITIVE_INFINITY;
            const loginPageRefreshMarginMs = Number.isFinite(loginPageTimeoutMs)
                ? Math.min(60000, Math.max(5000, loginPageTimeoutMs * 0.1))
                : 0;
            const loginPageFreshMs = Number.isFinite(loginPageTimeoutMs)
                ? Math.max(10000, loginPageTimeoutMs - loginPageRefreshMarginMs)
                : Number.POSITIVE_INFINITY;
            const lockButtons = Array.from(document.querySelectorAll('[data-login-tab-toggle], [data-forgot-password-open]'));

            const isLoginPageStale = () => Date.now() - loginPageIssuedAtMs >= loginPageFreshMs;

            const refreshExpiredLoginPage = (form) => {
                const url = new URL(window.location.href);
                const loginType = form.querySelector('input[name="loginType"]')?.value || '';
                url.searchParams.set('loginPageExpired', '1');
                url.searchParams.delete('error');
                url.searchParams.delete('logout');
                url.searchParams.delete('claimed');
                if (loginType === 'staff-password') {
                    url.searchParams.set('tab', 'staff');
                } else if (loginType !== 'system-admin-password') {
                    url.searchParams.delete('tab');
                }
                window.location.replace(url.toString());
            };

            const resetSubmitting = () => {
                locked = false;
                forms.forEach((form) => {
                    form.removeAttribute('aria-busy');
                    form.querySelectorAll('input:not([type="hidden"])').forEach((input) => {
                        input.readOnly = false;
                        input.removeAttribute('aria-readonly');
                    });
                    const button = form.querySelector('[data-login-submit-button]');
                    const label = form.querySelector('[data-login-submit-label]');
                    const loading = form.querySelector('[data-login-submit-loading]');
                    if (button) {
                        button.disabled = false;
                        button.removeAttribute('aria-disabled');
                    }
                    label?.classList.remove('hidden');
                    loading?.classList.add('hidden');
                    loading?.classList.remove('inline-flex');
                });
                lockButtons.forEach((button) => {
                    button.disabled = false;
                    button.removeAttribute('aria-disabled');
                });
            };

            const setSubmitting = (activeForm) => {
                locked = true;
                forms.forEach((form) => {
                    form.setAttribute('aria-busy', String(form === activeForm));
                    form.querySelectorAll('input:not([type="hidden"])').forEach((input) => {
                        input.readOnly = true;
                        input.setAttribute('aria-readonly', 'true');
                    });
                    const button = form.querySelector('[data-login-submit-button]');
                    const label = form.querySelector('[data-login-submit-label]');
                    const loading = form.querySelector('[data-login-submit-loading]');
                    if (button) {
                        button.disabled = true;
                        button.setAttribute('aria-disabled', 'true');
                    }
                    if (form === activeForm) {
                        label?.classList.add('hidden');
                        loading?.classList.remove('hidden');
                        loading?.classList.add('inline-flex');
                    }
                });
                lockButtons.forEach((button) => {
                    button.disabled = true;
                    button.setAttribute('aria-disabled', 'true');
                });
            };

            forms.forEach((form) => {
                form.addEventListener('submit', (event) => {
                    if (locked) {
                        event.preventDefault();
                        return;
                    }
                    if (typeof form.checkValidity === 'function' && !form.checkValidity()) {
                        return;
                    }
                    if (isLoginPageStale()) {
                        event.preventDefault();
                        refreshExpiredLoginPage(form);
                        return;
                    }
                    setSubmitting(form);
                });
            });
            window.addEventListener('pageshow', resetSubmitting);
        }

        bindPasswordLoginSubmitGuard();

        if (new URL(window.location.href).searchParams.has('loginPageExpired')) {
            const cleanUrl = new URL(window.location.href);
            cleanUrl.searchParams.delete('loginPageExpired');
            window.history.replaceState(window.history.state, '', cleanUrl);
        }

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
                accountTypeInput.value = accountType === 'system-admin'
                    ? 'system-admin'
                    : 'staff';
                const loginUsername = document.querySelector(
                    '[data-login-tab="' + accountTypeInput.value + '"] input[name="username"]'
                )?.value;
                usernameInput.value = (loginUsername || '').trim();
                if (usernameLabel) {
                    usernameLabel.textContent = accountTypeInput.value === 'system-admin'
                        ? 'System Admin ID'
                        : '<spring:message code="login.username" javaScriptEscape="true" />';
                }
                if (subtitle) {
                    subtitle.textContent = accountTypeInput.value === 'system-admin'
                        ? 'Enter your System Admin ID. The code goes to the email saved on your account.'
                        : '<spring:message code="login.clientResetHelp" javaScriptEscape="true" />';
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

            document.querySelectorAll('[data-forgot-password-open]').forEach((link) => {
                link.addEventListener('click', (event) => {
                    event.preventDefault();
                    open(link.getAttribute('data-forgot-password-open'));
                });
            });
            document.querySelectorAll('[data-forgot-password-close]').forEach((button) => {
                button.addEventListener('click', close);
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
