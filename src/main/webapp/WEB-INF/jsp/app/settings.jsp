<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="member.settings.breadcrumb" /></p>
    <c:choose>
        <c:when test="${settingsSection eq 'payment-details'}">
            <h1 class="erp-page-title"><spring:message code="member.settings.payment.title" /></h1>
        </c:when>
        <c:otherwise>
            <h1 class="erp-page-title"><spring:message code="member.settings.title" /></h1>
        </c:otherwise>
    </c:choose>
</div>

<c:if test="${settingsSection eq 'language'}">
<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker"><spring:message code="member.settings.language.eyebrow" /></p>
        <h2 class="aws-settings-title"><spring:message code="member.settings.language.title" /></h2>
    </div>
    <form action="/app/settings/language" method="post" class="aws-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="aws-settings-control-row">
            <label class="aws-settings-field">
                <spring:message code="member.settings.language.label" />
                <select name="language" class="aws-control">
                    <option value="en" ${memberSettingsLanguage eq 'en' ? 'selected' : ''}><spring:message code="member.settings.language.english" /></option>
                    <option value="sw" ${memberSettingsLanguage eq 'sw' ? 'selected' : ''}><spring:message code="member.settings.language.swahili" /></option>
                </select>
            </label>
            <p class="aws-settings-help">
                <spring:message code="member.settings.language.help" />
            </p>
        </div>
        <div class="aws-settings-footer">
            <button type="submit" class="app-btn btn-primary"><spring:message code="member.settings.language.save" /></button>
        </div>
    </form>
</section>
</c:if>

<c:if test="${settingsSection eq 'payment-details'}">
<section id="payment-details" class="erp-panel aws-settings-panel scroll-mt-24 overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker"><spring:message code="member.settings.payment.eyebrow" text="Loan Disbursement" /></p>
        <h2 class="aws-settings-title"><spring:message code="member.settings.payment.title" text="Financial Details for Disbursement Deposit" /></h2>
        <p class="aws-settings-description"><spring:message code="member.settings.payment.otpHelp" text="Request and enter an OTP before saving any change." /></p>
    </div>
    <form action="/app/settings/payment-details" method="post" class="aws-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="aws-settings-grid">
            <label class="aws-settings-field">
                <spring:message code="member.settings.payment.type" text="Payment Method" />
                <select name="destinationType" class="aws-control" required>
                    <option value="BANK_ACCOUNT" ${paymentDestinationType eq 'BANK_ACCOUNT' ? 'selected' : ''}><spring:message code="paymentDestination.BANK_ACCOUNT" text="Bank Account" /></option>
                    <option value="MOBILE_MONEY" ${paymentDestinationType eq 'MOBILE_MONEY' ? 'selected' : ''}><spring:message code="paymentDestination.MOBILE_MONEY" text="Mobile Money" /></option>
                    <option value="OTHER" ${paymentDestinationType eq 'OTHER' ? 'selected' : ''}><spring:message code="paymentDestination.OTHER" text="Other Payment Method" /></option>
                </select>
            </label>
            <label class="aws-settings-field">
                <spring:message code="member.settings.payment.provider" text="Bank or Provider" />
                <input name="provider" type="text" maxlength="120" value="${fn:escapeXml(paymentDetails.provider)}"
                       class="aws-control"
                       placeholder="<spring:message code='member.settings.payment.provider.placeholder' text='Example: CRDB Bank or M-Pesa' />" required />
            </label>
            <label class="aws-settings-field">
                <spring:message code="member.settings.payment.holder" text="Account Holder Name" />
                <input name="accountHolderName" type="text" maxlength="160" value="${fn:escapeXml(paymentDetails.accountHolderName)}"
                       class="aws-control"
                       required />
            </label>
            <label class="aws-settings-field">
                <spring:message code="member.settings.payment.identifier" text="Account or Payment Number" />
                <input name="accountIdentifier" type="text" maxlength="120" value="${fn:escapeXml(paymentDetails.accountIdentifier)}"
                       class="aws-control"
                       placeholder="Account number or mobile-money number" required />
            </label>
            <label class="aws-settings-field">
                <spring:message code="member.settings.payment.otp" text="Confirmation OTP" />
                <input id="paymentDetailsOtpCode" name="otpCode" type="text" inputmode="numeric" autocomplete="one-time-code"
                       maxlength="6" pattern="[0-9]{6}"
                       class="aws-control"
                       placeholder="Enter the 6-digit code" required />
            </label>
        </div>
        <p id="paymentDetailsOtpMessage" class="mt-4 hidden rounded-md border px-4 py-3 text-sm" role="status"></p>
        <div class="aws-settings-footer">
            <button id="requestPaymentDetailsOtpButton" type="button" class="app-btn btn-neutral">
                <spring:message code="member.settings.payment.requestOtp" text="Request OTP" />
            </button>
            <button type="submit" class="app-btn btn-primary"><spring:message code="member.settings.payment.save" text="Save Financial Details" /></button>
        </div>
    </form>
</section>
</c:if>

<c:if test="${settingsSection eq 'payment-details'}">
<script>
    (() => {
        const requestButton = document.getElementById("requestPaymentDetailsOtpButton");
        const message = document.getElementById("paymentDetailsOtpMessage");
        if (!requestButton || !message) {
            return;
        }
        requestButton.addEventListener("click", async () => {
            requestButton.disabled = true;
            requestButton.textContent = "Requesting...";
            message.className = "mt-4 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700";
            message.textContent = "Requesting confirmation code...";
            try {
                const body = new URLSearchParams();
                body.set("${_csrf.parameterName}", "${_csrf.token}");
                const response = await fetch("/app/settings/payment-details/request-otp", {
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
                    ? "mt-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
                    : "mt-4 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800";
                message.textContent = result.message || "The confirmation code could not be requested.";
                if (response.ok) {
                    const remaining = Number.isFinite(Number(result.resendAttemptsRemaining))
                        ? Math.max(0, Number(result.resendAttemptsRemaining))
                        : 3;
                    requestButton.textContent = remaining > 0
                        ? "Code not received? Resend (" + remaining + " left)"
                        : "Resend limit reached";
                    requestButton.disabled = remaining <= 0;
                }
            } catch (error) {
                message.className = "mt-4 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800";
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
</c:if>

<%@ include file="../fragments/footer.jspf" %>
