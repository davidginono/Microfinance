(() => {
    'use strict';
    function contacts(form) {
        if (!form.dataset.contactCheckUrl || form.dataset.contactCheckReady) return;
        form.dataset.contactCheckReady = 'true';
        const cleanups=[];
        form.querySelectorAll('[data-contact-field]').forEach(field => {
            const input=field.querySelector('input'), result=field.querySelector('[data-contact-result]'), labels=field.closest('[data-contact-fields]').dataset;
            if (!input || input.readOnly || input.disabled) return;
            let timer, request, version=0, composing=false;
            const stop=()=>{clearTimeout(timer);request?.abort();request=null;version++;};
            const show=(text,state)=>{
                result.replaceChildren();result.hidden=!text;result.removeAttribute('data-resource-status');result.classList.remove('console-status');
                if (!text) return;
                if(state==='checking') {
                    const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='small';spinner.setAttribute('aria-hidden','true');result.append(spinner,document.createTextNode(text));
                } else {
                    result.textContent=text;result.dataset.resourceStatus=state;window.ConsoleResourceDetails?.status(result);
                }
            };
            const error=text=>{show('');window.FormValidation.setError(input,text);};
            const check=async()=>{
                timer=null;
                if(!input.isConnected || input.disabled || input.readOnly || composing)return;
                const value=input.value.trim(), kind=field.dataset.contactField;
                if(!value){error('');return;}
                const digits=value.replace(/[^0-9]/g,''), phone=/^0\d{9}$/.test(digits)?'255'+digits.slice(1):digits;
                if(kind==='phone'?!/^255\d{9}$/.test(phone):value.length>255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {error(labels[kind==='phone'?'contactPhoneError':'contactEmailError']);return;}
                const current=++version;request=new AbortController();show(labels.contactChecking,'checking');
                const activeRequest=request;let timedOut=false;
                const timeout=setTimeout(()=>{timedOut=true;activeRequest.abort();},8000);
                const body=new URLSearchParams({field:kind,value});
                if(form.dataset.contactClientId)body.set('clientId',form.dataset.contactClientId);
                const csrfName=document.querySelector('meta[name="_csrf_parameter"]')?.content || '_csrf', csrf=[...form.elements].find(control=>control.name===csrfName);
                if(csrf)body.set(csrf.name,csrf.value);
                try {
                    const response=await fetch(form.dataset.contactCheckUrl,{method:'POST',credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},body,signal:request.signal});
                    if(!response.ok || !response.headers.get('content-type')?.includes('application/json'))throw new Error('Contact check unavailable');
                    const data=await response.json();
                    if(current!==version || !input.isConnected || input.value.trim()!==value)return;
                    if(typeof data.valid!=='boolean' || typeof data.exists!=='boolean')throw new Error('Invalid contact check');
                    if(!data.valid)error(labels[kind==='phone'?'contactPhoneError':'contactEmailError']);
                    else if(data.exists)error(labels.contactExists);
                    else {window.FormValidation.setError(input,'');show(labels.contactAvailable,'SUCCESS');}
                } catch(failure) {
                    if(current===version && input.isConnected && (failure.name!=='AbortError' || timedOut)){
                        show(labels.contactUnavailable,'WARNING');
                        const retry=document.createElement('button');retry.type='button';retry.className='app-btn btn-neutral';retry.textContent=labels.contactRetry;retry.addEventListener('click',()=>{stop();check();});result.append(retry);
                    }
                } finally {clearTimeout(timeout);if(current===version)request=null;}
            };
            const schedule=()=>{stop();show('');window.FormValidation.setError(input,'');if(!composing && input.value.trim())timer=setTimeout(check,450);};
            input.addEventListener('input',schedule);
            input.addEventListener('compositionstart',()=>{composing=true;stop();show('');});
            input.addEventListener('compositionend',()=>{composing=false;schedule();});
            input.addEventListener('focusout',event=>{if(event.relatedTarget?.form===form && event.relatedTarget.type==='submit')return;if(timer && !composing){clearTimeout(timer);check();}});
            form.addEventListener('reset',()=>{stop();show('');window.FormValidation.setError(input,'');});
            cleanups.push(()=>{stop();show('');});
        });
        const cleanup=()=>cleanups.forEach(stop=>stop());
        const navigated=()=>{if(!form.isConnected){cleanup();window.removeEventListener('pagehide',cleanup);document.removeEventListener('workspace:navigated',navigated);}};
        window.addEventListener('pagehide',cleanup);
        document.addEventListener('workspace:navigated',navigated);
    }
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once: true}));
    ready(() => document.querySelectorAll('form').forEach(form => {
        contacts(form);
        const business=form.querySelector('[data-profile-business-choice]'), businessFields=form.querySelector('[data-profile-business-fields]');
        const marital=form.querySelector('[data-profile-marital-status]'), spouse=form.querySelector('[data-profile-spouse-section]'), spouseFields=form.querySelector('[data-profile-spouse-fields]');
        const refreshProfile=()=>{
            if(business && businessFields){const hide=business.value!=='true';businessFields.hidden=hide;businessFields.disabled=hide;}
            if(marital && spouse && spouseFields){const hide=['SINGLE','DIVORCED','WIDOWED'].includes(marital.value);spouse.hidden=hide;spouseFields.disabled=hide;}
        };
        business?.addEventListener('change',refreshProfile);marital?.addEventListener('change',refreshProfile);
        if(business || marital){refreshProfile();form.addEventListener('reset',()=>queueMicrotask(refreshProfile));}
        if(!form.hasAttribute('data-onboarding-form'))return;
        const category=form.querySelector('[name="category"]'), expires=form.querySelector('[name="expiresOn"]'), file=form.querySelector('[name="file"]');
        if(category && expires && file) {
            const refreshUpload=()=>{const photo=['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expires.disabled=photo;expires.closest('label').hidden=photo;if(photo)expires.value='';file.accept='.png,.jpg,.jpeg';};
            category.addEventListener('change',refreshUpload);refreshUpload();
            file.addEventListener('change',()=>{const selected=file.files[0];let error='';if(selected && (!/\.(png|jpe?g)$/i.test(selected.name) || selected.size>Number(file.dataset.uploadMaxBytes)))error=file.dataset.uploadFileError;else if(selected && (Number(file.dataset.uploadRemainingFiles)<1 || selected.size>Number(file.dataset.uploadRemainingBytes)))error=file.dataset.uploadLimitError;file.setCustomValidity(error);});
        }
    }));
})();
