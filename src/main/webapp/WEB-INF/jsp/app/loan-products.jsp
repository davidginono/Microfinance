<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="products.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="products.title" /></h1>
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

        <ol class="loan-calculator-steps" aria-label="Loan eligibility calculator progress">
            <li class="is-current" data-calculator-step="details"><span>1</span><strong>Loan details</strong></li>
            <li data-calculator-step="eligibility"><span>2</span><strong>Eligibility</strong></li>
            <li data-calculator-step="repayment"><span>3</span><strong>Repayment plan</strong></li>
        </ol>

        <div class="app-modal-body loan-calculator-flow space-y-5">
            <section class="loan-calculator-input-panel" aria-labelledby="loanCalculatorDetailsHeading">
                <div class="loan-calculator-section-heading">
                    <span class="loan-calculator-step-number">1</span>
                    <div>
                        <h3 id="loanCalculatorDetailsHeading">Choose an amount and repayment period</h3>
                        <p>Enter only the figures you want to test. You can revise them and calculate again.</p>
                    </div>
                </div>
            <div class="grid gap-3 md:grid-cols-2">
                <div class="md:col-span-2">
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="products.calculator.chooseProduct" /></label>
                    <select id="productsLoanType" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                        <c:forEach items="${products}" var="p">
                            <option value="${p.id}" data-loan-type="${p.loanType}" data-min-months="${p.minimumRepaymentMonths}" data-max-months="${p.maxRepaymentMonths}">
                                <c:out value="${p.displayName}" />
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="products.amount.label" /> (TSh)</label>
                    <input id="productsLoanAmount" type="hidden" />
                    <input id="productsLoanAmountDisplay" type="text" inputmode="decimal" autocomplete="off"
                           class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="100,000" />
                </div>
                <div>
                    <label id="productsTenorDisplayLabel" class="mb-1 block text-sm font-medium text-slate-700"
                           data-month-label="<spring:message code='products.tenor.months.label' text='Total Months to Repay' />"
                           data-year-label="<spring:message code='products.tenor.years.label' text='Total Years to Repay' />"><spring:message code="products.tenor.label" /></label>
                    <input id="productsTenorMonths" type="hidden" />
                    <div class="tenure-unit-toggle-group mb-2 text-sm" role="group" aria-label="<spring:message code='newloan.tenureUnit' text='Tenure unit' />">
                        <button type="button" class="tenure-unit-toggle is-active px-3 py-2 transition" data-products-tenure-unit="MONTHS" aria-pressed="true"><spring:message code="common.months.label" text="Months" /></button>
                        <button type="button" class="tenure-unit-toggle px-3 py-2 transition" data-products-tenure-unit="YEARS" aria-pressed="false"><spring:message code="common.years.label" text="Years" /></button>
                    </div>
                    <input id="productsTenorDisplay" type="number" min="1" step="1"
                           class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           placeholder="12" />
                    <p id="productsTenorRangeHelp" class="mt-1 text-xs text-slate-500"
                       data-prefix="<spring:message code='newloan.allowedTenureRange' text='Allowed range:' />"
                       data-to-label="<spring:message code='common.to' text='to' />"
                       data-month-label="<spring:message code='common.months' text='month(s)' />"
                       data-year-label="<spring:message code='common.years' text='year(s)' />"
                       data-no-year-label="<spring:message code='products.tenor.noWholeYear' text='No full-year tenure for this product. Choose Months.' />"></p>
                </div>
            </div>
            </section>

            <div class="grid gap-3 md:grid-cols-2">
                <div class="app-modal-section loan-calculator-guidance">
                    <p class="font-semibold text-slate-800"><spring:message code="products.beforeYouApply.title" /></p>
                    <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                        <li><spring:message code="products.beforeYouApply.item1" /></li>
                        <li><spring:message code="products.beforeYouApply.item2" /></li>
                        <li><spring:message code="products.beforeYouApply.item3" /></li>
                    </ul>
                </div>
                <div id="productsEligibilityCard" class="app-modal-section loan-calculator-result-summary" aria-live="polite">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                        <p class="font-semibold text-slate-800"><spring:message code="products.eligibilityResult.title" /></p>
                        <span id="productsEligibilityStatus" class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-slate-600">
                            <spring:message code="products.eligibilityResult.notChecked" text="Not checked" />
                        </span>
                    </div>
                    <div class="mt-2 space-y-1 text-sm text-slate-600">
                        <p id="productsEligibilityMessage"><spring:message code="products.eligibilityResult.message" /></p>
                        <p><strong><spring:message code="products.eligibilityResult.savings" />:</strong> <span id="productsSavingsLabel">-</span></p>
                        <p><strong><spring:message code="products.eligibilityResult.ratio" />:</strong> <span id="productsRatioLabel">-</span></p>
                        <p><strong><spring:message code="products.eligibilityResult.maximum" />:</strong> <span id="productsMaxAllowedLabel">-</span></p>
                    </div>
                </div>
            </div>

            <div class="app-modal-section loan-calculator-actionbar flex flex-wrap items-center justify-between gap-3">
                <div class="text-sm text-slate-500"><spring:message code="products.loadHint" /></div>
                <button id="productsCalculatorLoadButton" type="button" class="app-btn btn-primary"><spring:message code="products.calculateEligibility.button" /></button>
            </div>

            <div id="productsCalculatorResults" class="hidden loan-calculator-results" tabindex="-1">
            <div class="loan-calculator-section-heading">
                <span class="loan-calculator-step-number">2</span>
                <div>
                    <h3>Your eligibility and repayment estimate</h3>
                    <p>Review the decision first, then inspect the supporting figures and repayment schedule.</p>
                </div>
            </div>
            <div id="productsFinancialFeedback" data-auto-scroll-message="true" class="hidden border px-4 py-3 text-sm"></div>
            <div id="productsFinancialLoading" class="hidden app-modal-section text-sm text-slate-600">
                <span class="sr-only"><spring:message code="products.loadingCalculator" /></span>
                <div class="skeleton-table" aria-hidden="true">
                    <span class="skeleton skeleton-title"></span>
                    <div class="skeleton-table-row">
                        <span class="skeleton skeleton-text"></span>
                        <span class="skeleton skeleton-text"></span>
                    </div>
                    <div class="skeleton-table-row">
                        <span class="skeleton skeleton-text"></span>
                        <span class="skeleton skeleton-text"></span>
                    </div>
                    <div class="skeleton-table-row">
                        <span class="skeleton skeleton-text"></span>
                        <span class="skeleton skeleton-text aws-skeleton-wide"></span>
                    </div>
                </div>
            </div>
            <div id="productsFinancialCard" class="hidden loan-calculator-detail-grid"></div>
            <div id="productsRepaymentScheduleCard" class="hidden app-modal-section">
                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <div>
                        <p class="font-semibold text-slate-800"><spring:message code="repayment.estimatedSchedule" text="Estimated Repayment Schedule" /></p>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="repayment.estimatedScheduleHelp" text="Estimated monthly installments with loan amount, interest, and balance after each payment." /></p>
                    </div>
                </div>
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
                    <div class="erp-table-scroll erp-table-scroll-sm">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th><spring:message code="repayment.pmtNo" text="Pmt No." /></th>
                            <th><spring:message code="repayment.month" text="Month" /></th>
                            <th><spring:message code="repayment.beginningBalance" text="Beginning Balance" /></th>
                            <th><spring:message code="repayment.amountToPay" text="Amount to Pay" /></th>
                            <th><spring:message code="loan.amount.label" text="Loan Amount" /></th>
                            <th><spring:message code="repayment.interest" text="Interest" /></th>
                            <th><spring:message code="repayment.endingBalance" text="Ending Balance" /></th>
                        </tr>
                        </thead>
                        <tbody id="productsRepaymentScheduleBody"></tbody>
                    </table>
                </div>
            </div>
            </div>
            <div class="loan-calculator-footer-actions">
                <button type="button" class="app-btn btn-neutral" data-calculator-edit>Change figures</button>
                <c:if test="${empty applicationLockApp and applicantPolicyEligible ne false}">
                    <a id="productsCalculatorApplyLink" class="app-btn btn-launch hidden" href="#">Continue to application</a>
                </c:if>
            </div>
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

