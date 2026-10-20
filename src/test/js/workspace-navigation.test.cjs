const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {JSDOM,requestInterceptor,VirtualConsole}=require('jsdom');
const read=name=>fs.readFileSync('src/main/resources/static/js/'+name,'utf8');
const runtime=read('workspace-navigation.js');
const init=`(()=>{const label=document.querySelector('h1').textContent;
window.initialized.push(label);
document.addEventListener('workspace:test',()=>window.commands.push(label));
document.addEventListener('DOMContentLoaded',()=>window.ready.push(label));
window.addEventListener('load',()=>window.loaded.push(label));
window.addEventListener('pagehide',()=>window.hidden.push(label));
window.__neoSelectEnhancerLoaded=true;
setTimeout(()=>window.late.push(label),200);
fetch('/pending').catch(()=>{});
document.querySelector('#command').onclick=()=>window.commands.push(label);
})();
`;
const discard=`<div id="accounting-discard-modal" hidden><div role="dialog" tabindex="-1"><button data-accounting-keep>Keep editing</button><div class="app-modal-actions"><button data-accounting-keep>Keep</button><button data-accounting-discard>Discard</button></div></div></div>`;
function html(title,guard=false){return `<!doctype html><html lang="en"><head><title>${title}</title><meta name="_csrf" content="${title}-csrf"><script src="/js/workspace-navigation.js" data-workspace-navigation></script><script src="/js/shell.js" defer></script><script src="/js/page.js" defer></script>${guard?'<script src="/js/accounting-form-ux.js" defer></script>':''}</head><body class="aws-console" data-navigation-loading="Loading workspace" data-navigation-error="Could not load" data-navigation-retry="Try again"><nav id="shellTopNav">Header</nav><aside id="appSidebar"><a href="/admin/dashboard">Dashboard</a><a href="/admin/users">Users</a><a href="/admin/settings-controls?section=loan">Products</a></aside><main class="shell-main"><h1 class="erp-page-title">${title}</h1><form class="accounting-create-form"><input name="name" value="Original"><button type="submit">Save</button></form><button id="command">Command</button></main><div id="pageSubmitPreloader" aria-hidden="true"></div>${guard?discard:''}</body></html>`;}
async function setup({guard=false,holdNavigation=false}={}){
 const requests=[],failures=[],pages=new Map([['/admin/dashboard',html('Dashboard',guard)],['/admin/users',html('Users')],['/admin/settings-controls?section=loan',html('Products')]]);
 const resources={interceptors:[requestInterceptor(request=>{const name=new URL(request.url).pathname.split('/').pop();return new Response(name==='page.js'?init:read(name),{headers:{'Content-Type':'application/javascript'}});})]};
 const console=new VirtualConsole();console.on('jsdomError',error=>failures.push(error));
 const dom=new JSDOM(html('Dashboard',guard),{url:'http://localhost/admin/dashboard',runScripts:'dangerously',resources,pretendToBeVisual:true,virtualConsole:console,beforeParse(w){
   for(const key of ['initialized','commands','ready','loaded','hidden','late'])w[key]=[];
   w.matchMedia=()=>({matches:false,addEventListener(){}});w.scrollTo=()=>{};
   w.fetch=(url,options={})=>{const target=new URL(url,w.location.href),item={url:target,options};requests.push(item);if(target.pathname.startsWith('/card/') || (holdNavigation && target.pathname==='/admin/users'))return new Promise(resolve=>item.resolve=resolve);if(target.pathname==='/pending')return new Promise(()=>{});return Promise.resolve({ok:pages.has(target.pathname+target.search),url:target.href,headers:{get:()=> 'text/html'},text:async()=>pages.get(target.pathname+target.search) || '<h1>Denied</h1>'});};
 }});
 await new Promise(resolve=>dom.window.addEventListener('load',resolve,{once:true}));
 return {dom,w:dom.window,d:dom.window.document,requests,pages,failures};
}
const until=async predicate=>{for(let i=0;i<100;i++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,10));}assert.fail('Navigation did not settle');};

