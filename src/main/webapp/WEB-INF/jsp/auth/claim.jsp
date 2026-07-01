<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Activate Your SACCO Admin Account</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css' />" />
    <style>
        body {
            font-family: 'Manrope', ui-sans-serif, system-ui;
            background:
                radial-gradient(circle at top, rgba(47, 52, 141, 0.08), transparent 34%),
                linear-gradient(180deg, #f8fbfd 0%, #edf3f8 100%);
            min-height: 100vh;
        }
        .claim-card {
            box-shadow: 0 24px 48px -24px rgba(15, 23, 42, 0.25);
        }
    </style>
</head>
<body class="text-slate-800">
<main class="flex min-h-screen items-center justify-center px-4 py-10">
    <div class="claim-card w-full max-w-xl rounded-2xl border border-slate-200 bg-white p-8">
        <div class="mb-6">
            <p class="text-xs font-semibold uppercase tracking-wider text-[#2F348D]">SACCO Minor Admin Activation</p>
            <h1 class="mt-1 text-2xl font-bold text-slate-900" style="font-family:'Sora',ui-sans-serif,system-ui;">Activate your staff account</h1>
            <p class="mt-1 text-sm text-slate-500">Verify your staff account, then create your password.</p>
        </div>

        <c:if test="${not empty claimMessage}">
            <div class="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">${claimMessage}</div>
        </c:if>
        <c:if test="${not empty claimError}">
            <div class="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">${claimError}</div>
        </c:if>

        <c:choose>
            <c:when test="${empty claimToken}">
                <p class="text-sm text-slate-600">If you received an activation email, please click the link in that email again. If it has expired, ask your Super Admin to resend it.</p>
                <div class="mt-6">
                    <a href="/login" class="inline-flex items-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">Back to sign in</a>
                </div>
            </c:when>
            <c:otherwise>
                <div class="mb-6 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
                    <p><span class="font-semibold">${memberFullName}</span></p>
                    <p class="text-xs text-slate-500">SACCO ${memberSaccoId}<c:if test="${not empty memberStationId}"> &middot; Station ${memberStationId}</c:if></p>
                    <p class="mt-2 text-xs text-slate-500"><c:out value="${otpDeliveryText}" /></p>
                </div>

                <form action="/auth/claim/request-otp" method="post" class="mb-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="token" value="${claimToken}" />
                    <button type="submit" class="w-full rounded-lg bg-[#2F348D] px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-[#262a73]">Send verification code</button>
                </form>

                <form action="/auth/claim/verify" method="post" class="space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="token" value="${claimToken}" />

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        ${otpFieldLabel}
                        <input name="otpCode" type="text" inputmode="numeric" autocomplete="one-time-code" required
                               class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm tracking-widest text-slate-800"
                               placeholder="${otpFieldPlaceholder}" />
                    </label>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <spring:message code="register.member.signature" text="Signature" />
                        <input name="signatureText" type="text" maxlength="120" required
                               class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               placeholder="e.g. James M Juma" />
                    </label>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Password
                        <input name="password" type="password" autocomplete="new-password" minlength="8" required
                               class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               placeholder="At least 8 characters" />
                    </label>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Confirm password
                        <input name="confirmPassword" type="password" autocomplete="new-password" minlength="8" required
                               class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               placeholder="Re-enter password" />
                    </label>

                    <button type="submit" class="w-full rounded-lg bg-[#3F9C4B] px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-[#357f3f]">Activate account</button>
                </form>

                <p class="mt-6 text-xs text-slate-500">This activation link expires at <span class="font-semibold">${invitationExpiresAt}</span>. Ask your Super Admin to resend if it lapses.</p>
            </c:otherwise>
        </c:choose>
    </div>
</main>
</body>
</html>
