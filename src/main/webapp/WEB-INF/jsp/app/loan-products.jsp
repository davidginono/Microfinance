<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="products.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="products.title" /></h1>
    <p class="erp-page-subtitle"><spring:message code="products.subtitle" /></p>
</div>

<section class="erp-form-wrap mb-4 space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
            <h5 class="erp-panel-title"><spring:message code="products.calculateEligibility.title" /></h5>
        
        </div>
        <div class="flex flex-wrap items-center gap-3">
            <button id="productsCalculatorButton" type="button" class="app-btn btn-primary"><spring:message code="products.calculateEligibility.button" /></button>
        </div>
    </div>
  
</section>

<div id="productsCalculatorModal"
     class="app-modal-overlay hidden">
    <div class="app-modal-panel app-modal-panel--wide">
        <div class="app-modal-scroll">
        <div class="app-modal-header">
            <div>
                <p class="text-sm font-semibold uppercase tracking-[0.25em] text-slate-500"><spring:message code="products.calculator.eyebrow" /></p>
                <h2 class="mt-2 text-3xl font-semibold text-sacco-ink"><spring:message code="products.calculator.title" /></h2>
                <p class="mt-2 text-sm text-slate-500"><spring:message code="products.calculator.subtitle" /></p>
            </div>
            <button id="productsCalculatorCloseButton" type="button" class="app-modal-close" aria-label="<spring:message code='products.calculator.closeAria' />">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                </svg>
            </button>
        </div>

        <div class="app-modal-body space-y-5">
            <div class="grid gap-3 md:grid-cols-2">
                <div class="md:col-span-2">
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="products.calculator.chooseProduct" /></label>
                    <select id="productsLoanType" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                        <c:forEach items="${products}" var="p">
                            <option value="${p.loanType}" data-max-months="${p.maxRepaymentMonths}">
                                <c:out value="${p.displayName}" />
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="products.amount.label" /> (TSh)</label>
                    <input id="productsLoanAmount" type="hidden" />
                    <input id="productsLoanAmountDisplay" type="text" inputmode="decimal" autocomplete="off"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="100,000.00" />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="products.tenor.label" /></label>
                    <input id="productsTenorMonths" type="number" min="1"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="12" />
                </div>
            </div>

            <div class="grid gap-3 md:grid-cols-2">
                <div class="app-modal-section">
                    <p class="font-semibold text-slate-800"><spring:message code="products.beforeYouApply.title" /></p>
                    <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                        <li><spring:message code="products.beforeYouApply.item1" /></li>
                        <li><spring:message code="products.beforeYouApply.item2" /></li>
                        <li><spring:message code="products.beforeYouApply.item3" /></li>
                    </ul>
                </div>
                <div id="productsEligibilityCard" class="app-modal-section">
                    <p class="font-semibold text-slate-800"><spring:message code="products.eligibilityResult.title" /></p>
                    <div class="mt-2 space-y-1 text-sm text-slate-600">
                        <p id="productsEligibilityMessage"><spring:message code="products.eligibilityResult.message" /></p>
                        <p><strong><spring:message code="products.eligibilityResult.savings" />:</strong> <span id="productsSavingsLabel">-</span></p>
                        <p><strong><spring:message code="products.eligibilityResult.ratio" />:</strong> <span id="productsRatioLabel">-</span></p>
                        <p><strong><spring:message code="products.eligibilityResult.maximum" />:</strong> <span id="productsMaxAllowedLabel">-</span></p>
                    </div>
                </div>
            </div>

            <div class="app-modal-section flex flex-wrap items-center justify-between gap-3">
                <div class="text-sm text-slate-500"><spring:message code="products.loadHint" /></div>
                <button id="productsCalculatorLoadButton" type="button" class="app-btn btn-primary"><spring:message code="products.calculateEligibility.button" /></button>
            </div>

            <div id="productsFinancialFeedback" data-auto-scroll-message="true" class="hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div id="productsFinancialLoading" class="hidden app-modal-section text-sm text-slate-600">
                <div class="flex items-center gap-3">
                    <span class="inline-flex h-3 w-3 animate-pulse rounded-full bg-sacco-blue"></span>
                    <spring:message code="products.loadingCalculator" />
                </div>
            </div>
            <div id="productsFinancialCard" class="hidden erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th><spring:message code="products.table.section" /></th>
                        <th><spring:message code="products.table.value" /></th>
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
    <p class="text-base font-medium text-slate-700"><spring:message code="products.chooseLoan" /></p>
