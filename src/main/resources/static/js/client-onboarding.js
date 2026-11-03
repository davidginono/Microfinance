(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once: true}));
    ready(() => document.querySelectorAll('form').forEach(form => {
        const business=form.querySelector('[data-profile-business-choice]'), businessFields=form.querySelector('[data-profile-business-fields]');
        const marital=form.querySelector('[data-profile-marital-status]'), spouse=form.querySelector('[data-profile-spouse-section]'), spouseFields=form.querySelector('[data-profile-spouse-fields]');
        const refreshProfile=()=>{
            if(business && businessFields){const hide=business.value!=='true';businessFields.hidden=hide;businessFields.disabled=hide;}
            if(marital && spouse && spouseFields){const hide=['SINGLE','DIVORCED','WIDOWED'].includes(marital.value);spouse.hidden=hide;spouseFields.disabled=hide;}
        };
        business?.addEventListener('change',refreshProfile);marital?.addEventListener('change',refreshProfile);
        if(business || marital){refreshProfile();form.addEventListener('reset',()=>queueMicrotask(refreshProfile));}
        if(!form.hasAttribute('data-onboarding-form'))return;
        const decision = form.querySelector('[data-kyc-decision]');
        const category=form.querySelector('[name="category"]'), expires=form.querySelector('[name="expiresOn"]'), file=form.querySelector('[name="file"]');
        if(category && expires && file) {
            const refreshUpload=()=>{const photo=['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expires.disabled=photo;expires.closest('label').hidden=photo;if(photo)expires.value='';file.accept='.png,.jpg,.jpeg';};
            category.addEventListener('change',refreshUpload);refreshUpload();
            file.addEventListener('change',()=>{const selected=file.files[0];let error='';if(selected && (!/\.(png|jpe?g)$/i.test(selected.name) || selected.size>Number(file.dataset.uploadMaxBytes)))error=file.dataset.uploadFileError;else if(selected && (Number(file.dataset.uploadRemainingFiles)<1 || selected.size>Number(file.dataset.uploadRemainingBytes)))error=file.dataset.uploadLimitError;file.setCustomValidity(error);});
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