test('sidebar switches JSP documents in place, initializes destination controls once and keeps the preloader hidden',async()=>{
 const {dom,w,d,requests,failures}=await setup();
 try{
  const original=d,header=d.querySelector('#shellTopNav'),sidebar=d.querySelector('#appSidebar'),oldPending=requests.find(r=>r.url.pathname==='/pending');
  const integration={enabled:true};w.hostIntegration=integration;
  d.querySelector('a[href="/admin/users"]').click();await until(()=>w.loaded.includes('Users'));
  assert.equal(w.document,original);assert.equal(w.location.pathname,'/admin/users');assert.equal(d.title,'Users');
  assert.equal(d.querySelector('#shellTopNav'),header);assert.equal(d.querySelector('#appSidebar'),sidebar);
  assert.equal(w.hostIntegration,integration);
  assert.equal(d.querySelector('meta[name=_csrf]').content,'Users-csrf');assert.equal(d.querySelector('#pageSubmitPreloader').classList.contains('is-active'),false);
  assert.equal(oldPending.options.signal.aborted,true);assert.deepEqual([...w.ready],['Dashboard','Users']);assert.deepEqual([...w.loaded],['Dashboard','Users']);
  d.dispatchEvent(new w.Event('workspace:test'));d.querySelector('#command').click();assert.deepEqual([...w.commands],['Users','Users']);
  await w.WorkspaceNavigation.visit('/admin/dashboard');d.dispatchEvent(new w.Event('workspace:test'));
  assert.deepEqual([...w.commands],['Users','Users','Dashboard']);assert.deepEqual([...w.hidden],['Dashboard','Users']);assert.equal(failures.length,0);
 }finally{dom.window.close();}
});

test('Back and Forward restore server-rendered workspaces while query-only history keeps its current document',async()=>{
 const {dom,w,d}=await setup();try{
  await w.WorkspaceNavigation.visit('/admin/users');await w.WorkspaceNavigation.visit('/admin/settings-controls?section=loan');
  w.history.back();await until(()=>d.querySelector('h1').textContent==='Users');
  w.history.forward();await until(()=>d.querySelector('h1').textContent==='Products');
  const main=d.querySelector('main');w.history.pushState({table:true},'', '?section=loan&page=1');w.history.back();
  await until(()=>!w.location.search.includes('page=1'));assert.equal(d.querySelector('main'),main);
 }finally{dom.window.close();}
});

test('denied and failed requests retain the current workspace and offer a local retry',async()=>{
 const {dom,w,d,pages}=await setup();try{
  pages.delete('/admin/users');const input=d.querySelector('input');input.value='Unsaved';
  await w.WorkspaceNavigation.visit('/admin/users');assert.equal(w.location.pathname,'/admin/dashboard');assert.equal(d.querySelector('input'),input);assert.equal(input.value,'Unsaved');
  assert.equal(d.querySelector('[role=alert]').textContent,'Could not loadTry again');
  pages.set('/admin/users',html('Users'));d.querySelector('[role=alert] button').click();await until(()=>w.loaded.includes('Users'));
  assert.equal(d.querySelector('[role=alert]'),null);
 }finally{dom.window.close();}
});

test('existing dirty form confirmation preserves edits and resumes sidebar navigation only after Discard',async()=>{
 const {dom,w,d,requests}=await setup({guard:true});try{
  d.querySelector('input[name=name]').value='Edited';d.querySelector('a[href="/admin/users"]').click();
  assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);assert.equal(requests.some(r=>r.url.pathname==='/admin/users'),false);
  d.querySelector('[data-accounting-keep]').click();assert.equal(d.querySelector('input[name=name]').value,'Edited');
  d.querySelector('a[href="/admin/users"]').click();d.querySelector('[data-accounting-discard]').click();await until(()=>w.loaded.includes('Users'));
  assert.equal(w.location.pathname,'/admin/users');assert.equal(d.querySelector('#accounting-discard-modal'),null);
 }finally{dom.window.close();}
});

