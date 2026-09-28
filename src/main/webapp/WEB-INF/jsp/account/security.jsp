<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Settings / Security</p>
    <h1 class="erp-page-title">OTP Security</h1>
</div>

<section class="erp-panel aws-settings-panel" aria-labelledby="otpSecurityTitle">
    <div class="aws-settings-header">
        <h2 id="otpSecurityTitle" class="aws-settings-title">When to use OTP</h2>
        <c:choose>
            <c:when test="${otpSelectionPolicy eq 'BOTH'}">
                <p class="aws-settings-description">Your institution requires OTP for both login and approvals.</p>
            </c:when>
            <c:when test="${otpSelectionPolicy eq 'AT_LEAST_ONE'}">
                <p class="aws-settings-description">Your institution requires login OTP, approval OTP, or both.</p>
            </c:when>
            <c:otherwise>
                <p class="aws-settings-description">Choose either option, both options, or neither.</p>
            </c:otherwise>
        </c:choose>
    </div>
    <form action="/account/security/preferences" method="post" class="aws-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="grid gap-3 md:grid-cols-2">
            <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <c:if test="${otpSelectionPolicy eq 'BOTH'}">
                    <input name="loginOtpEnabled" type="hidden" value="true" />
                </c:if>
                <input name="loginOtpEnabled" type="checkbox" value="true" class="mt-1"
                       ${otpPreferences.loginOtpEnabled ? 'checked' : ''}
                       ${otpSelectionPolicy eq 'BOTH' ? 'disabled' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Login</span>
                    <span class="mt-1 block text-xs text-slate-500">Request an OTP after a successful password sign-in.</span>
                </span>
            </label>
            <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <c:if test="${otpSelectionPolicy eq 'BOTH'}">
                    <input name="approvalOtpEnabled" type="hidden" value="true" />
                </c:if>
                <input name="approvalOtpEnabled" type="checkbox" value="true" class="mt-1"
                       ${otpPreferences.approvalOtpEnabled ? 'checked' : ''}
                       ${otpSelectionPolicy eq 'BOTH' ? 'disabled' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Approvals</span>
                    <span class="mt-1 block text-xs text-slate-500">Request an OTP for workflow confirmations currently protected by OTP.</span>
                </span>
            </label>
        </div>

        <div class="mt-5 border-t border-slate-200 pt-5">
            <h3 class="aws-settings-title">Confirmation</h3>
            <p class="aws-settings-description">Your current password or a confirmation OTP is required when turning an enabled option off.</p>
            <div class="mt-3 grid gap-3 md:grid-cols-2">
                <label class="aws-settings-field" for="otpSecurityPassword">
                    Current password
                    <input id="otpSecurityPassword" name="currentPassword" type="password"
                           autocomplete="current-password" class="aws-control" />
                </label>
                <label class="aws-settings-field" for="otpSecurityCode">
                    Confirmation OTP
                    <input id="otpSecurityCode" name="otpCode" type="text" inputmode="numeric"
                           autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}"
                           class="aws-control" />
                </label>
            </div>
            <p id="otpSecurityMessage" class="mt-3 hidden border px-4 py-3 text-sm" role="status"></p>
        </div>

        <div class="aws-settings-footer">
            <button id="requestSecurityOtpButton" type="button" class="app-btn btn-neutral">Request OTP</button>
            <button type="submit" class="app-btn btn-primary">Save OTP Preferences</button>
        </div>
    </form>
</section>

<script>
    (() => {
        const requestButton = document.getElementById("requestSecurityOtpButton");
        const message = document.getElementById("otpSecurityMessage");
        if (!requestButton || !message) {
            return;
        }
        requestButton.addEventListener("click", async () => {
            requestButton.disabled = true;
            requestButton.textContent = "Requesting...";
            message.className = "mt-3 border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700";
            message.textContent = "Requesting confirmation code...";
            try {
                const body = new URLSearchParams();
                body.set("${_csrf.parameterName}", "${_csrf.token}");
                const response = await fetch("/account/security/request-otp", {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "X-Requested-With": "XMLHttpRequest"
                    },
                    body
                });
                const result = await response.json();
                message.className = response.ok
                    ? "mt-3 border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
                    : "mt-3 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800";
                message.textContent = result.message || "The confirmation code could not be requested.";
                if (response.ok) {
                    const remaining = Math.max(0, Number(result.resendAttemptsRemaining || 0));
                    requestButton.textContent = remaining > 0
                        ? "Resend OTP (" + remaining + " left)"
                        : "Resend limit reached";
                    requestButton.disabled = remaining <= 0;
                }
            } catch (error) {
                message.className = "mt-3 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800";
                message.textContent = "The confirmation code could not be requested. Try again.";
                requestButton.textContent = "Request OTP";
                requestButton.disabled = false;
            } finally {
                if (requestButton.textContent === "Requesting...") {
                    requestButton.textContent = "Request OTP";
                    requestButton.disabled = false;
                }
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