</div>

<c:if test="${not empty applicationLockApp}">
    <div class="erp-section mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-slate-700">
            <spring:message code="products.applicationLock.intro" />
            <strong>${applicationLockApp.applicationNumber}</strong>
            (<strong>${applicationLockStatusLabel}</strong>).
            <spring:message code="products.applicationLock.followup" />
        </div>
        <a href="/app/loan-applications/${applicationLockApp.id}" class="app-btn btn-neutral"><spring:message code="products.applicationLock.viewCurrent" /></a>
    </div>
</c:if>

<c:if test="${empty applicationLockApp and not empty activeDisbursedLoanApp}">
        <div class="erp-section mb-4 text-sm text-slate-700">
        <spring:message code="products.activeLoan.intro" />
        <strong>${activeDisbursedLoanApp.applicationNumber}</strong>.
        <spring:message code="products.activeLoan.followup" />
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
            <tr>
                <th><spring:message code="loan.type" /></th>
                <th><spring:message code="products.table.amountRules" /></th>
                <th><spring:message code="products.table.guarantors" /></th>
                <th><spring:message code="products.table.annualInterest" /></th>
                <th><spring:message code="products.table.tenure" /></th>
                <th><spring:message code="products.table.action" /></th>
            </tr>
        </thead>
        <tbody>
        <c:forEach items="${products}" var="p">
            <tr>
                <td>
                    <div class="font-semibold text-slate-900"><c:out value="${p.displayName}" /></div>
                    <div class="mt-1 text-sm text-slate-500"><c:out value="${p.displayDescription}" /></div>
                </td>
                <td>
                    <div class="text-sm text-slate-700">
                        <spring:message code="products.amount.min" />:
                        <strong><fmt:formatNumber value="${p.minimumAmount}" minFractionDigits="2" maxFractionDigits="2" /></strong>
                    </div>
                    <div class="mt-1 text-sm text-slate-700">
                        <spring:message code="products.amount.max" />:
                        <strong>
                            <c:choose>
                                <c:when test="${p.maximumAmount ne null}">
                                    <fmt:formatNumber value="${p.maximumAmount}" minFractionDigits="2" maxFractionDigits="2" />
                                </c:when>
                                <c:otherwise><spring:message code="products.amount.notSet" /></c:otherwise>
                            </c:choose>
                        </strong>
                    </div>
                </td>
                <td>${p.guarantorsRequired}</td>
                <td>
                    <c:choose>
                        <c:when test="${p.loanType eq 'LOAN_ADVANCE'}"><spring:message code="products.interest.loanAdvance" /></c:when>
                        <c:otherwise><fmt:formatNumber value="${p.interestRate * 100}" minFractionDigits="0" maxFractionDigits="2" />%</c:otherwise>
                    </c:choose>
                </td>
                <td>${p.minimumRepaymentMonths} - ${p.maxRepaymentMonths} month(s)</td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicationLockApp}">
                            <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                <spring:message code="products.action.applicationOnReview" />
                            </button>
                        </c:when>
                        <c:when test="${not empty activeDisbursedLoanApp and not p.applicationWithActiveLoanAllowed}">
                            <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                <spring:message code="products.action.activeLoanNotAllowed" />
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
        const msgLoadPrompt = "<spring:message code='products.js.loadPrompt' />";
        const msgEnterAmountTenor = "<spring:message code='products.js.enterAmountTenor' />";
        const msgUnableLoadCalculator = "<spring:message code='products.js.unableLoadCalculator' />";
        const msgWithinEligibility = "<spring:message code='products.js.withinEligibility' />";
        const msgAboveEligibility = "<spring:message code='products.js.aboveEligibility' />";
        const msgLoaded = "<spring:message code='products.js.loaded' />";
        const msgFailedLoad = "<spring:message code='products.js.failedLoad' />";

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
            eligibilityMessage.textContent = msgLoadPrompt;
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
                showFeedback("error", msgEnterAmountTenor);
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
                    throw new Error(msgUnableLoadCalculator);
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
                    ? msgWithinEligibility
                    : msgAboveEligibility;

                showFeedback("success", payload.message || msgLoaded);
            } catch (error) {
                showFeedback("error", error.message || msgFailedLoad);
            } finally {
                loading.classList.add("hidden");
                loadButton.disabled = false;
                loadButton.classList.remove("opacity-60", "cursor-not-allowed");
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
