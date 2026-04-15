<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<style>
    @keyframes otp-pop {
        0% { transform: translateY(4px) scale(0.82); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
    }

    .otp-checkmark-pop {
        animation: otp-pop 180ms ease-out;
    }

    .guarantee-modal-subtitle-legacy {
        display: none;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Guarantee Requests</p>
    <h1 class="erp-page-title"><spring:message code="menu.grequests" /></h1>
</div>
<c:set var="hasGuarantorSignature" value="${not empty guarantorSavedSignatureText}" />
<div class="erp-table-wrap overflow-x-auto">
<table class="erp-table">
    <thead><tr><th>Loan Reference</th><th>Guarantee Name</th><th>Loan Type</th><th>Loan Amount</th><th>Status</th><th>Date</th><th>Actions</th></tr></thead>
    <tbody>
    <c:forEach items="${requests}" var="req">
        <c:set var="pendingRemoval" value="${guaranteePendingRemovalRequests[req.id]}" />
        <c:set var="canRequestRemoval" value="${req.status eq 'APPROVED' and guaranteeRemovalAllowed[req.id] and empty pendingRemoval}" />
        <tr>
            <td>${fn:substring(req.loanApplicationId, 0, 8)}</td>
            <td>${guaranteeNames[req.loanApplicationId]}</td>
            <td>
                <c:if test="${not empty guaranteeLoanTypes[req.loanApplicationId]}">
                    <spring:message code="loan.type.${guaranteeLoanTypes[req.loanApplicationId]}" />
                </c:if>
            </td>
            <td>${guaranteeLoanAmounts[req.loanApplicationId]}</td>
            <td>${req.status}</td>
            <td>
                <c:choose>
                    <c:when test="${not empty req.decidedAt}">${fn:replace(fn:substring(req.decidedAt, 0, 16), 'T', ' ')}</c:when>
                    <c:otherwise>${fn:replace(fn:substring(req.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                </c:choose>
            </td>
            <td>
                <c:if test="${req.status eq 'PENDING'}">
                    <div class="min-w-[240px] space-y-3">
                        <button type="button"
                                class="app-btn btn-approve"
                                data-guarantee-modal-open="approve-${req.id}">
                            Approve
                        </button>
                        <button type="button"
                                class="app-btn btn-reject"
                                data-guarantee-modal-open="reject-${req.id}">
                            Reject
                        </button>
                    </div>
                </c:if>
                <c:if test="${req.status ne 'PENDING'}">
                    <c:choose>
                        <c:when test="${canRequestRemoval}">
                            <button type="button"
                                    class="app-btn btn-neutral"
                                    data-guarantee-modal-open="undo-${req.id}">
                                Request Removal
                            </button>
                        </c:when>
                        <c:when test="${req.status eq 'APPROVED' and not empty pendingRemoval}">
                            <button type="button"
                                    class="app-btn btn-neutral action-button-disabled"
                                    disabled>
                                Removal Request Sent
                            </button>
                        </c:when>
                        <c:otherwise>
                            <span class="text-slate-400">-</span>
                        </c:otherwise>
                    </c:choose>
                </c:if>
            </td>
        </tr>
    </c:forEach>
    <c:if test="${empty requests}">
        <tr><td colspan="7" class="px-3 py-3 text-slate-500">No guarantee requests found.</td></tr>
    </c:if>
    </tbody>
</table>
</div>

<c:forEach items="${requests}" var="req">
    <c:if test="${req.status eq 'PENDING'}">
        <div class="app-modal-overlay hidden"
             data-guarantee-modal="approve-${req.id}">
            <div class="app-modal-panel">
                <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.25em] text-slate-500">Approve Guarantee</p>
                        <h2 class="mt-2 text-3xl font-semibold text-sacco-ink">${guaranteeNames[req.loanApplicationId]}</h2>
                        <p class="guarantee-modal-subtitle-legacy mt-2 text-sm text-slate-500">
                            Loan ${fn:substring(req.loanApplicationId, 0, 8)} •
                            <c:if test="${not empty guaranteeLoanTypes[req.loanApplicationId]}">
                                <spring:message code="loan.type.${guaranteeLoanTypes[req.loanApplicationId]}" />
                            </c:if>
                            • ${guaranteeLoanAmounts[req.loanApplicationId]}
                        </p>
                        <p class="mt-2 text-sm text-slate-500">
                            Loan ${fn:substring(req.loanApplicationId, 0, 8)}
                            <c:if test="${not empty guaranteeLoanTypes[req.loanApplicationId]}">
                                | <spring:message code="loan.type.${guaranteeLoanTypes[req.loanApplicationId]}" />
                            </c:if>
                            | ${guaranteeLoanAmounts[req.loanApplicationId]}
                        </p>
                    </div>
                    <button type="button"
                            class="app-modal-close"
                            data-guarantee-modal-close="approve-${req.id}"
                            aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>
                <form action="/app/guarantee-requests/${req.id}/approve" method="post" class="app-modal-body space-y-5">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="app-modal-section text-sm leading-7 text-slate-700">
                        <p>
                            <strong>Guarantor Declaration:</strong>
                            I,
                            <strong>
                                <c:choose>
                                    <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                                    <c:otherwise>the guarantor</c:otherwise>
                                </c:choose>
                            </strong>,
                            accept responsibility for recoveries and penalties if the applicant defaults on this loan.
                        </p>
                    </div>
                    <label class="app-modal-section flex items-start gap-3 text-sm text-slate-700">
                        <input type="checkbox"
                               name="guarantorDeclarationAccepted"
                               value="true"
                               class="mt-1 h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue"
                               required />
                        <span>I confirm that I agree to the guarantor declaration above before approving this request.</span>
                    </label>
                    <div class="app-modal-section">
                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <div>
                                <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                                <p class="mt-2 text-sm text-slate-600">Request a one-time code to confirm that you are the guarantor approving this loan.</p>
                                <c:if test="${not hasGuarantorSignature}">
                                    <p class="mt-2 text-sm text-rose-600">Add your signature on your member account before requesting OTP.</p>
                                </c:if>
                            </div>
                            <button type="button"
                                    class="app-btn btn-primary otp-request-button guarantor-otp-request inline-flex items-center justify-center gap-2"
                                    ${hasGuarantorSignature ? '' : 'disabled'}
                                    data-guarantor-feedback="approve-feedback-${req.id}">
                                <span class="otp-button-spinner hidden"></span>
                                <span class="otp-button-label">Send OTP Code</span>
                            </button>
                        </div>
                        <div id="approve-feedback-${req.id}" data-auto-scroll-message="true" class="mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                        <div class="mt-3">
                            <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                            <input type="text"
                                   name="guarantorSignatureOtpCode"
                                   inputmode="numeric"
                                   maxlength="6"
                                   autocomplete="one-time-code"
                                   class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                                   placeholder="123456"
                                   required />
                            <p class="mt-2 text-sm text-slate-500">Enter the 6-digit code sent to your email before confirming approval.</p>
                            <div class="guarantor-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                                <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                                <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                    <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                </svg>
                                <span data-otp-text>Checking code...</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex flex-wrap justify-end gap-3">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-guarantee-modal-close="approve-${req.id}">
                            Cancel
                        </button>
                        <button type="submit"
                                class="app-btn btn-approve"
                                ${hasGuarantorSignature ? '' : 'disabled'}>
                            Confirm Approval
                        </button>
                    </div>
                </form>
                </div>
            </div>
        </div>

        <div class="app-modal-overlay hidden"
             data-guarantee-modal="reject-${req.id}">
            <div class="app-modal-panel app-modal-panel--compact">
                <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.25em] text-slate-500">Reject Guarantee</p>
                        <h2 class="mt-2 text-2xl font-semibold text-sacco-ink">${guaranteeNames[req.loanApplicationId]}</h2>
                        <p class="mt-2 text-sm text-slate-500">This request will be marked as declined by guarantor.</p>
                    </div>
                    <button type="button"
                            class="app-modal-close"
                            data-guarantee-modal-close="reject-${req.id}"
                            aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>
                <form action="/app/guarantee-requests/${req.id}/reject" method="post" class="app-modal-body space-y-5">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="app-modal-section text-sm text-slate-700">
                        Rejecting this request will stop your approval for loan ${fn:substring(req.loanApplicationId, 0, 8)}.
                    </div>
                    <div class="flex flex-wrap justify-end gap-3">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-guarantee-modal-close="reject-${req.id}">
                            Cancel
                        </button>
                        <button type="submit" class="app-btn btn-reject">Confirm Rejection</button>
                    </div>
                </form>
                </div>
            </div>
        </div>
    </c:if>

    <c:if test="${req.status ne 'PENDING'}">
        <div class="app-modal-overlay hidden"
             data-guarantee-modal="undo-${req.id}">
            <div class="app-modal-panel app-modal-panel--compact">
                <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.25em] text-slate-500">Request Guarantor Removal</p>
                        <h2 class="mt-2 text-2xl font-semibold text-sacco-ink">${guaranteeNames[req.loanApplicationId]}</h2>
                        <p class="mt-2 text-sm text-slate-500">This asks the applicant to remove you from this loan instead of keeping you attached as a guarantor.</p>
                    </div>
                    <button type="button"
                            class="app-modal-close"
                            data-guarantee-modal-close="undo-${req.id}"
                            aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>
                <form action="/app/guarantee-requests/${req.id}/undo" method="post" class="app-modal-body space-y-5">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="app-modal-section text-sm text-slate-700">
                        Current decision: <strong>${req.status}</strong>. The applicant will review this request before your guarantor record is removed from the loan.
                    </div>
                    <div class="flex flex-wrap justify-end gap-3">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-guarantee-modal-close="undo-${req.id}">
                            Cancel
                        </button>
                        <button type="submit" class="app-btn btn-primary">Send Removal Request</button>
                    </div>
                </form>
                </div>
            </div>
        </div>
    </c:if>
</c:forEach>

<script>
    (() => {
        const body = document.body;
        const csrfToken = "${_csrf.token}";

        document.querySelectorAll("[data-guarantee-modal]").forEach((modal) => {
            modal.style.position = "fixed";
            modal.style.inset = "0";
            modal.style.zIndex = "90";
            document.body.appendChild(modal);
        });

        function closeAllGuaranteeModals() {
            document.querySelectorAll("[data-guarantee-modal]").forEach((modal) => {
                modal.classList.add("hidden");
                modal.classList.remove("is-open");
            });
            body.classList.remove("overflow-hidden");
        }

        document.querySelectorAll("[data-guarantee-modal-open]").forEach((button) => {
            button.addEventListener("click", () => {
                const key = button.getAttribute("data-guarantee-modal-open");
                closeAllGuaranteeModals();
                const modal = document.querySelector('[data-guarantee-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove("hidden");
                    modal.classList.add("is-open");
                    body.classList.add("overflow-hidden");
                }
            });
        });

        document.querySelectorAll("[data-guarantee-modal-close]").forEach((button) => {
            button.addEventListener("click", closeAllGuaranteeModals);
        });

        document.querySelectorAll("[data-guarantee-modal]").forEach((modal) => {
            modal.addEventListener("click", (event) => {
                if (event.target === modal) {
                    closeAllGuaranteeModals();
                }
            });
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeAllGuaranteeModals();
            }
        });

        function showOtpFeedback(element, type, text) {
            if (!element) {
                return;
            }
            element.textContent = text;
            element.classList.remove(
                "hidden",
                "border-emerald-200",
                "bg-emerald-50",
                "text-emerald-700",
                "border-rose-200",
                "bg-rose-50",
                "text-rose-700"
            );
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
            button.disabled = sent || loading || ${hasGuarantorSignature ? 'false' : 'true'};
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
                    render();
                }
            };
        }

        document.querySelectorAll(".guarantor-otp-request").forEach((button) => {
            button.addEventListener("click", async () => {
                const form = button.closest("form");
                const feedbackId = button.getAttribute("data-guarantor-feedback");
                const feedback = feedbackId ? document.getElementById(feedbackId) : (form ? form.querySelector("[id^='approve-feedback-']") : null);
                const otpInput = form ? form.querySelector("input[name='guarantorSignatureOtpCode']") : null;
                const statusBox = form ? form.querySelector(".guarantor-otp-live-status") : null;
                const proceedButton = form ? form.querySelector("button[type='submit'].btn-approve") : null;
                const otpUi = bindOtpLiveStatus(otpInput, statusBox, proceedButton);
                otpUi.reset();
                setOtpButtonState(button, "loading", "Send OTP Code", "Sending...", "OTP Sent");
                try {
                    const response = await fetch("/app/guarantee-requests/request-signature-otp", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "Accept": "application/json"
                        },
                        body: new URLSearchParams({
                            "${_csrf.parameterName}": csrfToken
                        })
                    });
                    const payload = await response.json();
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "Unable to send the OTP code right now.");
                    }
                    showOtpFeedback(feedback, "success", payload.message || "We sent a guarantor OTP code to your email.");
                    setOtpButtonState(button, "sent", "Send OTP Code", "Sending...", "OTP Sent");
                    otpUi.markRequested();
                    if (otpInput) {
                        otpInput.focus();
                    }
                } catch (error) {
                    showOtpFeedback(feedback, "error", error.message || "Unable to send the OTP code right now.");
                    setOtpButtonState(button, "idle", "Send OTP Code", "Sending...", "OTP Sent");
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