test('rapid choices abort superseded navigation and never mount a stale destination',async()=>{
 const {dom,w,d}=await setup();try{
  // The native fetch captured by the runtime reads this test page map asynchronously.
  const first=w.WorkspaceNavigation.visit('/admin/users'),second=w.WorkspaceNavigation.visit('/admin/settings-controls?section=loan');
  await Promise.all([first,second]);assert.equal(d.querySelector('h1').textContent,'Products');assert.equal(w.initialized.includes('Users'),false);
  const link=d.createElement('a');link.href='https://example.test';assert.equal(w.WorkspaceNavigation.handles(link),false);
  link.href='/admin/users';link.download='export';assert.equal(w.WorkspaceNavigation.handles(link),false);
 }finally{dom.window.close();}
});

test('Back from a changed form restores the current URL on cancellation and preserves forward history after Discard',async()=>{
 const {dom,w,d,pages}=await setup();try{
  pages.set('/admin/users',html('Users',true));await w.WorkspaceNavigation.visit('/admin/users');d.querySelector('input[name=name]').value='Edited';
  w.history.back();await until(()=>d.querySelector('#accounting-discard-modal')?.hidden===false);await until(()=>w.location.pathname==='/admin/users');
  assert.equal(d.querySelector('h1').textContent,'Users');assert.equal(d.querySelector('input[name=name]').value,'Edited');
  d.querySelector('[data-accounting-discard]').click();await until(()=>d.querySelector('h1').textContent==='Dashboard');
  w.history.forward();await until(()=>d.querySelector('h1').textContent==='Users');assert.equal(d.querySelector('input[name=name]').value,'Original');
 }finally{dom.window.close();}
});

test('missing workspace shells cannot replace the authorized document',async()=>{
 const {dom,w,d,pages}=await setup();try{
  const form=d.querySelector('form');pages.set('/admin/users','<body class="aws-console"><main class="shell-main">Access denied</main></body>');
  await w.WorkspaceNavigation.visit('/admin/users');assert.equal(d.querySelector('form'),form);assert.equal(w.location.pathname,'/admin/dashboard');
  assert.equal(d.querySelector('[role=alert]').textContent,'Could not loadTry again');
 }finally{dom.window.close();}
});

test('workspace data sections settle independently, retain form values and expose local Retry',async()=>{
 const {dom,w,d,pages,requests}=await setup();try{
  const card=id=>`<section data-dashboard-source="usage" data-dashboard-url="/card/${id}"><h2>${id}</h2><input name="memo" value="Original"><span data-dashboard-metric="activeUsers">—</span><div data-dashboard-data-status></div><button data-dashboard-retry hidden>Retry</button></section>`;
  pages.set('/admin/users',html('Users').replace('</main>',card('one')+card('two')+'</main>').replace('</head>','<script src="/js/ui-loading.js" defer></script><script src="/js/dashboard-data.js" defer></script></head>').replace('</body>','<template id="dashboard-labels" data-loading="Loading" data-load-error="Unavailable"></template></body>'));
  await w.WorkspaceNavigation.visit('/admin/users');
  const pending=requests.filter(r=>r.url.pathname.startsWith('/card/')),one=d.querySelector('[data-dashboard-url="/card/one"]'),two=d.querySelector('[data-dashboard-url="/card/two"]');
  assert.equal(pending.length,2);assert.equal(d.querySelectorAll('.app-loading-spinner').length,2);
  const payload=value=>({activeUsers:value,activeSessions:value,pageViews:value,logins:value,timeline:[]});
  pending[0].resolve({ok:true,json:async()=>payload(7)});await until(()=>one.querySelector('[data-dashboard-metric]').textContent==='7');
  assert.equal(one.querySelector('.app-loading-spinner'),null);assert.equal(two.querySelectorAll('.app-loading-spinner').length,1);
  const input=two.querySelector('input');input.value='Keep my entry';pending[1].resolve({ok:false});await until(()=>!two.querySelector('[data-dashboard-retry]').hidden);
  assert.equal(two.querySelector('.app-loading-spinner'),null);assert.equal(two.querySelector('input'),input);assert.equal(input.value,'Keep my entry');
  two.querySelector('[data-dashboard-retry]').click();const retried=requests.at(-1);assert.equal(two.querySelectorAll('.app-loading-spinner').length,1);
  retried.resolve({ok:true,json:async()=>payload(9)});await until(()=>two.querySelector('[data-dashboard-metric]').textContent==='9');
  assert.equal(two.querySelector('.app-loading-spinner'),null);assert.equal(two.querySelector('[data-dashboard-retry]').hidden,true);assert.equal(input.value,'Keep my entry');
 }finally{dom.window.close();}
});