<c:if test="${applicantPolicyEligible eq false}">
    <div class="erp-section mb-4 text-sm leading-6 text-rose-700">
        ${applicantPolicyReason}
                    </div>
</c:if>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead>
            <tr>
                <th><spring:message code="loan.type" /></th>
                <th><spring:message code="products.table.amountRules" /></th>
                <th><spring:message code="products.table.guarantors" /></th>
                <th><spring:message code="products.table.annualInterest" /></th>
                <th><spring:message code="products.table.repaymentMethod" text="Repayment Method" /></th>
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
                        <strong><fmt:formatNumber value="${p.minimumAmount}" minFractionDigits="0" maxFractionDigits="2" /></strong>
                    </div>
                    <div class="mt-1 text-sm text-slate-700">
                        <spring:message code="products.amount.max" />:
                        <strong>
                            <c:choose>
                                <c:when test="${p.maximumAmount ne null}">
                                    <fmt:formatNumber value="${p.maximumAmount}" minFractionDigits="0" maxFractionDigits="2" />
                                </c:when>
                                <c:otherwise><spring:message code="products.amount.notSet" /></c:otherwise>
                            </c:choose>
                        </strong>
                            </div>
                </td>
                <td>${p.guarantorsRequired}</td>
                <td>
                    <fmt:formatNumber value="${p.interestRate * 100}" minFractionDigits="0" maxFractionDigits="2" />%
                </td>
                <td>
                    <c:choose>
                        <c:when test="${p.interestMethod eq 'REDUCING_BALANCE'}"><spring:message code="interestMethod.REDUCING_BALANCE" text="Reducing Balance" /></c:when>
                        <c:otherwise><spring:message code="interestMethod.FLAT_RATE" text="Flat Rate" /></c:otherwise>
                    </c:choose>
                </td>
                <td>${p.minimumRepaymentMonths} - ${p.maxRepaymentMonths} <spring:message code="common.months" text="month(s)" /></td>
                <td>
                    <c:choose>
                        <c:when test="${not empty applicationLockApp}">
                            <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                <spring:message code="products.action.applicationOnReview" />
                            </button>
                        </c:when>
                        <c:when test="${applicantPolicyEligible eq false}">
                            <div class="space-y-2">
                                <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                    <spring:message code="products.js.notEligibleStatus" text="Not eligible" />
                                </button>
                                <p class="max-w-xs text-xs leading-5 text-rose-600">${applicantPolicyReason}</p>
