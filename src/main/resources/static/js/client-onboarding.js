(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once: true}));
    ready(() => document.querySelectorAll('[data-onboarding-form]').forEach(form => {
        const decision = form.querySelector('[data-kyc-decision]');
        if (!decision) return;
        const refresh = () => form.querySelectorAll('[data-kyc-verify]').forEach(section => {
            section.hidden = decision.value !== 'VERIFY';
            section.querySelectorAll('input,select,textarea').forEach(input => {input.disabled = section.hidden;});
        });
        decision.addEventListener('change', refresh);
        refresh();
    }));
})();
