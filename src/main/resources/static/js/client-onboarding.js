(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once: true}));
    ready(() => document.querySelectorAll('[data-onboarding-form]').forEach(form => {
        const decision = form.querySelector('[data-kyc-decision]');
        const category=form.querySelector('[name="category"]'), expires=form.querySelector('[name="expiresOn"]'), file=form.querySelector('[name="file"]');
        if(category && expires && file) {
            const refreshUpload=()=>{const photo=['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expires.disabled=photo;expires.closest('label').hidden=photo;if(photo)expires.value='';file.accept=photo?'.png,.jpg,.jpeg':'.pdf,.png,.jpg,.jpeg';};
            category.addEventListener('change',refreshUpload);refreshUpload();
        }
        if (!decision) return;
        const refresh = () => {
            form.querySelector('[name="clientMessage"]').required = decision.value !== 'VERIFY';
            form.querySelectorAll('[data-kyc-verify]').forEach(section => {
                section.hidden = decision.value !== 'VERIFY';
                section.querySelectorAll('input,select,textarea').forEach(input => {input.disabled = section.hidden;});
            });
        };
        decision.addEventListener('change', refresh);
        refresh();
    }));
})();
