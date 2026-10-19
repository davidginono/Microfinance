(() => {
'use strict';
const register=document.querySelector('[data-voucher-register]');
if(register){
 let selected=null;
 const buttons=[...register.querySelectorAll('[data-selection-action]')],status=register.querySelector('[data-selection-status]');
 const clear=()=>{selected=null;register.querySelectorAll('[data-voucher-id]').forEach(r=>{r.classList.remove('is-selected');r.querySelector('input').checked=false;});buttons.forEach(b=>b.disabled=true);status.textContent=status.dataset.empty;};
 register.addEventListener('click',e=>{const row=e.target.closest('[data-voucher-id]');if(!row || e.target.closest('a,button'))return;const next=row.dataset.voucherId===selected?null:row;clear();if(next){selected=next.dataset.voucherId;next.classList.add('is-selected');next.querySelector('input').checked=true;buttons.forEach(b=>b.disabled=b.dataset.selectionAction==='topup' && next.dataset.topupEligible!=='true');status.textContent=status.dataset.selected+' '+next.querySelector('a').textContent;}});
 buttons.forEach(b=>b.addEventListener('click',()=>{if(!selected)return;const url=register.dataset.base+'/'+encodeURIComponent(selected);if(b.dataset.selectionAction==='topup')location.assign(register.dataset.base+'/new?topUpLoanId='+encodeURIComponent(selected));else if(b.dataset.selectionAction==='print')window.printExport(url+'/print');else location.assign(url+(b.dataset.selectionAction==='pdf'?'/pdf':''));}));
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
const field=(row,suffix)=>row.querySelector('input[name$=".'+suffix+'"]');
const moneyOnly=new Set(['CASH','BANK','MOBILE_MONEY']);
const loanMode=()=>form.querySelector('[data-receipt-purpose]')?.value==='LOAN_REPAYMENT';
function update(){
 const loan=loanMode();
 form.querySelectorAll('[data-loan-only]').forEach(el=>{el.hidden=!loan;el.querySelectorAll('input,button').forEach(c=>c.disabled=!loan);});
 form.querySelectorAll('[data-other-only]').forEach(el=>{el.hidden=loan;el.querySelectorAll('input,button').forEach(c=>c.disabled=loan);});
 form.querySelector('[name="reference"]').maxLength=loan?80:160;
 const all=[...rows.children];
 all.forEach((r,i)=>{r.querySelector('[data-row-number]').textContent=i+1;r.querySelectorAll('[name]').forEach(el=>{el.name=el.name.replace(/rows\[\d+\]/,'rows['+i+']');el.removeAttribute('id');});r.querySelectorAll('[data-account-for]').forEach(b=>b.dataset.accountFor=b.dataset.accountFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelectorAll('[data-choice-for]').forEach(b=>b.dataset.choiceFor=b.dataset.choiceFor.replace(/rows\[\d+\]/,'rows['+i+']'));r.querySelector('[data-remove-row]').disabled=all.length===1;const mapped=!!field(r,'transactionId').value;r.querySelector('[data-manual]').hidden=loan||!mapped;r.querySelectorAll('[data-manual-accounts]').forEach(el=>{el.hidden=loan||mapped;el.querySelectorAll('input,button').forEach(c=>c.disabled=loan);});r.querySelectorAll('[data-loan-choice="loans"]').forEach(b=>b.disabled=!loan||!form.elements.clientId.value);});
 form.querySelector('[data-add-row]').disabled=all.length>=50;
 let cents=0n;
 all.forEach(r=>{const match=/^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(field(r,'amount').value);if(match){const fractional=match[2]||'',exponent=Number(match[3]||0),shift=2+exponent-fractional.length;if(Number.isInteger(exponent)&&shift>=0&&shift<=20)cents+=BigInt(match[1]+fractional)*10n**BigInt(shift);}});
 form.querySelector('[data-live-total]').value=(cents/100n).toLocaleString(document.documentElement.lang||'en')+'.'+(cents%100n).toString().padStart(2,'0');
}
form.querySelector('[data-receipt-purpose]')?.addEventListener('change',()=>{
 rows.querySelectorAll('input').forEach(input=>{if(/\.(loanId|transactionId|templateKey|debitAccountId|creditAccountId)$/.test(input.name))input.value='';});
 rows.querySelectorAll('[data-account-label],[data-mapping-label]').forEach(el=>el.textContent='');
 if(!loanMode())form.elements.interestAccountId.value='';
 update();form.dispatchEvent(new Event('input',{bubbles:true}));
});
form.addEventListener('input',()=>{const preview=form.querySelector('[data-voucher-preview]');if(preview)preview.hidden=true;update();});
form.addEventListener('submit',e=>{let hidden=form.querySelector('[data-submit-action]');if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.name='action';hidden.dataset.submitAction='';form.append(hidden);}hidden.value=e.submitter?.value||'post';});
form.querySelector('[data-add-row]').addEventListener('click',()=>{
 if(rows.children.length>=50){status.textContent=form.dataset.limit;return;}
 const row=template.cloneNode(true);row.querySelectorAll('input').forEach(i=>i.value=i.name.endsWith('.component')?'TOTAL':'');row.querySelectorAll('[data-account-label]').forEach(e=>e.textContent=form.dataset.chooseAccount);row.querySelectorAll('.coa-error').forEach(e=>e.remove());row.querySelector('[data-mapping-label]').textContent='';row.querySelectorAll('input').forEach(i=>{i.removeAttribute('aria-invalid');i.removeAttribute('aria-describedby');});rows.append(row);update();row.querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));
});
const picker=document.getElementById('voucher-picker');document.body.append(picker);
let opener=null,mode=null,target=null,page=0,version=0,controller,previousOverflow,previousRootOverflow;
const searchForm=picker.querySelector('[data-picker-search]'),results=picker.querySelector('[data-picker-results]'),ps=picker.querySelector('[data-picker-status]');
function close(){searchBinding.cancel();version++;controller?.abort();window.AppLoading?.end(results);picker.hidden=true;picker.classList.remove('is-open');document.body.style.overflow=previousOverflow;document.documentElement.style.overflow=previousRootOverflow;opener?.focus();}
picker.querySelector('[data-picker-close]').addEventListener('click',close);
picker.addEventListener('click',e=>{if(e.target===picker)close();});
picker.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();close();}if(e.key==='Tab'){const focus=[...picker.querySelectorAll('button:not(:disabled),input,a[href]')].filter(x=>!x.hidden);const first=focus[0],last=focus.at(-1);if(e.shiftKey && document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey && document.activeElement===last){e.preventDefault();first.focus();}}});
async function load(){
 controller?.abort();controller=new AbortController();
 const token=++version;ps.textContent=ps.dataset.loading;window.AppLoading?.end(results);results.replaceChildren();window.AppLoading?.begin(results,{table:false});picker.querySelector('[data-picker-prev]').disabled=true;picker.querySelector('[data-picker-next]').disabled=true;
 try{
  const url=new URL(form.dataset.base+'/'+(mode.startsWith('loan-')?'loan-choices/'+mode.slice(5):mode==='client'?'clients':mode==='mapping'?'transactions':'accounts'),location.origin);url.searchParams.set('search',searchForm.elements.search.value);url.searchParams.set('page',page);if(mode==='loan-loans')url.searchParams.set('clientId',form.elements.clientId.value);
  const response=await fetch(url,{headers:{Accept:'application/json','X-Requested-With':'XMLHttpRequest'},credentials:'same-origin',cache:'no-store',signal:controller.signal});if(!response.ok)throw Error();const data=await response.json();if(token!==version)return;
  const eligible=data.slice(0,25).filter(a=>mode!=='money'||moneyOnly.has(a.purpose));ps.textContent=eligible.length?'':ps.dataset.empty;
  eligible.forEach(a=>{const b=document.createElement('button');b.type='button';b.className='voucher-picker-option';b.textContent=mode==='client'?a.name+' · '+a.number:mode.startsWith('loan-')?a.label:mode==='mapping'?a.transactionName+' · '+a.component+' · '+a.debit.code+' → '+a.credit.code:a.code+' · '+a.name;
   b.addEventListener('click',()=>{
    if(mode==='mapping'){field(target,'transactionId').value=a.transactionId;field(target,'templateKey').value=a.templateKey;field(target,'component').value=a.component;target.querySelector('[data-mapping-label]').textContent=b.textContent;if(!field(target,'description').value)field(target,'description').value=a.transactionName;}
    else {const input=form.elements.namedItem(target);input.value=a.id;input.parentElement.querySelector('[data-account-label]').textContent=b.textContent;
     if(mode==='loan-clients'){rows.querySelectorAll('input[name$=".loanId"]').forEach(i=>{i.value='';i.parentElement.querySelector('[data-account-label]').textContent='';});}
    }
    update();form.dispatchEvent(new Event('input',{bubbles:true}));close();
   });results.append(b);
  });picker.querySelector('[data-picker-prev]').disabled=page===0;picker.querySelector('[data-picker-next]').disabled=data.length<=25;
 }catch(e){if(token===version){ps.textContent=ps.dataset.error;searchBinding.cancel();}}finally{if(token===version)window.AppLoading?.end(results);}
}
const searchBinding=window.AppSearch.bind(searchForm.elements.search,()=>{if(!picker.hidden){page=0;load();}},()=>{version++;controller?.abort();results.replaceChildren();picker.querySelector('[data-picker-prev]').disabled=true;picker.querySelector('[data-picker-next]').disabled=true;});
searchForm.addEventListener('submit',e=>{e.preventDefault();e.stopPropagation();searchBinding.run();});
picker.querySelector('[data-picker-prev]').addEventListener('click',()=>{searchBinding.cancel();page--;load();});picker.querySelector('[data-picker-next]').addEventListener('click',()=>{searchBinding.cancel();page++;load();});
form.addEventListener('click',e=>{
 const clearClient=e.target.closest('[data-client-clear]');if(clearClient&&!loanMode()){form.elements.clientId.value='';clearClient.closest('.voucher-account-choice').querySelector('[data-account-label]').textContent='';form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const remove=e.target.closest('[data-remove-row]');if(remove && rows.children.length>1){const previous=remove.closest('[data-voucher-row]').previousElementSibling;remove.closest('[data-voucher-row]').remove();update();(previous||rows.firstElementChild).querySelector('input[name$=".description"]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const manual=e.target.closest('[data-manual]');if(manual){const row=manual.closest('[data-voucher-row]');field(row,'transactionId').value='';field(row,'templateKey').value='';field(row,'component').value='TOTAL';row.querySelector('[data-mapping-label]').textContent='';update();row.querySelector('[data-account-for]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;}
 const button=e.target.closest('[data-account-for],[data-mapping],[data-loan-choice],[data-voucher-client]');if(!button || button.disabled)return;
 opener=button;mode=button.hasAttribute('data-voucher-client')?(loanMode()?'loan-clients':'client'):button.dataset.loanChoice?'loan-'+button.dataset.loanChoice:button.hasAttribute('data-mapping')?'mapping':button.hasAttribute('data-money-only')?'money':'account';target=mode==='mapping'?button.closest('[data-voucher-row]'):button.dataset.choiceFor||button.dataset.accountFor;page=0;searchForm.reset();previousOverflow=document.body.style.overflow;previousRootOverflow=document.documentElement.style.overflow;document.body.style.overflow='hidden';document.documentElement.style.overflow='hidden';picker.hidden=false;picker.classList.add('is-open');searchForm.elements.search.focus();load();
});
update();
})();
