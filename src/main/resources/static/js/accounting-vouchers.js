(() => {
'use strict';
const register=document.querySelector('[data-voucher-register]');
if(register){
 let selected=null;
 const buttons=[...register.querySelectorAll('[data-selection-action]')],status=register.querySelector('[data-selection-status]');
 const clear=()=>{selected=null;register.querySelectorAll('[data-voucher-id]').forEach(r=>{r.classList.remove('is-selected');r.querySelector('input').checked=false;});buttons.forEach(b=>b.disabled=true);status.textContent=status.dataset.empty;};
 register.addEventListener('click',e=>{const row=e.target.closest('[data-voucher-id]');if(!row || e.target.closest('a,button'))return;const next=row.dataset.voucherId===selected?null:row;clear();if(next){selected=next.dataset.voucherId;next.classList.add('is-selected');next.querySelector('input').checked=true;buttons.forEach(b=>b.disabled=b.dataset.selectionAction==='topup' && next.dataset.topupEligible!=='true');status.textContent=status.dataset.selected+' '+next.querySelector('a').textContent;}});
 buttons.forEach(b=>b.addEventListener('click',()=>{if(!selected)return;const url=register.dataset.base+'/'+encodeURIComponent(selected);if(b.dataset.selectionAction==='topup')window.WorkspaceNavigation.navigate(register.dataset.base+'/new?topUpLoanId='+encodeURIComponent(selected));else if(b.dataset.selectionAction==='print')window.printExport(url+'/print');else window.WorkspaceNavigation.navigate(url+(b.dataset.selectionAction==='pdf'?'/pdf':''));}));
 window.addEventListener('pageshow',clear);
 register.addEventListener('resource-table:reset',clear);
 register.addEventListener('resource-table:updated',clear);
 register.querySelectorAll('.voucher-filters').forEach(filters=>{
  filters.addEventListener('input',clear);
  filters.addEventListener('change',clear);
  filters.addEventListener('submit',clear);
 });
 clear();
}
const form=document.querySelector('[data-voucher-form]');
if(!form)return;
const rows=form.querySelector('[data-voucher-rows]'), template=rows.firstElementChild.cloneNode(true),status=form.querySelector('[data-row-status]');
window.VoucherSelect.cleanClone(template);
const field=(row,suffix)=>row.querySelector('input[name$=".'+suffix+'"]');
const moneyOnly=new Set(['CASH','BANK','MOBILE_MONEY']),pickers=new WeakMap();
const loanMode=()=>form.querySelector('[data-receipt-purpose]')?.value==='LOAN_REPAYMENT';
function update(){
 const loan=loanMode();
 form.querySelectorAll('[data-loan-only]').forEach(el=>{el.hidden=!loan;el.querySelectorAll('input,button,select').forEach(c=>c.disabled=!loan);});
 form.querySelectorAll('[data-other-only]').forEach(el=>{el.hidden=loan;if(el.matches('select')){el.disabled=loan;const wrapper=el.closest('.neo-select');if(wrapper)wrapper.hidden=loan;}el.querySelectorAll('input,button,select').forEach(c=>c.disabled=loan);});
 form.querySelector('[name="reference"]').maxLength=loan?80:160;
 const all=[...rows.children];
 all.forEach((r,i)=>{r.querySelector('[data-row-number]').textContent=i+1;r.querySelectorAll('[name]').forEach(el=>{el.name=el.name.replace(/rows\[\d+\]/,'rows['+i+']');el.removeAttribute('id');});r.querySelectorAll('[data-account-for]').forEach(b=>b.dataset.accountFor=b.dataset.accountFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelectorAll('[data-choice-for]').forEach(b=>b.dataset.choiceFor=b.dataset.choiceFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelector('[data-remove-row]').disabled=all.length===1;const mapped=!!field(r,'transactionId').value;r.querySelector('[data-manual]').hidden=loan||!mapped;r.querySelectorAll('[data-manual-accounts]').forEach(el=>{el.hidden=loan||mapped;el.querySelectorAll('input,button,select').forEach(c=>c.disabled=loan);});r.querySelectorAll('[data-loan-choice="loans"]').forEach(b=>b.disabled=!loan||!form.elements.clientId.value);});
 bindChoices();form.querySelectorAll('[data-voucher-select]').forEach(select=>pickers.get(select)?.sync());
 form.querySelector('[data-add-row]').disabled=all.length>=50;
 let cents=0n;
 all.forEach(r=>{const match=/^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(field(r,'amount').value);if(match){const fractional=match[2]||'',exponent=Number(match[3]||0),shift=2+exponent-fractional.length;if(Number.isInteger(exponent)&&shift>=0&&shift<=20)cents+=BigInt(match[1]+fractional)*10n**BigInt(shift);}});
 form.querySelector('[data-live-total]').value=(cents/100n).toLocaleString(document.documentElement.lang||'en')+'.'+(cents%100n).toString().padStart(2,'0');
}
form.querySelector('[data-receipt-purpose]')?.addEventListener('change',()=>{
 rows.querySelectorAll('input').forEach(input=>{if(/\.(loanId|transactionId|templateKey|debitAccountId|creditAccountId)$/.test(input.name))input.value='';});
 rows.querySelectorAll('[data-account-label],[data-mapping-label]').forEach(el=>el.textContent='');
 form.querySelectorAll('[data-voucher-select]').forEach(select=>pickers.get(select)?.reset());
 if(form.elements.interestAccountId)form.elements.interestAccountId.value='';
 update();form.dispatchEvent(new Event('input',{bubbles:true}));
});
form.addEventListener('input',()=>{const preview=form.querySelector('[data-voucher-preview]');if(preview)preview.hidden=true;update();});
form.addEventListener('submit',e=>{let hidden=form.querySelector('[data-submit-action]');if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.name='action';hidden.dataset.submitAction='';form.append(hidden);}hidden.value=e.submitter?.value||'post';});
form.querySelector('[data-add-row]').addEventListener('click',()=>{
 if(rows.children.length>=50){status.textContent=form.dataset.limit;return;}
 const row=template.cloneNode(true);row.querySelectorAll('input').forEach(i=>i.value=i.name.endsWith('.component')?'TOTAL':'');row.querySelectorAll('[data-account-label]').forEach(e=>e.textContent=form.dataset.chooseAccount);row.querySelectorAll('.coa-error').forEach(e=>e.remove());row.querySelector('[data-mapping-label]').textContent='';row.querySelectorAll('input').forEach(i=>{i.removeAttribute('aria-invalid');i.removeAttribute('aria-describedby');});rows.append(row);update();row.querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));
});
function bindChoices(){
 form.querySelectorAll('[data-voucher-select]').forEach(select=>{
  if(pickers.has(select))return;
  const mapping=select.hasAttribute('data-mapping'),row=select.closest('[data-voucher-row]'),holder=select.closest('.voucher-account-choice');
  const input=mapping?{get value(){return field(row,'transactionId').value?field(row,'templateKey').value+'|'+field(row,'component').value:'';},set value(value){}}:form.elements.namedItem(select.dataset.choiceFor||select.dataset.accountFor);
  const label=()=>mapping?row.querySelector('[data-mapping-label]').textContent:holder.querySelector('[data-account-label]').textContent;
  pickers.set(select,window.VoucherSelect.bind(select,{field:input,label,error:form.dataset.lookupError,
   url:()=>{const path=select.hasAttribute('data-voucher-client')?(loanMode()?'loan-choices/clients':'clients'):select.dataset.loanChoice?'loan-choices/'+select.dataset.loanChoice:mapping?'transactions':'accounts';const url=new URL(form.dataset.base+'/'+path,location.origin);if(select.dataset.loanChoice==='loans')url.searchParams.set('clientId',form.elements.clientId.value);return url;},
   choice:item=>mapping?{value:item.templateKey+'|'+item.component,label:item.transactionName+' · '+item.debit.name+' → '+item.credit.name}:select.hasAttribute('data-money-only')&&!moneyOnly.has(item.purpose)?null:{value:item.id,label:item.label||item.name},
   changed:(item,text)=>{
    if(mapping){field(row,'transactionId').value=item.transactionId;field(row,'templateKey').value=item.templateKey;field(row,'component').value=item.component;row.querySelector('[data-mapping-label]').textContent=text;if(!field(row,'description').value)field(row,'description').value=item.transactionName;}
    else{holder.querySelector('[data-account-label]').textContent=text;if(select.hasAttribute('data-voucher-client')){rows.querySelectorAll('input[name$=".loanId"]').forEach(input=>{input.value='';input.parentElement.querySelector('[data-account-label]').textContent='';});rows.querySelectorAll('[data-loan-choice="loans"]').forEach(loan=>pickers.get(loan)?.reset());}}
    form.dispatchEvent(new Event('input',{bubbles:true}));
   }
  }));
 });
}
form.addEventListener('click',e=>{
 const clearClient=e.target.closest('[data-client-clear]');if(clearClient&&!loanMode()){form.elements.clientId.value='';clearClient.closest('.voucher-account-choice').querySelector('[data-account-label]').textContent='';pickers.get(form.querySelector('[data-voucher-client]'))?.reset();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const remove=e.target.closest('[data-remove-row]');if(remove && rows.children.length>1){const previous=remove.closest('[data-voucher-row]').previousElementSibling;remove.closest('[data-voucher-row]').remove();update();(previous||rows.firstElementChild).querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const manual=e.target.closest('[data-manual]');if(manual){const row=manual.closest('[data-voucher-row]');field(row,'transactionId').value='';field(row,'templateKey').value='';field(row,'component').value='TOTAL';row.querySelector('[data-mapping-label]').textContent='';pickers.get(row.querySelector('[data-mapping]'))?.reset();update();row.querySelector('[data-account-for]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}

});
update();
})();
