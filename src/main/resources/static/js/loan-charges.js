(() => {
  'use strict';
  const region=document.querySelector('[data-charge-preview]');if(!region)return;
  const form=region.closest('form'),mode=form.elements.loanMode,error=form.querySelector('[data-charge-error]');
  const rows=[...region.querySelectorAll('[data-charge-kind]')],loading=region.querySelector('[data-charge-loading]');
  let previousMode=mode.value;
  const manual=()=>mode.value==='CUSTOM';
  function feedback(message=''){if(error){error.textContent=message;error.hidden=!message;}}
  function entryMode(){
    const changed=previousMode!==mode.value;
    for(const row of rows){
      const basis=row.querySelector('[data-charge-basis]');
      if(basis && (!basis.value || changed))basis.value='AMOUNT';
      const editable=manual() && region.dataset.customAllowed==='true';
      row.querySelectorAll('[data-charge-amount],[data-charge-percentage]').forEach(input=>{
        if(changed)input.value='';input.readOnly=!editable;
        input.required=editable && (input.matches('[data-charge-percentage]')?basis?.value==='PERCENTAGE':!basis || basis.value==='AMOUNT');
      });
    }
    previousMode=mode.value;
  }
  form.addEventListener('change',event=>{if(event.target===mode)entryMode();},true);
  function changed(event){
    const row=event.target.closest('[data-charge-kind]');if(!row || !manual() || !event.target.matches('[data-charge-amount],[data-charge-percentage]'))return;
    const basis=row.querySelector('[data-charge-basis]');if(basis)basis.value=event.target.matches('[data-charge-percentage]')?'PERCENTAGE':'AMOUNT';
    entryMode();
  }
  region.addEventListener('input',changed,true);region.addEventListener('change',changed,true);
  entryMode();
  function end(){window.AppLoading?.end(loading);if(loading)loading.hidden=true;region.setAttribute('aria-busy','false');}
  function clear(){
    end();feedback();
    for(const row of rows){
      const basis=row.querySelector('[data-charge-basis]')?.value||'AMOUNT';
      row.querySelectorAll('[data-charge-amount],[data-charge-percentage]').forEach(input=>{
        const source=basis==='PERCENTAGE'?input.matches('[data-charge-percentage]'):input.matches('[data-charge-amount]');
        if(!manual() || !source)input.value='';
      });
    }
  }
  form.addEventListener('loan:quote-reset',clear);
  form.addEventListener('loan:quote-pending',()=>{clear();region.setAttribute('aria-busy','true');if(loading){loading.hidden=false;window.AppLoading?.begin(loading,{table:false,size:'small'});}});
  form.addEventListener('loan:quote-settled',end);
  form.addEventListener('loan:quote-error',event=>{clear();feedback(event.detail);});
  form.addEventListener('loan:quote',event=>{
    end();const plan=event.detail.charges;if(!plan){clear();return;}
    for(const term of plan.terms){
      const row=rows.find(row=>row.dataset.chargeKind===term.code);if(!row)continue;
      const amount=row.querySelector('[data-charge-amount]'),percentage=row.querySelector('[data-charge-percentage]');
      const basis=row.querySelector('[data-charge-basis]')?.value||'AMOUNT';
      const source=basis==='PERCENTAGE'?percentage:amount;
      if(!manual()){amount.value=term.amount;if(percentage)percentage.value=term.percentage;}
      else if(source.value!==''){if(basis==='PERCENTAGE')amount.value=term.amount;else if(percentage)percentage.value=term.percentage;}
    }
    feedback(plan.error||'');
  });
  window.addEventListener('pagehide',clear);
})();
