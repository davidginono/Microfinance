(() => {
  'use strict';
  const region=document.querySelector('[data-charge-preview]');if(!region)return;
  const form=region.closest('form'), rows=region.querySelector('[data-charge-rows]'), error=region.querySelector('[data-charge-error]');
  const money=value=>new Intl.NumberFormat(document.documentElement.lang||'en',{minimumFractionDigits:2,maximumFractionDigits:2}).format(String(value));
  const policyLink=region.querySelector('[data-charge-policy-link]');
  function clear(){if(policyLink)policyLink.hidden=true;rows.replaceChildren();for(const name of ['total','deductions','separate','net','management']){const field=region.querySelector('[data-charge-'+name+']');if(field)field.textContent='—';}error.textContent='';region.setAttribute('aria-busy','true');}
  form.addEventListener('loan:quote-pending',clear);
  form.addEventListener('loan:quote-error',event=>{clear();region.setAttribute('aria-busy','false');error.textContent=event.detail;});
  form.addEventListener('loan:quote',event=>{
    const p=event.detail.charges;clear();region.setAttribute('aria-busy','false');if(!p)return;
    const insurance=region.querySelector('[data-insurance-fields]');if(insurance){insurance.hidden=!p.insuranceRequired;insurance.querySelectorAll('input').forEach(input=>input.disabled=!p.insuranceRequired);}
    if(!p.terms.length){const row=document.createElement('tr'),cell=document.createElement('td');cell.colSpan=4;cell.textContent=region.dataset.emptyLabel;row.append(cell);rows.append(row);}
    for(const t of p.terms){const row=document.createElement('tr');for(const text of [t.kind,t.basis+' · '+t.basisValue,money(t.amount),t.timing]){const cell=document.createElement('td');cell.textContent=text;row.append(cell);}rows.append(row);}
    for(const [name,value] of Object.entries({management:p.managementTotal||'0.00',total:p.total,deductions:p.deductions,separate:p.separate,net:p.netCash})){const field=region.querySelector('[data-charge-'+name+']');if(field)field.textContent=money(value);}
    error.textContent=p.error||'';
    if(policyLink){const id=form.elements.productId?.value;policyLink.hidden=!id;if(id)policyLink.href=(document.querySelector('meta[name="app-context-path"]')?.content||'')+'/finance/loan-recording/charge-policy?productId='+encodeURIComponent(id);}
  });
  window.addEventListener('pagehide',clear);
})();
