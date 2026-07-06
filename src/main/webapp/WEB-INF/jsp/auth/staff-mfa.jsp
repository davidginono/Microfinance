<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Sign-In Verification</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css' />" />
    <%@ include file="../fragments/otp-ui-styles.jspf" %>
    <style>
        body {
            font-family: "Manrope", ui-sans-serif, system-ui;
            background:
                radial-gradient(circle at top, rgba(59, 130, 246, 0.08), transparent 34%),
                linear-gradient(180deg, #f8fbfd 0%, #edf3f8 100%);
            min-height: 100vh;
        }

        .mfa-card {
            border-radius: 0.75rem;
            border: 1px solid #d7e3ee;
            background: #ffffff;
            padding: 1.5rem;
            box-shadow: 0 12px 34px rgba(15, 23, 42, 0.05);
        }

        .mfa-primary-btn {
            border-radius: 0.85rem;
            border: 1px solid #14b8c4;
            background: #14b8c4;
            color: #ffffff;
            transition: background-color 160ms ease, transform 160ms ease;
        }

        .mfa-primary-btn:hover {
            transform: translateY(-1px);
            background: #0ea5b7;
        }

        .mfa-secondary-btn {
            border-radius: 0.85rem;
            border: 1px solid #d7e1ef;
            background: #ffffff;
            color: #12304d;
            transition: background-color 160ms ease, border-color 160ms ease, transform 160ms ease;
        }

        .mfa-secondary-btn:hover {
            transform: translateY(-1px);
            border-color: #b8cfdd;
            background: #f9fcff;
        }

        .mfa-primary-btn:disabled,
        .mfa-secondary-btn:disabled {
            cursor: not-allowed;
            transform: none;
            opacity: 0.7;
        }

        @media (min-width: 640px) {
            .mfa-card {
                padding: 2rem;
            }
        }
    </style>
</head>
<body class="text-slate-800">
<main class="flex min-h-screen items-center justify-center px-4 py-10">
    <div class="mfa-card w-full max-w-xl">
        <div class="mb-6">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Sign-In Verification</p>
            <h1 class="mt-2 text-2xl font-bold text-slate-900" style="font-family:'Sora',ui-sans-serif,system-ui;">Enter your verification code</h1>
            <p class="mt-2 text-sm leading-6 text-slate-500">
                <c:choose>
                    <c:when test="${not empty deliveryMessage}">
                        <c:out value="${deliveryMessage}" />
                    </c:when>
                    <c:otherwise>
                        We sent a one-time code to <span class="font-semibold text-slate-700"><c:out value="${maskedEmail}" /></span>.
                    </c:otherwise>
                </c:choose>
            </p>
        </div>

        <div id="staffMfaError" class="hidden rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700"></div>
        <div id="staffMfaSuccess" class="hidden rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700"></div>

        <form id="staffMfaVerifyForm" class="mt-5 space-y-4">
            <input type="hidden" id="staffMfaCsrfName" value="${_csrf.parameterName}" />
            <input type="hidden" id="staffMfaCsrfToken" value="${_csrf.token}" />

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Verification code
                <input id="staffMfaOtpCode"
                       name="otpCode"
                       type="text"
                       inputmode="numeric"
                       autocomplete="one-time-code"
                       maxlength="6"
                       required
                       data-otp-hidden="true"
                       data-otp-label="Sign-in verification code"
                       class="sr-only"
                       placeholder="123456" />
            </label>

            <button id="staffMfaVerifyButton" type="submit" class="mfa-primary-btn w-full px-4 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70">
                Verify and continue
            </button>
        </form>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <button id="staffMfaResendButton" type="button" class="mfa-secondary-btn px-4 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70">
                Resend code
            </button>
            <form action="/login/mfa/cancel" method="post">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <button type="submit" class="mfa-secondary-btn w-full px-4 py-3 text-sm font-semibold">
                    Cancel
                </button>
            </form>
        </div>

        <p class="mt-5 text-xs leading-5 text-slate-500">This second verification step follows your station's OTP settings.</p>
    </div>
</main>

<script>
    (function () {
        const csrfName = document.getElementById('staffMfaCsrfName')?.value;
        const csrfToken = document.getElementById('staffMfaCsrfToken')?.value;
        const otpInput = document.getElementById('staffMfaOtpCode');
        const verifyForm = document.getElementById('staffMfaVerifyForm');
        const verifyButton = document.getElementById('staffMfaVerifyButton');
        const resendButton = document.getElementById('staffMfaResendButton');
        const errorBox = document.getElementById('staffMfaError');
        const successBox = document.getElementById('staffMfaSuccess');

        function setMessage(target, message) {
            if (!target) {
                return;
            }
            if (message && String(message).trim()) {
                target.textContent = message;
                target.classList.remove('hidden');
            } else {
                target.textContent = '';
                target.classList.add('hidden');
            }
        }

        function setBusy(button, busy, busyLabel, idleLabel) {
            if (!button) {
                return;
            }
            button.disabled = busy;
            button.textContent = busy ? busyLabel : idleLabel;
        }

        async function postForm(url, params) {
            const body = new URLSearchParams();
            if (csrfName && csrfToken) {
                body.set(csrfName, csrfToken);
            }
            Object.entries(params || {}).forEach(([key, value]) => {
                body.set(key, value == null ? '' : String(value));
            });
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: body.toString()
            });
            const payload = await response.json().catch(() => ({
                valid: false,
                message: 'We could not complete that verification step.'
            }));
            return { response, payload };
        }

        verifyForm?.addEventListener('submit', async function (event) {
            event.preventDefault();
            setMessage(errorBox, '');
            setMessage(successBox, '');
            const code = otpInput ? otpInput.value.trim() : '';
            if (!/^\d{6}$/.test(code)) {
                setMessage(errorBox, 'Enter the 6-digit verification code.');
                window.SaccosOtp?.focusBoxes(otpInput);
                return;
            }

            setBusy(verifyButton, true, 'Verifying...', 'Verify and continue');
            try {
                const result = await postForm('/login/mfa/verify', { otpCode: code });
                if (!result.response.ok || !result.payload.valid) {
                    setMessage(errorBox, result.payload.message || 'We could not verify that code.');
                    return;
                }
                window.location.href = result.payload.redirectUrl || '/';
            } catch (error) {
                setMessage(errorBox, 'We could not verify that code right now. Try again.');
            } finally {
                setBusy(verifyButton, false, 'Verifying...', 'Verify and continue');
            }
        });

        resendButton?.addEventListener('click', async function () {
            setMessage(errorBox, '');
            setMessage(successBox, '');
            setBusy(resendButton, true, 'Sending...', 'Resend code');
            try {
                const result = await postForm('/login/mfa/resend');
                if (!result.response.ok || !result.payload.valid) {
                    setMessage(errorBox, result.payload.message || 'We could not send a new code.');
                    return;
                }
                setMessage(successBox, result.payload.message || 'We sent a new verification code.');
                window.SaccosOtp?.focusBoxes(otpInput);
            } catch (error) {
                setMessage(errorBox, 'We could not send a new code right now. Try again.');
            } finally {
                setBusy(resendButton, false, 'Sending...', 'Resend code');
            }
        });
    })();
</script>
</body>
</html>
