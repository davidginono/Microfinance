(function () {
    'use strict';
    function initWizard(root, form) {
        const nav = root.querySelector('[data-recording-steps]');
        const buttons = [...nav.querySelectorAll('[data-recording-step-button]')];
        const panels = buttons.map(button => document.getElementById(button.getAttribute('aria-controls')));
        const previous = form.querySelector('[data-recording-previous]');
        const next = form.querySelector('[data-recording-next]');
        const submit = form.querySelector('[data-recording-submit]');
        const last = panels.length - 1;
        let active = 0, reached = 0;

        function show(index, focus = true) {
            active = index;
            reached = Math.max(reached, index);
            panels.forEach((panel, i) => { panel.hidden = i !== index; });
            buttons.forEach((button, i) => {
                button.disabled = i > reached;
                if (i === index) button.setAttribute('aria-current', 'step');
                else button.removeAttribute('aria-current');
            });
            previous.hidden = index === 0;
            next.hidden = index === last;
            submit.hidden = index !== last;
            if (focus) {
                const heading = panels[index].querySelector('[data-recording-step-heading]');
                heading.focus({preventScroll:true});
                heading.scrollIntoView?.({block:'start'});
            }
        }

        function go(index) {
            for (let step = active; step < index; step++) {
                const invalid = [...panels[step].querySelectorAll('input,select,textarea')]
                    .filter(control => control.willValidate && !control.validity.valid);
                if (invalid.length) {
                    show(step, false);
                    invalid.forEach(control => control.checkValidity());
                    return;
                }
            }
            show(index);
        }

        buttons.forEach((button, index) => button.addEventListener('click', () => go(index)));
        panels.forEach((panel, index) => panel.addEventListener('validation:reveal', () => show(index, false)));
        previous.addEventListener('click', () => go(active - 1));
        next.addEventListener('click', () => go(active + 1));
        form.addEventListener('keydown', event => {
            if (event.key !== 'Enter' || event.isComposing || event.defaultPrevented
                || !panels[active].contains(event.target)
                || !event.target.matches('input:not([type="hidden"],[type="checkbox"],[type="radio"],[type="file"],[type="button"],[type="submit"])')) return;
            event.preventDefault();
            if (active === last) form.requestSubmit(submit);
            else go(active + 1);
        });
        form.addEventListener('submit', event => {
            if (event.submitter !== submit || active === last) return;
            event.preventDefault();
            go(active + 1);
        });
        show(0, false);
        nav.hidden = false;
        root.classList.add('recording-wizard-ready');
    }

    function init() {
        document.querySelectorAll('[data-recording-evidence]:not([data-evidence-ready])').forEach(function (root) {
            root.dataset.evidenceReady = 'true';
            var form = root.closest('form'), endpoint = root.dataset.root;
            var clientSelect = form.querySelector('select[name="clientId"]');
            var error = root.querySelector('[data-evidence-error]'), requirements = [], productVersion = 0, nextFieldId = 0;
            function fail() { error.textContent = root.dataset.error; error.hidden = false; }
            initWizard(root, form);
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
