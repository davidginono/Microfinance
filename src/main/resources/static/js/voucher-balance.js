/* Shared exact-decimal balance feedback for voucher creation and batch correction. */
(() => {
 'use strict';
 if(window.VoucherBalance)return;
 const ownedErrors=new WeakMap(),bound=new WeakSet();
 const cents=value=>{
  const match=/^(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(value);
  if(!match||!(match[1]||match[2]))return null;
  const fraction=match[2]||'',shift=2+Number(match[3]||0)-fraction.length;
  if(!Number.isInteger(shift)||Math.abs(shift)>20)return null;
  const digits=BigInt((match[1]||'0')+fraction),power=10n**BigInt(Math.abs(shift));
  return shift>=0?digits*power:digits%power===0n?digits/power:null;
 };
 const raw=value=>(value/100n).toString()+'.'+(value%100n).toString().padStart(2,'0');
 const format=value=>(value/100n).toLocaleString(document.documentElement.lang||'en')+'.'+(value%100n).toString().padStart(2,'0');
 const visible=field=>field.parentElement?.querySelector('[data-amount-display]')||field;
 function refresh(form,attempt=false){
  let invalid=false,shown=false,first;
  const message=form.dataset.balanceError;
  form.querySelectorAll('[data-voucher-row],[data-editor-line]').forEach((row,index)=>{
   const fields=[...row.querySelectorAll('input[name]')],amount=fields.find(f=>/^rows\[\d+\]\.amount$/.test(f.name));
   fields.forEach(field=>{
    const previous=ownedErrors.get(field);
    if(previous && (field.validationMessage===previous||field.disabled))field.setCustomValidity('');
    ownedErrors.delete(field);
   });
   const sides=fields.filter(f=>/\.entries\[\d+\]\.side$/.test(f.name)&&!f.disabled);
   const error=row.querySelector('[data-balance-error]');
   let debit=0n,credit=0n,complete=true,entered=false;
   const amounts=[];
   sides.forEach(side=>{
    const field=fields.find(f=>f.name===side.name.replace(/\.side$/,'.amount'));if(!field){complete=false;return;}
    amounts.push(field);entered||=field.value!=='';const value=cents(field.value);
    if(value===null||value<=0n||!['DEBIT','CREDIT'].includes(side.value))complete=false;
    else if(side.value==='DEBIT')debit+=value;else credit+=value;
   });
   if(sides.length && amount?.type==='hidden')amount.value=raw(debit);
   const balanced=!sides.length || complete&&debit>0n&&debit===credit&&debit===cents(amount?.value||'');
   const show=!balanced&&(entered||attempt);
   if(error){error.id=(form.id||'voucher')+'-balance-'+index;error.hidden=!show;}
   row.querySelectorAll('[data-debit-total]').forEach(output=>output.value=format(debit));
   row.querySelectorAll('[data-credit-total]').forEach(output=>output.value=format(credit));
   if(show){invalid=true;shown=true;first||=amounts[0];}
   if(form.dataset.readonly!=='true' && amounts[0] && message){
    const control=amounts[0],proxy=visible(control),id=error?.id;
    if(show && !control.validity.customError){control.setCustomValidity(message);ownedErrors.set(control,message);}
    if(id)for(const node of new Set([control,proxy])){
     const descriptions=(node.getAttribute('aria-describedby')||'').split(/\s+/).filter(value=>value&&!value.startsWith((form.id||'voucher')+'-balance-'));
     if(show)descriptions.push(id);
     if(descriptions.length)node.setAttribute('aria-describedby',descriptions.join(' '));else node.removeAttribute('aria-describedby');
    }
   }
  });
  const alert=form.querySelector('[data-voucher-balance-error]');if(alert){alert.hidden=!shown;if(shown)window.ConsoleResourceDetails?.init(alert);}
  form.querySelectorAll('button[name="action"][value="post"],button[data-form-command="SUBMIT"],[data-editor-submit]').forEach(button=>{
   if(invalid&&!button.disabled){button.disabled=true;button.dataset.balanceBlocked='true';}
   else if(!invalid&&button.dataset.balanceBlocked==='true'&&form.getAttribute('aria-busy')!=='true'&&button.getAttribute('aria-busy')!=='true'){button.disabled=false;delete button.dataset.balanceBlocked;}
  });
  return {invalid,first};
 }
 function bind(form){
  if(bound.has(form))return;bound.add(form);
  form.addEventListener('submit',event=>{
   if(event.submitter?.formNoValidate)return;
   const result=refresh(form,true);if(!result.invalid)return;
   event.preventDefault();event.stopImmediatePropagation();
   if(result.first){const control=visible(result.first);control.focus({preventScroll:true});control.scrollIntoView?.({block:'center',behavior:'instant'});}
  },true);
  window.addEventListener('pageshow',()=>setTimeout(()=>{if(form.isConnected)refresh(form);},0));
  form.addEventListener('reset',()=>setTimeout(()=>refresh(form),0));
  refresh(form);
 }
 window.VoucherBalance={cents,format,refresh,bind};
})();
