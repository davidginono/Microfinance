<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/loan-detail-styles.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/confirm-modal.jspf" %>
<style>
    @keyframes otp-pop {
        0% { transform: translateY(4px) scale(0.82); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
    }

    .otp-checkmark-pop {
        animation: otp-pop 180ms ease-out;
    }
</style>

<c:set var="hasBoardSavedSignature" value="${not empty boardSavedSignatureText}" />

<div class="erp-page-header">
    <p class="erp-breadcrumb">Board Panel / Review Detail</p>
    <h1 class="erp-page-title">Board Review Detail</h1>
</div>

<div class="loan-view-hero-summary">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6 lg:px-8">
        <div class="min-w-0">
            <div class="loan-hero-primary-grid">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label">Loan Application ID</div>
                    <div class="loan-hero-inline-value loan-hero-inline-value--id">${loanIdShort}</div>
                </div>
                <c:if test="${not empty disbursedLoanId}">
                    <div class="loan-hero-inline-fact">
                        <div class="loan-hero-inline-label">Loan ID</div>
                        <div class="loan-hero-inline-value loan-hero-inline-value--id">${disbursedLoanId}</div>
                    </div>
                </c:if>
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label">Status</div>
                    <div class="loan-hero-inline-value">
                        <span class="inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${boardStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label">Applicant Name</div>
                    <div class="loan-hero-fact-value">${applicant.fullName}</div>
                </div>
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label">Loan Type</div>
                    <div class="loan-hero-fact-value"><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></div>
                </div>
            </div>
        </div>
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Loan Amount</div>
                <div class="loan-stat-value">${app.amount}</div>
            </div>
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Tenor</div>
                <div class="loan-stat-value">${app.tenorMonths} Month<c:if test="${app.tenorMonths ne 1}">s</c:if></div>
            </div>
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Current Savings</div>
                <div class="loan-stat-value">${applicantExternalAccountStatus.savingsLabel}</div>
            </div>
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Current Shares</div>
                <div class="loan-stat-value">${applicantExternalAccountStatus.sharesLabel}</div>
            </div>
        </div>

        <div class="mt-6 loan-simple-progress">
            <h3 class="text-lg font-semibold text-slate-900">Progress</h3>
            <div class="loan-simple-progress-list">
                <c:forEach items="${loanProgressItems}" var="item">
                    <div class="loan-simple-progress-item${item.active ? ' is-active' : ' is-pending'}${item.current ? ' is-current' : ''}">
                        <span class="loan-simple-progress-dot"></span>
                        <span>${item.label}</span>
                    </div>
                </c:forEach>
            </div>
        </div>

    </div>
</div>

<div class="mt-4 space-y-4">
    <c:if test="${not applicantExternalAccountStatus.available}">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            ${applicantExternalAccountStatus.statusMessage}
        </div>
    </c:if>
    <c:if test="${not empty managerReason}">
        <div class="rounded-lg border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong>Manager Reason:</strong> ${managerReason}
        </div>
    </c:if>
    <div class="loan-view-summary-card px-5 py-5">
        <div>
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">Applicant Details</p>
            <p class="mt-1 text-sm text-slate-600">Key applicant information for board review before making a decision.</p>
        </div>
        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div class="applicant-info-card">
                <div class="applicant-info-label">Full Name</div>
                <div class="applicant-info-value">${applicant.fullName}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Member Number</div>
                <div class="applicant-info-value">${applicant.memberNo}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">My Decision</div>
                <div class="applicant-info-value">${myReview.decision}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Quick Context</div>
                <div class="applicant-info-value">
                    ${fn:length(attachments)} attachment(s), ${fn:length(guarantorRequests)} guarantor request(s)
                </div>
            </div>
        </div>
    </div>
</div>

<div class="loan-view-summary-card px-5 py-5">
    <div>
        <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">Committee Assessors</p>
        <p class="mt-2 text-base text-slate-600">All board members assigned to this application and the decisions recorded so far.</p>
    </div>
    <div class="mt-5 erp-table-wrap overflow-x-auto">
        <table class="erp-table">
            <thead>
            <tr>
                <th class="px-3 py-2 text-left">Assessor</th>
                <th class="px-3 py-2 text-left">Member No</th>
                <th class="px-3 py-2 text-left">Decision</th>
                <th class="px-3 py-2 text-left">Comment</th>
                <th class="px-3 py-2 text-left">Submitted</th>
                <th class="px-3 py-2 text-left">Signature</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${boardAssessors}" var="assessor">
                <tr>
                    <td class="px-3 py-2">
                        <div class="font-medium text-slate-900">${assessor.name}</div>
                        <c:if test="${assessor.isMine}">
                            <div class="mt-1 text-xs uppercase tracking-wide text-sacco-blue">Your review</div>
                        </c:if>
                    </td>
                    <td class="px-3 py-2 text-slate-700">${assessor.memberNo}</td>
                    <td class="px-3 py-2 text-slate-700">${assessor.decision}</td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${not empty assessor.comment}">${assessor.comment}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${not empty assessor.decidedAt}">${fn:replace(fn:substring(assessor.decidedAt, 0, 16), 'T', ' ')}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${assessor.decision eq 'APPROVED' and not empty assessor.signatureText}">
                                <div class="text-xl text-slate-900" style="font-family:'Brush Script MT','Segoe Script','Lucida Handwriting',cursive;">${assessor.signatureText}</div>
                                <c:if test="${not empty assessor.signatureVerifiedAt}">
                                    <div class="mt-1 text-xs text-slate-500">${fn:replace(fn:substring(assessor.signatureVerifiedAt, 0, 16), 'T', ' ')}</div>
                                </c:if>
                            </c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Loan Details</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">Section</th>
            <th class="px-3 py-2 text-left">Value</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${financialFields}" var="entry">
            <tr>
                <td class="px-3 py-2 font-medium text-slate-700">${entry.key}</td>
                <td class="px-3 py-2">${entry.value}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty financialFields}">
            <tr><td colspan="2" class="px-3 py-3 text-slate-500">No official financial details loaded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Attachments</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">File</th>
            <th class="px-3 py-2 text-left">Size</th>
            <th class="px-3 py-2 text-left">Uploaded</th>
            <th class="px-3 py-2 text-left"></th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${attachments}" var="file">
            <tr>
                <td class="px-3 py-2">${file.originalName}</td>
                <td class="px-3 py-2">${file.sizeLabel}</td>
                <td class="px-3 py-2">${fn:replace(fn:substring(file.uploadedAt, 0, 16), 'T', ' ')}</td>
                <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary">Download</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty attachments}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500">No attachments uploaded.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Guarantor Requests</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">Guarantor</th>
            <th class="px-3 py-2 text-left">Status</th>
            <th class="px-3 py-2 text-left">Date</th>
            <th class="px-3 py-2 text-left">Financial Status</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${guarantorRequests}" var="req">
            <tr>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">${guarantorNames[req.guarantorMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(req.guarantorMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">${req.status}</td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty req.decidedAt}">${fn:replace(fn:substring(req.decidedAt, 0, 16), 'T', ' ')}</c:when>
                        <c:otherwise>${fn:replace(fn:substring(req.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <button type="button"
                            class="app-btn btn-neutral guarantor-financial-trigger"
                            data-url="/board/loan-applications/${app.id}/guarantors/${req.guarantorMemberId}/financial-status">
                        <span class="guarantor-financial-spinner hidden" data-financial-spinner></span>
                        <span data-financial-label>Load Status</span>
                    </button>
                    <div class="guarantor-financial-result hidden">
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label">Savings</span>
                            <span class="guarantor-financial-result-value" data-financial-savings>-</span>
                        </div>
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label">Shares</span>
                            <span class="guarantor-financial-result-value" data-financial-shares>-</span>
                        </div>
                        <div class="guarantor-financial-result-note" data-financial-note></div>
                    </div>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty guarantorRequests}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500">No guarantors selected yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<c:choose>
    <c:when test="${myReview.decision eq 'PENDING' and app.status eq 'AWAITING_BOARD'}">
        <form action="/board/loan-applications/${app.id}/decision" method="post" class="loan-view-summary-card mt-4 space-y-4 px-5 py-5" data-board-decision-form="true">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div>
                <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Board Decision</p>
                <p class="mt-1 text-sm text-slate-600">Add your comment, then approve or reject this review.</p>
            </div>
            <div>
                <label class="mb-2 block text-sm font-medium text-slate-700">Comment</label>
                <textarea class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" name="comment"></textarea>
            </div>
            <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                        <p class="mt-2 text-sm text-slate-600">Request a one-time code to confirm this board approval.</p>
                    </div>
                    <button type="button"
                            class="app-btn btn-primary otp-request-button board-otp-request inline-flex items-center justify-center gap-2"
                            data-request-url="/board/loan-applications/${app.id}/request-signature-otp"
                            ${hasBoardSavedSignature ? '' : 'disabled'}>
                        <span class="otp-button-spinner hidden"></span>
                        <span class="otp-button-label">Send OTP Code</span>
                    </button>
                </div>
                <div class="board-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                <div class="mt-3">
                    <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                    <input type="text"
                           name="boardSignatureOtpCode"
                           inputmode="numeric"
                           maxlength="6"
                           autocomplete="one-time-code"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                           placeholder="123456"
                           ${hasBoardSavedSignature ? 'required' : ''} />
                    <p class="mt-2 text-sm text-slate-500">Enter the 6-digit code sent to your email before confirming approval.</p>
                    <div class="board-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                        <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                        <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                        </svg>
                        <span data-otp-text>Checking code...</span>
                    </div>
                </div>
            </div>
            <div class="grid gap-3 sm:grid-cols-2 loan-final-action-row">
                <button type="submit" name="decision" value="APPROVED" class="app-btn btn-primary board-approve-submit" ${hasBoardSavedSignature ? '' : 'disabled'}>Approve Review</button>
                <button type="submit" name="decision" value="REJECTED" class="app-btn btn-reject board-reject-submit" formnovalidate>Reject Review</button>
            </div>
            <div class="border-t border-slate-200 pt-4 loan-final-action-row">
                <a href="/documents/loan-applications/${app.id}/print" class="app-btn btn-primary w-full justify-center">Print Loan Application</a>
            </div>
        </form>
    </c:when>
    <c:otherwise>
        <div class="loan-view-summary-card mt-4 px-5 py-5">
            <div class="loan-view-action-cluster">
                <a href="/documents/loan-applications/${app.id}/print" class="app-btn btn-primary">Print Loan Application</a>
            </div>
        </div>
    </c:otherwise>
</c:choose>

<script>
    (() => {
        const csrfToken = "${_csrf.token}";

        function showOtpFeedback(element, type, text) {
            if (!element) {
                return;
            }
            element.textContent = text;
            element.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
            if (type === "success") {
                element.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
            } else {
                element.classList.add("border-rose-200", "bg-rose-50", "text-rose-700");
            }
        }

        function setOtpButtonState(button, state, idleLabel, loadingLabel, sentLabel) {
            if (!button) {
                return;
            }
            const spinner = button.querySelector(".otp-button-spinner");
            const label = button.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            if (spinner) {
                spinner.classList.toggle("hidden", !loading);
            }
            if (label) {
                label.textContent = loading ? loadingLabel : (sent ? sentLabel : idleLabel);
            }
            button.disabled = loading || sent || ${hasBoardSavedSignature ? 'false' : 'true'};
            button.classList.toggle("is-loading", loading);
            button.classList.toggle("is-sent", sent);
        }

        function bindOtpLiveStatus(input, statusBox, proceedButton) {
            if (!input || !statusBox) {
                return {
                    markRequested: function () {},
                    reset: function () {}
                };
            }
            const spinner = statusBox.querySelector("[data-otp-spinner]");
            const tick = statusBox.querySelector("[data-otp-tick]");
            const text = statusBox.querySelector("[data-otp-text]");
            let otpRequested = Boolean((input.value || "").trim());
            let lastReady = false;
            let verificationComplete = false;
            let verificationTimer = null;

            function clearVerification() {
                if (verificationTimer) {
                    window.clearTimeout(verificationTimer);
                    verificationTimer = null;
                }
            }

            function render() {
                input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                const ready = /^\d{6}$/.test(input.value);
                if (!otpRequested || !input.value) {
                    clearVerification();
                    verificationComplete = false;
                    lastReady = false;
                    statusBox.classList.add("hidden");
                    statusBox.classList.remove("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                    spinner.classList.remove("hidden");
                    tick.classList.add("hidden");
                    text.textContent = "Checking code...";
                    return;
                }
                statusBox.classList.remove("hidden");
                statusBox.classList.add("flex");
                if (ready) {
                    if (!verificationComplete) {
                        if (!verificationTimer) {
                            verificationTimer = window.setTimeout(function () {
                                verificationTimer = null;
                                verificationComplete = true;
                                render();
                            }, 240);
                        }
                        statusBox.classList.remove("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                        statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                        spinner.classList.remove("hidden");
                        tick.classList.add("hidden");
                        text.textContent = "Verifying code...";
                        return;
                    }
                    if (!lastReady && proceedButton && !proceedButton.disabled) {
                        try {
                            proceedButton.focus({ preventScroll: true });
                        } catch (ignored) {
                            proceedButton.focus();
                        }
                    }
                    statusBox.classList.remove("border-slate-200", "bg-white", "text-slate-600");
                    statusBox.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    spinner.classList.add("hidden");
                    tick.classList.remove("hidden");
                    tick.classList.remove("otp-checkmark-pop");
                    void tick.offsetWidth;
                    tick.classList.add("otp-checkmark-pop");
                    text.textContent = "Verified";
                } else {
                    clearVerification();
                    verificationComplete = false;
                    statusBox.classList.remove("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                    spinner.classList.remove("hidden");
                    tick.classList.add("hidden");
                    text.textContent = "Checking code...";
                }
                lastReady = ready;
            }

            input.addEventListener("input", render);
            render();
            return {
                markRequested: function () {
                    otpRequested = true;
                    verificationComplete = false;
                    render();
                },
                reset: function () {
                    otpRequested = false;
                    clearVerification();
                    verificationComplete = false;
                    input.value = "";
                    render();
                }
            };
        }

        document.querySelectorAll(".board-otp-request").forEach((button) => {
            button.addEventListener("click", async () => {
                const form = button.closest("form");
                const input = form ? form.querySelector("input[name='boardSignatureOtpCode']") : null;
                const statusBox = form ? form.querySelector(".board-otp-live-status") : null;
                const feedback = form ? form.querySelector(".board-otp-feedback") : null;
                const proceedButton = form ? form.querySelector("button[name='decision'][value='APPROVED']") : null;
                const otpUi = bindOtpLiveStatus(input, statusBox, proceedButton);
                const requestUrl = button.getAttribute("data-request-url");

                if (!requestUrl) {
                    showOtpFeedback(feedback, "error", "Board OTP request is not configured.");
                    return;
                }

                otpUi.reset();
                setOtpButtonState(button, "loading", "Send OTP Code", "Sending...", "OTP Sent");
                try {
                    const response = await fetch(requestUrl, {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                            "X-CSRF-TOKEN": csrfToken
                        },
                        body: "_csrf=" + encodeURIComponent(csrfToken)
                    });
                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "Unable to send the OTP code right now.");
                    }
                    showOtpFeedback(feedback, "success", payload.message || "We sent a board approval code to your email.");
                    setOtpButtonState(button, "sent", "Send OTP Code", "Sending...", "OTP Sent");
                    otpUi.markRequested();
                    if (input) {
                        input.focus();
                    }
                } catch (error) {
                    showOtpFeedback(feedback, "error", error.message || "Unable to send the OTP code right now.");
                    setOtpButtonState(button, "idle", "Send OTP Code", "Sending...", "OTP Sent");
                }
            });
        });

        document.querySelectorAll("form[data-board-decision-form='true']").forEach((form) => {
            const approveButton = form.querySelector(".board-approve-submit");
            const rejectButton = form.querySelector(".board-reject-submit");

            function clearRejectConfirmation() {
                delete form.dataset.confirmEyebrow;
                delete form.dataset.confirmTitle;
                delete form.dataset.confirmMessage;
                delete form.dataset.confirmProceed;
            }

            approveButton?.addEventListener("click", clearRejectConfirmation);

            rejectButton?.addEventListener("click", () => {
                form.dataset.confirmEyebrow = "Confirm Rejection";
                form.dataset.confirmTitle = "Reject Board Review";
                form.dataset.confirmMessage = "Reject this loan review? This board decision will be recorded immediately.";
                form.dataset.confirmProceed = "Reject Review";
            });

            form.addEventListener("submit", (event) => {
                if (event.submitter && event.submitter.classList.contains("board-approve-submit")) {
                    clearRejectConfirmation();
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/guarantor-financial-fetch.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
