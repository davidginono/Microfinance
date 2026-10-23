(() => {
    const forms = Array.from(document.querySelectorAll('form.accounting-create-form'));
    const modal = document.getElementById('accounting-discard-modal');
    if (!forms.length || !modal) return;
    document.body.appendChild(modal);
    const panel = modal.querySelector('[role="dialog"]');
    let submitting = false, discarding = false, submitted, initialized = false, pending, previousFocus, previousOverflow, previousRootOverflow;
    const disabledOnSubmit = new Set();
    const values = form => JSON.stringify(Array.from(form.elements).filter(el => el.name && (el.type !== 'hidden' || form.hasAttribute('data-voucher-form')) && el.type !== 'submit' && el.name !== 'action').map(el => [el.name, el.value, el.checked]));
    const states = forms.map(form => ({form, initial:values(form), edited:false,
        restoredErrors:Boolean(form.querySelector('.accounting-error-summary .coa-error, .loan-error-summary'))}));
    const dirty = except => states.some(state => state !== except && (state.restoredErrors || values(state.form) !== state.initial));
    const initialize = (complete = false) => {
        if (initialized) return;
        initialized = complete;
        // Deferred form scripts may normalize controls after this guard is loaded.
        states.forEach(state => { if (!state.edited) state.initial = values(state.form); });
    };
    document.addEventListener('DOMContentLoaded', () => queueMicrotask(() => initialize()), {once:true});
    window.addEventListener('pageshow', () => queueMicrotask(() => initialize(true)), {once:true});
    if (document.readyState === 'complete' && !window.WorkspaceNavigation?.restoring) queueMicrotask(() => initialize(true));
    states.forEach(state => ['input','change'].forEach(type => state.form.addEventListener(type, () => state.edited = true)));
    const close = () => {
        modal.hidden = true; modal.classList.remove('is-open');
        document.body.style.overflow = previousOverflow;
        document.documentElement.style.overflow = previousRootOverflow;
        previousFocus?.focus(); pending = null;
    };
    const confirmDiscard = action => {
        pending = action; previousFocus = document.activeElement; previousOverflow = document.body.style.overflow; previousRootOverflow = document.documentElement.style.overflow;
        modal.hidden = false; modal.classList.add('is-open'); document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
        modal.querySelector('.app-modal-actions [data-accounting-keep]').focus();
    };
    modal.querySelectorAll('[data-accounting-keep]').forEach(button => button.addEventListener('click', close));
    modal.querySelector('[data-accounting-discard]').addEventListener('click', () => { const action = pending; close(); discarding = true; action?.(); });
    modal.addEventListener('keydown', event => {
        if (event.key === 'Escape') { event.preventDefault(); close(); }
        if (event.key === 'Tab') {
            const buttons = Array.from(panel.querySelectorAll('button')), first = buttons[0], last = buttons.at(-1);
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    document.addEventListener('accounting:before-close', event => {
        const state = states.find(state => state.form.closest('.library-modal')) || states[0], form = state.form;
        if (!submitting && !discarding && (state.restoredErrors || values(form) !== state.initial)) { event.preventDefault(); confirmDiscard(() => {
            form.reset();
            form.querySelectorAll('select').forEach(select => select.dispatchEvent(new Event('change', {bubbles:true})));
            const rows = form.querySelectorAll('.library-rule');
            rows.forEach((row, index) => { if (index > 0) row.remove(); });
            form.querySelectorAll('.library-remove').forEach(button => button.disabled = true);
            const add = form.querySelector('#library-add-rule'); if (add) add.disabled = false;
            state.initial = values(form); state.restoredErrors = false; discarding = false; event.detail.close();
        }); }
    });
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || submitting || discarding || !dirty() || link.target === '_blank' || link.getAttribute('href').startsWith('#')) return;
        event.preventDefault(); confirmDiscard(() => link.click());
    });
    document.addEventListener('workspace:before-navigate', event => {
        if (!submitting && !discarding && dirty()) {event.preventDefault(); confirmDiscard(event.detail.resume);}
    });
    document.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        const state = states.find(state => state.form === event.target), form = event.target;
        if (!submitting && !discarding && dirty(state)) {
            event.preventDefault(); confirmDiscard(() => form.requestSubmit(event.submitter)); return;
        }
        if (state) {
            if (submitting) { event.preventDefault(); return; }
            submitted = {state, event, values:values(form)};
            submitting = true; form.setAttribute('aria-busy', 'true');
            if ((form.hasAttribute('data-account-version-form') || form.hasAttribute('data-preserve-submit-command')) && event.submitter?.name) {
                const command = document.createElement('input');
                command.type = 'hidden'; command.name = event.submitter.name; command.value = event.submitter.value;
                command.dataset.accountVersionCommand = ''; form.appendChild(command);
            }
            form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => {
                if (!button.disabled) { disabledOnSubmit.add(button); button.disabled = true; }
            });
        }
    });
    // Security logout must never wait for a discard confirmation or beforeunload.
    document.addEventListener('session:logout', () => {
        submitting = true;
        if (!modal.hidden) close();
    });
    window.addEventListener('beforeunload', event => { if (!submitting && !discarding && dirty()) { event.preventDefault(); event.returnValue = ''; } });
    window.addEventListener('pagehide', () => {
        initialized = true;
        // Keep the submitted baseline when Back restores this document from cache.
        // A cancelled/invalid submission never changes that baseline.
        if (submitted && !submitted.event.defaultPrevented) {
            submitted.state.initial = submitted.values;
            submitted.state.restoredErrors = false;
        }
    });
    window.addEventListener('pageshow', () => {
        submitting = false; discarding = false; submitted = null;
        forms.forEach(form => form.removeAttribute('aria-busy'));
        disabledOnSubmit.forEach(button => button.disabled = false); disabledOnSubmit.clear();
        forms.forEach(form => form.querySelectorAll('[data-account-version-command]').forEach(input => input.remove()));
    });
    forms.forEach((form, formIndex) => form.querySelectorAll('label').forEach((label, index) => {
        const error = label.querySelector('.coa-error'), control = label.querySelector('input,select,textarea');
        if (!error || !control) return;
        error.id = `accounting-field-error-${formIndex}-${index}`; control.setAttribute('aria-invalid', 'true');
        control.setAttribute('aria-describedby', error.id);
        const details = label.closest('details'); if (details) details.open = true;
    }));
    const failed = states.find(state => state.restoredErrors && !state.form.closest('.library-modal'));
    failed?.form.querySelector('.accounting-error-summary, .loan-error-summary')?.focus();
})();
