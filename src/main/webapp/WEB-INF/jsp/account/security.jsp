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

        <div class="aws-settings-footer">
            <button type="submit" class="app-btn btn-launch">Save OTP Preferences</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