test('mobile workspace switches preserve the remembered desktop sidebar choice',async()=>{
 const {dom,w}=await setup();try{
  w.WorkspaceNavigation.rememberSidebar(true);await w.WorkspaceNavigation.visit('/admin/users');
  assert.equal(w.WorkspaceNavigation.sidebarCollapsed,true);assert.equal(w.document.documentElement.classList.contains('shell-sidebar-collapsed'),false);
  w.WorkspaceNavigation.rememberSidebar(false);await w.WorkspaceNavigation.visit('/admin/settings-controls?section=loan');assert.equal(w.WorkspaceNavigation.sidebarCollapsed,false);
 }finally{dom.window.close();}
});

test('selecting the current workspace cancels a pending switch and keeps entered values',async()=>{
 const {dom,w,d,requests}=await setup();try{
  const input=d.querySelector('input[name=name]');input.value='Retain this';
  const pending=w.WorkspaceNavigation.visit('/admin/users');await w.WorkspaceNavigation.visit('/admin/dashboard');await pending;
  assert.equal(requests.find(r=>r.url.pathname==='/admin/users').options.signal.aborted,true);
  assert.equal(d.querySelector('h1').textContent,'Dashboard');assert.equal(d.querySelector('input[name=name]'),input);assert.equal(input.value,'Retain this');assert.equal(d.querySelector('#workspace-navigation-feedback'),null);
 }finally{dom.window.close();}
});

test('workspace HTML requests use an unlabeled main canvas spinner, recover original inputs on failure and cancel cleanly',async()=>{
 const {dom,w,d,requests}=await setup({holdNavigation:true});try{
  const main=d.querySelector('main'),header=d.querySelector('#shellTopNav'),input=d.querySelector('input');input.value='Keep my entry';
  const visit=w.WorkspaceNavigation.visit('/admin/users'),pending=requests.find(r=>r.url.pathname==='/admin/users');
  const status=d.querySelector('#workspace-navigation-feedback');
  assert.equal(status.textContent,'');assert.equal(status.getAttribute('aria-label'),'Loading workspace');
  assert.equal(status.querySelector('.app-loading-spinner').dataset.loadingSize,'large');
  assert.equal(main.classList.contains('is-workspace-pending'),true);assert.equal(main.getAttribute('aria-busy'),'true');
  assert.equal(d.querySelector('#shellTopNav'),header);assert.equal(d.querySelector('input'),input);
  pending.resolve({ok:false});await visit;
  assert.equal(main.classList.contains('is-workspace-pending'),false);assert.equal(main.hasAttribute('aria-busy'),false);
  assert.equal(input.value,'Keep my entry');assert.equal(d.querySelector('.app-loading-spinner'),null);
  assert.equal(d.querySelector('[role=alert] button').textContent,'Try again');
  const cancelled=w.WorkspaceNavigation.visit('/admin/users');const last=requests.at(-1);
  await w.WorkspaceNavigation.visit('/admin/dashboard');last.resolve({ok:false});await cancelled;
  assert.equal(last.options.signal.aborted,true);assert.equal(main.classList.contains('is-workspace-pending'),false);
  assert.equal(d.querySelector('#workspace-navigation-feedback'),null);assert.equal(input.value,'Keep my entry');
 }finally{dom.window.close();}
});
