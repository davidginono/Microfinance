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
 function init(){
  const workspace=document.querySelector('.fs-workspace');
  if(!workspace)return;

  const exportForm=document.querySelector('[data-fs-export]');
  const saveForm=document.querySelector('[data-fs-save]');
  const comparison=workspace.querySelector('[data-fs-comparison-setting]');
  const search=workspace.querySelector('[data-report-preview-search]');
  const section=()=>workspace.querySelector('[data-fs-report-section]');
  const applyContext=current=>{
   const kind=current.querySelector('[data-fs-paper]').dataset.kind;
   if(search)search.value=current.dataset.previewSearch || '';
   for(const link of document.querySelectorAll('[data-fs-nav]')){
    const active=link.dataset.fsNav===(kind==='LEDGER'?'ledger':'statements');
    link.classList.toggle('shell-nav-active',active);
    if(active){
     link.setAttribute('aria-current','page');
     const parent=document.querySelector('[data-fs-parent]');
     if(parent){parent.href=link.href;parent.textContent=link.textContent.trim();}
    }else link.removeAttribute('aria-current');
   }
   for(const tab of workspace.querySelectorAll('[data-fs-tab]')){
    if(tab.dataset.fsTab===kind)tab.setAttribute('aria-current','page');
    else tab.removeAttribute('aria-current');
   }
   if(comparison)comparison.hidden=!['POSITION','INCOME','CASH_FLOW','NOTES'].includes(kind);
   const fields={kind,selectionKind:current.dataset.selectionKind,line:current.dataset.line,account:current.dataset.account};
   if(exportForm){
    for(const [name,value] of Object.entries(fields)){
     let input=exportForm.elements.namedItem(name);
     if(!value){input?.remove();continue;}
     if(!input){input=document.createElement('input');input.type='hidden';input.name=name;exportForm.append(input);}
     input.value=value;
    }
    exportForm.querySelector('[data-print-action]').href=current.dataset.printUrl;
   }
   if(saveForm){
    for(const [name,value] of Object.entries({...fields,page:current.dataset.page})){
     saveForm.elements.namedItem(name).value=value || '';
    }
    saveForm.hidden=current.dataset.saved==='true';
   }
   applyViewSettings(workspace);
   window.refreshEnhancedSelects?.(workspace.querySelector('.fs-toolbar'));
  };

  history.replaceState({...history.state,statementPreview:true},'',location.href);
  let pending,renderedUrl=location.href;
  const lockActions=busy=>{for(const form of [exportForm,saveForm])if(form)form.inert=busy;};

  async function switchStatement(url,push,focus){
   pending?.abort();
   const request=new AbortController();pending=request;
   const current=section();
   window.AppLoading.end(current);
   window.AppLoading.begin(current,{table:false,size:'large',label:workspace.dataset.tabLoading});
   lockActions(true);
   try{
    const response=await fetch(url,{
     credentials:'same-origin',cache:'no-store',headers:{'X-Statement-Fragment':'report'},
     signal:AbortSignal.any([request.signal,AbortSignal.timeout(20000)])
    });
    if(response.redirected||!response.ok)throw new Error('Statement unavailable');
    const parsed=new DOMParser().parseFromString(await response.text(),'text/html');
    const next=parsed.querySelector('[data-fs-report-section]');
    if(!next?.querySelector('[data-fs-paper]')||!next.dataset.printUrl)throw new Error('Statement unavailable');
    if(request.signal.aborted)return;
    window.AppLoading.end(current);
    current.replaceWith(next);
    applyContext(next);
    renderedUrl=url;
    if(!push && !focus && search)history.replaceState(history.state,'',url);
    if(push)history.pushState({...history.state,statementPreview:true},'',url);
    if(focus)next.querySelector('.fs-paper-scroll')?.focus({preventScroll:true});
   }catch(error){
    if(request.signal.aborted)return;
    window.AppLoading.fail(current,{label:workspace.dataset.tabError,retry:()=>switchStatement(url,push,focus)});
   }finally{
    if(pending===request){pending=null;lockActions(false);}
   }
  }

  const binding=search && window.AppSearch?.bind(search,()=>{
   const url=new URL(renderedUrl);
   url.searchParams.delete('page');
   const term=search.value.trim();
   if(term)url.searchParams.set('previewSearch',term);
   else url.searchParams.delete('previewSearch');
   void switchStatement(url.href,false,false);
  },()=>{
   pending?.abort();pending=null;window.AppLoading.end(section());lockActions(false);
  });

  workspace.addEventListener('click',event=>{
   const link=event.target instanceof Element?event.target.closest('[data-fs-tab], [data-fs-drilldown]'):null;
   if(!link||event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;
   event.preventDefault();binding?.cancel();
   if(link.href!==renderedUrl)void switchStatement(link.href,true,link.hasAttribute('data-fs-drilldown'));
   else{
    pending?.abort();pending=null;window.AppLoading.end(section());lockActions(false);
    section().querySelector('[data-ui-loading-error]')?.remove();
    if(location.href!==renderedUrl)history.pushState({...history.state,statementPreview:true},'',renderedUrl);
   }
  });
  window.addEventListener('popstate',event=>{
   if(event.state?.statementPreview && location.href!==renderedUrl)void switchStatement(location.href,false,false);
   else if(event.state?.statementPreview && pending){pending.abort();pending=null;window.AppLoading.end(section());lockActions(false);}
  });
  window.addEventListener('pagehide',()=>{binding?.cancel();pending?.abort();});
  applyContext(section());
 }
 if(window.WorkspaceNavigation)window.WorkspaceNavigation.onReady(init);
 else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});
 else init();
})();
