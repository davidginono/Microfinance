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

            <div id="staffMfaOtpStatus" class="hidden items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-600" aria-live="polite">
                <span id="staffMfaOtpSpinner" class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#14b8c4]"></span>
                <svg id="staffMfaOtpTick" class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.2a1 1 0 01-1.41 0L3.296 9.19a1 1 0 111.408-1.42l4.044 4.018 6.548-6.5a1 1 0 011.408.002z" clip-rule="evenodd" />
                </svg>
                <svg id="staffMfaOtpErrorIcon" class="hidden h-5 w-5 text-rose-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                </svg>
                <span id="staffMfaOtpStatusText">Enter the code to verify it.</span>
            </div>

            <button id="staffMfaVerifyButton" type="submit" class="mfa-primary-btn w-full px-4 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-70" disabled>
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
        const otpStatus = document.getElementById('staffMfaOtpStatus');
        const otpStatusText = document.getElementById('staffMfaOtpStatusText');
        const otpSpinner = document.getElementById('staffMfaOtpSpinner');
        const otpTick = document.getElementById('staffMfaOtpTick');
        const otpErrorIcon = document.getElementById('staffMfaOtpErrorIcon');
        let verifiedCode = '';
        let checkSequence = 0;

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

        function cleanCode() {
            return (otpInput ? otpInput.value : '').replace(/\D/g, '').slice(0, 6);
        }

        function updateVerifyButton() {
            if (!verifyButton) {
                return;
            }
            verifyButton.disabled = verifiedCode !== cleanCode();
        }

        function setOtpStatus(state, message) {
            if (!otpStatus || !otpStatusText || !otpSpinner || !otpTick || !otpErrorIcon) {
                return;
            }
            const visible = state !== 'idle';
            otpStatus.classList.toggle('hidden', !visible);
            otpStatus.classList.toggle('flex', visible);
            otpStatus.classList.remove(
                'border-slate-200', 'bg-slate-50', 'text-slate-600',
                'border-emerald-200', 'bg-emerald-50', 'text-emerald-700',
                'border-rose-200', 'bg-rose-50', 'text-rose-700'
            );
            otpSpinner.classList.toggle('hidden', state !== 'checking');
            otpTick.classList.toggle('hidden', state !== 'valid');
            otpErrorIcon.classList.toggle('hidden', state !== 'invalid');
            if (state === 'valid') {
                otpStatus.classList.add('border-emerald-200', 'bg-emerald-50', 'text-emerald-700');
                otpTick.classList.remove('otp-checkmark-pop');
                void otpTick.offsetWidth;
                otpTick.classList.add('otp-checkmark-pop');
            } else if (state === 'invalid') {
                otpStatus.classList.add('border-rose-200', 'bg-rose-50', 'text-rose-700');
            } else {
                otpStatus.classList.add('border-slate-200', 'bg-slate-50', 'text-slate-600');
            }
            otpStatusText.textContent = message || '';
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

        async function checkOtpCode(code) {
            const sequence = ++checkSequence;
            verifiedCode = '';
            updateVerifyButton();
            setOtpStatus('checking', 'Verifying code...');
            try {
                const result = await postForm('/login/mfa/check', { otpCode: code });
                if (sequence !== checkSequence || code !== cleanCode()) {
                    return;
                }
                if (!result.response.ok || !result.payload.valid) {
                    setOtpStatus('invalid', result.payload.message || 'We could not verify that code.');
                    window.SaccosOtp?.focusBoxes(otpInput);
                    return;
                }
                if (result.payload.redirectUrl) {
                    window.location.href = result.payload.redirectUrl;
                    return;
                }
                verifiedCode = code;
                setOtpStatus('valid', result.payload.message || 'OTP code verified.');
            } catch (error) {
                if (sequence === checkSequence) {
                    setOtpStatus('invalid', 'We could not verify that code right now. Try again.');
                }
            } finally {
                if (sequence === checkSequence) {
                    updateVerifyButton();
                }
            }
        }

        function syncOtpVerification() {
            setMessage(errorBox, '');
            setMessage(successBox, '');
            const code = cleanCode();
            if (code !== verifiedCode) {
                verifiedCode = '';
            }
            if (!code) {
                checkSequence += 1;
                setOtpStatus('idle', '');
                updateVerifyButton();
                return;
            }
            if (code.length < 6) {
                checkSequence += 1;
                setOtpStatus('pending', 'Enter all 6 digits to verify the code.');
                updateVerifyButton();
                return;
            }
            checkOtpCode(code);
        }

        otpInput?.addEventListener('input', syncOtpVerification);
        updateVerifyButton();

        verifyForm?.addEventListener('submit', async function (event) {
            event.preventDefault();
            setMessage(errorBox, '');
            setMessage(successBox, '');
            const code = cleanCode();
            if (!/^\d{6}$/.test(code)) {
                setOtpStatus('invalid', 'Enter the 6-digit verification code.');
                window.SaccosOtp?.focusBoxes(otpInput);
                return;
            }
            if (verifiedCode !== code) {
                await checkOtpCode(code);
                if (verifiedCode !== code) {
                    return;
                }
            }

            setBusy(verifyButton, true, 'Verifying...', 'Verify and continue');
            try {
                const result = await postForm('/login/mfa/verify', { otpCode: code });
                if (!result.response.ok || !result.payload.valid) {
                    verifiedCode = '';
                    setOtpStatus('invalid', result.payload.message || 'We could not verify that code.');
                    updateVerifyButton();
                    setMessage(errorBox, result.payload.message || 'We could not verify that code.');
                    return;
                }
                window.location.href = result.payload.redirectUrl || '/';
            } catch (error) {
                setMessage(errorBox, 'We could not verify that code right now. Try again.');
            } finally {
                setBusy(verifyButton, false, 'Verifying...', 'Verify and continue');
                updateVerifyButton();
            }
        });

        resendButton?.addEventListener('click', async function () {
            setMessage(errorBox, '');
            setMessage(successBox, '');
            verifiedCode = '';
            checkSequence += 1;
            updateVerifyButton();
            setOtpStatus('idle', '');
            setBusy(resendButton, true, 'Sending...', 'Resend code');
            try {
                const result = await postForm('/login/mfa/resend');
                if (!result.response.ok || !result.payload.valid) {
                    setMessage(errorBox, result.payload.message || 'We could not send a new code.');
                    return;
                }
                if (result.payload.redirectUrl) {
                    window.location.href = result.payload.redirectUrl;
                    return;
                }
                if (otpInput) {
                    otpInput.value = '';
                    otpInput.dispatchEvent(new Event('input', { bubbles: true }));
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
