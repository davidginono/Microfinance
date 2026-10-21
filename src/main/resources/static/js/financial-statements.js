(() => {
 'use strict';
 const year=document.querySelector('[data-fs-year]');
 if(year){const from=document.querySelector('input[name="from"]'),through=document.querySelector('input[name="through"]');year.addEventListener('change',()=>{if(!year.value)return;from.value=`${year.value}-01-01`;const end=`${year.value}-12-31`;through.value=end>through.dataset.today?through.dataset.today:end;});for(const field of [from,through])field.addEventListener('change',()=>{year.value='';});}
 const boundControls=new WeakSet();
 function applyViewSettings(root,saveSettings=false){
  const paper=root.querySelector('[data-fs-paper]');
  if(!paper)return;
  const key=`statement-view:${paper.dataset.kind}`;
  let prefs={};
  try{const stored=JSON.parse(localStorage.getItem(key)||'{}');if(stored&&typeof stored==='object')prefs=stored;}catch(_){}
  const save=()=>{try{localStorage.setItem(key,JSON.stringify(prefs));}catch(_){}};
  for(const [name,selector,allowed] of [['font','[data-fs-font]',['reference','sans','serif','mono']],['density','[data-fs-density]',['compact','comfortable']]]){
   const control=root.querySelector(selector);if(!control)continue;
   if(!saveSettings)control.value=allowed.includes(prefs[name])?prefs[name]:allowed[0];
   paper.dataset[name]=control.value;
   prefs[name]=control.value;
   if(!boundControls.has(control)){boundControls.add(control);control.addEventListener('change',()=>applyViewSettings(root,true));}
  }
  const comparison=root.querySelector('[data-fs-comparison]');
  if(comparison){
   if(!saveSettings)comparison.checked=prefs.comparison!==false;
   paper.dataset.comparison=comparison.checked?'shown':'hidden';prefs.comparison=comparison.checked;
   if(!boundControls.has(comparison)){boundControls.add(comparison);comparison.addEventListener('change',()=>applyViewSettings(root,true));}
  }
  if(saveSettings)save();
 }
 applyViewSettings(document);
 if(!document.querySelector('.fs-workspace'))return;
 history.replaceState({...history.state,statementPreview:true},'',location.href);
 let pending,renderedUrl=location.href;
 const workspace=document.querySelector('.fs-workspace');
 function clearPending(){
  const section=workspace.querySelector('[data-fs-report-section]');
  section.removeAttribute('aria-busy');section.querySelector('.fs-tab-status')?.remove();
 }
 async function switchStatement(url,push){
  pending?.abort();
  const request=new AbortController();pending=request;
  clearPending();
  const current=workspace.querySelector('[data-fs-report-section]'),status=document.createElement('div');
  status.className='fs-tab-status';status.setAttribute('role','status');status.setAttribute('aria-label',workspace.dataset.tabLoading);
  const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='large';spinner.setAttribute('aria-hidden','true');status.append(spinner);
  current.setAttribute('aria-busy','true');current.prepend(status);
  try{
   const response=await fetch(url,{credentials:'same-origin',cache:'no-store',signal:AbortSignal.any([request.signal,AbortSignal.timeout(20000)])});
   if(response.redirected)throw new Error('Statement unavailable');
   if(!response.ok)throw new Error('Statement unavailable');
   const parsed=new DOMParser().parseFromString(await response.text(),'text/html');
   const next=parsed.querySelector('[data-fs-report-section]'),paper=next?.querySelector('[data-fs-paper]');
   if(!paper)throw new Error('Statement unavailable');
   if(request.signal.aborted)return;
   current.replaceWith(next);
   for(const tab of workspace.querySelectorAll('[data-fs-tab]')){
    if(tab.href===new URL(url,location.href).href)tab.setAttribute('aria-current','page');else tab.removeAttribute('aria-current');
   }
   const comparison=workspace.querySelector('[data-fs-comparison-setting]');
   if(comparison)comparison.hidden=!['POSITION','INCOME','CASH_FLOW','NOTES'].includes(paper.dataset.kind);
   const exportForm=workspace.querySelector('.fs-toolbar form'),nextExport=parsed.querySelector('.fs-toolbar form');
   if(exportForm&&nextExport){
    exportForm.querySelectorAll('input[type=hidden]').forEach(input=>input.remove());
    for(const input of nextExport.querySelectorAll('input[type=hidden]'))exportForm.prepend(input);
    exportForm.querySelector('[data-print-action]')?.setAttribute('href',nextExport.querySelector('[data-print-action]').getAttribute('href'));
   }
   const saveKind=document.querySelector('[data-fs-save] [name=kind]');if(saveKind)saveKind.value=paper.dataset.kind;
   applyViewSettings(document);window.refreshEnhancedSelects?.(workspace.querySelector('.fs-toolbar'));
   renderedUrl=new URL(url,location.href).href;
   if(push)history.pushState({...history.state,statementPreview:true},'',url);
   workspace.querySelector('.fs-tabs [aria-current="page"]')?.focus({preventScroll:true});
  }catch(error){
   if(request.signal.aborted)return;
   current.removeAttribute('aria-busy');status.removeAttribute('aria-label');status.setAttribute('role','alert');status.textContent=workspace.dataset.tabError;
  }finally{
   if(pending===request)pending=null;
  }
 }
 document.addEventListener('click',event=>{
  const link=event.target instanceof Element?event.target.closest('[data-fs-tab]'):null;
  if(!link||event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;
  event.preventDefault();
  if(link.href!==renderedUrl)void switchStatement(link.href,true);
  else{pending?.abort();pending=null;clearPending();if(location.href!==renderedUrl)history.pushState({...history.state,statementPreview:true},'',renderedUrl);}
 });
 window.addEventListener('popstate',event=>{if(event.state?.statementPreview)void switchStatement(location.href,false);});
 window.addEventListener('pagehide',()=>pending?.abort());
})();
