<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Apply for a Loan</p>
    <h1 class="erp-page-title"><spring:message code="products.title" /></h1>
    <p class="erp-page-subtitle">Choose the right SACCO loan product, then continue with the correct application flow.</p>
</div>

<section class="erp-form-wrap mb-4 space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
            <h5 class="erp-panel-title">Calculate Loan Eligibility Before Applying</h5>
        
        </div>
        <div class="flex flex-wrap items-center gap-3">
            <button id="productsCalculatorButton" type="button" class="app-btn btn-primary">Calculate Loan</button>
        </div>
    </div>
  
</section>

<div id="productsCalculatorModal"
     class="app-modal-overlay hidden">
    <div class="app-modal-panel app-modal-panel--wide">
        <div class="app-modal-scroll">
        <div class="app-modal-header">
            <div>
                <p class="text-sm font-semibold uppercase tracking-[0.25em] text-slate-500">Loan Eligibility</p>
                <h2 class="mt-2 text-3xl font-semibold text-sacco-ink">Calculate Loan Eligibility</h2>
                <p class="mt-2 text-sm text-slate-500">Estimate eligibility and repayment values before opening the loan form.</p>
            </div>
            <button id="productsCalculatorCloseButton" type="button" class="app-modal-close" aria-label="Close modal">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                </svg>
            </button>
        </div>

        <div class="app-modal-body space-y-5">
            <div class="grid gap-3 md:grid-cols-2">
                <div class="md:col-span-2">
                    <label class="mb-1 block text-sm font-medium text-slate-700">Choose Loan Product</label>
                    <select id="productsLoanType" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                        <c:forEach items="${products}" var="p">
                            <option value="${p.loanType}" data-max-months="${p.maxRepaymentMonths}">
                                <spring:message code="loan.type.${p.loanType}" />
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-slate-700">Loan Amount (TSh)</label>
                    <input id="productsLoanAmount" type="hidden" />
                    <input id="productsLoanAmountDisplay" type="text" inputmode="decimal" autocomplete="off"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="100,000.00" />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-slate-700">Total Months to Repay</label>
                    <input id="productsTenorMonths" type="number" min="1"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="12" />
                </div>
            </div>

            <div class="grid gap-3 md:grid-cols-2">
                <div class="app-modal-section">
                    <p class="font-semibold text-slate-800">Before You Apply</p>
                    <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                        <li>Pick a product, amount, and repayment period first.</li>
                        <li>The calculator checks your savings-based eligibility before you apply.</li>
                        <li>Repayment values shown here help you compare products before opening the form.</li>
                    </ul>
                </div>
                <div id="productsEligibilityCard" class="app-modal-section">
                    <p class="font-semibold text-slate-800">Eligibility Result</p>
                    <div class="mt-2 space-y-1 text-sm text-slate-600">
                        <p id="productsEligibilityMessage">Load the calculator to check whether the selected amount qualifies.</p>
                        <p><strong>Savings:</strong> <span id="productsSavingsLabel">-</span></p>
                        <p><strong>Eligibility Ratio:</strong> <span id="productsRatioLabel">-</span></p>
                        <p><strong>Maximum Allowed:</strong> <span id="productsMaxAllowedLabel">-</span></p>
                    </div>
                </div>
            </div>

            <div class="app-modal-section flex flex-wrap items-center justify-between gap-3">
                <div class="text-sm text-slate-500">Load the calculator once you have entered the amount and repayment period.</div>
                <button id="productsCalculatorLoadButton" type="button" class="app-btn btn-primary">Calculate Loan</button>
            </div>

            <div id="productsFinancialFeedback" data-auto-scroll-message="true" class="hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div id="productsFinancialLoading" class="hidden app-modal-section text-sm text-slate-600">
                <div class="flex items-center gap-3">
                    <span class="inline-flex h-3 w-3 animate-pulse rounded-full bg-sacco-blue"></span>
                    Loading loan calculator results...
                </div>
            </div>
            <div id="productsFinancialCard" class="hidden erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th>Section</th>
                        <th>Value</th>
                    </tr>
                    </thead>
                    <tbody id="productsFinancialBody"></tbody>
                </table>
            </div>
        </div>
        </div>
    </div>
