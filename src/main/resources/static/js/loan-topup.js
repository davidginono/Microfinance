(() => {
  'use strict';
  const root=document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-topup-summary]').forEach(region=>{
    const form=region.closest('form');if(!form)return;
    const posting=region.hasAttribute('data-topup-posting');
    const date=form.elements[posting?'disbursementDate':'applicationDate'];
    const principal=form.elements.topUpAdditionalAmount || form.elements.principal;
    const error=region.querySelector('[data-topup-error]');
    const indicator=region.querySelector('[data-topup-loading]');
    let version=0,controller,timer;
    function loading(waiting){
      region.setAttribute('aria-busy',String(waiting));
      if(indicator)indicator.hidden=!waiting;
    }
    function clearSettlement(){
      for(const field of ['principal','interest','excluded','total','cash','charges']){const node=region.querySelector('[data-topup-'+field+']');if(node)node.textContent='—';}
    }
    function invalidate(){++version;controller?.abort();clearTimeout(timer);loading(false);
      if(posting)clearSettlement();
      if(posting){form.elements.settlementSequence.value='';form.elements.settlementPrincipal.value='';form.elements.settlementInterest.value='';if(form.elements.settlementCharges)form.elements.settlementCharges.value='';if(form.elements.settlementChargeSequence)form.elements.settlementChargeSequence.value='';region.querySelector('[name="settlementConfirmed"]').checked=false;}
    }
    function formatMoney(value){
          const locale=document.documentElement.lang || undefined,formatter=new Intl.NumberFormat(locale,{maximumFractionDigits:0});
          const decimal=new Intl.NumberFormat(locale).formatToParts(1.1).find(part=>part.type==='decimal')?.value || '.';
          const raw=String(value),negative=raw.startsWith('-'),[whole,fraction='']=raw.replace(/^-/,'').split('.');return (negative?'-':'')+formatter.format(BigInt(whole))+decimal+fraction.padEnd(2,'0');
    }
    function apply(q){
          for(const [field,value] of Object.entries({principal:q.principal,interest:q.interest,excluded:q.excludedInterest,total:q.total,cash:q.cash,charges:q.charges||'0.00'})){
            const node=region.querySelector('[data-topup-'+field+']');if(node)node.textContent=formatMoney(value);
          }
          region.querySelector('[data-topup-date]').textContent=date.value;
          if(posting){form.dataset.confirmMessage=region.dataset.confirmation+' '+region.dataset.sourceNumber+' · TZS '+formatMoney(q.cash);form.elements.settlementSequence.value=q.sequence;form.elements.settlementPrincipal.value=q.principal;form.elements.settlementInterest.value=q.interest;if(form.elements.settlementCharges)form.elements.settlementCharges.value=q.charges||'0.00';if(form.elements.settlementChargeSequence)form.elements.settlementChargeSequence.value=q.chargeSequence||0;
            const interest=region.querySelector('[data-topup-interest-account]');if(interest){interest.hidden=Number(q.interest)===0;interest.querySelector('select').required=Number(q.interest)>0;}}
    }
    if(posting){
      form.addEventListener('funding:pending',invalidate);
      form.addEventListener('funding:quote',event=>{error.textContent='';apply(event.detail);});
      form.addEventListener('funding:error',()=>{invalidate();error.textContent=region.dataset.error;});
      return;
    }
    async function refresh(){
      invalidate();const request=version;if(!date?.value){clearSettlement();return;}
      timer=setTimeout(async()=>{
        controller=new AbortController();loading(true);
        try{
          const url=new URL(root+'/finance/loan-recording/top-up-quote',location.origin);
          url.searchParams.set('date',date.value);
          url.searchParams.set('source',region.dataset.source);if(principal?.value)url.searchParams.set('additionalAmount',principal.value);
          const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:controller.signal});
          if(response.redirected)throw new Error(region.dataset.error);
          const q=await response.json();if(request!==version)return;
          if(!response.ok)throw new Error(q.error || region.dataset.error);
          apply(q);error.textContent='';
        }catch(failure){if(request===version && failure.name!=='AbortError'){clearSettlement();error.textContent=failure.message || region.dataset.error;}}
        finally{if(request===version)loading(false);}
      },350);
    }
    form.addEventListener('loan:quote',event=>{
      const result=event.detail.topUp;
      if(!result)return;
      for(const [field,value] of Object.entries({'new-principal':result.principal,deductions:result.deductions,'net-cash':result.netCash})){
        region.querySelector('[data-topup-'+field+']').textContent=formatMoney(value);
      }
    });
    const clearQuote=()=>{
      for(const field of ['new-principal','deductions','net-cash']) {
        const node=region.querySelector('[data-topup-'+field+']');if(node)node.textContent='—';
      }
    };
    form.addEventListener('loan:quote-error',clearQuote);
    form.addEventListener('loan:quote-reset',clearQuote);
    date?.addEventListener('change',refresh);principal?.addEventListener('input',refresh);
    window.addEventListener('pagehide',invalidate);window.addEventListener('pageshow',refresh);refresh();
  });
})();
