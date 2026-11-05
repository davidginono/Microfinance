(() => {
    'use strict';
    function creation(form) {
        const labels=form.querySelector('[data-creation-labels]')?.dataset;
        if(form.dataset.onboardingForm!=='create' || !labels || form.dataset.creationReady)return;
        form.dataset.creationReady='true';
        const rows=form.querySelector('[data-creation-documents]'),template=form.querySelector('[data-creation-document-template]'),add=form.querySelector('[data-creation-add]');
        const allRows=()=>[...rows.querySelectorAll('[data-creation-document-row]')];
        const validate=()=>{
            const inputs=allRows().map(row=>row.querySelector('input[type=file]'));
            const bytes=inputs.reduce((total,input)=>total+(input.files[0]?.size || 0),0);
            inputs.forEach(input=>{const file=input.files[0];input.setCustomValidity(file && (!/\.(png|jpe?g)$/i.test(file.name) || file.size>1048576)?labels.fileError:bytes>10485760?labels.limitError:'');});
        };
        const renumber=()=>{allRows().forEach((row,index)=>row.querySelectorAll('[name]').forEach(input=>input.name=input.name.replace(/documents\[\d+\]/,'documents['+index+']')));add.disabled=allRows().length>=10;validate();};
        const bind=row=>{
            const category=row.querySelector('select'),expiry=row.querySelector('input[type=date]'),remove=row.querySelector('[data-creation-remove]');
            const refresh=()=>{const photo=['PROFILE_PHOTO','SPOUSE_PHOTO'].includes(category.value);expiry.disabled=photo;expiry.closest('label').hidden=photo;if(photo)expiry.value='';};
            category.addEventListener('change',refresh);row.querySelector('input[type=file]').addEventListener('change',validate);refresh();remove.hidden=false;
            remove.addEventListener('click',()=>{row.remove();if(!allRows().length)append();renumber();add.focus();form.dispatchEvent(new Event('change',{bubbles:true}));});
        };
        const append=()=>{const row=template.content.firstElementChild.cloneNode(true);rows.append(row);renumber();bind(row);window.refreshEnhancedSelects?.(row);};
        allRows().forEach(bind);add.hidden=false;add.addEventListener('click',()=>{if(allRows().length<10)append();});
        let pending=false,request;
        const failure=(message,kind)=>{
            form.querySelectorAll('[data-creation-draft-error]').forEach(error=>error.remove());
            const error=document.createElement('div');error.className='console-message';error.dataset.creationDraftError='';error.dataset.messageType=kind;error.setAttribute('role',kind==='error'?'alert':'status');error.tabIndex=-1;error.textContent=message;
            form.querySelector('[data-form-actions]').before(error);window.ConsoleResourceDetails?.init(form);error.focus();
        };
        form.addEventListener('submit',async event=>{
            if(event.submitter?.value!=='DRAFT' || !window.showToast)return;
            event.preventDefault();event.stopPropagation();if(pending)return;
            const data=new URLSearchParams();for(const [name,value] of new FormData(form))if(typeof value==='string'&&!name.startsWith('documents['))data.append(name,value);data.set('action','DRAFT');
            const controls=[...form.querySelectorAll('button[type=submit]')].filter(button=>!button.disabled);
            pending=true;request=new AbortController();const timeout=setTimeout(()=>request.abort(),20000);
            document.dispatchEvent(new CustomEvent('workspace:form-pending',{detail:{form}}));
            const finish=window.AppLoading?.action(event.submitter);form.inert=true;form.setAttribute('aria-busy','true');controls.forEach(button=>button.disabled=true);
            let saved=false;
            try {
                const response=await fetch(event.submitter.formAction || form.action || location.href,{method:'POST',credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json','X-Profile-Draft':'save'},body:data,signal:request.signal});
                if(!response.headers.get('content-type')?.includes('application/json'))throw new Error('Unconfirmed draft');
                const result=await response.json();
                if(!response.ok || result.saved!==true){form.inert=false;failure(result.message || labels.draftError,'error');return;}
                if(result.requestKey!==data.get('requestKey'))throw new Error('Unconfirmed draft');
                saved=true;form.querySelectorAll('[data-creation-draft-error],[data-workspace-form-error]').forEach(error=>error.remove());
                const files=allRows().some(row=>row.querySelector('input[type=file]').files.length);
                const feedback=window.showToast('success',files?labels.draftFiles:labels.draftSaved,{placement:'workspace'});
                if(feedback?.element)window.scrollToFeedback?.(feedback.element);
            } catch {if(form.isConnected){form.inert=false;failure(labels.draftUncertain,'warning');}}
            finally {clearTimeout(timeout);finish?.();pending=false;form.inert=false;form.removeAttribute('aria-busy');controls.forEach(button=>button.disabled=false);document.dispatchEvent(new CustomEvent('workspace:form-settled',{detail:{form,saved:saved&&!allRows().some(row=>row.querySelector('input[type=file]').files.length)}}));}
        });
        window.addEventListener('pagehide',()=>request?.abort());
    }
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
        creation(form);
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
