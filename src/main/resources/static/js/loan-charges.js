(() => {
  'use strict';
  const region=document.querySelector('[data-charge-preview]');if(!region)return;
  const form=region.closest('form'), rows=region.querySelector('[data-charge-rows]'), error=region.querySelector('[data-charge-error]');
  const money=value=>new Intl.NumberFormat(document.documentElement.lang||'en',{minimumFractionDigits:2,maximumFractionDigits:2}).format(String(value));
  const policyLink=region.querySelector('[data-charge-policy-link]');
  const mode=form.querySelector('[name="loanMode"]'), management=region.querySelector('[data-management-charge-inputs]'), insurance=region.querySelector('[data-insurance-fields]');
  let insuranceRequired=false, lastMode;
  function entryMode(){
    const manual=mode?.value==='CUSTOM';
    if(lastMode && lastMode!==mode?.value){
      region.querySelectorAll('[data-recorded-fee],[name="insuranceEvidence"]').forEach(input=>input.value='');
      insuranceRequired=false;
    }
    const help=region.querySelector('[data-manual-charge-help]'), permission=region.querySelector('[data-manual-charge-permission]');
    if(help)help.hidden=!manual;if(permission)permission.hidden=!manual;
    region.querySelectorAll('[data-configured-charge-summary],[data-configured-charge-help]').forEach(box=>box.hidden=manual);
    if(management){
      if(manual)management.open=true;else if(lastMode==='CUSTOM')management.open=false;
      management.querySelector('summary').hidden=manual;
      management.querySelectorAll('input').forEach(input=>input.required=manual);
    }
    if(insurance){insurance.hidden=!manual && !insuranceRequired;insurance.querySelectorAll('input').forEach(input=>input.disabled=!manual && !insuranceRequired);}
    region.querySelector('[name="insurancePremium"]')?.toggleAttribute('required',manual || insuranceRequired);
    region.querySelector('[name="insuranceEvidence"]')?.toggleAttribute('required',insuranceRequired);
    lastMode=mode?.value;
  }
  // Run before the quote listener serializes the form on a mode change.
  form.addEventListener('change',event=>{if(event.target===mode)entryMode();},true);
  entryMode();
  function clear(){if(policyLink)policyLink.hidden=true;rows.replaceChildren();for(const name of ['total','deductions','separate','net','management']){const field=region.querySelector('[data-charge-'+name+']');if(field)field.textContent='—';}error.textContent='';region.querySelector(':scope > [data-ui-loading-error]')?.remove();region.setAttribute('aria-busy','false');}
  form.addEventListener('loan:quote-reset',clear);
  form.addEventListener('loan:quote-pending',()=>{clear();region.setAttribute('aria-busy','true');});
  form.addEventListener('loan:quote-settled',()=>region.setAttribute('aria-busy','false'));
  form.addEventListener('loan:quote-error',event=>{clear();if(window.AppLoading?.fail)window.AppLoading.fail(region,{label:event.detail,retry:()=>form.dispatchEvent(new Event('loan:quote-retry'))});else error.textContent=event.detail;});
  form.addEventListener('loan:quote',event=>{
    const p=event.detail.charges;clear();if(!p)return;
    insuranceRequired=!!p.insuranceRequired;entryMode();
    if(!p.terms.length){const row=document.createElement('tr'),cell=document.createElement('td');cell.colSpan=4;cell.textContent=region.dataset.emptyLabel;row.append(cell);rows.append(row);}
    for(const t of p.terms){const row=document.createElement('tr');for(const text of [t.kind,t.basis+' · '+t.basisValue,money(t.amount),t.timing]){const cell=document.createElement('td');cell.textContent=text;row.append(cell);}rows.append(row);}
    for(const [name,value] of Object.entries({management:p.managementTotal||'0.00',total:p.total,deductions:p.deductions,separate:p.separate,net:p.netCash})){const field=region.querySelector('[data-charge-'+name+']');if(field)field.textContent=money(value);}
    error.textContent=p.error||'';
    if(policyLink){const id=form.elements.productId?.value;policyLink.hidden=!id;if(id)policyLink.href=(document.querySelector('meta[name="app-context-path"]')?.content||'')+'/finance/loan-recording/charge-policy?productId='+encodeURIComponent(id);}
  });
  window.addEventListener('pagehide',clear);
})();
