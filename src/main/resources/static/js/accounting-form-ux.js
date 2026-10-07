(() => {
    const form = document.querySelector('.accounting-create-form');
    const modal = document.getElementById('accounting-discard-modal');
    if (!form || !modal) return;
    document.body.appendChild(modal);
    const panel = modal.querySelector('[role="dialog"]');
    let submitting = false, pending, previousFocus, previousOverflow, previousRootOverflow;
    const disabledOnSubmit = new Set();
    const values = () => JSON.stringify(Array.from(form.elements).filter(el => el.name && el.type !== 'hidden' && el.type !== 'submit').map(el => [el.name, el.value, el.checked]));
    let initial = values();
    const errors = form.querySelector('.accounting-error-summary .coa-error');
    let restoredErrors = Boolean(errors);
    const dirty = () => restoredErrors || values() !== initial;
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
    modal.querySelector('[data-accounting-discard]').addEventListener('click', () => { const action = pending; close(); submitting = true; action?.(); });
    modal.addEventListener('keydown', event => {
        if (event.key === 'Escape') { event.preventDefault(); close(); }
        if (event.key === 'Tab') {
            const buttons = Array.from(panel.querySelectorAll('button')), first = buttons[0], last = buttons.at(-1);
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    document.addEventListener('accounting:before-close', event => {
        if (!submitting && dirty()) { event.preventDefault(); confirmDiscard(() => {
            form.reset();
            form.querySelectorAll('select').forEach(select => select.dispatchEvent(new Event('change', {bubbles:true})));
            const rows = form.querySelectorAll('.library-rule');
            rows.forEach((row, index) => { if (index > 0) row.remove(); });
            form.querySelectorAll('.library-remove').forEach(button => button.disabled = true);
            const add = form.querySelector('#library-add-rule'); if (add) add.disabled = false;
            initial = values(); restoredErrors = false; submitting = false; event.detail.close();
        }); }
    });
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || submitting || !dirty() || link.target === '_blank' || link.getAttribute('href').startsWith('#')) return;
        event.preventDefault(); confirmDiscard(() => location.assign(link.href));
    });
    document.addEventListener('submit', event => {
        if (event.target === form) {
            if (submitting) { event.preventDefault(); return; }
            submitting = true; form.setAttribute('aria-busy', 'true');
            form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => {
                if (!button.disabled) { disabledOnSubmit.add(button); button.disabled = true; }
            });
        } else if (!submitting && dirty()) {
            event.preventDefault(); confirmDiscard(() => event.target.requestSubmit(event.submitter));
        }
    });
    window.addEventListener('beforeunload', event => { if (!submitting && dirty()) { event.preventDefault(); event.returnValue = ''; } });
    window.addEventListener('pageshow', () => {
        submitting = false; form.removeAttribute('aria-busy');
        disabledOnSubmit.forEach(button => button.disabled = false); disabledOnSubmit.clear();
    });
    form.querySelectorAll('label').forEach((label, index) => {
        const error = label.querySelector('.coa-error'), control = label.querySelector('input,select,textarea');
        if (!error || !control) return;
        error.id = `accounting-field-error-${index}`; control.setAttribute('aria-invalid', 'true');
        control.setAttribute('aria-describedby', error.id);
        const details = label.closest('details'); if (details) details.open = true;
    });
    if (errors && !form.closest('.library-modal')) form.querySelector('.accounting-error-summary').focus();
})();
