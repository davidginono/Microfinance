(() => {
 'use strict';
 const year=document.querySelector('[data-fs-year]');
 if(year){const from=document.querySelector('input[name="from"]'),through=document.querySelector('input[name="through"]');year.addEventListener('change',()=>{if(!year.value)return;from.value=`${year.value}-01-01`;const end=`${year.value}-12-31`;through.value=end>through.dataset.today?through.dataset.today:end;});for(const field of [from,through])field.addEventListener('change',()=>{year.value='';});}
 function applyViewSettings(root){
  const paper=root.querySelector('[data-fs-paper]');
  if(!paper)return;
  const key=`statement-view:${paper.dataset.kind}`;
  let prefs={};
  try{const stored=JSON.parse(localStorage.getItem(key)||'{}');if(stored&&typeof stored==='object')prefs=stored;}catch(_){}
  const save=()=>{try{localStorage.setItem(key,JSON.stringify(prefs));}catch(_){}};
  for(const [name,selector,allowed] of [['font','[data-fs-font]',['reference','sans','serif','mono']],['density','[data-fs-density]',['compact','comfortable']]]){
   const control=root.querySelector(selector);if(!control)continue;
   if(allowed.includes(prefs[name]))control.value=prefs[name];
   paper.dataset[name]=control.value;
   control.addEventListener('change',()=>{paper.dataset[name]=control.value;prefs[name]=control.value;save();});
  }
  const comparison=root.querySelector('[data-fs-comparison]');
  if(comparison){comparison.checked=prefs.comparison!==false;const apply=()=>paper.dataset.comparison=comparison.checked?'shown':'hidden';apply();comparison.addEventListener('change',()=>{apply();prefs.comparison=comparison.checked;save();});}
 }
 applyViewSettings(document);
 if(!document.querySelector('.fs-workspace'))return;
 history.replaceState({...history.state,statementPreview:true},'',location.href);
 let pending;
 async function switchStatement(url,push){
  pending?.abort();
  const request=new AbortController();pending=request;
  const current=document.querySelector('.fs-workspace'),status=current.querySelector('.fs-tab-status');
  current.setAttribute('aria-busy','true');status.hidden=false;status.textContent=current.dataset.tabLoading;
  try{
   const response=await fetch(url,{credentials:'same-origin',cache:'no-store',signal:AbortSignal.any([request.signal,AbortSignal.timeout(20000)])});
   if(response.redirected)throw new Error('Statement unavailable');
   if(!response.ok)throw new Error('Statement unavailable');
   const parsed=new DOMParser().parseFromString(await response.text(),'text/html');
   const next=parsed.querySelector('.fs-workspace');
   if(!next||request.signal.aborted)throw new Error('Statement unavailable');
   applyViewSettings(next);
   next.querySelector('.fs-view-settings').open=current.querySelector('.fs-view-settings').open;
   current.replaceWith(next);
   const actions=parsed.querySelector('[data-fs-preview-actions]');
   if(actions)document.querySelector('[data-fs-preview-actions]')?.replaceWith(actions);
   window.refreshEnhancedSelects?.(next);
   if(push)history.pushState({statementPreview:true},'',url);
   next.querySelector('.fs-tabs [aria-current="page"]')?.focus({preventScroll:true});
  }catch(error){
   if(request.signal.aborted)return;
   status.textContent=current.dataset.tabError;
  }finally{
   if(pending===request){document.querySelector('.fs-workspace')?.removeAttribute('aria-busy');pending=null;}
  }
 }
 document.addEventListener('click',event=>{
  const link=event.target instanceof Element?event.target.closest('[data-fs-tab]'):null;
  if(!link||event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;
  event.preventDefault();
  if(link.href!==location.href)void switchStatement(link.href,true);
  else{pending?.abort();pending=null;const root=document.querySelector('.fs-workspace');root.removeAttribute('aria-busy');root.querySelector('.fs-tab-status').hidden=true;}
 });
 window.addEventListener('popstate',event=>{if(event.state?.statementPreview)void switchStatement(location.href,false);});
 window.addEventListener('pagehide',()=>pending?.abort());
})();