</div>
                        </c:when>
                        <c:when test="${not empty activeDisbursedLoanApp and not p.applicationWithActiveLoanAllowed}">
                            <button type="button" class="app-btn btn-neutral opacity-60 cursor-not-allowed" disabled>
                                <spring:message code="products.action.activeLoanNotAllowed" />
                            </button>
                        </c:when>
                        <c:otherwise>
                            <a class="app-btn btn-launch" href="/app/loan-applications/new?loanProductId=${p.id}">
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
        const tenorDisplayInput = document.getElementById("productsTenorDisplay");
        const tenorDisplayLabel = document.getElementById("productsTenorDisplayLabel");
        const tenorRangeHelp = document.getElementById("productsTenorRangeHelp");
        const tenureUnitButtons = Array.from(document.querySelectorAll("[data-products-tenure-unit]"));
        const feedback = document.getElementById("productsFinancialFeedback");
        const loading = document.getElementById("productsFinancialLoading");
        const card = document.getElementById("productsFinancialCard");
        const scheduleCard = document.getElementById("productsRepaymentScheduleCard");
        const scheduleBody = document.getElementById("productsRepaymentScheduleBody");
        const eligibilityMessage = document.getElementById("productsEligibilityMessage");
        const eligibilityStatus = document.getElementById("productsEligibilityStatus");
        const resultsRegion = document.getElementById("productsCalculatorResults");
        const editFiguresButton = document.querySelector("[data-calculator-edit]");
        const applyLink = document.getElementById("productsCalculatorApplyLink");
        const calculatorSteps = Array.from(document.querySelectorAll("[data-calculator-step]"));
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
        const msgEligibleStatus = "<spring:message code='products.js.eligibleStatus' text='Eligible' />";
        const msgNotEligibleStatus = "<spring:message code='products.js.notEligibleStatus' text='Not eligible' />";
        const msgNotCheckedStatus = "<spring:message code='products.eligibilityResult.notChecked' text='Not checked' />";
        const msgLoaded = "<spring:message code='products.js.loaded' />";
        const msgFailedLoad = "<spring:message code='products.js.failedLoad' />";
        const tableSectionLabel = "<spring:message code='products.table.section' text='Section' />";
        const tableValueLabel = "<spring:message code='products.table.value' text='Value' />";
        let tenureUnit = "MONTHS";

        if (modal) {
            document.body.appendChild(modal);
        }

        function setCalculatorStage(stage) {
            const order = ["details", "eligibility", "repayment"];
            const currentIndex = Math.max(0, order.indexOf(stage));
            calculatorSteps.forEach(function (item) {
                const itemIndex = order.indexOf(item.dataset.calculatorStep);
                item.classList.toggle("is-current", itemIndex === currentIndex);
                item.classList.toggle("is-complete", itemIndex < currentIndex);
            });
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

        function trimMoneyDecimalZeros(value) {
            if (!value || !value.includes(".")) {
                return value || "";
            }
            return value.replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
        }

        function formatMoneyInputValue(value, trimDecimals) {
            const normalized = normalizeMoneyInput(value);
            if (!normalized) {
                return "";
            }
            const displayValue = trimDecimals ? trimMoneyDecimalZeros(normalized) : normalized;
            const pieces = displayValue.split(".");
            const whole = pieces[0].replace(/^0+(?=\d)/, "") || "0";
            const withCommas = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
            return pieces.length > 1 ? withCommas + "." + pieces[1] : withCommas;
        }

        function syncAmountInput(trimDecimals) {
            const normalized = normalizeMoneyInput(amountDisplayInput.value);
            amountInput.value = normalized;
            amountDisplayInput.value = formatMoneyInputValue(normalized, trimDecimals);
        }

        function selectedProductOption() {
            return loanTypeInput ? loanTypeInput.options[loanTypeInput.selectedIndex] : null;
        }

        function minTenorMonths() {
            const option = selectedProductOption();
            const value = Number(option ? option.dataset.minMonths : 1);
            return Number.isFinite(value) && value > 0 ? Math.round(value) : 1;
        }

        function maxTenorMonths() {
            const option = selectedProductOption();
            const value = Number(option ? option.dataset.maxMonths : 0);
            return Number.isFinite(value) && value > 0 ? Math.round(value) : 0;
        }

        function yearsFromMonths(months, rounder) {
            if (!Number.isFinite(months) || months <= 0) {
                return 0;
            }
            return rounder(months / 12);
        }

        function visibleTenorBounds() {
            const minMonths = minTenorMonths();
            const maxMonths = maxTenorMonths();
            if (tenureUnit === "YEARS") {
                return {
                    min: Math.max(1, yearsFromMonths(minMonths, Math.ceil)),
                    max: maxMonths > 0 ? yearsFromMonths(maxMonths, Math.floor) : 0
                };
            }
            return {
                min: minMonths,
                max: maxMonths
            };
        }

        function hasWholeYearTenureOption() {
            const maxMonths = maxTenorMonths();
            return tenureUnit !== "YEARS" || maxMonths <= 0 || yearsFromMonths(maxMonths, Math.floor) >= Math.max(1, yearsFromMonths(minTenorMonths(), Math.ceil));
        }

        function renderTenureCopy() {
            if (tenorDisplayLabel) {
                tenorDisplayLabel.textContent = tenureUnit === "YEARS"
                    ? (tenorDisplayLabel.dataset.yearLabel || "Total Years to Repay")
                    : (tenorDisplayLabel.dataset.monthLabel || "Total Months to Repay");
            }
            if (!tenorRangeHelp) {
                return;
            }
            if (!hasWholeYearTenureOption()) {
                tenorRangeHelp.textContent = tenorRangeHelp.dataset.noYearLabel || "No full-year tenure for this product. Choose Months.";
                return;
            }
            const bounds = visibleTenorBounds();
            const prefix = tenorRangeHelp.dataset.prefix || "Allowed range:";
            const toLabel = tenorRangeHelp.dataset.toLabel || "to";
            const unitLabel = tenureUnit === "YEARS"
                ? (tenorRangeHelp.dataset.yearLabel || "year(s)")
                : (tenorRangeHelp.dataset.monthLabel || "month(s)");
            tenorRangeHelp.textContent = maxTenorMonths() > 0
                ? prefix + " " + bounds.min + " " + toLabel + " " + bounds.max + " " + unitLabel
                : prefix + " " + bounds.min + "+ " + unitLabel;
        }

        function monthsFromVisibleTenor() {
            const rawValue = Number(tenorDisplayInput ? tenorDisplayInput.value : tenorInput.value);
            if (!Number.isFinite(rawValue) || rawValue <= 0) {
                return "";
            }
            return tenureUnit === "YEARS" ? Math.round(rawValue) * 12 : Math.round(rawValue);
        }

        function refreshTenureConstraints() {
            if (!tenorDisplayInput) {
                return;
            }
            const bounds = visibleTenorBounds();
            tenorDisplayInput.min = bounds.min;
            if (bounds.max >= bounds.min) {
                tenorDisplayInput.max = bounds.max;
            } else {
                tenorDisplayInput.removeAttribute("max");
            }
            tenorDisplayInput.step = "1";
            tenorDisplayInput.placeholder = tenureUnit === "YEARS" ? "1" : "12";
            renderTenureCopy();
        }

        function syncTenorInput() {
            const months = monthsFromVisibleTenor();
            tenorInput.value = months;
            if (!tenorDisplayInput) {
                return Boolean(months);
            }
            tenorDisplayInput.setCustomValidity("");
            if (!months) {
                return false;
            }
            if (tenureUnit === "YEARS" && !/^\d+$/.test(String(tenorDisplayInput.value || "").trim())) {
                tenorDisplayInput.setCustomValidity("Enter whole years only.");
                return false;
            }
            if (!hasWholeYearTenureOption()) {
                tenorDisplayInput.setCustomValidity(tenorRangeHelp?.dataset.noYearLabel || "No full-year tenure for this product. Choose Months.");
                return false;
            }
            const minMonths = minTenorMonths();
            const maxMonths = maxTenorMonths();
            if (months < minMonths || (maxMonths > 0 && months > maxMonths)) {
                const bounds = visibleTenorBounds();
                const unitLabel = tenureUnit === "YEARS" ? "years" : "months";
                const rangeLabel = maxMonths > 0 ? "between " + bounds.min + " and " + bounds.max : "of at least " + bounds.min;
                tenorDisplayInput.setCustomValidity("Choose a tenure " + rangeLabel + " " + unitLabel + ".");
                return false;
            }
            return true;
        }

        function setTenureUnit(unit) {
            const currentMonths = Number(tenorInput.value || monthsFromVisibleTenor());
            tenureUnit = unit === "YEARS" ? "YEARS" : "MONTHS";
            tenureUnitButtons.forEach(function (button) {
                const active = button.dataset.productsTenureUnit === tenureUnit;
                button.setAttribute("aria-pressed", active ? "true" : "false");
                button.classList.toggle("is-active", active);
            });
            refreshTenureConstraints();
            if (tenorDisplayInput && Number.isFinite(currentMonths) && currentMonths > 0) {
                tenorDisplayInput.value = tenureUnit === "YEARS"
                    ? String(Math.max(1, Math.round(currentMonths / 12)))
                    : String(Math.round(currentMonths));
            }
            syncTenorInput();
            clearPreview();
        }

        function showFeedback(type, text) {
            feedback.classList.add("hidden");
            feedback.textContent = "";
            window.showToast?.(type === "success" ? "success" : "error", text, { contextElement: modal });
        }

        function clearPreview() {
            card.classList.add("hidden");
            card.innerHTML = "";
            scheduleCard.classList.add("hidden");
            scheduleBody.innerHTML = "";
            eligibilityMessage.textContent = msgLoadPrompt;
            updateEligibilityStatus(null);
            savingsLabel.textContent = "-";
            ratioLabel.textContent = "-";
            maxAllowedLabel.textContent = "-";
            feedback.classList.add("hidden");
            resultsRegion?.classList.add("hidden");
            applyLink?.classList.add("hidden");
            setCalculatorStage("details");
        }

        function renderFinancialSections(sections) {
            card.innerHTML = "";
            Object.entries(sections || {}).forEach(function (section) {
                const wrapper = document.createElement("div");
                wrapper.className = "erp-table-wrap loan-calculator-detail-card";
                wrapper.innerHTML = ""
                    +"<div class='app-table-titlebar'>"
                    +"<div class='app-table-heading'><h3></h3></div>"
                    + "</div>"
                    +"<div class='erp-table-scroll erp-table-scroll-sm loan-calculator-detail-scroll'><table class='erp-table loan-calculator-detail-table'>"
                    + "<thead><tr><th></th><th></th></tr></thead>"
                    + "<tbody></tbody>"
                    +"</table></div>";
                wrapper.querySelector("h3").textContent = section[0];
                const headerCells = wrapper.querySelectorAll("th");
                headerCells[0].textContent = tableSectionLabel;
                headerCells[1].textContent = tableValueLabel;
                const tableBody = wrapper.querySelector("tbody");
                Object.entries(section[1] || {}).forEach(function (entry) {
                    const row = document.createElement("tr");
                    row.innerHTML = "<td class='px-3 py-2 font-medium text-slate-700'></td><td class='px-3 py-2'></td>";
                    row.children[0].textContent = entry[0].replace(/\bPrincipal\b/g, "Loan Amount");
                    row.children[1].textContent = entry[1];
                    tableBody.appendChild(row);
                });
                card.appendChild(wrapper);
            });
            card.classList.toggle("hidden", card.children.length === 0);
        }

        function updateEligibilityStatus(eligible) {
            if (!eligibilityStatus) {
                return;
            }
            eligibilityStatus.className = "rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide";
            if (eligible === true) {
                eligibilityStatus.textContent = msgEligibleStatus;
                eligibilityStatus.classList.add("bg-emerald-100", "text-emerald-700");
                return;
            }
            if (eligible === false) {
                eligibilityStatus.textContent = msgNotEligibleStatus;
                eligibilityStatus.classList.add("bg-rose-100", "text-rose-700");
                return;
            }
            eligibilityStatus.textContent = msgNotCheckedStatus;
            eligibilityStatus.classList.add("bg-slate-100", "text-slate-600");
        }

        [loanTypeInput, tenorDisplayInput].forEach(function (input) {
            input.addEventListener("change", clearPreview);
        });
        loanTypeInput.addEventListener("change", function () {
            refreshTenureConstraints();
            syncTenorInput();
        });
        tenorDisplayInput.addEventListener("input", function () {
            syncTenorInput();
            clearPreview();
        });
        tenorDisplayInput.addEventListener("change", function () {
            syncTenorInput();
            clearPreview();
        });
        tenureUnitButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                setTenureUnit(button.dataset.productsTenureUnit);
            });
        });
        amountDisplayInput.addEventListener("input", function () {
            syncAmountInput(false);
            clearPreview();
        });
        amountDisplayInput.addEventListener("change", function () {
            syncAmountInput(true);
            clearPreview();
        });
        syncAmountInput(true);
        refreshTenureConstraints();
        syncTenorInput();

        openButton.addEventListener("click", openModal);
        closeButton.addEventListener("click", closeModal);
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeModal();
            }
        });

        loadButton.addEventListener("click", async function () {
            const validTenor = syncTenorInput();
            const amount = amountInput.value.trim();
            const tenorMonths = tenorInput.value.trim();
            if (!amount || !tenorMonths || !validTenor) {
                if (tenorDisplayInput && !validTenor) {
                    tenorDisplayInput.reportValidity();
                }
                showFeedback("error", msgEnterAmountTenor);
                return;
            }

            loading.classList.remove("hidden");
            loadButton.disabled = true;
            loadButton.classList.add("opacity-60", "cursor-not-allowed");

            try {
                const response = await fetch("/app/loan-applications/financial-preview", {
                    method: "POST",
                    headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"
                    },
                    body: new URLSearchParams({
                        [csrfParam]: csrfToken,
                        loanProductId: loanTypeInput.value,
                        amount: amount,
                        tenorMonths: tenorMonths
                    })
                });

                const payload = await response.json().catch(function () {
                    return {};
                });
                if (!response.ok) {
                    throw new Error(payload.message || msgUnableLoadCalculator);
                }

                renderFinancialSections(payload.fieldSections || {});
                scheduleBody.innerHTML = "";
                (payload.repaymentSchedule || []).forEach(function (item) {
                    const row = document.createElement("tr");
                    row.innerHTML = ""
                        + "<td class='px-3 py-2 font-medium text-slate-700'></td>"
                        + "<td class='px-3 py-2'></td>"
                        + "<td class='px-3 py-2'></td>"
                        + "<td class='px-3 py-2'></td>"
                        + "<td class='px-3 py-2'></td>"
                        + "<td class='px-3 py-2'></td>"
                        + "<td class='px-3 py-2'></td>";
                    row.children[0].textContent = item.pmtNo || "-";
                    row.children[1].textContent = item.month || "-";
                    row.children[2].textContent = item.beginningBalance || "-";
                    row.children[3].textContent = item.payment || item.installment || "-";
                    row.children[4].textContent = item.loanAmount || item.principal || "-";
                    row.children[5].textContent = item.interest || "-";
                    row.children[6].textContent = item.endingBalance || item.outstandingBalance || "-";
                    scheduleBody.appendChild(row);
                });
                scheduleCard.classList.toggle("hidden", scheduleBody.children.length === 0);

                const eligibility = payload.eligibility || {};
                savingsLabel.textContent = eligibility.savingsLabel || "-";
                ratioLabel.textContent = eligibility.savingsMultipleLabel || eligibility.ratioPercentLabel || "-";
                maxAllowedLabel.textContent = eligibility.maxAllowedLabel || "-";
                eligibilityMessage.textContent = eligibility.eligible
                    ? msgWithinEligibility
                    : msgAboveEligibility;
                updateEligibilityStatus(eligibility.eligible === true ? true : eligibility.eligible === false ? false : null);

                resultsRegion?.classList.remove("hidden");
                setCalculatorStage(scheduleBody.children.length ? "repayment" : "eligibility");
                if (applyLink && eligibility.eligible === true) {
                    applyLink.href = "/app/loan-applications/new?loanProductId=" + encodeURIComponent(loanTypeInput.value);
                    applyLink.classList.remove("hidden");
                } else {
                    applyLink?.classList.add("hidden");
                }
                window.requestAnimationFrame(function () {
                    resultsRegion?.focus({ preventScroll: true });
                    resultsRegion?.scrollIntoView({ behavior: "smooth", block: "start" });
                });

                showFeedback("success", payload.message || msgLoaded);
            } catch (error) {
                showFeedback("error", error.message || msgFailedLoad);
            } finally {
                loading.classList.add("hidden");
                loadButton.disabled = false;
                loadButton.classList.remove("opacity-60", "cursor-not-allowed");
            }
        });

        editFiguresButton?.addEventListener("click", function () {
            resultsRegion?.classList.add("hidden");
            setCalculatorStage("details");
            amountDisplayInput.focus();
            amountDisplayInput.scrollIntoView({ behavior: "smooth", block: "center" });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