</div>

<div class="mb-3 px-1">
    <p class="text-base font-medium text-slate-700">Choose a loan to apply.</p>
</div>

<c:if test="${not empty applicationLockApp}">
    <div class="erp-section mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-slate-700">
            One loan application is already on review:
            <strong>${fn:substring(applicationLockApp.id, 0, 8)}</strong>
            (<strong>${applicationLockStatusLabel}</strong>).
            Start another one after this loan is disbursed.
        </div>
        <a href="/app/loan-applications/${applicationLockApp.id}" class="app-btn btn-neutral">View Current Application</a>
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
            <tr>
                <th><spring:message code="loan.type" /></th>
                <th>Guarantors</th>
                <th>Interest</th>
                <th>Max Repayment Period</th>
                <th>Action</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach items="${products}" var="p">
            <tr>
                <td><spring:message code="loan.type.${p.loanType}" /></td>
                <td>${p.guarantorsRequired}</td>
                <td>
                    <c:choose>
                        <c:when test="${p.loanType eq 'LOAN_ADVANCE'}">0% at 1 month, 12% after</c:when>
                        <c:otherwise><fmt:formatNumber value="${p.interestRate * 100}" minFractionDigits="0" maxFractionDigits="2" />%</c:otherwise>
                    </c:choose>
                </td>
                <td>${p.maxRepaymentMonths} month(s)</td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicationLockApp}">
                            <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                Application On Review
                            </button>
                        </c:when>
                        <c:otherwise>
                            <a class="app-btn btn-primary" href="/app/loan-applications/new?loanType=${p.loanType}">
                                <spring:message code="products.apply" />
                            </a>
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<script>
    (function () {
        const openButton = document.getElementById("productsCalculatorButton");
        const closeButton = document.getElementById("productsCalculatorCloseButton");
        const loadButton = document.getElementById("productsCalculatorLoadButton");
        const modal = document.getElementById("productsCalculatorModal");
        const loanTypeInput = document.getElementById("productsLoanType");
        const amountInput = document.getElementById("productsLoanAmount");
        const amountDisplayInput = document.getElementById("productsLoanAmountDisplay");
        const tenorInput = document.getElementById("productsTenorMonths");
        const feedback = document.getElementById("productsFinancialFeedback");
        const loading = document.getElementById("productsFinancialLoading");
        const card = document.getElementById("productsFinancialCard");
        const body = document.getElementById("productsFinancialBody");
        const eligibilityMessage = document.getElementById("productsEligibilityMessage");
        const savingsLabel = document.getElementById("productsSavingsLabel");
        const ratioLabel = document.getElementById("productsRatioLabel");
        const maxAllowedLabel = document.getElementById("productsMaxAllowedLabel");
        const csrfToken = "${_csrf.token}";
        const csrfParam = "${_csrf.parameterName}";
        const bodyElement = document.body;

        if (modal) {
            modal.style.position = "fixed";
            modal.style.inset = "0";
            modal.style.zIndex = "90";
            document.body.appendChild(modal);
        }

        function openModal() {
            if (!modal) {
                return;
            }
            modal.classList.remove("hidden");
            modal.classList.add("is-open");
            bodyElement.classList.add("overflow-hidden");
        }

        function closeModal() {
            if (!modal) {
                return;
            }
            modal.classList.add("hidden");
            modal.classList.remove("is-open");
            bodyElement.classList.remove("overflow-hidden");
        }

        function normalizeMoneyInput(value) {
            const cleaned = (value || "").replace(/,/g, "").replace(/[^\d.]/g, "");
            if (!cleaned) {
                return "";
            }
            const parts = cleaned.split(".");
            const integerPart = parts.shift() || "";
            const decimalPart = parts.join("").slice(0, 2);
            return decimalPart ? integerPart + "." + decimalPart : integerPart;
        }

        function formatMoneyInputValue(value) {
            const normalized = normalizeMoneyInput(value);
            if (!normalized) {
                return "";
            }
            const pieces = normalized.split(".");
            const whole = pieces[0].replace(/^0+(?=\d)/, "") || "0";
            const withCommas = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
            return pieces.length > 1 ? withCommas + "." + pieces[1] : withCommas;
        }

        function syncAmountInput() {
            const normalized = normalizeMoneyInput(amountDisplayInput.value);
            amountInput.value = normalized;
            amountDisplayInput.value = formatMoneyInputValue(normalized);
        }

        function showFeedback(type, text) {
            feedback.classList.add("hidden");
            feedback.textContent = "";
            window.showToast?.(type === "success" ? "success" : "error", text);
        }

        function clearPreview() {
            card.classList.add("hidden");
            body.innerHTML = "";
            eligibilityMessage.textContent = "Load the calculator to check whether the selected amount qualifies.";
            savingsLabel.textContent = "-";
            ratioLabel.textContent = "-";
            maxAllowedLabel.textContent = "-";
            feedback.classList.add("hidden");
        }

        [loanTypeInput, tenorInput].forEach(function (input) {
            input.addEventListener("change", clearPreview);
        });
        amountDisplayInput.addEventListener("input", syncAmountInput);
        amountDisplayInput.addEventListener("change", function () {
            syncAmountInput();
            clearPreview();
        });
        syncAmountInput();

        openButton.addEventListener("click", openModal);
        closeButton.addEventListener("click", closeModal);
        modal.addEventListener("click", function (event) {
            if (event.target === modal) {
                closeModal();
            }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeModal();
            }
        });

        loadButton.addEventListener("click", async function () {
            const amount = amountInput.value.trim();
            const tenorMonths = tenorInput.value.trim();
            if (!amount || !tenorMonths) {
                showFeedback("error", "Enter both loan amount and repayment months before loading the calculator.");
                return;
            }

            loading.classList.remove("hidden");
            loadButton.disabled = true;
            loadButton.classList.add("opacity-60", "cursor-not-allowed");

            try {
                const response = await fetch("/app/loan-applications/financial-preview", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
                    },
                    body: new URLSearchParams({
                        [csrfParam]: csrfToken,
                        loanType: loanTypeInput.value,
                        amount: amount,
                        tenorMonths: tenorMonths
                    })
                });

                if (!response.ok) {
                    throw new Error("Unable to load the loan calculator right now.");
                }

                const payload = await response.json();
                body.innerHTML = "";
                Object.entries(payload.fields || {}).forEach(function (entry) {
                    const row = document.createElement("tr");
                    row.innerHTML = "<td class='px-3 py-2 font-medium text-slate-700'></td><td class='px-3 py-2'></td>";
                    row.children[0].textContent = entry[0];
                    row.children[1].textContent = entry[1];
                    body.appendChild(row);
                });
                card.classList.remove("hidden");

                const eligibility = payload.eligibility || {};
                savingsLabel.textContent = eligibility.savingsLabel || "-";
                ratioLabel.textContent = eligibility.ratioPercentLabel || "-";
                maxAllowedLabel.textContent = eligibility.maxAllowedLabel || "-";
                eligibilityMessage.textContent = eligibility.eligible
                    ? "This amount is within your current eligibility."
                    : "This amount is above your current eligibility. Reduce the amount before applying.";

                showFeedback("success", payload.message || "Loan details loaded.");
            } catch (error) {
                showFeedback("error", error.message || "Failed to load the loan calculator.");
            } finally {
                loading.classList.add("hidden");
                loadButton.disabled = false;
                loadButton.classList.remove("opacity-60", "cursor-not-allowed");
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
