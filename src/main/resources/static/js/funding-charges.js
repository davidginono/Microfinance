(() => {
    'use strict';
    const ready=window.WorkspaceNavigation?.onReady || (callback=>callback());
    ready(()=>document.querySelectorAll('form[data-funding-quote-url]').forEach(form=>{
        if(form.dataset.fundingReady)return;form.dataset.fundingReady='true';
        const date=form.elements.disbursementDate,amount=form.elements.depositAmount;
        const error=form.querySelector('[data-funding-error]'),submit=form.querySelector('[data-funding-submit]');
        const toggle=form.querySelector('[data-funding-picker-toggle]'),picker=form.querySelector('.funding-charge-picker');
        const initiallyDisabled=!!submit?.disabled;
        const lifetime=new AbortController();
        const loading=form.querySelector('[data-funding-cash]');
        let controller,version=0;
        const money=value=>{const [whole,fraction='']=String(value).split('.');return whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')+'.'+fraction.padEnd(2,'0');};
        toggle?.addEventListener('click',()=>{picker.hidden=!picker.hidden;toggle.setAttribute('aria-expanded',String(!picker.hidden));if(!picker.hidden)picker.querySelector('input:not(:disabled)')?.focus();});
        async function refresh(){
            const current=++version;controller?.abort();const active=controller=new AbortController();
            if(loading)window.AppLoading?.end(loading);
            if(submit)submit.disabled=true;
            if(amount)amount.value='';
            for(const key of ['deductions','separate','cash']){const node=form.querySelector('[data-funding-'+key+']');if(node)node.textContent='—';}
            if(error){error.hidden=true;error.textContent='';}
            const confirmation=form.querySelector('[data-funding-confirm]');if(confirmation)confirmation.checked=false;
            form.dispatchEvent(new Event('funding:pending'));
            if(!date?.value)return;
            if(loading)window.AppLoading?.begin(loading,{table:false,size:'small'});
            const timeout=setTimeout(()=>active.abort(),10000);
            try{
                const url=new URL(form.dataset.fundingQuoteUrl,location.origin);url.searchParams.set('date',date.value);
                form.querySelectorAll('[name="deductChargeIds"]:checked').forEach(input=>url.searchParams.append('deductChargeIds',input.value));
                const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:active.signal});
                if(response.redirected)throw new Error(form.dataset.fundingError);
                const quote=await response.json();if(current!==version)return;
                if(!response.ok)throw new Error(quote.error || form.dataset.fundingError);
                if(!['cash','deductions','separate'].every(key=>/^\d+\.\d{2}$/.test(quote[key])))throw new Error(form.dataset.fundingError);
                if(amount)amount.value=quote.cash;
                for(const key of ['deductions','separate','cash']){const node=form.querySelector('[data-funding-'+key+']');if(node)node.textContent=money(quote[key]);}
                form.dispatchEvent(new CustomEvent('funding:quote',{detail:quote}));
                if(submit)submit.disabled=initiallyDisabled;
            }catch(failure){
                if(current!==version)return;
                if(error){error.textContent=failure.name==='AbortError'?form.dataset.fundingError:failure.message || form.dataset.fundingError;error.hidden=false;}
                form.dispatchEvent(new Event('funding:error'));
            }finally{clearTimeout(timeout);if(current===version && loading)window.AppLoading?.end(loading);}
        }
        picker?.addEventListener('change',refresh);
        date?.addEventListener('change',refresh);
        form.addEventListener('submit',event=>{if(submit?.disabled)event.preventDefault();});
        window.addEventListener('pagehide',()=>{++version;controller?.abort();},{signal:lifetime.signal});
        window.addEventListener('pageshow',event=>{if(event.persisted)void refresh();},{signal:lifetime.signal});
        document.addEventListener('workspace:navigated',()=>{if(!form.isConnected){++version;controller?.abort();lifetime.abort();}},{signal:lifetime.signal});
        void refresh();
    }));
})();
