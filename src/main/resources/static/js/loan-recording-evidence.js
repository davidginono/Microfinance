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
        const guarantors = panels.find(panel => panel.querySelector('[data-repeat-list="guarantors"]'));
        let active = 0, reached = 0, requiredGuarantors = null;

        function skipped(index) {
            return !!guarantors && panels[index] === guarantors && form.elements.loanFor?.value === 'GROUP'
                && (form.elements.loanMode.value === 'CUSTOM' || requiredGuarantors === 0);
        }

        function show(index, focus = true) {
            active = index;
            reached = Math.max(reached, index);
            panels.forEach((panel, i) => { panel.hidden = i !== index; });
            let number = 0;
            buttons.forEach((button, i) => {
                const omit = skipped(i);
                (button.closest('li') || button).hidden = omit;
                const counter = button.querySelector('.recording-step-number');
                if (counter && !omit) counter.textContent = ++number;
                button.disabled = omit || i > reached;
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
            if (index < 0 || index > last || skipped(index)) return;
            for (let step = active; step < index; step++) {
                if (skipped(step)) continue;
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

        function move(direction) {
            let index = active + direction;
            while (skipped(index)) index += direction;
            go(index);
        }

        function updateSteps() {
            if (guarantors) {
                const omit = skipped(panels.indexOf(guarantors));
                guarantors.disabled = omit;
                guarantors.dataset.recordingSkipped = String(omit);
            }
            root.querySelectorAll('[data-guarantor-label]').forEach(label => {
                label.textContent = form.elements.loanFor?.value === 'GROUP' ? label.dataset.groupLabel : label.dataset.individualLabel;
            });
            if (skipped(active)) show(active + 1);
            else show(active, false);
        }

        buttons.forEach((button, index) => button.addEventListener('click', () => go(index)));
        panels.forEach((panel, index) => panel.addEventListener('validation:reveal', () => {if (!skipped(index)) show(index, false);}));
        previous.addEventListener('click', () => move(-1));
        next.addEventListener('click', () => move(1));
        form.addEventListener('recording:product', event => {
            const count = event.detail?.guarantors;
            requiredGuarantors = Number.isInteger(count) && count >= 0 ? count : null;
            updateSteps();
        });
        form.addEventListener('change', event => {
            if (['loanFor','loanMode','productId'].includes(event.target.name)) updateSteps();
        });
        form.addEventListener('keydown', event => {
            if (event.key !== 'Enter' || event.isComposing || event.defaultPrevented
                || !panels[active].contains(event.target)
                || !event.target.matches('input:not([type="hidden"],[type="checkbox"],[type="radio"],[type="file"],[type="button"],[type="submit"])')) return;
            event.preventDefault();
            if (active === last) form.requestSubmit(submit);
            else move(1);
        });
        form.addEventListener('submit', event => {
            if (event.submitter !== submit || active === last) return;
            event.preventDefault();
            move(1);
        });
        updateSteps();
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
                var index = 0;
                list.querySelectorAll('[data-repeat-row]').forEach(function (row) {
                    var controls = [...row.querySelectorAll('input,select,textarea')].filter(control =>
                        !control.matches('[data-document-field],[data-row-photos]') && (control.dataset.field || /^applicationDetails\./.test(control.name)));
                    var populated = controls.some(control => control.value.trim()) || !!row.querySelector('[data-row-photo-list]')?.children.length;
                    controls.forEach(function (control) {
                        if (!control.dataset.field) control.dataset.field = control.name.substring(control.name.lastIndexOf('.') + 1);
                        if (!('recordingRequired' in control.dataset)) control.dataset.recordingRequired = String(control.required);
                        control.required = populated && control.dataset.recordingRequired === 'true';
                        if (populated) control.name = 'applicationDetails.' + list.dataset.repeatList + '[' + index + '].' + control.dataset.field;
                        else control.removeAttribute('name');
                        if (!control.id) control.id = 'recording-evidence-control-' + (++nextFieldId);
                        var label = control.labels[0] || control.parentElement.querySelector('label');
                        if (label) label.htmlFor = control.id;
                    });
                    if (populated) index++;
                });
            }
            function ensurePreview(list) {
                if (list.children.length) return;
                list.append(root.querySelector('[data-row-template="' + list.dataset.repeatList + '"]').content.cloneNode(true));
                reindex(list);
            }
            function placeAdd(list, button) {
                var actions = list.lastElementChild?.querySelector('.recording-item-actions');
                if (actions) actions.append(button);
                else list.after(button);
            }
            root.querySelectorAll('[data-repeat-list]').forEach(function (list) { ensurePreview(list); reindex(list); });
            ['input','change'].forEach(type => root.addEventListener(type, function (event) {
                var list = event.target.closest('[data-repeat-list]');
                if (list) reindex(list);
            }));
            root.querySelectorAll('[data-add-row]').forEach(function (button) {
                var kind = button.dataset.addRow, list = root.querySelector('[data-repeat-list="' + kind + '"]');
                button.disabled = list.children.length >= 20;
                placeAdd(list, button);
                button.addEventListener('click', function () {
                    if (list.children.length >= 20) return;
                    list.append(root.querySelector('[data-row-template="' + kind + '"]').content.cloneNode(true));
                    reindex(list); placeAdd(list, button);
                    list.lastElementChild.querySelector('input,select')?.focus();
                    button.disabled = list.children.length >= 20;
                    form.dispatchEvent(new Event('change',{bubbles:true}));
                });
            });
            root.addEventListener('click', function (event) {
                var button = event.target.closest('[data-remove-row]');
                if (!button) return;
                var list = button.closest('[data-repeat-list]'), row = button.closest('[data-repeat-row]');
                var next = row.nextElementSibling || row.previousElementSibling;
                var add = root.querySelector('[data-add-row="' + list.dataset.repeatList + '"]');
                list.after(add);
                uploads.filter(upload => row.contains(upload.row)).forEach(removeUpload);
                row.remove(); ensurePreview(list); reindex(list); bindUploads();
                placeAdd(list, add);
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
            function activeUploads() {
                return uploads.filter(upload => !upload.owner?.closest('[data-recording-skipped="true"]'));
            }
            function syncFiles() {
                var selection = new DataTransfer();
                activeUploads().forEach(function (upload) { selection.items.add(upload.file); });
                files.files = selection.files;
                files.setCustomValidity(activeUploads().length > 10 ? root.dataset.uploadLimit : '');
            }
            function removeUpload(upload) {
                uploads.splice(uploads.indexOf(upload),1);
                if (upload.preview) URL.revokeObjectURL(upload.preview);
                upload.row.remove();
            }
            function addUploads(selection, owner) {
                var photo = !!owner, target = photo ? owner.querySelector('[data-row-photo-list]') : documentList;
                var picker = photo ? owner.querySelector('[data-row-photos]') : files;
                var kind = photo ? owner.closest('[data-repeat-list]').dataset.repeatList : '';
                var type = kind === 'guarantors' ? 'GUARANTOR_PHOTO' : 'COLLATERAL_PHOTO';
                var selected = Array.from(selection);
                var message = '';
                if (activeUploads().length + selected.length > 10) message = root.dataset.uploadLimit;
                if (photo && selected.some(file => !/\.(png|jpe?g)$/i.test(file.name) || !['image/png','image/jpeg'].includes(file.type) || file.size > 5*1024*1024))
                    message = owner.querySelector('.attachment-dropzone-help').textContent;
                var localError = picker.closest('.recording-row-photos')?.querySelector('[data-photo-error]');
                if (message) {
                    if (photo && !localError) {
                        localError = document.createElement('p'); localError.dataset.photoError = ''; localError.className = 'coa-error';
                        localError.setAttribute('role','alert'); picker.closest('.recording-row-photos').append(localError);
                    }
                    if (localError) localError.textContent = message;
                    else { error.textContent = message; error.hidden = false; }
                    syncFiles(); return;
                }
                localError?.remove();
                selected.forEach(function (file) {
                    if (uploads.some(upload => upload.owner === owner && upload.file.name === file.name && upload.file.size === file.size && upload.file.lastModified === file.lastModified)) return;
                    target.append(root.querySelector('[data-evidence-document-template]').content.cloneNode(true));
                    var row = target.lastElementChild;
                    row.querySelector('[data-evidence-filename]').textContent = file.name;
                    var title = row.querySelector('[data-document-field="documentNames"]');
                    var subject = photo ? (kind === 'guarantors' ? owner.querySelector('[data-field="fullName"]')?.value : owner.querySelector('[name$=".itemName"]')?.value) : '';
                    title.value = ((subject ? subject.trim() + ' - ' : '') + file.name).slice(0,120);
                    row.querySelector('[data-document-field="documentTypes"]').value = photo ? type : 'OTHER';
                    var upload = {file:file, row:row, owner:owner}; uploads.push(upload);
                    if (photo) {
                        row.classList.add('recording-photo-token');
                        row.querySelectorAll('[data-document-field]').forEach(control => { control.closest('label').hidden = true; });
                        var size = document.createElement('span'); size.className = 'recording-photo-size';
                        size.textContent = new Intl.NumberFormat(document.documentElement.lang || 'en',{maximumFractionDigits:1}).format(file.size/1024) + ' KB';
                        row.querySelector('[data-evidence-filename]').append(size);
                        if (URL.createObjectURL) {
                            upload.preview = URL.createObjectURL(file);
                            var image = document.createElement('img'); image.src = upload.preview; image.alt = ''; image.className = 'recording-photo-thumbnail';
                            row.prepend(image);
                        }
                    }
                    var remove = row.querySelector('[data-remove-upload]');
                    remove.setAttribute('aria-label',(remove.dataset.removeLabel || remove.textContent.trim()) + ' ' + file.name);
                    remove.addEventListener('click', function () {
                        removeUpload(upload); bindUploads(); picker.focus();
                        files.dispatchEvent(new Event('input',{bubbles:true}));
                    });
                });
                bindUploads(); documentRequirements();
                form.dispatchEvent(new Event('change',{bubbles:true}));
            }
            files.addEventListener('change', function () { addUploads(files.files,null); });
            root.addEventListener('change', function (event) {
                if (!event.target.matches('[data-row-photos]')) return;
                addUploads(event.target.files,event.target.closest('[data-repeat-row]'));
                event.target.value = '';
            });
            root.addEventListener('dragover', function (event) {
                if (event.target.closest('.recording-row-photos .attachment-dropzone')) event.preventDefault();
            });
            root.addEventListener('drop', function (event) {
                var zone = event.target.closest('.recording-row-photos .attachment-dropzone');
                if (!zone) return;
                event.preventDefault(); addUploads(event.dataTransfer.files,zone.closest('[data-repeat-row]'));
            });
            window.addEventListener('pagehide', () => uploads.forEach(upload => {if(upload.preview) URL.revokeObjectURL(upload.preview);}), {once:true});
            function bindUploads() {
                root.querySelectorAll('[data-repeat-list]').forEach(reindex);
                activeUploads().forEach(function (upload,index) {
                    if (upload.owner) {
                        var subject = upload.owner.querySelector('[data-field="fullName"],[data-field="itemName"]')?.value;
                        upload.row.querySelector('[data-document-field="documentNames"]').value = ((subject ? subject.trim() + ' - ' : '') + upload.file.name).slice(0,120);
                    }
                    upload.row.querySelectorAll('[data-document-field]').forEach(function (control) {
                        control.name = 'applicationDetails.' + control.dataset.documentField + '[' + index + ']';
                        if (!control.id) control.id = 'recording-document-' + (++nextFieldId);
                        control.parentElement.htmlFor = control.id;
                    });
                });
                syncFiles();
            }
            form.addEventListener('submit', bindUploads);
            form.addEventListener('recording:product', bindUploads);
            form.addEventListener('change', event => {
                if (['loanFor','loanMode'].includes(event.target.name)) bindUploads();
            });
            async function product(changed) {
                var id = form.elements.productId.value, sequence = ++productVersion;
                form.dispatchEvent(new CustomEvent('recording:product',{detail:null}));
                var draft = form.querySelector('[data-recording-draft]'); if (draft) draft.disabled = form.elements.loanFor?.value!=='GROUP'&&(form.elements.loanMode.value !== 'CONFIGURED' || clientSelect?.selectedOptions[0]?.dataset.clientKind !== 'PROVISIONAL');
                error.hidden = true;
                if (form.elements.loanMode.value !== 'CONFIGURED' || !id) {
                    requirements = []; documentRequirements(); root.querySelector('[data-evidence-fields]').replaceChildren();
                    root.querySelector('[data-evidence-requirements]').textContent = ''; return;
                }
                try {
                    var response = await fetch(endpoint + '/product/' + encodeURIComponent(id),{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
                    if (!response.ok || response.redirected) throw new Error();
                    var data = await response.json(); if (sequence !== productVersion) return;
                    form.dispatchEvent(new CustomEvent('recording:product',{detail:data.product}));
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
            clientSelect?.addEventListener('change',function () { var draft=form.querySelector('[data-recording-draft]');if(draft)draft.disabled=form.elements.loanFor?.value!=='GROUP'&&(form.elements.loanMode.value!=='CONFIGURED'||clientSelect?.selectedOptions[0]?.dataset.clientKind!=='PROVISIONAL'); });
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
