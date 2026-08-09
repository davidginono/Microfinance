<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="guarantor.selection.breadcrumb" text="Member Workspace / Guarantor Selection" /></p>
    <h1 class="erp-page-title"><spring:message code="guarantor.select" text="Select Guarantor" /></h1>
</div>
<form method="post" action="/app/loan-applications/${app.id}/guarantors" class="erp-form-wrap guarantor-selection-page">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

    <section class="loan-workflow-section loan-guarantor-section" aria-labelledby="guarantorSelectionTitle">
        <div class="loan-selection-header">
            <div>
                <h2 id="guarantorSelectionTitle" class="erp-panel-title"><spring:message code="guarantor.selection.privateSearch" text="Guarantor Search" /></h2>
            </div>
            <span id="guarantorSelectedCount" class="app-badge loan-selection-counter">
                0 <spring:message code="newloan.js.selectedSuffix" text="selected" /> / ${app.requiredGuarantors}
            </span>
        </div>

        <div class="loan-guarantor-search-row">
            <input id="guarantorSearch" type="text" autocomplete="off" placeholder="<spring:message code='guarantor.selection.searchPlaceholder' text='Search by member number or name' />"
                   class="fcms-control loan-guarantor-search-input" />
            <div id="guarantorDropdown" class="loan-guarantor-dropdown hidden"></div>
        </div>
        <p id="guarantorHint" class="mt-2 text-sm text-slate-500"><spring:message code="guarantor.selection.typeTwo" text="Type at least 2 characters to search." /></p>

        <div id="selectedGuarantors" class="loan-selected-guarantors" aria-live="polite">
            <c:forEach items="${selectedGuarantorItems}" var="item">
                <button type="button"
                        class="selected-guarantor-chip"
                        data-id="${item.id}"
                        data-member-no="${item.memberNo}"
                        data-full-name="${item.fullName}"
                        aria-label="Remove ${item.fullName}">
                    <span>${item.memberNo} - ${item.fullName}</span>
                    <span class="selected-guarantor-remove" aria-hidden="true">&times;</span>
                </button>
            </c:forEach>
        </div>
        <div id="selectedGuarantorInputs"></div>
    </section>

    <div class="loan-flow-navigation">
        <button type="submit" class="app-btn btn-primary"><spring:message code="guarantor.selection.save" text="Save Guarantors" /></button>
    </div>
</form>

<script>
    (function () {
        const required = Number("${app.requiredGuarantors}");
        const searchInput = document.getElementById("guarantorSearch");
        const dropdown = document.getElementById("guarantorDropdown");
        const hint = document.getElementById("guarantorHint");
        const counter = document.getElementById("guarantorSelectedCount");
        const selectedContainer = document.getElementById("selectedGuarantors");
        const hiddenInputs = document.getElementById("selectedGuarantorInputs");
        const selected = new Map();
        const msgSelectedSuffix = "<spring:message code='newloan.js.selectedSuffix' text='selected' />";
        const msgTypeTwo = "<spring:message code='guarantor.selection.typeTwo' text='Type at least 2 characters to search.' />";
        const msgNoMatches = "<spring:message code='newloan.js.noMatches' text='No matching members found.' />";
        const msgOnlySelectGuarantors = "<spring:message code='newloan.js.onlySelectGuarantors' text='You can only select {0} guarantors.' />";
        const msgGuarantorSelected = "<spring:message code='newloan.js.guarantorSelected' text='Guarantor selected.' />";
        const msgMatchingMembers = "<spring:message code='newloan.js.matchingMembers' text='matching member(s) found.' />";
        let debounceHandle;

        function updateCounter() {
            counter.textContent = selected.size + " " + msgSelectedSuffix + " / " + required;
        }

        function renderHiddenInputs() {
            hiddenInputs.innerHTML = "";
            Array.from(selected.keys()).forEach(function (id) {
                const input = document.createElement("input");
                input.type = "hidden";
                input.name = "guarantorIds";
                input.value = id;
                hiddenInputs.appendChild(input);
            });
        }

        function renderSelected() {
            selectedContainer.innerHTML = "";
            Array.from(selected.values()).forEach(function (item) {
                const chip = document.createElement("button");
                chip.type = "button";
                chip.className = "selected-guarantor-chip";
                chip.setAttribute("aria-label", "Remove " + item.fullName);
                chip.innerHTML = "<span>" + item.memberNo + " - " + item.fullName + "</span><span class='selected-guarantor-remove' aria-hidden='true'>&times;</span>";
                chip.addEventListener("click", function () {
                    selected.delete(item.id);
                    renderSelected();
                });
                selectedContainer.appendChild(chip);
            });
            renderHiddenInputs();
            updateCounter();
        }

        Array.from(document.querySelectorAll(".selected-guarantor-chip")).forEach(function (chip) {
            selected.set(chip.dataset.id, {
                id: chip.dataset.id,
                memberNo: chip.dataset.memberNo,
                fullName: chip.dataset.fullName
            });
        });
        renderSelected();

        function hideDropdown() {
            dropdown.classList.add("hidden");
            dropdown.innerHTML = "";
        }

        function showResults(items) {
            dropdown.innerHTML = "";
            if (!items.length) {
                const empty = document.createElement("div");
                empty.className = "px-4 py-3 text-sm text-slate-500";
                empty.textContent = msgNoMatches;
                dropdown.appendChild(empty);
            } else {
                items.forEach(function (item) {
                    const row = document.createElement("button");
                    row.type = "button";
                    row.className = "block w-full text-left";
                    row.textContent = item.memberNo + " - " + item.fullName;
                    row.addEventListener("click", function () {
                        if (selected.size >= required) {
                            hint.textContent = msgOnlySelectGuarantors.replace("{0}", required);
                            hideDropdown();
                            return;
                        }
                        selected.set(item.id, item);
                        renderSelected();
                        searchInput.value = "";
                        hint.textContent = msgGuarantorSelected;
                        hideDropdown();
                    });
                    dropdown.appendChild(row);
                });
            }
            dropdown.classList.remove("hidden");
        }

        searchInput.addEventListener("input", function () {
            clearTimeout(debounceHandle);
            const term = this.value.trim();
            if (term.length < 2) {
                hint.textContent = msgTypeTwo;
                hideDropdown();
                return;
            }
            debounceHandle = setTimeout(async function () {
                const response = await fetch("/app/guarantors/search?q=" + encodeURIComponent(term), {
                    headers: {
                        "X-Requested-With": "XMLHttpRequest"
                    }
                });
                const data = response.ok ? await response.json() : [];
                const filtered = data.filter(function (item) { return !selected.has(item.id); });
                hint.textContent = filtered.length + " " + msgMatchingMembers;
                showResults(filtered);
            }, 250);
        });

        document.addEventListener("click", function (event) {
            if (!dropdown.contains(event.target) && event.target !== searchInput) {
                hideDropdown();
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
