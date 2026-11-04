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
        let dialog, pending = false, removing = false, controller, returnFocus, overflow, rootOverflow;
        const savedList = profile.querySelector('[data-profile-document-list]');
        const showRemoveButtons = () => savedList.querySelectorAll('[data-profile-remove]').forEach(button => button.hidden = false);
        showRemoveButtons();
        const restore = () => {
            placements.forEach(({block, anchor}) => {anchor.after(block);block.hidden = true;});
            checks.forEach(check => {check.removeAttribute('form');check.required = false;});
        };
        const close = () => {if (!pending && !removing) dialog?.close();};
        const parse = async response => {
            if (!response.ok || !response.headers.get('Content-Type')?.includes('text/html')) throw new Error(labels.uploadError);
            return new DOMParser().parseFromString(await response.text(), 'text/html');
        };
        const uploadForm = page => page.querySelector('form[data-onboarding-form="document"]');
        document.addEventListener('click', async event => {
            const button = event.target.closest('[data-profile-remove]');
            if (!button || (!profile.contains(button) && !dialog?.contains(button)) || pending || removing) return;
            const modal = dialog?.contains(button);
            const list = modal ? button.closest('.kyc-upload-saved') : savedList;
            const csrf = profile.querySelector('[data-profile-csrf]');
            list.querySelector('[data-profile-remove-error]')?.remove();
            removing = true;
            button.disabled = true;
            try {
                const data = new URLSearchParams({revisionId:savedList.dataset.revisionId});
                data.set(csrf.name,csrf.value);
                const page = await parse(await fetch(button.dataset.profileRemove, {method:'POST',credentials:'same-origin',body:data}));
                const updated = page.querySelector('[data-profile-document-list]');
                if (!updated || updated.dataset.revisionId !== savedList.dataset.revisionId ||
                    Array.from(updated.querySelectorAll('[data-profile-remove]')).some(action => action.dataset.profileRemove === button.dataset.profileRemove)) {
                    throw new Error(labels.removeError);
                }
                savedList.replaceChildren(...updated.childNodes);
                showRemoveButtons();
                checks.forEach(check => {
                    check.checked = false;
                    check.dispatchEvent(new Event('change', {bubbles:true}));
                });
                if (modal) {
                    button.closest('[data-profile-document]').remove();
                    list.hidden = !list.querySelector('[data-profile-document]');
                    dialog.dispatchEvent(new Event('profile:file-removed'));
                    (list.querySelector('[data-profile-remove]') || dialog.querySelector('[data-profile-choose]')).focus();
                } else {
                    (savedList.querySelector('[data-profile-remove]') || link).focus();
                }
            } catch {
                const error = node('p', labels.removeError, 'coa-error');
                error.dataset.profileRemoveError = '';
                error.setAttribute('role','alert');
                list.prepend(error);
                button.disabled = false;
                button.focus();
            } finally {
                removing = false;
            }
        });
        async function open() {
            if (dialog?.open || removing) return;
            const previousChecks = checks.map(check => check.checked), previews = new Set();let accepted = false, attachmentsChanged = false;
            returnFocus = document.activeElement;overflow = document.body.style.overflow;rootOverflow = document.documentElement.style.overflow;
            dialog = node('dialog', undefined, 'app-modal-panel kyc-upload-dialog');dialog.setAttribute('aria-labelledby', 'profile-upload-title');
            const header = node('div', undefined, 'app-modal-header'), title = node('h2', link.textContent.trim(), 'erp-panel-title');title.id = 'profile-upload-title';
            const dismiss = node('button', '×', 'app-modal-close');dismiss.type = 'button';dismiss.setAttribute('aria-label', labels.close);dismiss.addEventListener('click', close);
            header.append(title, dismiss);const body = node('div', undefined, 'app-modal-body');dialog.append(header, body);document.body.append(dialog);
            document.body.style.overflow = 'hidden';document.documentElement.style.overflow = 'hidden';dialog.showModal();
            const fail = message => {
                let error = body.querySelector('[data-upload-error]');
                if (!error) {
                    error = node('div', undefined, 'console-message');
                    error.dataset.uploadError = '';
                    error.dataset.messageType = 'error';
                    error.setAttribute('role', 'alert');
                    error.tabIndex = -1;
                    (body.querySelector('form') || body).append(error);
                }
                error.textContent = message;
                window.ConsoleResourceDetails?.init(dialog);
                error.focus();
            };
            dialog.addEventListener('cancel', event => {if (pending || removing) event.preventDefault();});
            dialog.addEventListener('close', () => {
                controller?.abort();previews.forEach(url => URL.revokeObjectURL(url));restore();if (!accepted) checks.forEach((check,index) => {check.checked = attachmentsChanged ? false : previousChecks[index];check.dispatchEvent(new Event('change',{bubbles:true}));});dialog.remove();document.body.style.overflow = overflow;document.documentElement.style.overflow = rootOverflow;returnFocus?.focus();
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
                const pickerField = node('div', undefined, 'kyc-field'), pickerLabel = node('label', undefined, 'kyc-field'), picker = node('input');picker.id = 'profile-upload-images';pickerLabel.htmlFor = picker.id;picker.type = 'file';picker.multiple = true;picker.accept = '.png,.jpg,.jpeg';picker.hidden = true;pickerLabel.append(node('span', labels.fileLabel));
                const dropzone = node('div', undefined, 'kyc-upload-dropzone'), choose = node('button', labels.chooseFiles, 'app-btn btn-neutral');choose.type = 'button';choose.addEventListener('click', () => picker.click());dropzone.append(node('span', labels.dropImages), choose, picker);
                const help = node('p', sourcePicker.closest('.kyc-field')?.querySelector('.kyc-field-hint')?.textContent || sourcePicker.dataset.uploadFileError, 'kyc-field-hint');help.id = 'profile-upload-help';choose.setAttribute('aria-describedby',help.id);pickerField.append(pickerLabel,dropzone,help);
                const queue = node('ul', undefined, 'kyc-upload-queue'), confirmations = node('div', undefined, 'kyc-upload-confirmations'), announcement = node('span', undefined, 'sr-only');queue.setAttribute('aria-label',labels.selectedImages);announcement.setAttribute('role','status');
                choose.dataset.profileChoose = '';
                const saved = node('section', undefined, 'kyc-upload-saved');
                if (savedList.querySelector('[data-profile-document]')) {
                    saved.append(node('h3', labels.uploadedFiles), ...[...savedList.children]
                        .filter(child => !child.matches('[data-profile-evidence]')).map(child => child.cloneNode(true)));
                } else {
                    saved.hidden = true;
                }
                const actions = node('div', undefined, 'app-modal-actions');
                const cancel = node('button', cancelText, 'app-btn btn-neutral');cancel.type = 'button';cancel.addEventListener('click', close);
                const submit = node('button', labels.upload, 'app-btn btn-launch');
                submit.type = 'submit';
                submit.setAttribute('form', form.id);
                actions.append(cancel, submit);
                form.append(pickerField, queue, saved, confirmations);
                body.append(form, announcement);
                dialog.append(actions);
                placements.forEach(({block}) => {confirmations.append(block);block.hidden = false;});
                checks.forEach(check => {check.setAttribute('form', profile.id);check.required = true;});
                const rows = [];let tokenIndex = 0;
                const number = new Intl.NumberFormat(document.documentElement.lang || 'en', {maximumFractionDigits:1}), date = new Intl.DateTimeFormat('en-GB', {timeZone:'Africa/Dar_es_Salaam',day:'2-digit',month:'2-digit',year:'numeric'});
                const fileSize = bytes => bytes < 1024 ? number.format(bytes)+' B' : bytes < 1024*1024 ? number.format(bytes/1024)+' KB' : number.format(bytes/(1024*1024))+' MB';
                const hasDocuments = () => Boolean(savedList.querySelector('[data-profile-document]'));
                const update = () => {queue.hidden = !rows.length;confirmations.hidden = !rows.length && !hasDocuments();submit.hidden = confirmations.hidden;submit.textContent = rows.some(row => !row.done) ? labels.upload : labels.done;};
                dialog.addEventListener('profile:file-removed', () => {attachmentsChanged = true;update();});
                const addFiles = incoming => {
                    if (pending || !incoming.length) return;
                    const invalid = incoming.find(file => !/\.(png|jpe?g)$/i.test(file.name) || file.size<1 || file.size>maxBytes);
                    if(invalid){fail(invalid.name+': '+sourcePicker.dataset.uploadFileError);picker.value='';return;}
                    if(rows.length+incoming.length>remainingFiles || [...rows.map(row=>row.file),...incoming].reduce((total,file)=>total+file.size,0)>remainingBytes){fail(sourcePicker.dataset.uploadLimitError);picker.value='';return;}
                    body.querySelector('[data-upload-error]')?.remove();
                    checks.forEach(check => {check.checked = false;check.dispatchEvent(new Event('change', {bubbles:true}));});
                    for (const file of incoming) {
                        const row = {file, done:false, metadata:null}, item = node('li', undefined, 'kyc-upload-row');
                        const displayName = file.name.replace(/\\/g,'/').split('/').pop(), token = node('div', undefined, 'kyc-upload-token'), details = node('div', undefined, 'kyc-upload-details');
                        const name = node('p', displayName, 'kyc-upload-name'), category = categoryTemplate.cloneNode(true), expiry = expiryTemplate.cloneNode(true), metadataList = node('dl',undefined,'kyc-upload-metadata');
                        const detail = (label,value) => {const pair = node('div');pair.append(node('dt',label),node('dd',value));metadataList.append(pair);};
                        detail(labels.fileSize,fileSize(file.size));if(Number.isFinite(file.lastModified))detail(labels.fileModified,date.format(new Date(file.lastModified)));
                        row.status = node('div',undefined,'kyc-upload-status');row.status.hidden = true;details.append(name,metadataList,row.status);
                        if(URL.createObjectURL){row.preview = URL.createObjectURL(file);previews.add(row.preview);const image = node('img',undefined,'kyc-upload-thumbnail');image.src = row.preview;image.alt = '';image.loading = 'lazy';image.decoding = 'async';image.addEventListener('error',()=>image.hidden = true);token.append(image);}
                        category.removeAttribute('id');expiry.removeAttribute('id');category.name = '';expiry.name = '';category.dataset.nativeSelect = 'true';
                        const categoryField = node('label', undefined, 'kyc-field'), expiryField = node('label', undefined, 'kyc-field');categoryField.append(node('span', categoryLabel), category);expiryField.append(node('span', expiryLabel), expiry);
                        const remove = node('button', undefined, 'erp-icon-btn kyc-upload-remove');remove.type = 'button';remove.innerHTML = '<svg class="console-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M3 3l10 10M13 3L3 13"/></svg>';const removeLabel = labels.removeSelected.replace('{0}',String(++tokenIndex)).replace('{1}',displayName);remove.setAttribute('aria-label',removeLabel);remove.title = removeLabel;
                        token.append(details,remove);
                        row.item = item;row.category = category;row.expiry = expiry;row.remove = remove;
                        const refresh = () => {const photo = ['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expiryField.hidden = photo;expiry.disabled = photo;if (photo) expiry.value = '';checks.forEach(check => check.checked = false);};
                        category.addEventListener('change', refresh);expiry.addEventListener('change', () => checks.forEach(check => check.checked = false));refresh();
                        remove.addEventListener('click', () => {const index = rows.indexOf(row);rows.splice(index,1);if(row.preview){URL.revokeObjectURL(row.preview);previews.delete(row.preview);}item.remove();checks.forEach(check => check.checked = false);update();const next = rows[Math.min(index,rows.length-1)]?.remove;(next && !next.disabled ? next : choose).focus();});
                        item.append(token, categoryField, expiryField);queue.append(item);rows.push(row);
                    }
                    picker.value = '';update();window.refreshEnhancedSelects?.(queue);
                };
                picker.addEventListener('change', () => addFiles([...picker.files]));
                dropzone.addEventListener('dragover',event=>{event.preventDefault();if(!pending)dropzone.classList.add('is-dragging');});
                dropzone.addEventListener('dragleave',event=>{if(!dropzone.contains(event.relatedTarget))dropzone.classList.remove('is-dragging');});
                dropzone.addEventListener('drop',event=>{event.preventDefault();event.stopPropagation();dropzone.classList.remove('is-dragging');addFiles([...(event.dataTransfer?.files || [])]);});
                form.addEventListener('submit', async event => {
                    event.preventDefault();event.stopPropagation();if (pending || removing) return;
                    const missing = checks.find(check => !check.checkValidity());if (missing) {missing.reportValidity();return;}
                    body.querySelector('[data-upload-error]')?.remove();
                    const pendingRows = rows.filter(row => !row.done);announcement.textContent = pendingRows.length ? labels.uploadingFiles.replace('{0}',String(pendingRows.length)) : '';
                    pending = true;form.setAttribute('aria-busy', 'true');dismiss.disabled = true;cancel.disabled = true;picker.disabled = true;choose.disabled = true;submit.disabled = true;checks.forEach(check => check.disabled = true);
                    rows.forEach(row => {row.category.disabled = true;row.expiry.disabled = true;row.remove.disabled = true;});
                    const finish = window.AppLoading?.action(submit);
                    let activeRow;
                    try {
                        for (const row of pendingRows) {
                            activeRow = row;row.status.className = 'kyc-upload-status';row.status.removeAttribute('role');row.status.removeAttribute('data-resource-status');row.status.replaceChildren();row.status.hidden = false;row.item.dataset.uploadState = 'loading';const spinner = node('span',undefined,'app-loading-spinner');spinner.dataset.loadingSize = 'small';spinner.setAttribute('aria-hidden','true');row.status.append(spinner,node('span',labels.uploadingFile));
                            if (!row.metadata) {row.metadata = metadata || uploadForm(await parse(await fetch(link.href, {credentials:'same-origin', cache:'no-store', signal:controller.signal})));metadata = null;}
                            if (!row.metadata) throw new Error(labels.uploadError);
                            const data = new FormData(row.metadata);data.set('category', row.category.value);data.set('expiresOn', row.expiry.value);data.set('file', row.file);
                            const page = await parse(await fetch(link.href, {method:'POST', credentials:'same-origin', body:data, signal:controller.signal}));
                            const errors = [...page.querySelectorAll('[data-workspace-response-error],.field-error,.coa-error')].map(error => error.textContent.trim()).filter(Boolean);
                            const list = page.querySelector('[data-profile-document-list]');
                            if (uploadForm(page) || !list || errors.length) throw new Error(errors.join('\n') || labels.uploadError);
                            savedList.replaceChildren(...list.childNodes);
                            showRemoveButtons();
                            row.done = true;attachmentsChanged = true;row.item.dataset.uploadState = 'success';row.status.textContent = labels.uploaded;row.status.dataset.resourceStatus = 'SUCCESS';window.ConsoleResourceDetails?.init(row.status);
                        }
                        accepted = true;pending = false;dialog.close();
                    } catch (error) {if (error.name !== 'AbortError') {const message = error instanceof TypeError ? labels.uploadError : error.message || labels.uploadError;if(activeRow){activeRow.item.dataset.uploadState = 'error';activeRow.status.className = 'kyc-upload-status coa-error';activeRow.status.textContent = message;activeRow.status.setAttribute('role','alert');}else fail(message);}}
                    finally {
                        finish?.();pending = false;form.removeAttribute('aria-busy');dismiss.disabled = false;cancel.disabled = false;picker.disabled = false;choose.disabled = false;submit.disabled = false;checks.forEach(check => check.disabled = false);
                        rows.forEach(row => {row.category.disabled = row.done;row.expiry.disabled = row.done || ['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(row.category.value);row.remove.disabled = row.done;});update();
                    }
                });
                update();window.FormValidation?.refresh(form);choose.focus();
            } catch (error) {window.AppLoading?.end(body);if (error.name !== 'AbortError') fail(labels.uploadError);}
        }
        link.addEventListener('click', event => {if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;event.preventDefault();event.stopPropagation();void open();});
        profile.addEventListener('profile:before-review', event => {
            if (removing || checks.some(check => !check.checked)) {
                event.preventDefault();
                if (!removing) void open();
            }
        });
        profile.addEventListener('submit', event => {
            if (!event.defaultPrevented && (removing || (event.submitter?.value === 'SUBMITTED' && checks.some(check => !check.checked)))) {
                event.preventDefault();event.stopPropagation();
                if (!removing) void open();
            }
        });
        window.addEventListener('pagehide', () => {controller?.abort();if (dialog?.open) {pending = false;dialog.close();}});
    }));
})();
