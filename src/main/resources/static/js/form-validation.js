/* Present existing browser and bound server errors without replacing form submission. */
(() => {
    'use strict';
    const states = new Map();
    const errors = '[id$=".errors"],.field-error-text,[data-field-error-for]';
    const summaries = '.accounting-error-summary,.loan-error-summary';
    let serial = 0, firstInvalid = null;
    const labels = () => document.getElementById('form-validation-labels')?.dataset || {};
    const proxy = control => control.closest('.erp-date-control')?.querySelector('[data-date-display]')
        || control.closest('.erp-amount-control')?.querySelector('[data-amount-display]')
        || control.closest('.neo-select')?.querySelector('.neo-select-button') || control;
    const native = target => target?.closest?.('.neo-select')?.querySelector('select')
        || target?.closest?.('.erp-date-control')?.querySelector('[data-date-native]')
        || target?.closest?.('.erp-amount-control')?.querySelector('.erp-amount-native') || target;
    const eligible = control => control?.matches?.('input,select,textarea') && control.form
        && control.type !== 'hidden' && !control.disabled && !control.readOnly && control.willValidate;
    const members = control => control.type === 'radio' && control.name
        ? Array.from(control.form?.elements || [control]).filter(el => el.type === 'radio' && el.name === control.name && !el.disabled) : [control];
    const canonical = control => members(control)[0] || control;
    const value = control => control.type === 'radio' ? members(control).find(member => member.checked)?.value || ''
        : control.type === 'checkbox' ? control.checked : control.value;
    function describe(control, id, add) {
        const ids = new Set((control.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
        if (add) ids.add(id); else ids.delete(id);
        if (ids.size) control.setAttribute('aria-describedby', [...ids].join(' ')); else control.removeAttribute('aria-describedby');
    }
    function fieldName(control) {
        if (control.getAttribute('aria-label')) return control.getAttribute('aria-label');
        if (control.type === 'radio') {
            const legend = control.closest('fieldset')?.querySelector('legend');
            if (legend) return legend.textContent.trim();
        }
        const labelled = control.getAttribute('aria-labelledby');
        if (labelled) return labelled.split(/\s+/).map(id => document.getElementById(id)?.textContent || '').join(' ').trim();
        const label = control.labels?.[0] || proxy(control).labels?.[0];
        if (!label) return labels().field;
        const copy = label.cloneNode(true);
        copy.querySelectorAll('input,select,textarea,button,small,svg,.erp-field-error,.coa-error,.field-error-text').forEach(el => el.remove());
        return copy.textContent.replace(/\s+/g, ' ').replace(/\s*\*\s*$/, '').trim() || labels().field;
    }
    function message(control) {
        const visible = proxy(control), source = visible.validity && !visible.validity.valid ? visible : control;
        const validity = source.validity, text = labels();
        if (validity.customError) return source.validationMessage;
        const key = validity.valueMissing ? 'required' : validity.rangeUnderflow ? 'minimum' : validity.rangeOverflow ? 'maximum'
            : validity.tooShort ? 'short' : validity.tooLong ? 'long' : 'invalid';
        return (text[key] || source.validationMessage).replace('{0}', fieldName(control) || text.field || '')
            .replace('{1}', source.min || source.minLength).replace('{2}', source.max || source.maxLength);
    }
    function place(control, error) {
        const visible = proxy(control), wrapper = visible.closest('.neo-select,.erp-date-control,.erp-amount-control');
        const anchor = wrapper || (control.type === 'checkbox' || control.type === 'radio' ? control.closest('label') : control) || control;
        if (control.type === 'radio' && control.closest('fieldset')) control.closest('fieldset').append(error);
        else anchor.after(error);
    }
    function show(control, text, existing) {
        control = canonical(control);
        let state = states.get(control);
        if (existing && state && state.error !== existing) {clear(control); state = null;}
        if (!state || !state.error.isConnected) {
            const error = existing || document.createElement('span');
            if (!error.id || document.getElementById(error.id) !== error) error.id = `erp-field-error-${++serial}`;
            state = {error, server:!!existing, value:value(control)}; states.set(control, state);
            place(control, error);
        }
        const {error} = state;
        error.classList.add('erp-field-error'); error.hidden = false;
        error.setAttribute('aria-live', 'polite'); error.setAttribute('aria-atomic', 'true');
        if (!existing && error.textContent !== text) error.textContent = text;
        members(control).forEach(member => {
            new Set([member, proxy(member)]).forEach(node => {
                if (node !== member) (member.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean).forEach(id => describe(node, id, true));
                node.classList.add('erp-validation-invalid'); node.setAttribute('aria-invalid', 'true'); describe(node, error.id, true);
            });
        });
        return state;
    }
    function clear(control) {
        control = canonical(control);
        const state = states.get(control); if (!state) return;
        members(control).forEach(member => new Set([member, proxy(member)]).forEach(node => {
            node.classList.remove('erp-validation-invalid', 'field-error-input'); node.removeAttribute('aria-invalid'); describe(node, state.error.id, false);
        }));
        if (state.server) state.error.hidden = true; else state.error.remove();
        states.delete(control);
    }
    function validate(control, edited = false) {
        control = canonical(control);
        const state = states.get(control);
        if (state?.server && (!edited || value(control) === state.value)) return;
        if (state?.server) clear(control);
        if (control.validity.valid && (!proxy(control).validity || proxy(control).validity.valid)) clear(control);
        else show(control, message(control));
    }
    function reveal(control) {
        for (let parent = control.parentElement; parent; parent = parent.parentElement) if (parent.tagName === 'DETAILS') parent.open = true;
        const visible = proxy(control);
        visible.focus({preventScroll:true}); visible.scrollIntoView?.({block:'center', behavior:'instant'});
    }
    function focusFirst(form) {
        const control = Array.from(form.elements).find(el => states.has(el) && !el.disabled && !states.get(el).error.hidden);
        if (!control || control.closest('[hidden],.hidden')) return false;
        reveal(control); return true;
    }
    function scan(root = document) {
        const nodes = [...root.querySelectorAll(errors)]; if (root.matches?.(errors)) nodes.unshift(root);
        for (const error of nodes) {
            if (error.classList.contains('erp-field-error') || error.closest(summaries) || error.hidden || !error.textContent.trim()) continue;
            const form = error.closest('form'); if (!form) continue;
            const key = error.dataset.fieldErrorFor || error.id.replace(/\.errors$/, '');
            let control = Array.from(form.elements).find(el => el.id === key || el.name === key);
            if (control?.type === 'hidden') control = Array.from(form.querySelectorAll('select')).find(el => el.dataset.choiceFor === key || el.dataset.accountFor === key);
            if (!control) control = error.closest('label')?.querySelector('input:not([type=hidden]),select,textarea');
            if (control) show(control, error.textContent, error);
        }
    }
    document.addEventListener('focusout', event => {
        const control = native(event.target);
        // Let submission validate in one pass: inserting a blur error during
        // mousedown can move the submit button before its click is delivered.
        if (event.relatedTarget?.form === control?.form && event.relatedTarget?.type === 'submit') return;
        if (eligible(control)) validate(control);
    });
    for (const type of ['input','change']) document.addEventListener(type, event => {
        const control = native(event.target); if (eligible(control) && states.has(canonical(control))) validate(control, true);
    });
    document.addEventListener('invalid', event => {
        const control = native(event.target); if (!eligible(control)) return;
        event.preventDefault(); validate(control);
        if (!firstInvalid) {
            firstInvalid = control;
            // Browsers can run microtasks between invalid events; wait for the full validation task.
            setTimeout(() => {const first = firstInvalid; firstInvalid = null; if (first?.isConnected) reveal(first);}, 0);
        }
    }, true);
    document.addEventListener('reset', event => queueMicrotask(() => {
        if (!event.defaultPrevented) for (const control of states.keys()) if (control.form === event.target) clear(control);
    }));
    const focusErrors = () => Array.from(document.querySelectorAll('form')).some(form => focusFirst(form));
    const ready = () => {scan(); queueMicrotask(focusErrors);};
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once:true});
    else ready();
    document.addEventListener('workspace:navigated', focusErrors);
    new MutationObserver(records => {
        for (const record of records) record.addedNodes.forEach(node => {if (node.nodeType === 1) scan(node);});
        for (const [control, state] of states) if (!control.isConnected || !state.error.isConnected) {clear(control); states.delete(control);}
    }).observe(document.body, {childList:true, subtree:true});
    window.FormValidation = {refresh:scan, focusFirst};
})();
