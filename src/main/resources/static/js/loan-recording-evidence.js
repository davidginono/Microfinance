(function () {
    'use strict';
    function init() {
        document.querySelectorAll('[data-recording-evidence]:not([data-evidence-ready])').forEach(function (root) {
            root.dataset.evidenceReady = 'true';
            var form = root.closest('form'), endpoint = root.dataset.root;
            var clientSelect = form.querySelector('select[name="clientId"]');
            var error = root.querySelector('[data-evidence-error]'), requirements = [], productVersion = 0, nextFieldId = 0;
            function fail() { error.textContent = root.dataset.error; error.hidden = false; }
            var tabs = [...root.querySelectorAll('[role="tab"]')];
            function selectTab(selected) {
                tabs.forEach(function (tab) {
                    var active = tab === selected;
                    tab.setAttribute('aria-selected', String(active));
                    tab.tabIndex = active ? 0 : -1;
                    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
                });
            }
            tabs.forEach(function (tab, index) {
                tab.addEventListener('click', function () { selectTab(tab); });
                tab.addEventListener('keydown', function (event) {
                    var next = {ArrowLeft: (index + tabs.length - 1) % tabs.length, ArrowRight: (index + 1) % tabs.length, Home: 0, End: tabs.length - 1}[event.key];
                    if (next == null) return;
                    event.preventDefault(); selectTab(tabs[next]); tabs[next].focus();
                });
            });
            selectTab(tabs[0]);
            function reindex(list) {
                list.querySelectorAll('[data-repeat-row]').forEach(function (row, index) {
                    row.querySelectorAll('input,select,textarea').forEach(function (control) {
                        if (control.dataset.field) control.name = 'applicationDetails.' + list.dataset.repeatList + '[' + index + '].' + control.dataset.field;
                        else if (control.name) control.name = control.name.replace(/\[\d+\]/, '[' + index + ']');
                        if (!control.id) control.id = 'recording-evidence-control-' + (++nextFieldId);
                        var label = control.labels[0] || control.parentElement.querySelector('label');
                        if (label) label.htmlFor = control.id;
                    });
                });
            }
            root.querySelectorAll('[data-repeat-list]').forEach(reindex);
            root.querySelectorAll('[data-add-row]').forEach(function (button) {
                var kind = button.dataset.addRow, list = root.querySelector('[data-repeat-list="' + kind + '"]');
                button.disabled = list.children.length >= 20;
                button.addEventListener('click', function () {
                    if (list.children.length >= 20) return;
                    list.append(root.querySelector('[data-row-template="' + kind + '"]').content.cloneNode(true));
                    reindex(list);
                    list.lastElementChild.dispatchEvent(new Event('loan:choices-added',{bubbles:true}));
                    (list.lastElementChild.querySelector('.neo-select-button') || list.lastElementChild.querySelector('input,select'))?.focus();
                    button.disabled = list.children.length >= 20;
                    form.dispatchEvent(new Event('change',{bubbles:true}));
                });
            });
            root.addEventListener('click', function (event) {
                var button = event.target.closest('[data-remove-row]');
                if (!button) return;
                var list = button.closest('[data-repeat-list]'), row = button.closest('[data-repeat-row]');
                var next = row.nextElementSibling || row.previousElementSibling;
                row.remove(); reindex(list);
                var add = root.querySelector('[data-add-row="' + list.dataset.repeatList + '"]');
                add.disabled = false;
                (next?.querySelector('[data-remove-row]') || add).focus();
                form.dispatchEvent(new Event('change',{bubbles:true}));
            });
            function documentRequirements() {
                root.querySelectorAll('[data-document-field="requiredAttachmentIds"]').forEach(function (select) {
                    var value = select.value; while (select.options.length > 1) select.remove(1);
                    requirements.forEach(function (item) { select.add(new Option(item.name + ' (' + item.maxSizeMb + ' MB)',item.id)); });
                    select.value = requirements.some(function (item) { return item.id === value; }) ? value : '';
                });
            }
            var files = form.querySelector('input[type="file"][name="attachments"]');
            var uploads = [], documentList = root.querySelector('[data-evidence-documents]');
            function syncFiles() {
                var selection = new DataTransfer();
                uploads.forEach(function (upload) { selection.items.add(upload.file); });
                files.files = selection.files;
                files.setCustomValidity(uploads.length > 10 ? root.dataset.uploadLimit : '');
            }
            files.addEventListener('change', function () {
                Array.from(files.files).forEach(function (file) {
                    if (uploads.some(function (upload) { return upload.file.name === file.name && upload.file.size === file.size && upload.file.lastModified === file.lastModified; })) return;
                    documentList.append(root.querySelector('[data-evidence-document-template]').content.cloneNode(true));
                    var row = documentList.lastElementChild; row.querySelector('[data-evidence-filename]').textContent = file.name;
                    row.querySelector('[data-document-field="documentNames"]').value = file.name.slice(0,120);
                    row.querySelector('[data-document-field="documentTypes"]').value = 'OTHER';
                    var upload = {file: file, row: row}; uploads.push(upload);
                    row.querySelector('[data-remove-upload]').addEventListener('click', function () {
                        var index = uploads.indexOf(upload);
                        uploads.splice(index,1); row.remove(); bindUploads();
                        (uploads[Math.min(index, uploads.length - 1)]?.row.querySelector('[data-remove-upload]') || files).focus();
                        files.dispatchEvent(new Event('input',{bubbles:true}));
                    });
                });
                bindUploads(); documentRequirements();
            });
            function bindUploads() {
                uploads.forEach(function (upload,index) {
                    upload.row.querySelectorAll('[data-document-field]').forEach(function (control) {
                        control.name = 'applicationDetails.' + control.dataset.documentField + '[' + index + ']';
                        if (!control.id) control.id = 'recording-document-' + (++nextFieldId);
                        control.parentElement.htmlFor = control.id;
                    });
                });
                syncFiles();
            }
            async function product(changed) {
                var id = form.elements.productId.value, sequence = ++productVersion;
                var draft = form.querySelector('[data-recording-draft]'); if (draft) draft.disabled = form.elements.loanMode.value !== 'CONFIGURED' || clientSelect?.selectedOptions[0]?.dataset.clientKind !== 'PROVISIONAL';
                error.hidden = true;
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
                            control = document.createElement('select'); control.add(new Option(root.dataset.selectPlaceholder,''));
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
                        control.className = 'aws-control'; control.maxLength = 1000; control.required = field.required;
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
            if (form.querySelector('[data-workspace-response-error]')) {
                window.FormValidation?.refresh(form);
                window.FormValidation?.focusFirst(form);
            }
        });
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(init);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
    else init();
})();
