<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Activate Your Institution Admin Account</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png?v=20260826-clean' />" />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-400.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-700.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/open-sans.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/tailwind.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-components.css?v=20260809-loan-detail-action-v33' />" />
    <%@ include file="../fragments/otp-ui-styles.jspf" %>
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20260812-navbar-v6' />" />
</head>
<body class="aws-auth-shell text-slate-800">
<main class="flex min-h-screen items-center justify-center px-4 py-10">
    <div class="claim-card w-full max-w-xl border border-slate-200 bg-white p-8">
        <div class="mb-6">
            <p class="text-xs font-semibold uppercase tracking-wider text-[#2F348D]">Institution Admin Activation</p>
            <h1 class="mt-1 text-2xl font-bold text-slate-900">Activate your staff account</h1>
            <p class="mt-1 text-sm text-slate-500">Verify your staff account, then create your password.</p>
        </div>

        <c:if test="${not empty claimMessage}">
            <div class="mb-4 border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">${claimMessage}</div>
        </c:if>
        <c:if test="${not empty claimError}">
            <div class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">${claimError}</div>
        </c:if>

        <c:choose>
            <c:when test="${empty claimToken}">
                <p class="text-sm text-slate-600">If you received an activation email, please click the link in that email again. If it has expired, ask your Super Admin to resend it.</p>
                <div class="mt-6">
                    <a href="/login" class="inline-flex items-center border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">Back to sign in</a>
                </div>
            </c:when>
            <c:otherwise>
                <div class="mb-6 border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
                    <p><span class="font-semibold">${memberFullName}</span></p>
                    <c:if test="${not empty staffNo}">
                        <p class="mt-1 text-xs text-slate-500">Staff Number ${staffNo}</p>
                    </c:if>
                    <p class="text-xs text-slate-500">Institution ${memberSaccoId}<c:if test="${not empty memberStationId}"> &middot; Branch ${memberStationId}</c:if></p>
                    <p class="mt-2 text-xs text-slate-500"><c:out value="${otpDeliveryText}" /></p>
                </div>

                <form action="/auth/claim/request-otp" method="post" class="mb-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="token" value="${claimToken}" />
                    <button type="submit" class="w-full bg-[#2F348D] px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-[#262a73]">Send verification code</button>
                </form>

                <form action="/auth/claim/verify" method="post" class="space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="token" value="${claimToken}" />

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        ${otpFieldLabel}
                        <input name="otpCode" type="text" inputmode="numeric" autocomplete="one-time-code" required
                               maxlength="6"
                               data-otp-hidden="true"
                               data-otp-label="${otpFieldLabel}"
                               class="sr-only"
                               placeholder="${otpFieldPlaceholder}" />
                    </label>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Password
                        <input name="password" type="password" autocomplete="new-password" minlength="8" required
                               class="mt-1 w-full border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               placeholder="At least 8 characters" />
                    </label>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Confirm password
                        <input name="confirmPassword" type="password" autocomplete="new-password" minlength="8" required
                               class="mt-1 w-full border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               placeholder="Re-enter password" />
                    </label>

                    <button type="submit" class="w-full bg-[#3F9C4B] px-4 py-2 text-sm font-semibold text-white shadow transition hover:bg-[#357f3f]">Activate account</button>
                </form>

                <p class="mt-6 text-xs text-slate-500">This activation link expires at <span class="font-semibold">${invitationExpiresAt}</span>. Ask your Super Admin to resend if it lapses.</p>
            </c:otherwise>
        </c:choose>
    </div>
</main>
</body>
</html>
