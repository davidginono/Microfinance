(() => {
'use strict';
const register=document.querySelector('[data-voucher-register]');
if(register){
 let selected=null;
 const buttons=[...register.querySelectorAll('[data-selection-action]')],status=register.querySelector('[data-selection-status]');
 const clear=()=>{selected=null;register.querySelectorAll('[data-voucher-id]').forEach(r=>{r.classList.remove('is-selected');r.querySelector('input').checked=false;});buttons.forEach(b=>b.disabled=true);status.textContent=status.dataset.empty;};
 register.addEventListener('click',e=>{const row=e.target.closest('[data-voucher-id]');if(!row || e.target.closest('a,button'))return;const next=row.dataset.voucherId===selected?null:row;clear();if(next){selected=next.dataset.voucherId;next.classList.add('is-selected');next.querySelector('input').checked=true;buttons.forEach(b=>b.disabled=(b.dataset.selectionAction==='topup' && next.dataset.topupEligible!=='true') || (b.dataset.selectionAction==='cancel' && (next.dataset.cancelEligible!=='true' || (next.dataset.cancelFunded==='true' && register.dataset.canCancelFunded!=='true'))) || (['pdf','print'].includes(b.dataset.selectionAction) && next.dataset.recordExportable==='false'));status.textContent=status.dataset.selected+' '+next.querySelector('a').textContent;}});
 buttons.forEach(b=>b.addEventListener('click',()=>{if(!selected || b.disabled)return;const row=[...register.querySelectorAll('[data-voucher-id]')].find(row=>row.dataset.voucherId===selected);const url=row?.dataset.recordUrl || register.dataset.base+'/'+encodeURIComponent(selected);if(b.dataset.selectionAction==='topup')window.WorkspaceNavigation.navigate(register.dataset.base+'/new?topUpLoanId='+encodeURIComponent(selected));else if(b.dataset.selectionAction==='cancel')window.WorkspaceNavigation.navigate(url+'/cancel');else if(b.dataset.selectionAction==='print')window.printExport(url+'/print');else window.WorkspaceNavigation.navigate(url+(b.dataset.selectionAction==='pdf'?'/pdf':''));}));
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
const equityMode=()=>form.elements.namedItem('equity')?.value==='true';
const activity=()=>form.elements.namedItem('activityId')?.value||'';
const moneyCents=window.VoucherBalance.cents,showMoney=window.VoucherBalance.format;
function update(){
 const loan=loanMode();
 form.querySelectorAll('[data-equity-only]').forEach(el=>{el.hidden=!equityMode();el.querySelectorAll('[data-equity-control]').forEach(control=>{control.disabled=!equityMode();control.required=equityMode();});});
 form.querySelectorAll('[data-loan-only]').forEach(el=>{el.hidden=!loan;el.querySelectorAll('input,button,select').forEach(c=>c.disabled=!loan);});
 form.querySelectorAll('[data-other-only]').forEach(el=>{el.hidden=loan;if(el.matches('select')){el.disabled=loan;const wrapper=el.closest('.neo-select');if(wrapper)wrapper.hidden=loan;}el.querySelectorAll('input,button,select').forEach(c=>c.disabled=loan);});
 form.querySelector('[name="reference"]').maxLength=loan?80:160;
 const all=[...rows.children];
 all.forEach((r,i)=>{r.querySelector('[data-row-number]').textContent=i+1;r.querySelectorAll('[name]').forEach(el=>{el.name=el.name.replace(/rows\[\d+\]/,'rows['+i+']');el.removeAttribute('id');});r.querySelectorAll('[data-account-for]').forEach(b=>b.dataset.accountFor=b.dataset.accountFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelectorAll('[data-choice-for]').forEach(b=>b.dataset.choiceFor=b.dataset.choiceFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelector('[data-remove-row]').disabled=all.length===1;const mapped=!!field(r,'transactionId').value;const choices=r.querySelector('[data-configured-transactions]');if(choices)choices.hidden=loan||!activity();const mapping=r.querySelector('[data-mapping]');mapping.disabled=loan||!activity();r.querySelectorAll('[data-manual-accounts]').forEach(el=>{el.hidden=loan||mapped||!!activity()||form.dataset.newVoucher==='true';el.querySelectorAll('input,button,select').forEach(c=>c.disabled=loan||mapped||!!activity()||form.dataset.newVoucher==='true');});r.querySelectorAll('[data-loan-choice="loans"]').forEach(b=>b.disabled=!loan||!form.elements.clientId.value);});
 all.forEach(r=>{
  const mapped=!!field(r,'transactionId').value,direct=!loan&&!mapped&&!activity(),editable=direct&&form.dataset.newVoucher!=='true';
  if(editable&&!r.querySelector('[data-split-entry]')){
   for(const side of ['DEBIT','CREDIT']){const money=form.dataset.kind==='RECEIPT'?side==='DEBIT':form.dataset.kind==='PAYMENT'?side==='CREDIT':false;const account=money?form.elements.moneyAccountId:field(r,side==='DEBIT'?'debitAccountId':'creditAccountId');addPosting(r,side,(money&&form.dataset.pettyCash==='true'?form.dataset.pettyAccount:account?.value)||'',account?.dataset.postingLabel||account?.closest('.voucher-account-choice')?.querySelector('[data-account-label]')?.textContent||'',field(r,'amount').value);}
   for(const side of ['debit','credit']){const account=field(r,side+'AccountId');if(account)account.value='';}
  }
  const entries=[...r.querySelectorAll('[data-split-entry]')],split=entries.length>0;
  const rowAmount=field(r,'amount'),amountLabel=r.querySelector('[data-transaction-amount]');
  rowAmount.type=direct?'hidden':'number';rowAmount.required=!direct;rowAmount.dataset.amountInput=direct?'false':'true';if(amountLabel)amountLabel.hidden=direct;
  if(!direct)window.AmountInputs?.refresh(rowAmount);
  entries.forEach((entry,n)=>{
   entry.querySelectorAll('[name]').forEach(el=>el.name=el.name.replace(/entries\[\d+\]/,'entries['+n+']'));
   const select=entry.querySelector('[data-entry-account]'),side=entry.querySelector('[data-entry-side]').value;
   select.dataset.accountFor=entry.querySelector('input[name$=".accountId"]').name;
   const money=form.dataset.kind==='RECEIPT'?side==='DEBIT':form.dataset.kind==='PAYMENT'?side==='CREDIT':false;
   select.dataset.purpose=form.dataset.kind==='JOURNAL'?'':money?'MONEY':form.dataset.pettyCash==='true'?'EXPENSE':'OTHER';
   const same=entries.filter(e=>e.querySelector('[data-entry-side]').value===side);
   entry.querySelector('[data-remove-entry]').disabled=same.length===1||!editable;
  });
  const totals=r.querySelector('[data-split-totals]');
  if(totals){
   totals.hidden=!split||!editable;
  }
  r.querySelectorAll('[data-add-entry]').forEach(b=>b.disabled=!editable||entries.length>=50||(form.dataset.pettyCash==='true'&&b.dataset.addEntry==='DEBIT'));
 });
 window.VoucherBalance.refresh(form);
 const moneyHeader=form.querySelector('[data-header-money-account]');
 if(moneyHeader){const needed=loan||!!activity()||all.some(r=>!!field(r,'transactionId').value);moneyHeader.hidden=!needed;moneyHeader.querySelectorAll('input,select,button').forEach(c=>c.disabled=!needed);}
 bindChoices();form.querySelectorAll('[data-voucher-select]').forEach(select=>pickers.get(select)?.sync());
 form.querySelector('[data-add-row]').disabled=all.length>=50;
 const cents=all.reduce((sum,r)=>sum+(moneyCents(field(r,'amount').value)||0n),0n);
 form.querySelector('[data-live-total]').value=showMoney(cents);
}
form.querySelector('[data-receipt-purpose]')?.addEventListener('change',()=>{
 rows.querySelectorAll('[data-split-entry]').forEach(entry=>entry.remove());
 rows.querySelectorAll('input').forEach(input=>{if(/\.(loanId|transactionId|templateKey|debitAccountId|creditAccountId)$/.test(input.name))input.value='';});
 rows.querySelectorAll('input[name$=".component"]').forEach(input=>input.value='TOTAL');
 rows.querySelectorAll('[data-account-label],[data-mapping-label]').forEach(el=>el.textContent='');
 form.querySelectorAll('[data-voucher-select]').forEach(select=>pickers.get(select)?.reset());
 if(form.elements.interestAccountId)form.elements.interestAccountId.value='';
 update();form.dispatchEvent(new Event('input',{bubbles:true}));
});
const invalidatePreview=()=>{form.querySelectorAll('[data-voucher-preview]').forEach(preview=>preview.hidden=true);update();};
form.addEventListener('input',invalidatePreview);form.addEventListener('change',invalidatePreview);
form.addEventListener('submit',e=>{let hidden=form.querySelector('[data-submit-action]');if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.name='action';hidden.dataset.submitAction='';form.append(hidden);}hidden.value=e.submitter?.value||'post';});
form.querySelector('[data-add-row]').addEventListener('click',()=>{
 if(rows.children.length>=50){status.textContent=form.dataset.limit;return;}
 const row=template.cloneNode(true);row.querySelectorAll('[data-split-entry]').forEach(e=>e.remove());row.querySelectorAll('input').forEach(i=>i.value=i.name.endsWith('.component')?'TOTAL':'');row.querySelectorAll('[data-account-label]').forEach(e=>e.textContent=form.dataset.chooseAccount);row.querySelectorAll('.coa-error').forEach(e=>e.remove());row.querySelector('[data-mapping-label]').textContent='';row.querySelectorAll('input').forEach(i=>{i.removeAttribute('aria-invalid');i.removeAttribute('aria-describedby');});rows.append(row);update();row.querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));
});
function addPosting(row,side,account='',label='',amount=''){
 const list=row.querySelector('[data-split-list]'),entry=document.createElement('div'),index=list.children.length;
 entry.className='voucher-split-entry accounting-grid';entry.dataset.splitEntry='';
 const prefix='rows['+[...rows.children].indexOf(row)+'].entries['+index+']';
 const field=document.createElement('div');field.className='voucher-field';const title=document.createElement('label');title.textContent=side==='DEBIT'?form.dataset.debitLabel:form.dataset.creditLabel;field.append(title);
 const holder=document.createElement('div');holder.className='voucher-account-choice';field.append(holder);
 for(const [name,value] of [['side',side],['accountId',account]]){const input=document.createElement('input');input.type='hidden';input.name=prefix+'.'+name;input.value=value;if(name==='side')input.dataset.entrySide='';holder.append(input);}
 const text=document.createElement('span');text.hidden=true;text.dataset.accountLabel='';text.textContent=label;holder.append(text);
 const select=document.createElement('select');select.className='aws-control';select.dataset.accountFor=prefix+'.accountId';select.dataset.entryAccount='';select.dataset.voucherSelect='';select.dataset.searchable='true';select.setAttribute('aria-label',title.textContent);select.add(new Option(form.dataset.chooseAccount,''));holder.append(select);
 const amountLabel=document.createElement('label');amountLabel.textContent=form.dataset.amountLabel;const input=document.createElement('input');input.type='number';input.className='aws-control';input.dataset.amountInput='true';input.name=prefix+'.amount';input.value=amount;input.min='0.01';input.max='9999999999999999.99';input.step='0.01';input.inputMode='decimal';input.required=true;amountLabel.append(input);
 const remove=document.createElement('button');remove.type='button';remove.className='app-btn btn-neutral erp-icon-btn';remove.dataset.removeEntry='';remove.setAttribute('aria-label',form.dataset.removeEntryLabel);remove.title=form.dataset.removeEntryLabel;
 const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('width','16');icon.setAttribute('height','16');icon.setAttribute('viewBox','0 0 24 24');icon.setAttribute('fill','none');icon.setAttribute('stroke','currentColor');icon.setAttribute('stroke-width','2');icon.setAttribute('aria-hidden','true');const path=document.createElementNS(icon.namespaceURI,'path');path.setAttribute('d','M18 6 6 18M6 6l12 12');icon.append(path);remove.append(icon);
 entry.append(field,amountLabel,remove);list.append(entry);
}
function bindChoices(){
 form.querySelectorAll('[data-voucher-select]').forEach(select=>{
  if(pickers.has(select))return;
  const type=select.hasAttribute('data-voucher-type'),mapping=select.hasAttribute('data-mapping'),row=select.closest('[data-voucher-row]'),holder=select.closest('.voucher-account-choice');
  const input=type?{get value(){return equityMode()?'EQUITY':activity()||(form.dataset.newVoucher==='true'&&form.elements.receiptPurpose.value==='OTHER'?'':form.elements.receiptPurpose.value);},set value(value){}}:mapping?{get value(){return field(row,'transactionId').value?field(row,'templateKey').value+'|'+field(row,'component').value:'';},set value(value){}}:form.elements.namedItem(select.dataset.choiceFor||select.dataset.accountFor);
  const label=()=>mapping?row.querySelector('[data-mapping-label]').textContent:holder.querySelector('[data-account-label]').textContent;
  pickers.set(select,window.VoucherSelect.bind(select,{field:input,label,error:form.dataset.lookupError,fixed:type?[...select.options].filter(option=>option.value).map(option=>({value:option.value,label:option.textContent,item:{id:option.value}})):[],
   url:()=>{const path=type?'activities':select.hasAttribute('data-voucher-client')?(loanMode()?'loan-choices/clients':'clients'):select.dataset.loanChoice?'loan-choices/'+select.dataset.loanChoice:mapping?'transactions':'accounts';const url=new URL(form.dataset.base+'/'+path,location.origin);if(select.dataset.loanChoice==='loans')url.searchParams.set('clientId',form.elements.clientId.value);if(mapping&&activity())url.searchParams.set('activityId',activity());if(path==='accounts')url.searchParams.set('purpose',select.dataset.purpose||(select.hasAttribute('data-money-only')?'MONEY':''));return url;},
   choice:item=>type?{value:item.id,label:document.documentElement.lang==='sw'&&item.nameSw?item.nameSw:item.name}:mapping?{value:item.templateKey+'|'+item.component,label:item.transactionName+' · '+item.debit.name+' → '+item.credit.name}:select.hasAttribute('data-money-only')&&!(item.moneyEligible===true||moneyOnly.has(item.purpose))?null:{value:item.id,label:item.label||(item.code?item.code+' · ':'')+item.name},
   changed:(item,text)=>{
    if(type){form.dataset.newVoucher='false';form.elements.receiptPurpose.value=item.id==='LOAN_REPAYMENT'?'LOAN_REPAYMENT':'OTHER';form.elements.equity.value=String(item.id==='EQUITY');form.elements.activityId.value=['OTHER','LOAN_REPAYMENT','EQUITY'].includes(item.id)?'':item.id;holder.querySelector('[data-account-label]').textContent=text;form.elements.receiptPurpose.dispatchEvent(new Event('change',{bubbles:true}));return;}
    if(mapping){row.querySelectorAll('[data-split-entry]').forEach(e=>e.remove());field(row,'transactionId').value=item.transactionId;field(row,'templateKey').value=item.templateKey;field(row,'component').value=item.component;row.querySelector('[data-mapping-label]').textContent=text;if(!field(row,'description').value)field(row,'description').value=item.transactionName;}
    else{holder.querySelector('[data-account-label]').textContent=text;if(select.hasAttribute('data-voucher-client')){rows.querySelectorAll('input[name$=".loanId"]').forEach(input=>{input.value='';input.parentElement.querySelector('[data-account-label]').textContent='';});rows.querySelectorAll('[data-loan-choice="loans"]').forEach(loan=>pickers.get(loan)?.reset());}}
    form.dispatchEvent(new Event('input',{bubbles:true}));
   }
  }));
 });
}
form.addEventListener('click',e=>{
 const clearClient=e.target.closest('[data-client-clear]');if(clearClient&&!loanMode()){form.elements.clientId.value='';clearClient.closest('.voucher-account-choice').querySelector('[data-account-label]').textContent='';pickers.get(form.querySelector('[data-voucher-client]'))?.reset();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const addEntry=e.target.closest('[data-add-entry]');if(addEntry&&!addEntry.disabled){
  const row=addEntry.closest('[data-voucher-row]'),list=row.querySelector('[data-split-list]');
  if(list.children.length<50)addPosting(row,addEntry.dataset.addEntry);
  update();const last=list.lastElementChild;(last?.querySelector('.neo-select-button')||last?.querySelector('select'))?.focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;
 }
 const removeEntry=e.target.closest('button[data-remove-entry]');if(removeEntry&&!removeEntry.disabled){const row=removeEntry.closest('[data-voucher-row]');removeEntry.closest('[data-split-entry]').remove();update();row.querySelector('[data-add-entry]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const remove=e.target.closest('[data-remove-row]');if(remove && rows.children.length>1){const previous=remove.closest('[data-voucher-row]').previousElementSibling;remove.closest('[data-voucher-row]').remove();update();(previous||rows.firstElementChild).querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}


});
update();
window.VoucherBalance.bind(form);
const numberOutput=form.querySelector('[data-voucher-number]');
if(numberOutput){
 const numberValue=numberOutput.querySelector('[data-voucher-number-value]'),retry=form.querySelector('[data-retry-voucher-number]'),date=form.querySelector('[name="effectiveDate"]');
 let requestVersion=0,requestController=null;
 async function loadNumber(){
  const version=++requestVersion;requestController?.abort();requestController=new AbortController();
  numberOutput.setAttribute('aria-busy','true');numberValue.classList.add('sr-only');numberValue.textContent=form.dataset.numberLoading;numberOutput.querySelector('.voucher-number-spinner').hidden=false;retry.hidden=true;
  const url=new URL(form.dataset.base+'/number',location.origin),key=form.elements.namedItem('requestKey')?.value;
  if(!key||!date.value){numberValue.classList.remove('sr-only');numberValue.textContent=form.dataset.numberError;numberOutput.setAttribute('aria-busy','false');numberOutput.querySelector('.voucher-number-spinner').hidden=true;retry.hidden=false;return;}
  url.searchParams.set('requestKey',key);url.searchParams.set('effectiveDate',date.value);
  const headers={'Accept':'application/json','Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'};
  if(form.dataset.numberCsrfHeader&&form.dataset.numberCsrfToken)headers[form.dataset.numberCsrfHeader]=form.dataset.numberCsrfToken;
  try{
   const response=await fetch(url,{method:'POST',headers,body:new URLSearchParams({requestKey:key,effectiveDate:date.value}),credentials:'same-origin',cache:'no-store',signal:requestController.signal});
   if(!response.ok)throw new Error('Voucher number request failed');
   const result=await response.json();if(typeof result.number!=='string'||version!==requestVersion){if(version===requestVersion)throw new Error('Voucher number was not returned');return;}
   numberValue.classList.remove('sr-only');numberValue.textContent=result.number;numberOutput.setAttribute('aria-busy','false');numberOutput.querySelector('.voucher-number-spinner').hidden=true;
  }catch(error){if(error.name==='AbortError'||version!==requestVersion)return;numberValue.classList.remove('sr-only');numberValue.textContent=form.dataset.numberError;numberOutput.setAttribute('aria-busy','false');numberOutput.querySelector('.voucher-number-spinner').hidden=true;retry.hidden=false;}
 }
 date.addEventListener('change',loadNumber);retry.addEventListener('click',loadNumber);loadNumber();
}
})();
