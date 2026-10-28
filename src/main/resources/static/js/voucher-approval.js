(() => {
'use strict';
const qs=(selector,root=document)=>root.querySelector(selector);
const all=(selector,root=document)=>[...root.querySelectorAll(selector)];
document.querySelectorAll('form').forEach(form=>{const command=form.querySelector('[data-approval-command]');if(command)form.addEventListener('submit',event=>{command.value=event.submitter?.dataset.formCommand||command.value;});});
// Native dialogs provide keyboard trapping; body placement covers the entire workspace.
all('dialog.approval-dialog').forEach((dialog,index)=>{document.body.append(dialog);const heading=qs('h2',dialog);heading.id=`approval-dialog-title-${index}`;dialog.setAttribute('aria-labelledby',heading.id);let opener;dialog.addEventListener('close',()=>{const frame=qs('iframe',dialog);if(frame)frame.removeAttribute('src');opener?.focus();});dialog.addEventListener('approval:open',()=>{opener=document.activeElement;dialog.showModal();});all('[data-dialog-close]',dialog).forEach(b=>b.addEventListener('click',()=>dialog.close()));});
all('dialog.approval-dialog form').forEach(form=>{let submitting=false;form.addEventListener('submit',event=>{if(submitting){event.preventDefault();return;}submitting=true;form.setAttribute('aria-busy','true');all('button[type=submit],button:not([type])',form).forEach(b=>b.disabled=true);});window.addEventListener('pageshow',()=>{submitting=false;form.removeAttribute('aria-busy');all('button[type=submit],button:not([type])',form).forEach(b=>b.disabled=false);});});
const open=dialog=>dialog?.dispatchEvent(new Event('approval:open'));
all('[data-document-open]').forEach(a=>a.addEventListener('click',e=>{e.preventDefault();const d=qs('[data-document-dialog]');qs('iframe',d).src=a.href;open(d);}));
all('[data-workflow-action]').forEach(b=>b.addEventListener('click',()=>{const d=qs('[data-action-dialog]');qs('[name=action]',d).value=b.dataset.workflowAction;qs('[data-dialog-title]',d).textContent=b.dataset.actionLabel;qs('[data-confirm-action]',d).textContent=b.dataset.actionLabel;qs('[name=comments]',d).required=['REJECT','HOLD','FORCE'].includes(b.dataset.workflowAction);qs('[data-post-warning]',d).hidden=!(b.dataset.workflowAction==='POST'||b.dataset.workflowAction==='APPROVE'&&d.dataset.autoPost==='true');open(d);}));
function selection(region,prefix){if(!region)return;const button=qs(`[data-${prefix}-open]`,region),status=qs(`[data-${prefix}-selection]`,region);if(!button)return;let chosen;
 const reset=()=>{chosen=null;all('tr.is-selected',region).forEach(r=>r.classList.remove('is-selected'));all(`input[data-${prefix}-select]`,region).forEach(c=>c.checked=false);button.disabled=true;all('[data-register-action]',region).forEach(b=>b.disabled=true);if(status)status.textContent=status.dataset.empty||'';};
 region.addEventListener('click',e=>{const row=e.target.closest(`tr[data-${prefix}-url]`);if(!row || e.target.closest('a,button,select,textarea') || (e.target.matches('input') && !e.target.matches(`[data-${prefix}-select]`)))return;const input=qs(`[data-${prefix}-select]`,row);if(!input || input.disabled)return;const same=chosen===row;reset();if(!same){chosen=row;row.classList.add('is-selected');input.checked=true;button.disabled=false;all('[data-register-action]',region).forEach(b=>b.disabled=row.dataset[b.dataset.registerAction==='APPROVE'?'canApprove':'canReject']!=='true');if(status)status.textContent=(status.dataset.selected||'')+' '+(qs('a',row)?.textContent||'');}});
 all('[data-register-action]',region).forEach(b=>b.addEventListener('click',()=>{if(!chosen||b.disabled)return;const dialog=qs('[data-register-dialog]'),form=qs('form',dialog);form.action=chosen.dataset.approvalUrl+'/actions';qs('[name=version]',form).value=chosen.dataset.version;qs('[name=action]',form).value=b.dataset.registerAction;qs('[name=comments]',form).required=b.dataset.registerAction==='REJECT';qs('[data-register-dialog-title]',form).textContent=b.dataset.actionLabel;qs('[data-register-confirm]',form).textContent=b.dataset.actionLabel;qs('[data-register-reference]',form).textContent=qs('a',chosen).textContent+' · '+qs('[data-approval-amount]',chosen).textContent.trim()+' TZS';qs('[data-register-post-warning]',form).hidden=!(b.dataset.registerAction==='APPROVE'&&chosen.dataset.autoPost==='true');open(dialog);}));
 button.addEventListener('click',()=>{const url=chosen?.dataset[`${prefix}Url`];if(url)window.WorkspaceNavigation.navigate(url);});region.addEventListener('resource-table:reset',reset);window.addEventListener('pageshow',reset);reset();
}
selection(qs('[data-approval-register]'),'approval');selection(qs('[data-resource-table="voucher-approval-items"]'),'item');
const register=qs('[data-approval-register]');if(register){const table=qs('[data-resource-table=voucher-approval-register]',register);const updateCards=()=>{const category=qs('[name=category]',table)?.value;all('.approval-tile',register).forEach(tile=>{let active=false;all('a[data-table-query-param]',tile).forEach(link=>{const current=!!category&&new URL(link.href,location.href).searchParams.get('category')===category;link.toggleAttribute('aria-current',current);if(current)link.setAttribute('aria-current','page');active||=current;const url=new URL(link.href);const type=qs('[name=type]',table)?.value;if(type)url.searchParams.set('type',type);else url.searchParams.delete('type');link.href=url.href;});tile.classList.toggle('is-active',active);});};table?.addEventListener('resource-table:updated',updateCards);updateCards();}
const editor=qs('[data-approval-editor]');
if(editor){const rows=qs('[data-editor-lines]',editor),template=rows.firstElementChild.cloneNode(true),readonly=editor.dataset.readonly==='true',pickers=new WeakMap();
 window.VoucherSelect.cleanClone(template);
 const visibleField=field=>{if(field.hasAttribute('data-amount-input'))return qs('[data-amount-display]',field.parentElement)||field;if(field.hasAttribute('data-date-native'))return qs('[data-date-display]',field.parentElement);if(field.type!=='hidden')return field;const select=qs('select[data-voucher-select]',field.parentElement);return select?.closest('.neo-select')?.querySelector('button')||select||qs('[data-account-label]',field.parentElement)||qs('[data-mapping-clear]',field.closest('tr')||editor)||qs('[name$=".description"]',field.closest('tr')||editor);};
 const renumber=()=>{all('[data-editor-line]',rows).forEach((r,n)=>{const number=qs('[data-line-number]',r);number.firstChild.textContent=String(n+1);const checkbox=qs('[data-line-select]',r);if(checkbox)checkbox.setAttribute('aria-label',`${editor.dataset.lineLabel} ${n+1}`);all('[aria-label]',r).forEach(i=>i.setAttribute('aria-label',i.getAttribute('aria-label').replace(/\d+$/,String(n+1))));all('[name]',r).forEach(i=>{i.name=i.name.replace(/rows\[\d+\]/,`rows[${n}]`);i.id=`field-${i.name}`;});const remove=qs('[data-line-remove]',r);if(remove)remove.disabled=rows.children.length<=1;});const add=qs('[data-line-add]',editor);if(add)add.disabled=rows.children.length>=50;bindChoices();};
 all('[name]',editor).forEach(i=>{if(!i.id)i.id=`field-${i.name}`;});
 bindChoices();
 all('[data-issue-field]',editor).forEach(link=>{const field=editor.elements.namedItem(link.dataset.issueField);if(!field)return;const visible=visibleField(field);let message=link.textContent;const line=field.name.match(/^rows\[(\d+)\]/);if(!field.value&&field.name.endsWith('.debitAccountId'))message=editor.dataset.debitRequired;if(!field.value&&field.name.endsWith('.creditAccountId'))message=editor.dataset.creditRequired;link.textContent=(line?`${editor.dataset.lineLabel} ${Number(line[1])+1}: `:'')+message;visible.classList.add('approval-field-error');visible.setAttribute('aria-invalid','true');visible.closest('tr')?.classList.add('approval-warning-row');const note=document.createElement('small');note.className='approval-inline-error';note.id='approval-error-'+field.name.replace(/\W/g,'-');note.textContent=message;visible.setAttribute('aria-describedby',note.id);visible.parentElement.append(note);link.addEventListener('click',e=>{e.preventDefault();visible.scrollIntoView({block:'center',behavior:'smooth'});visible.focus();const row=visible.closest('[data-editor-line]');if(row){all('[data-editor-line]',rows).forEach(r=>{r.classList.remove('is-selected');qs('[data-line-select]',r).checked=false;});selectedLine=row;row.classList.add('is-selected');qs('[data-line-select]',row).checked=true;refreshLines();}});});
 qs('[data-line-add]',editor)?.addEventListener('click',()=>{if(rows.children.length>=50)return;const row=template.cloneNode(true);all('input',row).forEach(i=>{i.value=i.name.endsWith('.component')?'TOTAL':'';i.removeAttribute('aria-invalid');if(i.getAttribute('aria-describedby')?.startsWith('approval-error-'))i.removeAttribute('aria-describedby');i.classList.remove('approval-field-error');});all('small',row).forEach(i=>i.remove());all('.approval-account',row).forEach(a=>{if(!qs('[data-account-pick]',a) && !qs('[data-money-side="true"]',a)){accountSelect(a);}});all('.approval-inline-error',row).forEach(e=>e.remove());all('[data-line-select]',row).forEach(e=>e.checked=false);row.classList.remove('approval-warning-row');rows.append(row);renumber();qs('[name$=".description"]',row).focus();editor.dispatchEvent(new Event('input',{bubbles:true}));});
 rows.addEventListener('click',e=>{const clear=e.target.closest('[data-mapping-clear]');if(clear && !readonly){const row=clear.closest('[data-editor-line]');all('input[type=hidden]',row).forEach(i=>i.value=i.name.endsWith('.component')?'TOTAL':'');all('.approval-account',row).forEach(a=>{qs('[data-account-label]',a).value='';if(!qs('[data-account-pick]',a) && !qs('[data-money-side="true"]',a)){accountSelect(a);}});clear.closest('[data-mapping-label]').remove();bindChoices();all('[data-voucher-select]',row).forEach(select=>pickers.get(select)?.reset());qs('[data-account-pick]',row)?.focus();editor.dispatchEvent(new Event('input',{bubbles:true}));return;}if(!readonly && e.target.closest('[data-line-remove]') && rows.children.length>1){e.target.closest('[data-editor-line]').remove();renumber();editor.dispatchEvent(new Event('input',{bubbles:true}));}});
 renumber();
 let selectedLine;
 const refreshLines=()=>{
   const remove=qs('[data-line-remove-selected]',editor),choose=qs('[data-choose-selected]',editor);
   if(remove)remove.disabled=readonly||!selectedLine||rows.children.length<=1;
   if(choose)choose.disabled=readonly||!selectedLine||!qs('[data-account-pick]',selectedLine);
   const money=qs('[name=moneyAccountId]',editor)?.parentElement;
   all('[data-money-side="true"]',rows).forEach(i=>i.value=qs('[data-account-label]',money||editor)?.value||'');
   const output=qs('[data-editor-total]',editor);if(output){let cents=0n,valid=true;all('[name$=".amount"]',rows).forEach(i=>{if(!/^\d{1,16}(\.\d{0,2})?$/.test(i.value)){valid=false;return;}const [whole,fraction='']=i.value.split('.');cents+=BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'));});output.textContent=valid?`${(cents/100n).toLocaleString()}.${(cents%100n).toString().padStart(2,'0')}`:'—';}
 };
 rows.addEventListener('click',e=>{const row=e.target.closest('[data-editor-line]');if(!row||e.target.closest('a,button,select,textarea')||(e.target.matches('input')&&!e.target.matches('[data-line-select]')))return;const choose=e.target.matches('[data-line-select]')?e.target.checked:selectedLine!==row;all('[data-editor-line]',rows).forEach(r=>{r.classList.remove('is-selected');qs('[data-line-select]',r).checked=false;});selectedLine=choose?row:null;if(selectedLine){selectedLine.classList.add('is-selected');qs('[data-line-select]',selectedLine).checked=true;}refreshLines();});
 qs('[data-line-remove-selected]',editor)?.addEventListener('click',()=>{if(!selectedLine||rows.children.length<=1)return;selectedLine.remove();selectedLine=null;renumber();editor.dispatchEvent(new Event('input',{bubbles:true}));});
 qs('[data-choose-selected]',editor)?.addEventListener('click',()=>qs('[data-account-pick]',selectedLine||rows)?.closest('.neo-select')?.querySelector('button')?.click());
 editor.addEventListener('input',()=>{all('[data-voucher-select]',editor).forEach(select=>pickers.get(select)?.sync());const submit=qs('[data-editor-submit]',editor);if(submit)submit.disabled=true;refreshLines();});refreshLines();
 const resetLines=()=>{selectedLine=null;all('[data-editor-line]',rows).forEach(r=>{r.classList.remove('is-selected');qs('[data-line-select]',r).checked=false;});refreshLines();};qs('[data-resource-table=approval-voucher-lines]',editor)?.addEventListener('resource-table:reset',resetLines);window.addEventListener('pageshow',event=>{if(event.persisted)resetLines();});
 function accountSelect(holder){
  const label=qs('[data-account-label]',holder),select=document.createElement('select');select.className='aws-control';select.dataset.accountPick='';select.dataset.voucherSelect='';select.dataset.searchable='true';select.setAttribute('aria-label',label.getAttribute('aria-label'));select.add(new Option(document.body.dataset.shellSelect,''));label.hidden=true;holder.append(select);
 }
 function bindChoices(){
  all('select[data-voucher-select]',editor).forEach(select=>{
   if(pickers.has(select))return;
   const holder=select.closest('.approval-account'),input=qs('input[type=hidden]',holder),label=qs('[data-account-label]',holder),client=select.hasAttribute('data-client-pick');
   pickers.set(select,window.VoucherSelect.bind(select,{field:input,label:()=>label.value,url:()=>client?editor.dataset.clientUrl:editor.dataset.accountUrl,error:editor.dataset.lookupError,choice:item=>({value:item.id,label:item.name}),changed:(item,text)=>{label.value=text;editor.dispatchEvent(new Event('input',{bubbles:true}));}}));
  });
 }
 editor.addEventListener('click',e=>{const clear=e.target.closest('[data-client-clear]');if(clear&&!readonly){const holder=clear.closest('.approval-account');qs('input[type=hidden]',holder).value='';qs('[data-account-label]',holder).value='';pickers.get(qs('select',holder))?.reset();editor.dispatchEvent(new Event('input',{bubbles:true}));}});
 // Warning deep links focus their associated visible control after rendering.
 if(location.hash.startsWith('#field-')){let name;try{name=decodeURIComponent(location.hash.slice(7));}catch{}const field=editor.elements.namedItem(name);if(field){const visible=visibleField(field);visible?.focus();const row=visible?.closest('[data-editor-line]');if(row){selectedLine=row;row.classList.add('is-selected');qs('[data-line-select]',row).checked=true;refreshLines();}}}
}
const rules=qs('[data-approval-rules]');
if(rules){
 const list=qs('[data-rule-list]',rules),ruleTemplate=qs('[data-rule]',list).cloneNode(true),stepTemplate=qs('[data-step]',ruleTemplate).cloneNode(true);
 const update=()=>{
  all('[data-rule]',list).forEach((rule,n)=>{
   qs('[data-rule-number]',rule).textContent=String(n+1);
   all('[data-step]',rule).forEach((step,j)=>{qs('[data-step-number]',step).textContent=String(j+1);all('[name]',step).forEach(field=>field.name=field.name.replace(/stages\[\d+\]/,`stages[${j}]`));qs('[data-step-remove]',step).disabled=all('[data-step]',rule).length<=1;});
   all('[name]',rule).forEach(field=>{field.name=field.name.replace(/rules\[\d+\]/,`rules[${n}]`);field.id='approval-'+field.name.replace(/[^a-zA-Z0-9]/g,'-');});
   qs('[data-rule-remove]',rule).disabled=list.children.length<=1;
   qs('[data-step-add]',rule).disabled=all('[data-step]',rule).length>=10;
  });
  qs('[data-rule-add]',rules).disabled=list.children.length>=20;
 };
 const changed=()=>{update();rules.dispatchEvent(new Event('input',{bubbles:true}));};
 qs('[data-rule-add]',rules)?.addEventListener('click',()=>{
  if(list.children.length>=20)return;const rule=ruleTemplate.cloneNode(true),steps=qs('[data-steps]',rule);steps.replaceChildren(stepTemplate.cloneNode(true));
  all('input',rule).forEach(field=>field.value=field.name.endsWith('.minimum')?'0':field.name.endsWith('.approvals')?'1':'');
  all('select',rule).forEach(field=>field.value=field.name.endsWith('.role')?'MANAGER':field.name.endsWith('.scope')?'SINGLE':'false');
  list.append(rule);changed();qs('select',rule).focus();
 });
 list.addEventListener('click',event=>{
  const rule=event.target.closest('[data-rule]');if(!rule)return;
  if(event.target.closest('[data-rule-remove]') && list.children.length>1){rule.remove();changed();return;}
  if(event.target.closest('[data-step-add]') && all('[data-step]',rule).length<10){const step=stepTemplate.cloneNode(true);qs('select',step).value='MANAGER';qs('input',step).value='1';qs('[data-steps]',rule).append(step);changed();qs('select',step).focus();}
  if(event.target.closest('[data-step-remove]') && all('[data-step]',rule).length>1){event.target.closest('[data-step]').remove();changed();}
 });
 if(!qs('fieldset',rules).disabled)update();
}
})();
