<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Guarantor Selection</p>
    <h1 class="erp-page-title"><spring:message code="guarantor.select" /> (${app.requiredGuarantors})</h1>
    <p class="erp-page-subtitle">Search privately and store the exact guarantor selection required for this application.</p>
</div>
<form method="post" action="/app/loan-applications/${app.id}/guarantors" class="erp-form-wrap space-y-4">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

    <div class="erp-section-muted">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
                <p class="erp-panel-title">Private Guarantor Search</p>
                <p class="text-sm text-slate-500">Type to search. Only a small suggestion list is shown.</p>
            </div>
            <span id="guarantorSelectedCount" class="rounded-full bg-sacco-blue/10 px-3 py-1 text-sm font-semibold text-sacco-blue">
                0 selected / ${app.requiredGuarantors}
            </span>
        </div>

        <div class="relative">
            <input id="guarantorSearch" type="text" autocomplete="off" placeholder="Search by member number or name"
                   class="w-full border border-slate-300 bg-white px-3 py-3 text-sm focus:border-sacco-blue focus:outline-none" />
            <div id="guarantorDropdown" class="absolute left-0 right-0 z-20 mt-2 hidden max-h-52 overflow-y-auto rounded border border-slate-200 bg-white shadow-lg"></div>
        </div>
        <p id="guarantorHint" class="mt-2 text-sm text-slate-500">Type at least 2 characters to search.</p>

        <div id="selectedGuarantors" class="mt-3 flex flex-wrap gap-2">
            <c:forEach items="${selectedGuarantorItems}" var="item">
                <button type="button"
                        class="selected-guarantor-chip inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700"
                        data-id="${item.id}"
                        data-member-no="${item.memberNo}"
                        data-full-name="${item.fullName}">
                    <span>${item.memberNo} - ${item.fullName}</span>
                    <span class="text-slate-400">x</span>
                </button>
            </c:forEach>
        </div>
        <div id="selectedGuarantorInputs"></div>
    </div>

    <button type="submit" class="app-btn btn-primary">Save Guarantors</button>
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
        let debounceHandle;

        function updateCounter() {
            counter.textContent = selected.size + " selected / " + required;
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
                chip.className = "inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700";
                chip.innerHTML = "<span>" + item.memberNo + " - " + item.fullName + "</span><span class='text-slate-400'>x</span>";
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
                empty.textContent = "No matching members found.";
                dropdown.appendChild(empty);
            } else {
                items.forEach(function (item) {
                    const row = document.createElement("button");
                    row.type = "button";
                    row.className = "block w-full border-b border-slate-100 px-4 py-3 text-left text-sm text-slate-700 hover:bg-slate-50";
                    row.textContent = item.memberNo + " - " + item.fullName;
                    row.addEventListener("click", function () {
                        if (selected.size >= required) {
                            hint.textContent = "You can only select " + required + " guarantors.";
                            hideDropdown();
                            return;
                        }
                        selected.set(item.id, item);
                        renderSelected();
                        searchInput.value = "";
                        hint.textContent = "Guarantor selected.";
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
                hint.textContent = "Type at least 2 characters to search.";
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
                hint.textContent = filtered.length + " matching member(s) found.";
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
