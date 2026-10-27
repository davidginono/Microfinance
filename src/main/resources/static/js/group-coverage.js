(() => {
    'use strict';
    const ready=window.WorkspaceNavigation?.onReady || (fn=>document.addEventListener('DOMContentLoaded',fn,{once:true}));
    ready(()=>document.querySelectorAll('[data-coverage-form]').forEach(form=>{
        const kind=form.querySelector('[data-coverage-kind]'),promise=form.querySelector('[data-coverage-promise]'),reason=form.querySelector('[data-coverage-reason]');
        const sync=()=>{if(promise){promise.hidden=kind.value!=='PROMISED';promise.disabled=promise.hidden;}if(reason)reason.required=kind.value!=='ACKNOWLEDGED';};
        if(kind){kind.addEventListener('change',sync);sync();}
        form.querySelectorAll('.coa-error').forEach((error,index)=>{const label=error.closest('label') || (error.previousElementSibling?.tagName==='LABEL'?error.previousElementSibling:null);const control=label?.querySelector('input:not([type=hidden]),select,textarea');if(control){if(!error.id)error.id='coverage-field-error-'+index;control.setAttribute('aria-invalid','true');control.setAttribute('aria-describedby',error.id);}});
        const invalid=[...form.querySelectorAll('[aria-invalid="true"]')].find(el=>!el.disabled&&!el.closest('[hidden]')) || form.querySelector('.accounting-error-summary[role=alert]');
        if(invalid){invalid.focus();invalid.scrollIntoView({block:'center'});}
    }));
})();
