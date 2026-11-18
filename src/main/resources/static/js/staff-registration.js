(() => {
    'use strict';
    document.querySelectorAll('[data-staff-registration]').forEach(form => {
        const fields = ['email', 'phone'].map(field => {
            const input = form.elements.namedItem(field), status = form.querySelector(`#staff-${field}-status`);
            let controller, version = 0;
            const show = (text = '', error = false) => {
                status.textContent = text;
                status.classList.toggle('erp-field-error', error);
                status.removeAttribute('aria-busy');
            };
            const invalidate = () => { ++version; controller?.abort(); show(); };
            const check = async () => {
                const value = input.value.trim();
                if (!value) return;
                const valid = field === 'phone' ? /^255[0-9]{9}$/.test(value)
                    : value.length <= 160 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
                if (!valid) { show(form.dataset[`${field}Invalid`], true); return; }
                const current = ++version;
                controller = new AbortController();
                show(form.dataset.checking); status.setAttribute('aria-busy', 'true');
                const body = new URLSearchParams({field, value});
                const csrf = form.querySelector('[data-staff-csrf]');
                body.set(csrf.name, csrf.value);
                try {
                    const response = await fetch(form.dataset.contactCheckUrl, {
                        method: 'POST', body, signal: controller.signal, credentials: 'same-origin', cache: 'no-store',
                        headers: {Accept: 'application/json'}
                    });
                    if (!response.ok || response.redirected) throw new Error('Contact check failed');
                    const result = await response.json();
                    if (current !== version || !input.isConnected) return;
                    if (typeof result.available !== 'boolean') throw new Error('Invalid contact status');
                    show(result.available ? form.dataset.available : form.dataset.used, !result.available);
                } catch (error) {
                    if (current === version && input.isConnected && error.name !== 'AbortError') {
                        show(form.dataset.failed, true); binding.cancel();
                    }
                }
            };
            const binding = window.AppSearch.bind(input, check, invalidate);
            if (input.value) binding.run();
            return {binding, invalidate};
        });
        const name = form.elements.namedItem('fullName');
        const validateName = () => {
            const normalized = name.value.trim().replace(/\s+/gu, ' '), parts = normalized.split(' ');
            const valid = normalized.length <= 120 && parts.length === 3
                && parts.every(part => /^\p{L}[\p{L}\p{M}]*(?:['’-]\p{L}[\p{L}\p{M}]*)*$/u.test(part));
            name.setCustomValidity(valid ? '' : form.dataset.nameInvalid);
        };
        name.addEventListener('input', validateName);
        validateName();
        const cancel = () => fields.forEach(({binding, invalidate}) => { binding.cancel(); invalidate(); });
        window.addEventListener('pagehide', cancel);
        document.addEventListener('resource:before-close', event => { if (event.detail.modal.contains(form)) cancel(); });
        document.querySelectorAll('[data-user-modal-open="create-user"]').forEach(button =>
            button.addEventListener('click', () => fields.forEach(({binding}) => binding.run())));
        form.addEventListener('reset', () => { cancel(); queueMicrotask(validateName); });
        window.addEventListener('pageshow', () => fields.forEach(({binding}) => binding.run()));
    });
})();
