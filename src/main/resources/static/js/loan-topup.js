(() => {
  'use strict';
  const root=document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-topup-summary]').forEach(region=>{
    const form=region.closest('form');if(!form)return;
    const posting=region.hasAttribute('data-topup-posting');
    const date=form.elements[posting?'disbursementDate':'applicationDate'];
    const principal=form.elements.principal;
    const error=region.querySelector('[data-topup-error]');
    let version=0,controller,timer;
    function invalidate(){++version;controller?.abort();clearTimeout(timer);
      for(const field of ['principal','interest','excluded','total','cash']){const node=region.querySelector('[data-topup-'+field+']');if(node)node.textContent='—';}
      if(posting){form.elements.settlementSequence.value='';form.elements.settlementPrincipal.value='';form.elements.settlementInterest.value='';region.querySelector('[name="settlementConfirmed"]').checked=false;}
    }
    async function refresh(){
      invalidate();const request=version;if(!date?.value)return;
      timer=setTimeout(async()=>{
        controller=new AbortController();region.setAttribute('aria-busy','true');error.textContent='';
        try{
          const url=new URL(posting?region.dataset.quoteUrl:root+'/finance/loan-recording/top-up-quote',location.origin);
          url.searchParams.set('date',date.value);
          if(!posting){url.searchParams.set('source',region.dataset.source);if(principal?.value)url.searchParams.set('principal',principal.value);}
          const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:controller.signal});
          if(response.redirected)throw new Error(region.dataset.error);
          const q=await response.json();if(request!==version)return;
          if(!response.ok)throw new Error(q.error || region.dataset.error);
          const locale=document.documentElement.lang || undefined,formatter=new Intl.NumberFormat(locale,{maximumFractionDigits:0});
          const decimal=new Intl.NumberFormat(locale).formatToParts(1.1).find(part=>part.type==='decimal')?.value || '.';
          const money=value=>{const raw=String(value),negative=raw.startsWith('-'),[whole,fraction='']=raw.replace(/^-/,'').split('.');return (negative?'-':'')+formatter.format(BigInt(whole))+decimal+fraction.padEnd(2,'0');};
          for(const [field,value] of Object.entries({principal:q.principal,interest:q.interest,excluded:q.excludedInterest,total:q.total,cash:q.cash})){
            const node=region.querySelector('[data-topup-'+field+']');if(node)node.textContent=money(value);
          }
          region.querySelector('[data-topup-date]').textContent=date.value;
          if(posting){form.dataset.confirmMessage=region.dataset.confirmation+' '+region.dataset.sourceNumber+' · TZS '+money(q.cash);form.elements.settlementSequence.value=q.sequence;form.elements.settlementPrincipal.value=q.principal;form.elements.settlementInterest.value=q.interest;
            if(form.elements.depositAmount){form.elements.depositAmount.value=q.cash;form.elements.depositAmount.dispatchEvent(new Event('input',{bubbles:true}));}const interest=region.querySelector('[data-topup-interest-account]');if(interest){interest.hidden=Number(q.interest)===0;interest.querySelector('select').required=Number(q.interest)>0;}}
        }catch(failure){if(request===version && failure.name!=='AbortError')error.textContent=failure.message || region.dataset.error;}
        finally{if(request===version)region.setAttribute('aria-busy','false');}
      },350);
    }
    date?.addEventListener('change',refresh);principal?.addEventListener('input',refresh);
    window.addEventListener('pagehide',invalidate);window.addEventListener('pageshow',refresh);refresh();
  });
})();
