(function () {
    'use strict';
    function failure(form,message){
        var alert=form.querySelector('[data-portal-form-error]');
        if(!alert){alert=document.createElement('p');alert.dataset.portalFormError='';alert.className='console-message';alert.dataset.messageType='error';alert.setAttribute('role','alert');alert.tabIndex=-1;var actions=form.querySelector('.application-footer,.accounting-form-actions');if(actions)actions.before(alert);else form.append(alert);}
        alert.textContent=message;alert.focus({preventScroll:true});alert.scrollIntoView({block:'nearest',behavior:'instant'});
    }
    document.addEventListener('submit',async function(event){
        var form=event.target;
        if(event.defaultPrevented||!form.matches('form[data-portal-recover="true"]'))return;
        var action=new URL(form.action,location.href);
        if(action.origin!==location.origin||!action.pathname.includes('/client-portal/workspace/'))return;
        event.preventDefault();if(form.dataset.portalPending==='true')return;
        var data=new FormData(form,event.submitter),disabled=[];form.dataset.portalPending='true';form.setAttribute('aria-busy','true');
        var finishLoading=window.AppLoading?.action(event.submitter);
        form.querySelectorAll('button').forEach(function(button){if(!button.disabled){disabled.push(button);button.disabled=true;}});
        try{
            var response=await fetch(action.href,{method:'POST',body:data,credentials:'same-origin',headers:{Accept:'text/html'}});
            var destination=new URL(response.url,location.href);
            if(response.ok&&response.redirected&&destination.origin===location.origin&&destination.pathname.includes('/client-portal/workspace/')){
                document.dispatchEvent(new CustomEvent('workspace:form-settled',{detail:{form:form,saved:true}}));location.assign(destination.href);return;
            }
            var page=new DOMParser().parseFromString(await response.text(),'text/html');
            var returned=page.querySelector('form[data-onboarding-form="self"]');
            if(form.dataset.onboardingForm==='self'&&returned&&
                !form.dispatchEvent(new CustomEvent('profile:validation-response',{cancelable:true,detail:returned})))return;
            var messages=Array.from(page.querySelectorAll('.field-error,[data-message-type="error"]')).map(function(e){return e.textContent.trim();}).filter(Boolean);
            failure(form,messages.length?Array.from(new Set(messages)).join(' '):document.body.dataset.portalSaveError);
        }catch(_){
            failure(form,document.body.dataset.portalSaveError);
        }finally{
            finishLoading?.();
            form.dataset.portalPending='false';form.removeAttribute('aria-busy');disabled.forEach(function(button){button.disabled=false;});document.dispatchEvent(new CustomEvent('workspace:form-settled',{detail:{form:form}}));
        }
    });
})();
