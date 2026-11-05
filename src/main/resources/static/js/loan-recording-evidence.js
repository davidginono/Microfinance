(function () {
    'use strict';
    function init() {
        document.querySelectorAll('[data-recording-evidence]:not([data-evidence-ready])').forEach(function (root) {
            root.dataset.evidenceReady = 'true';
            var form = root.closest('form'), endpoint = root.dataset.root;
            var clientSelect = form.querySelector('select[name="clientId"]');
            var error = root.querySelector('[data-evidence-error]'), requirements = [], productVersion = 0;
            function fail() { error.textContent = root.dataset.error; error.hidden = false; }
            function reindex(list) {
                list.querySelectorAll('[data-repeat-row]').forEach(function (row, index) {
                    row.querySelectorAll('input,select,textarea').forEach(function (control, field) {
                        if (control.dataset.field) control.name = 'applicationDetails.' + list.dataset.repeatList + '[' + index + '].' + control.dataset.field;
                        else if (control.name) control.name = control.name.replace(/\[\d+\]/, '[' + index + ']');
                        control.id = 'recording-' + list.dataset.repeatList + '-' + index + '-' + field;
                        var label = control.parentElement.querySelector('label');
                        if (label && control.type !== 'search') label.htmlFor = control.id;
                    });
                });
            }
            function bindChoice(select) {
                if (select.dataset.evidenceChoiceReady) return;
                select.dataset.evidenceChoiceReady = 'true';
                var group = select.closest('[data-evidence-choice-group]');
                var search = group.querySelector('[data-evidence-search]');
                var sequence = 0, controller;
                var binding = window.AppSearch.bind(search, async function () {
                    controller?.abort(); controller = new AbortController();
                    var request = ++sequence;
                    try {
                        var response = await fetch(endpoint + '/choices/' + select.dataset.evidenceChoice + '?' + new URLSearchParams({search:search.value}),
                            {credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:{Accept:'application/json'}});
                        if (!response.ok || response.redirected) throw new Error();
                        var rows = await response.json();
                        if (request !== sequence || !select.isConnected) return;
                        var selected = select.selectedOptions[0]?.cloneNode(true);
                        select.replaceChildren(new Option('', ''));
                        if (selected?.value) select.add(selected);
                        rows.slice(0,25).forEach(function (row) { if (row.id === selected?.value) selected.textContent = row.label; else select.add(new Option(row.label,row.id)); });
                        if (selected) select.value = selected.value;
                        window.refreshEnhancedSelects?.(group);
                    } catch (failure) { if (failure.name !== 'AbortError') fail(); }
                }, function () { ++sequence; controller?.abort(); });
                search.addEventListener('keydown', function (event) { if (event.key === 'Enter') { event.preventDefault(); binding.run(); } });
                binding.run();
            }
            root.querySelectorAll('[data-repeat-list]').forEach(reindex);
            root.querySelectorAll('[data-evidence-choice]').forEach(bindChoice);
            root.querySelectorAll('[data-add-row]').forEach(function (button) {
                button.addEventListener('click', function () {
                    var kind = button.dataset.addRow, list = root.querySelector('[data-repeat-list="' + kind + '"]');
                    if (list.children.length >= 20) return;
                    list.append(root.querySelector('[data-row-template="' + kind + '"]').content.cloneNode(true));
                    reindex(list);
                    list.lastElementChild.querySelectorAll('[data-evidence-choice]').forEach(bindChoice);
                    list.lastElementChild.querySelector('input,select')?.focus();
                    form.dispatchEvent(new Event('change',{bubbles:true}));
                });
            });
            root.addEventListener('click', function (event) {
                var button = event.target.closest('[data-remove-row]');
                if (!button) return;
                var list = button.closest('[data-repeat-list]'); button.closest('[data-repeat-row]').remove(); reindex(list);
                form.dispatchEvent(new Event('change',{bubbles:true}));
            });
            function documentRequirements() {
                root.querySelectorAll('[data-document-field="requiredAttachmentIds"]').forEach(function (select) {
                    var value = select.value; while (select.options.length > 1) select.remove(1);
                    requirements.forEach(function (item) { select.add(new Option(item.name + ' (' + item.maxSizeMb + ' MB)',item.id)); });
                    select.value = value;
                });
            }
            var files = form.querySelector('input[type="file"][name="attachments"]');
            files.addEventListener('change', function () {
                var list = root.querySelector('[data-evidence-documents]'); list.replaceChildren();
                Array.from(files.files).forEach(function (file,index) {
                    list.append(root.querySelector('[data-evidence-document-template]').content.cloneNode(true));
                    var row = list.lastElementChild; row.querySelector('[data-evidence-filename]').textContent = file.name;
                    row.querySelectorAll('[data-document-field]').forEach(function (control) {
                        control.name = 'applicationDetails.' + control.dataset.documentField + '[' + index + ']';
                        control.id = 'recording-document-' + index + '-' + control.dataset.documentField;
                        control.parentElement.htmlFor = control.id;
                    });
                    row.querySelector('[data-document-field="documentNames"]').value = file.name.slice(0,120);
                    row.querySelector('[data-document-field="documentTypes"]').value = 'OTHER';
                });
                documentRequirements();
            });
            async function product(changed) {
                var id = form.elements.productId.value, sequence = ++productVersion;
                var draft = form.querySelector('[data-recording-draft]'); if (draft) draft.disabled = form.elements.loanMode.value !== 'CONFIGURED' || clientSelect?.selectedOptions[0]?.dataset.clientKind !== 'PROVISIONAL';
                if (form.elements.loanMode.value !== 'CONFIGURED' || !id) {
                    requirements = []; documentRequirements(); root.querySelector('[data-evidence-fields]').replaceChildren();
                    root.querySelector('[data-evidence-requirements]').textContent = ''; return;
                }
                try {
                    var response = await fetch(endpoint + '/product/' + encodeURIComponent(id),{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
                    if (!response.ok || response.redirected) throw new Error();
                    var data = await response.json(); if (sequence !== productVersion) return;
                    requirements = data.requirements; documentRequirements();
                    root.querySelector('[data-evidence-requirements]').textContent = requirements.map(function (item) { return item.name + ' (' + item.maxSizeMb + ' MB)'; }).join(', ');
                    if (!changed) return;
                    var fields = root.querySelector('[data-evidence-fields]'); fields.replaceChildren();
                    data.fields.forEach(function (field,index) {
                        var wrap = document.createElement('div'), label = document.createElement('label'), control;
                        if (field.type === 'boolean' || field.enumOptions?.length) {
                            control = document.createElement('select'); control.add(new Option('',''));
                            if (field.type === 'boolean') {
                                control.add(new Option(root.querySelector('[data-evidence-yes]').textContent,'true'));
                                control.add(new Option(root.querySelector('[data-evidence-no]').textContent,'false'));
                            } else field.enumOptions.forEach(function (value) { control.add(new Option(value,value)); });
                        } else if (field.type === 'textarea') { control = document.createElement('textarea'); control.rows = 2; }
                        else {
                            control = document.createElement('input');
                            control.type = ['number','integer'].includes(field.type) ? 'number' : field.type === 'date' ? 'date' : 'text';
                            control.step = field.type === 'integer' ? '1' : '0.01';
                        }
                        control.name = 'applicationDetails.fields[' + field.name + ']'; control.id = 'recording-field-' + index;
                        control.className = 'aws-control'; control.maxLength = 1000;
                        label.htmlFor = control.id; label.textContent = field.label + (field.required ? ' *' : '');
                        wrap.append(label,control); fields.append(wrap);
                    });
                } catch (_) { if (sequence === productVersion) fail(); }
            }
            clientSelect?.addEventListener('change',function () { var draft=form.querySelector('[data-recording-draft]');if(draft)draft.disabled=form.elements.loanMode.value!=='CONFIGURED'||clientSelect?.selectedOptions[0]?.dataset.clientKind!=='PROVISIONAL'; });
            form.elements.productId.addEventListener('change',function () { product(true); });
            form.elements.loanMode.addEventListener('change',function () { product(true); });
            form.addEventListener('loan:quote',function (event) {
                var fees = form.querySelector('[data-intake-fees]'); if (!fees) return;
                var amounts = event.detail.applicationFees; fees.hidden = !amounts;
                if (amounts) fees.querySelectorAll('[data-intake-fee]').forEach(function (field) {
                    var amount = amounts[field.dataset.intakeFee];
                    field.textContent = amount == null ? '' : new Intl.NumberFormat(document.documentElement.lang,{minimumFractionDigits:2,maximumFractionDigits:2}).format(amount);
                });
            });
            form.addEventListener('loan:quote-reset',function () { var fees=form.querySelector('[data-intake-fees]');if(fees)fees.hidden=true; });
            product(false);
        });
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(init);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
    else init();
})();
