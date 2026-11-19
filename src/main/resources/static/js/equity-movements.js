(() => {
 'use strict';
 const ready=window.WorkspaceNavigation?.onReady || (fn=>document.readyState==='loading'?document.addEventListener('DOMContentLoaded',fn,{once:true}):fn());
 ready(()=>{
  const region=document.querySelector('[data-equity-movements]');if(!region||region.dataset.equityMovementsReady)return;region.dataset.equityMovementsReady='true';
  // The shared shell mounts overlays on body before workspace initialization.
  const modal=document.getElementById('equity-movement-modal'),classify=modal.querySelector('[data-equity-classify]'),button=region.querySelector('[data-equity-edit]');
  let dismissed=false;
  const selectedRow=()=>[...region.querySelectorAll('[data-equity-select]')].find(c=>c.checked);
  const close=(dismiss=false)=>{if(classify.matches('[aria-busy="true"],[inert]'))return;if(dismiss)dismissed=true;modal.hidden=true;modal.classList.remove('is-open');modal.setAttribute('aria-hidden','true');};
  const open=()=>{const selected=selectedRow();if(!selected||button.disabled)return;dismissed=false;modal.querySelector('[data-equity-reference]').textContent=selected.getAttribute('aria-label');modal.hidden=false;modal.classList.remove('hidden');modal.classList.add('is-open');modal.setAttribute('aria-hidden','false');};
  button.addEventListener('click',open);modal.querySelectorAll('[data-equity-cancel]').forEach(control=>control.addEventListener('click',()=>close(true)));
  const highlight=()=>region.querySelectorAll('[data-equity-select]').forEach(c=>c.closest('tr').classList.toggle('resource-table-selected',c.checked));
  const clear=()=>{close();region.querySelectorAll('[data-equity-select]').forEach(c=>{c.checked=false;});highlight();classify.elements.journal.value='';classify.elements.version.value='';button.disabled=true;region.querySelector('[data-equity-selection-status]').textContent='';};
  region.addEventListener('change',event=>{const selected=event.target.closest('[data-equity-select]');if(!selected)return;region.querySelectorAll('[data-equity-select]').forEach(c=>{if(c!==selected)c.checked=false;});if(!selected.checked){clear();return;}highlight();classify.elements.journal.value=selected.value;classify.elements.version.value=selected.dataset.version;classify.elements.category.value=selected.dataset.category;classify.elements.category.dispatchEvent(new Event('change',{bubbles:true}));classify.elements.reason.value='';classify.querySelector('[role="alert"]')?.remove();button.disabled=false;region.querySelector('[data-equity-selection-status]').textContent=selected.getAttribute('aria-label');});
  region.addEventListener('click',event=>{if(event.target.closest('a,button,input,select,textarea,label'))return;event.target.closest('tbody tr')?.querySelector('[data-equity-select]')?.click();});
  region.addEventListener('resource-table:reset',clear);region.addEventListener('resource-table:updated',()=>{clear();const query=new URL(location.href).searchParams;for(const name of ['from','through'])classify.elements[name].value=query.get(name)||region.querySelector('form[method=get] [name='+name+']').value;classify.elements.page.value=query.get('page')||'0';});
  classify.addEventListener('submit',event=>{if(!classify.elements.journal.value)event.preventDefault();});
  const retained=classify.elements.journal.value,retainedVersion=classify.elements.version.value;
  if(retained){
   const restore=()=>{const selected=[...region.querySelectorAll('[data-equity-select]')].find(c=>c.value===retained);if(!selected){clear();return;}selected.checked=true;classify.elements.journal.value=retained;classify.elements.version.value=retainedVersion;highlight();button.disabled=false;open();};
   restore();window.addEventListener('pageshow',()=>{if(!dismissed)requestAnimationFrame(restore);},{once:true});
  }
 });
})();
