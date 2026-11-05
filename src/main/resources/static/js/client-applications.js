(function () {
    'use strict';
    function init() {
        document.querySelectorAll('[data-client-application]:not([data-application-ready])').forEach(function (form) {
            form.dataset.applicationReady = 'true';
            var borrower = form.dataset.borrowerWorkspace === 'true';
            var root = form.dataset.root, error = form.querySelector('[data-application-error]'), requirements = [], lookups = new WeakMap();
            function fail() { error.textContent = error.dataset.errorText; error.hidden = false; }
            function valueLabel(value){var node=form.querySelector('[data-application-value="'+value+'"]');return node?node.textContent:String(value);}
            function panel(name) {
                form.querySelectorAll('[data-application-panel]').forEach(function (p) { p.hidden = p.dataset.applicationPanel !== name; });
                form.querySelectorAll('[data-application-tab]').forEach(function (t) { t.setAttribute('aria-selected', String(t.dataset.applicationTab === name)); t.tabIndex = t.dataset.applicationTab === name ? 0 : -1; });
            }
            form.querySelectorAll('[data-application-tab]').forEach(function (tab) {
                tab.addEventListener('click', function () { panel(tab.dataset.applicationTab); });
                tab.addEventListener('keydown', function (event) { var tabs = Array.from(form.querySelectorAll('[data-application-tab]')), index = tabs.indexOf(tab); if (['ArrowRight','ArrowLeft','Home','End'].includes(event.key)) { event.preventDefault(); var next = tabs[event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length]; next.click(); next.focus(); } });
            });
            panel(form.dataset.initialTab || 'information');
            form.addEventListener('invalid', function (e) { var p = e.target.closest('[data-application-panel]'); if (p) panel(p.dataset.applicationPanel); }, true);
            function reindex(list) {
                var kind = list.dataset.repeatList;
                list.querySelectorAll('[data-repeat-row]').forEach(function (row, i) {
                    row.querySelectorAll('input,select,textarea').forEach(function (input, fieldIndex) {
                        if (input.dataset.field) input.name = kind + '[' + i + '].' + input.dataset.field;
                        else if (input.dataset.documentField) input.name = input.dataset.documentField + '[' + i + ']';
                        else if (input.name && kind !== 'documents') input.name = input.name.replace(/\[\d+\]/, '[' + i + ']');
                        var label = input.labels?.[0] || input.parentElement.querySelector('label');
                        input.id = 'application-' + kind + '-' + i + '-' + fieldIndex; if (label && input.type !== 'search') label.htmlFor = input.id;
                    });
                });
            }
            function requiredOptions() {
                form.querySelectorAll('[data-document-field="requiredAttachmentIds"]').forEach(function (select) {
                    var old = select.value; while (select.options.length > 1) select.remove(1);
                    requirements.forEach(function (r) { select.add(new Option(r.name + ' (' + r.maxSizeMb + ' MB)', r.id)); }); select.value = old;
                });
            }
            form.querySelectorAll('[data-add-row]').forEach(function (button) { button.addEventListener('click', function () {
                var kind = button.dataset.addRow, list = form.querySelector('[data-repeat-list="' + kind + '"]');
                if (list.children.length >= (kind === 'documents' ? 10 : 20)) return;
                list.appendChild(form.querySelector('[data-row-template="' + kind + '"]').content.cloneNode(true)); reindex(list); requiredOptions(); bindChoices(list.lastElementChild);
                var first = list.lastElementChild.querySelector('.neo-select-button,input:not([type=hidden]):not(.neo-select-native),select:not(.neo-select-native),textarea'); if (first) first.focus();
            }); });
            form.querySelectorAll('[data-repeat-list]').forEach(reindex);
            form.addEventListener('click', function (e) { var remove = e.target.closest('[data-remove-row]'); if (remove) { var list = remove.closest('[data-repeat-list]'); remove.closest('[data-repeat-row]').remove(); reindex(list); form.dispatchEvent(new Event('change', {bubbles:true})); } });
            function bindChoices(scope) {
                scope.querySelectorAll('[data-application-choice]').forEach(function (select) {
                    if (lookups.has(select)) return;
                    lookups.set(select, window.VoucherSelect.bind(select, {
                        field: select,
                        label: function () { return select.selectedOptions[0]?.textContent || ''; },
                        url: function () {
                            var endpoint = new URL(root + '/choices/' + select.dataset.applicationChoice, location.origin);
                            var client = form.elements.namedItem('clientId')?.value;
                            if (client) endpoint.searchParams.set('clientId', client);
                            return endpoint.href;
                        },
                        choice: function (item) { return {value:String(item.id),label:item.label}; },
                        changed: function () {},
                        error: error.dataset.errorText
                    }));
                });
            }
            bindChoices(form);
            form.addEventListener('change', function (event) {
                if (event.target.name === 'clientId') form.querySelectorAll('[data-application-choice="guarantors"]').forEach(function (select) { lookups.get(select)?.cancel(); });
            });
            var standard = new Set(['purpose','business','monthlyIncome','monthlyExpenses','otherDebtRepayments','collateralDescription','collateralEstimatedValue']);
            var productRequest = 0;
            async function product(changed) {
                var id = form.elements.namedItem('productId').value, sequence = ++productRequest; if (!id) return;
                try {
                    var response = await fetch(root + '/product/' + encodeURIComponent(id), {headers:{Accept:'application/json'}}); if (!response.ok) throw new Error();
                    var data = await response.json(); if (sequence !== productRequest) return;
                    requirements = data.requirements; requiredOptions();
                    var summary=form.querySelector('[data-product-summary]');summary.textContent=summary.dataset.prefix+': '+data.product.minimumTenure+'-'+data.product.maximumTenure+' '+valueLabel(data.product.tenureUnit)+' | '+valueLabel(data.product.frequency)+' | '+summary.dataset.guarantors+': '+data.product.guarantors;
                    form.querySelector('[data-required-documents]').textContent = requirements.map(function (r) { return r.name + ' (' + r.maxSizeMb + ' MB)'; }).join('; ');
                    if (changed) {
                        var fields = form.querySelector('[data-product-fields]'); fields.replaceChildren();
                        data.fields.filter(function (f) { return !standard.has(f.name); }).forEach(function (field) {
                            var wrap = document.createElement('div'), label = document.createElement('label'), input;
                            if (field.enumOptions && field.enumOptions.length) { input=document.createElement('select'); input.add(new Option('','')); field.enumOptions.forEach(function (v) {input.add(new Option(v,v));}); }
                            else if(field.type==='boolean'){input=document.createElement('select');input.add(new Option('',''));input.add(new Option(valueLabel('true'),'true'));input.add(new Option(valueLabel('false'),'false'));}else if(field.type==='textarea'){input=document.createElement('textarea');input.rows=2;input.maxLength=1000;}else {input=document.createElement('input');input.type=field.type==='number'||field.type==='integer'?'number':field.type==='date'?'date':'text'; input.maxLength=1000; if(input.type==='number'){input.step=field.type==='integer'?'1':'0.01';if(field.min!==null)input.min=field.min;if(field.max!==null)input.max=field.max;} }
                            input.className='aws-control'; input.name='fields['+field.name+']'; input.id='product-field-'+fields.children.length;label.htmlFor=input.id;label.textContent=(field.label||field.name)+(field.required?' *':'');wrap.append(label,input);fields.append(wrap);
                        });
                        form.elements.namedItem('tenureUnit').value = data.product.tenureUnit;
                        form.elements.namedItem('tenureValue').value = data.product.minimumTenure;
                    }
                } catch (_) { fail(); }
            }
            if (!borrower) {
                form.elements.namedItem('productId').addEventListener('change', function () { product(true); });
                product(false);
            }

            var quoteButton = form.querySelector('[data-application-quote]');
            var quoteOutput = form.querySelector('[data-quote-output]');
            var quoteStatus = form.querySelector('[data-quote-status]');
            var estimate = form.querySelector('[data-repayment-estimate]');
            var estimateRows = form.querySelector('[data-repayment-estimate-rows]');
            var quoteRequest = 0;
            var snapshot = form.elements.namedItem('financialSnapshotJson');
            function clearQuote() {
                quoteRequest++;
                quoteOutput.replaceChildren();
                estimate.hidden = true;
                estimateRows.replaceChildren();
                quoteStatus.hidden = true;
                if (snapshot) snapshot.value = '';
            }
            form.addEventListener('input', function (event) {
                if (event.target.closest('[data-application-panel="information"],[data-application-panel="collateral"]')
                    && event.target.name) clearQuote();
            });
            form.addEventListener('change', function (event) {
                if (event.target.closest('[data-application-panel="information"],[data-application-panel="collateral"]')
                    || event.target === form) clearQuote();
            });
            function renderQuote(quote) {
                form.querySelectorAll('[data-quote-label]').forEach(function (label) {
                    var key = label.dataset.quoteLabel, value = quote[key];
                    if (value == null) return;
                    var entry = document.createElement('div'), dt = document.createElement('dt'), dd = document.createElement('dd');
                    dt.textContent = label.textContent;
                    dd.textContent = key === 'interestRate'
                        ? new Intl.NumberFormat(document.documentElement.lang, {style:'percent',maximumFractionDigits:4}).format(Number(value))
                        : valueLabel(value);
                    entry.append(dt, dd);
                    quoteOutput.append(entry);
                });
            }
            function renderSchedule(rows) {
                rows.forEach(function (row) {
                    var tr = estimateRows.insertRow();
                    ['pmtNo','month','beginningBalance','payment','loanAmount','interest','endingBalance'].forEach(function (key) {
                        tr.insertCell().textContent = row[key];
                    });
                });
                estimate.hidden = rows.length === 0;
            }
            async function loadQuote() {
                error.hidden = true;
                clearQuote();
                var sequence = quoteRequest;
                var data = new URLSearchParams();
                var quoteFields = new Set([form.dataset.csrfName,'requestKey','applicationId','clientId','productId','loanProductId',
                    'loanType','topUpLoanId','applicationDate','amount','tenureValue','tenureUnit','income','expenses','otherDebt',
                    'purpose','business','useOfFunds','collateralDescription','collateralEstimatedValue']);
                form.querySelectorAll('[data-product-field]').forEach(function (field) { quoteFields.add(field.name); });
                new FormData(form).forEach(function (value, key) {
                    if (typeof value === 'string' && (quoteFields.has(key) || key.startsWith('fields['))) data.append(key, value);
                });
                quoteButton.disabled = true;
                quoteButton.setAttribute('aria-busy', 'true');
                try {
                    var response = await fetch(root + (borrower ? '/financial-preview' : '/quote'), {
                        method:'POST', body:data, headers:{Accept:'application/json'}
                    });
                    var payload = await response.json().catch(function () { return {}; });
                    if (sequence !== quoteRequest) return;
                    if (!response.ok) throw new Error(payload.message || error.dataset.errorText);
                    renderQuote(borrower ? JSON.parse(payload.snapshotJson) : payload);
                    renderSchedule(payload.repaymentSchedule || []);
                    if (borrower) {
                        snapshot.value = payload.snapshotJson || '';
                        quoteStatus.textContent = payload.message;
                        quoteStatus.hidden = false;
                    }
                } catch (failure) {
                    if (sequence === quoteRequest) {
                        quoteStatus.textContent = failure.message === 'Failed to fetch' ? error.dataset.errorText : failure.message;
                        quoteStatus.hidden = false;
                    }
                } finally {
                    quoteButton.disabled = false;
                    quoteButton.removeAttribute('aria-busy');
                }
            }
            quoteButton.addEventListener('click', loadQuote);

            if (borrower) {
                var serverError = form.querySelector('[data-application-server-error]');
                if (snapshot.value && !serverError) void loadQuote();
                if (serverError && form.dataset.initialTab === 'information') serverError.focus();
                var purpose = form.elements.namedItem('purpose');
                purpose.setAttribute('aria-describedby', 'application-purpose-help');
                purpose.addEventListener('input', function () {
                    var start = purpose.selectionStart, end = purpose.selectionEnd;
                    purpose.value = purpose.value.toUpperCase();
                    purpose.setSelectionRange(start, end);
                    purpose.setCustomValidity(purpose.value.trim().split(/\s+/).length > 10 ? form.dataset.purposeError : '');
                });
                var terms = form.elements.namedItem('termsAccepted');
                form.addEventListener('submit', function (event) {
                    delete form.dataset.confirmMessage;
                    if (event.defaultPrevented) return;
                    if (event.submitter?.value !== 'SEND_TO_GUARANTORS') return;
                    if (!terms.checked) {
                        event.preventDefault();
                        terms.required = true;
                        terms.reportValidity();
                        terms.required = false;
                        return;
                    }
                    form.dataset.confirmMessage = form.dataset.submitConfirmation;
                    form.dataset.confirmTitle = event.submitter.textContent.trim();
                    form.dataset.confirmProceed = form.dataset.confirmTitle;
                });
                bindSignatureOtp(form, root);
            }
        });
    }
    function bindSignatureOtp(form, root) {
        var button = form.querySelector('#requestApplicantSignatureOtpButton');
        if (!button) return;
        var input = form.elements.namedItem('applicantSignatureOtpCode');
        var feedback = form.querySelector('#applicantSignatureOtpFeedback');
        var status = form.querySelector('#applicantSignatureOtpLiveStatus');
        var statusText = status.querySelector('[data-otp-text]');
        var spinner = status.querySelector('[data-otp-spinner]');
        var tick = status.querySelector('[data-otp-tick]');
        var idle = button.querySelector('.otp-button-label').textContent;
        var checking = statusText.textContent;
        var errorText = form.querySelector('[data-application-error]').dataset.errorText;
        var version = 0;

        async function post(endpoint, otpCode) {
            var data = new URLSearchParams({
                [form.dataset.csrfName]: form.elements.namedItem(form.dataset.csrfName).value,
                applicationId: form.elements.namedItem('applicationId').value
            });
            if (otpCode !== undefined) data.set('otpCode', otpCode);
            var response = await fetch(root + endpoint, {method:'POST',body:data,headers:{Accept:'application/json'}});
            var payload = await response.json();
            if (!response.ok || payload.valid === false) throw new Error(payload.message || errorText);
            return payload;
        }
        button.addEventListener('click', async function () {
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
            button.querySelector('.otp-button-spinner').classList.remove('hidden');
            var cooldownStarted = false;
            try {
                var payload = await post('/request-signature-otp');
                feedback.textContent = payload.message;
                if (window.SaccosOtp) {
                    window.SaccosOtp.startCooldown(button, payload, {idle:idle});
                    cooldownStarted = true;
                    window.SaccosOtp.focusBoxes(input);
                } else {
                    input.focus();
                }
            } catch (failure) {
                feedback.textContent = failure.message || errorText;
            } finally {
                feedback.classList.remove('hidden');
                button.querySelector('.otp-button-spinner').classList.add('hidden');
                button.removeAttribute('aria-busy');
                if (!cooldownStarted) button.disabled = false;
            }
        });
        input.addEventListener('input', async function () {
            var code = input.value, request = ++version;
            status.classList.toggle('hidden', !/^\d{6}$/.test(code));
            if (status.classList.contains('hidden')) return;
            spinner.classList.remove('hidden');
            tick.classList.add('hidden');
            statusText.textContent = checking;
            try {
                var payload = await post('/verify-signature-otp', code);
                if (request !== version) return;
                statusText.textContent = payload.message;
                tick.classList.remove('hidden');
            } catch (failure) {
                if (request === version) statusText.textContent = failure.message || errorText;
            } finally {
                if (request === version) spinner.classList.add('hidden');
            }
        });
    }
    if(window.WorkspaceNavigation&&window.WorkspaceNavigation.onReady)window.WorkspaceNavigation.onReady(init);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
