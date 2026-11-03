/* Upload files without leaving or saving the live client profile. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', fn, {once:true}) : fn());
    ready(() => document.querySelectorAll('form[data-onboarding-form="self"]').forEach(profile => {
        if (typeof HTMLDialogElement === 'undefined' || !HTMLDialogElement.prototype.showModal) return;
        const link = profile.querySelector('[data-profile-add-documents]'), labels = profile.querySelector('[data-profile-wizard-labels]')?.dataset;
        const notice = profile.querySelector('[data-profile-confirmations]'), declaration = profile.querySelector('[data-profile-declaration]');
        if (!labels || !notice || !declaration || profile.dataset.uploadReady) return;
        profile.dataset.uploadReady = 'true';
        const checks = [profile.elements.noticeAcknowledged, profile.elements.declarationAccepted];
        const placements = [notice, declaration].map(block => {const anchor = document.createComment('Profile confirmation');block.before(anchor);block.hidden = true;return {block, anchor};});
        checks.forEach(check => check.required = false);
        if (!link) return;
        const node = (tag, text, className) => {const el = document.createElement(tag);if (text !== undefined) el.textContent = text;if (className) el.className = className;return el;};
        let dialog, pending = false, controller, returnFocus, overflow, rootOverflow;
        const restore = () => {
            placements.forEach(({block, anchor}) => {anchor.after(block);block.hidden = true;});
            checks.forEach(check => {check.removeAttribute('form');check.required = false;});
        };
        const close = () => {if (!pending) dialog?.close();};
        const parse = async response => {
            if (!response.ok || !response.headers.get('Content-Type')?.includes('text/html')) throw new Error(labels.uploadError);
            return new DOMParser().parseFromString(await response.text(), 'text/html');
        };
        const uploadForm = page => page.querySelector('form[data-onboarding-form="document"]');
        async function open() {
            if (dialog?.open) return;
            const previousChecks = checks.map(check => check.checked);let accepted = false, uploaded = false;
            returnFocus = document.activeElement;overflow = document.body.style.overflow;rootOverflow = document.documentElement.style.overflow;
            dialog = node('dialog', undefined, 'app-modal-panel kyc-upload-dialog');dialog.setAttribute('aria-labelledby', 'profile-upload-title');
            const header = node('div', undefined, 'app-modal-header'), title = node('h2', link.textContent.trim(), 'erp-panel-title');title.id = 'profile-upload-title';
            const dismiss = node('button', '×', 'app-modal-close');dismiss.type = 'button';dismiss.setAttribute('aria-label', labels.close);dismiss.addEventListener('click', close);
            header.append(title, dismiss);const body = node('div', undefined, 'app-modal-body');dialog.append(header, body);document.body.append(dialog);
            document.body.style.overflow = 'hidden';document.documentElement.style.overflow = 'hidden';dialog.showModal();
            const fail = message => {let error = body.querySelector('[data-upload-error]');if (!error) {error = node('div', undefined, 'console-message');error.dataset.uploadError = '';error.dataset.messageType = 'error';error.setAttribute('role', 'alert');error.tabIndex = -1;const actions = body.querySelector('[data-upload-actions]');if (actions) actions.before(error);else body.append(error);}error.textContent = message;window.ConsoleResourceDetails?.init(dialog);error.focus();};
            dialog.addEventListener('cancel', event => {if (pending) event.preventDefault();});
            dialog.addEventListener('close', () => {
                controller?.abort();restore();if (!accepted) checks.forEach((check,index) => {check.checked = uploaded ? false : previousChecks[index];check.dispatchEvent(new Event('change',{bubbles:true}));});dialog.remove();document.body.style.overflow = overflow;document.documentElement.style.overflow = rootOverflow;returnFocus?.focus();
            }, {once:true});
            controller = new AbortController();window.AppLoading?.begin(body);
            try {
                const initialPage = await parse(await fetch(link.href, {credentials:'same-origin', cache:'no-store', signal:controller.signal}));
                let metadata = uploadForm(initialPage);if (!metadata) throw new Error(labels.uploadError);
                window.AppLoading?.end(body);
                const sourcePicker = metadata.querySelector('[name="file"]');
                const maxBytes = Number(sourcePicker.dataset.uploadMaxBytes), remainingFiles = Number(sourcePicker.dataset.uploadRemainingFiles), remainingBytes = Number(sourcePicker.dataset.uploadRemainingBytes);
                const categoryTemplate = metadata.querySelector('[name="category"]'), expiryTemplate = metadata.querySelector('[name="expiresOn"]');
                const categoryLabel = categoryTemplate.closest('label').querySelector('span').textContent;
                const expiryLabel = expiryTemplate.closest('label').querySelector('span').textContent;
                const cancelText = metadata.querySelector('.accounting-form-actions a').textContent.trim();
                const form = node('form', undefined, 'kyc-client-form');form.id = 'profile-document-upload-form';form.dataset.nativeNavigation = '';
                const pickerField = node('div', undefined, 'kyc-field'), pickerLabel = node('label', undefined, 'kyc-field'), picker = node('input');picker.id = 'profile-upload-images';pickerLabel.htmlFor = picker.id;picker.type = 'file';picker.multiple = true;picker.accept = '.png,.jpg,.jpeg';pickerLabel.append(node('span', labels.chooseFiles));
                const help = node('p', sourcePicker.closest('.kyc-field')?.querySelector('.kyc-field-hint')?.textContent || sourcePicker.dataset.uploadFileError, 'kyc-field-hint');help.id = 'profile-upload-help';picker.setAttribute('aria-describedby',help.id);pickerField.append(pickerLabel,picker,help);
                const queue = node('div', undefined, 'kyc-upload-queue'), confirmations = node('div', undefined, 'kyc-upload-confirmations');
                const actions = node('div', undefined, 'app-modal-actions');actions.dataset.uploadActions = '';
                const cancel = node('button', cancelText, 'app-btn btn-neutral');cancel.type = 'button';cancel.addEventListener('click', close);
                const submit = node('button', labels.upload, 'app-btn btn-launch');submit.type = 'submit';actions.append(cancel, submit);
                form.append(pickerField, queue, confirmations, actions);body.append(form);
                placements.forEach(({block}) => {confirmations.append(block);block.hidden = false;});
                checks.forEach(check => {check.setAttribute('form', profile.id);check.required = true;});
                const rows = [];
                const hasDocuments = () => Boolean(profile.querySelector('[data-profile-document-list] [data-profile-document]'));
                const update = () => {confirmations.hidden = !rows.length && !hasDocuments();submit.hidden = confirmations.hidden;submit.textContent = rows.some(row => !row.done) ? labels.upload : labels.done;};
                picker.addEventListener('change', () => {
                    if (!picker.files.length) return;
                    const incoming = [...picker.files];
                    const invalid = incoming.find(file => !/\.(png|jpe?g)$/i.test(file.name) || file.size<1 || file.size>maxBytes);
                    if(invalid){fail(invalid.name+': '+sourcePicker.dataset.uploadFileError);picker.value='';return;}
                    if(rows.length+incoming.length>remainingFiles || [...rows.map(row=>row.file),...incoming].reduce((total,file)=>total+file.size,0)>remainingBytes){fail(sourcePicker.dataset.uploadLimitError);picker.value='';return;}
                    body.querySelector('[data-upload-error]')?.remove();
                    checks.forEach(check => {check.checked = false;check.dispatchEvent(new Event('change', {bubbles:true}));});
                    for (const file of incoming) {
                        const row = {file, done:false, metadata:null}, item = node('div', undefined, 'kyc-upload-row');
                        const name = node('p', file.name, 'kyc-upload-name'), category = categoryTemplate.cloneNode(true), expiry = expiryTemplate.cloneNode(true);
                        category.removeAttribute('id');expiry.removeAttribute('id');category.name = '';expiry.name = '';category.dataset.nativeSelect = 'true';
                        const categoryField = node('label', undefined, 'kyc-field'), expiryField = node('label', undefined, 'kyc-field');categoryField.append(node('span', categoryLabel), category);expiryField.append(node('span', expiryLabel), expiry);
                        const remove = node('button', labels.remove, 'app-btn btn-neutral');remove.type = 'button';remove.setAttribute('aria-label', labels.remove + ': ' + file.name);
                        row.item = item;row.category = category;row.expiry = expiry;row.remove = remove;
                        const refresh = () => {const photo = ['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expiryField.hidden = photo;expiry.disabled = photo;if (photo) expiry.value = '';checks.forEach(check => check.checked = false);};
                        category.addEventListener('change', refresh);expiry.addEventListener('change', () => checks.forEach(check => check.checked = false));refresh();
                        remove.addEventListener('click', () => {rows.splice(rows.indexOf(row), 1);item.remove();checks.forEach(check => check.checked = false);update();});
                        item.append(name, categoryField, expiryField, remove);queue.append(item);rows.push(row);
                    }
                    picker.value = '';update();window.refreshEnhancedSelects?.(queue);
                });
                form.addEventListener('submit', async event => {
                    event.preventDefault();event.stopPropagation();if (pending) return;
                    const missing = checks.find(check => !check.checkValidity());if (missing) {missing.reportValidity();return;}
                    body.querySelector('[data-upload-error]')?.remove();
                    pending = true;form.setAttribute('aria-busy', 'true');dismiss.disabled = true;cancel.disabled = true;picker.disabled = true;submit.disabled = true;checks.forEach(check => check.disabled = true);
                    rows.forEach(row => {row.category.disabled = true;row.expiry.disabled = true;row.remove.disabled = true;});
                    const finish = window.AppLoading?.action(submit);
                    try {
                        for (const row of rows.filter(row => !row.done)) {
                            if (!row.metadata) {row.metadata = metadata || uploadForm(await parse(await fetch(link.href, {credentials:'same-origin', cache:'no-store', signal:controller.signal})));metadata = null;}
                            if (!row.metadata) throw new Error(labels.uploadError);
                            const data = new FormData(row.metadata);data.set('category', row.category.value);data.set('expiresOn', row.expiry.value);data.set('file', row.file);
                            const page = await parse(await fetch(link.href, {method:'POST', credentials:'same-origin', body:data, signal:controller.signal}));
                            const errors = [...page.querySelectorAll('[data-workspace-response-error],.field-error,.coa-error')].map(error => error.textContent.trim()).filter(Boolean);
                            const list = page.querySelector('[data-profile-document-list]');
                            if (uploadForm(page) || !list || errors.length) throw new Error(errors.join('\n') || labels.uploadError);
                            profile.querySelector('[data-profile-document-list]').replaceChildren(...list.childNodes);
                            row.done = true;uploaded = true;row.item.append(node('span', labels.uploaded));
                        }
                        accepted = true;pending = false;dialog.close();
                    } catch (error) {if (error.name !== 'AbortError') fail(error instanceof TypeError ? labels.uploadError : error.message || labels.uploadError);}
                    finally {
                        finish?.();pending = false;form.removeAttribute('aria-busy');dismiss.disabled = false;cancel.disabled = false;picker.disabled = false;submit.disabled = false;checks.forEach(check => check.disabled = false);
                        rows.forEach(row => {row.category.disabled = row.done;row.expiry.disabled = row.done || ['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(row.category.value);row.remove.disabled = row.done;});update();
                    }
                });
                update();window.FormValidation?.refresh(form);picker.focus();
            } catch (error) {window.AppLoading?.end(body);if (error.name !== 'AbortError') fail(labels.uploadError);}
        }
        link.addEventListener('click', event => {if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;event.preventDefault();event.stopPropagation();void open();});
        profile.addEventListener('profile:before-review', event => {if (checks.some(check => !check.checked)) {event.preventDefault();void open();}});
        profile.addEventListener('submit', event => {if (event.submitter?.value === 'SUBMITTED' && checks.some(check => !check.checked)) {event.preventDefault();event.stopPropagation();void open();}});
        window.addEventListener('pagehide', () => {controller?.abort();if (dialog?.open) {pending = false;dialog.close();}});
    }));
})();
