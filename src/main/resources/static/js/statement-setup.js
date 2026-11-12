(() => {
 'use strict';
 const ready=window.WorkspaceNavigation?.onReady || (fn=>document.readyState==='loading'?document.addEventListener('DOMContentLoaded',fn,{once:true}):fn());
 ready(()=>{
  const form=document.querySelector('[data-statement-setup]');
  if(form&&!form.dataset.statementSetupReady){
   form.dataset.statementSetupReady='true';
   const renumber=list=>{[...list.children].forEach((row,i)=>row.querySelectorAll('[name]').forEach(input=>{input.name=input.name.replace(/\[[^\]]*\]/,'['+i+']');}));const add=form.querySelector('[data-setup-add="'+list.dataset.setupList+'"]');if(add)add.disabled=list.children.length>=Number(list.dataset.max);};
   form.addEventListener('click',event=>{
    const add=event.target.closest('[data-setup-add]'),remove=event.target.closest('[data-setup-remove]'),move=event.target.closest('[data-setup-move]');
    if(move){const row=move.closest('.fs-setup-row'),list=row.parentElement,other=Number(move.dataset.setupMove)<0?row.previousElementSibling:row.nextElementSibling;if(other){if(Number(move.dataset.setupMove)<0)list.insertBefore(row,other);else list.insertBefore(other,row);renumber(list);move.focus();}}
    if(add){const key=add.dataset.setupAdd,list=form.querySelector('[data-setup-list="'+key+'"]');if(list.children.length>=Number(list.dataset.max))return;const template=document.querySelector('[data-setup-template="'+key+'"]');list.append(template.content.cloneNode(true));renumber(list);list.lastElementChild.querySelector('input')?.focus();}
    if(remove){const row=remove.closest('.fs-setup-row'),list=row.parentElement;row.remove();renumber(list);form.querySelector('[data-setup-add="'+list.dataset.setupList+'"]')?.focus();}
   });
   form.addEventListener('submit',()=>form.querySelectorAll('[data-setup-list]').forEach(renumber));
  }
  const region=document.querySelector('[data-equity-movements]');if(!region||region.dataset.equityMovementsReady)return;region.dataset.equityMovementsReady='true';
  const classify=region.querySelector('[data-equity-classify]'),button=region.querySelector('[data-equity-save]');
  const clear=()=>{region.querySelectorAll('[data-equity-select]').forEach(c=>{c.checked=false;});classify.elements.journal.value='';classify.elements.version.value='';button.disabled=true;region.querySelector('[data-equity-selection-status]').textContent='';};
  region.addEventListener('change',event=>{const selected=event.target.closest('[data-equity-select]');if(!selected)return;region.querySelectorAll('[data-equity-select]').forEach(c=>{if(c!==selected)c.checked=false;});if(!selected.checked){clear();return;}classify.elements.journal.value=selected.value;classify.elements.version.value=selected.dataset.version;classify.elements.category.value=selected.dataset.category;button.disabled=false;region.querySelector('[data-equity-selection-status]').textContent=selected.getAttribute('aria-label');});
  region.addEventListener('resource-table:reset',clear);region.addEventListener('resource-table:updated',()=>{clear();const query=new URL(location.href).searchParams;for(const name of ['from','through'])classify.elements[name].value=query.get(name)||region.querySelector('form[method=get] [name='+name+']').value;classify.elements.page.value=query.get('page')||'0';});
  classify.addEventListener('submit',event=>{if(!classify.elements.journal.value)event.preventDefault();});
  const retained=classify.elements.journal.value;if(retained){const selected=[...region.querySelectorAll('[data-equity-select]')].find(c=>c.value===retained);if(selected){selected.checked=true;button.disabled=false;}else clear();}
 });
})();
