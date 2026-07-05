<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Staff MFA Verification</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css' />" />
    <style>
        body {
            font-family: "Manrope", ui-sans-serif, system-ui;
            background:
                radial-gradient(circle at top, rgba(47, 52, 141, 0.08), transparent 34%),
                linear-gradient(180deg, #f8fbfd 0%, #edf3f8 100%);
            min-height: 100vh;
        }
        .mfa-card {
            box-shadow: 0 24px 48px -24px rgba(15, 23, 42, 0.22);
        }
    </style>
</head>
<body class="text-slate-800">
<main class="flex min-h-screen items-center justify-center px-4 py-10">
    <div class="mfa-card w-full max-w-xl rounded-2xl border border-slate-200 bg-white p-8">
        <div class="mb-6">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-[#2F348D]">Admin Sign-In Verification</p>
            <h1 class="mt-2 text-2xl font-bold text-slate-900" style="font-family:'Sora',ui-sans-serif,system-ui;">Enter your verification code</h1>
            <p class="mt-2 text-sm text-slate-500">We sent a one-time code to <span class="font-semibold text-slate-700">${maskedEmail}</span>.</p>
        </div>

        <div id="staffMfaError" class="hidden rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700"></div>
        <div id="staffMfaSuccess" class="hidden rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700"></div>

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
                       class="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-3 text-base tracking-[0.28em] text-slate-800"
                       placeholder="6-digit code" />
            </label>

            <button id="staffMfaVerifyButton" type="submit" class="w-full rounded-lg bg-[#2F348D] px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-[#262a73] disabled:cursor-not-allowed disabled:opacity-70">
                Verify and continue
            </button>
        </form>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <button id="staffMfaResendButton" type="button" class="rounded-lg border border-slate-300 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-70">
                Resend code
            </button>
            <form action="/login/staff/mfa/cancel" method="post">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <button type="submit" class="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50">
                    Cancel
                </button>
            </form>
        </div>

        <p class="mt-5 text-xs text-slate-500">Only SACCOS admin accounts use this second verification step when the local development bypass is off.</p>
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
            if (!code) {
                setMessage(errorBox, 'Enter the verification code from your email.');
                otpInput?.focus();
                return;
            }

            setBusy(verifyButton, true, 'Verifying...', 'Verify and continue');
            try {
                const result = await postForm('/login/staff/mfa/verify', { otpCode: code });
                if (!result.response.ok || !result.payload.valid) {
                    setMessage(errorBox, result.payload.message || 'We could not verify that code.');
                    return;
                }
                window.location.href = result.payload.redirectUrl || '/admin/dashboard';
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
                const result = await postForm('/login/staff/mfa/resend');
                if (!result.response.ok || !result.payload.valid) {
                    setMessage(errorBox, result.payload.message || 'We could not send a new code.');
                    return;
                }
                setMessage(successBox, result.payload.message || 'We sent a new verification code.');
                otpInput?.focus();
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
