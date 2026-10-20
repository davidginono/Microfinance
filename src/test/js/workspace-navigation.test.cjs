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
function html(title,guard=false){return `<!doctype html><html lang="en"><head><title>${title}</title><meta name="_csrf" content="${title}-csrf"><script src="/js/workspace-navigation.js" data-workspace-navigation></script><script src="/js/shell.js" defer></script><script src="/js/page.js" defer></script>${guard?'<script src="/js/accounting-form-ux.js" defer></script>':''}</head><body class="aws-console" data-navigation-loading="Loading workspace" data-navigation-error="Could not load" data-navigation-retry="Try again"><nav id="shellTopNav">Header</nav><aside id="appSidebar"><a href="/admin/dashboard">Dashboard</a><a href="/admin/users">Users</a><a href="/admin/settings-controls?section=loan">Products</a></aside><main class="shell-main"><h1 class="erp-page-title">${title}</h1><form class="accounting-create-form"><input name="name" value="Original"><button type="submit">Save</button></form><button id="command">Command</button></main>${guard?discard:''}</body></html>`;}
async function setup({guard=false,holdNavigation=false,pageScript=init}={}){
 const requests=[],failures=[],pages=new Map([['/admin/dashboard',html('Dashboard',guard)],['/admin/users',html('Users')],['/admin/settings-controls?section=loan',html('Products')]]);
 const resources={interceptors:[requestInterceptor(request=>{const name=new URL(request.url).pathname.split('/').pop();return new Response(name==='page.js'?pageScript:read(name),{headers:{'Content-Type':'application/javascript'}});})]};
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

test('a saved configuration initialized after its guard stays clean on an in-place workspace visit',async()=>{
 const {dom,w,d,pages}=await setup({pageScript:init+`if(document.querySelector('[data-normalize]'))document.querySelector('[name=name]').value='Saved configuration';`});
 try{
  pages.set('/admin/users',html('Users',true).replace('<script src="/js/page.js" defer></script><script src="/js/accounting-form-ux.js" defer></script>',
   '<script src="/js/accounting-form-ux.js" defer></script><script src="/js/page.js" defer></script>').replace('<main class="shell-main">','<main class="shell-main" data-normalize>'));
  await w.WorkspaceNavigation.visit('/admin/users');
  assert.equal(d.querySelector('[name=name]').value,'Saved configuration');
  await w.WorkspaceNavigation.visit('/admin/settings-controls?section=loan');
  assert.equal(d.querySelector('h1').textContent,'Products');
  await w.WorkspaceNavigation.visit('/admin/users');d.querySelector('[name=name]').value='Unsaved edit';
  await w.WorkspaceNavigation.visit('/admin/settings-controls?section=loan');
  assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);
  assert.equal(d.querySelector('[name=name]').value,'Unsaved edit');
 }finally{dom.window.close();}
});

test('sidebar switches JSP documents in place, initializes destination controls once and keeps the preloader hidden',async()=>{
 const {dom,w,d,requests,failures}=await setup();
 try{
  const original=d,header=d.querySelector('#shellTopNav'),sidebar=d.querySelector('#appSidebar'),oldPending=requests.find(r=>r.url.pathname==='/pending');
  const integration={enabled:true};w.hostIntegration=integration;
  d.querySelector('a[href="/admin/users"]').click();await until(()=>w.loaded.includes('Users'));
  assert.equal(w.document,original);assert.equal(w.location.pathname,'/admin/users');assert.equal(d.title,'Users');
  assert.equal(d.querySelector('#shellTopNav'),header);assert.equal(d.querySelector('#appSidebar'),sidebar);
  assert.equal(w.hostIntegration,integration);
  assert.equal(d.querySelector('meta[name=_csrf]').content,'Users-csrf');assert.equal(d.querySelector('#pageSubmitPreloader'),null);
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

test('a first visit uses the normal workspace spinner and restores inputs on failure or cancellation',async()=>{
 const {dom,w,d,requests}=await setup({holdNavigation:true});try{
  const main=d.querySelector('main'),header=d.querySelector('#shellTopNav'),input=d.querySelector('input');input.value='Keep my entry';
  const visit=w.WorkspaceNavigation.visit('/admin/users'),pending=requests.find(r=>r.url.pathname==='/admin/users');
  const status=d.querySelector('#workspace-navigation-feedback');
  assert.equal(status.textContent,'');assert.equal(status.getAttribute('aria-label'),'Loading workspace');
  assert.equal(status.querySelector('.app-loading-spinner').dataset.loadingSize,'large');assert.equal(status.classList.contains('workspace-navigation-spinner'),true);
  assert.equal(status.querySelector('.app-loading-spinner').getAttribute('aria-hidden'),'true');
  assert.equal(main.classList.contains('is-workspace-preview'),false);
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

const rect=(x,y,width,height)=>({x,y,left:x,top:y,right:x+width,bottom:y+height,width,height});
function layoutFixture(d){
 const main=d.querySelector('main');main.getBoundingClientRect=()=>rect(200,60,900,900);
 const title=main.querySelector('h1');title.getBoundingClientRect=()=>rect(224,84,300,32);
 const cards=[];
 for(const [x,y,width,height] of [[224,140,420,240],[664,140,412,360],[224,520,852,280]]){
  const card=d.createElement('section');card.className='erp-panel';card.innerHTML='<div class="erp-panel-header"><h2>Private client name</h2></div><div><input value="Private balance"><a href="/private/loan">Secret</a></div>';
  card.getBoundingClientRect=()=>rect(x,y,width,height);card.firstElementChild.getBoundingClientRect=()=>rect(x,y,width,52);main.append(card);cards.push(card);
 }
 // Nested and hidden regions must not duplicate indicators or leak hidden structure.
 const nested=d.createElement('div');nested.className='erp-table-wrap';nested.getBoundingClientRect=()=>rect(224,192,420,188);cards[0].append(nested);
 const hidden=cards[0].cloneNode(true);hidden.hidden=true;hidden.getBoundingClientRect=()=>rect(224,900,852,240);main.append(hidden);
 return {main,cards};
}

test('return navigation uses measured destination card positions with one loader per section and no cached content',async()=>{
 const {dom,w,d,pages,requests}=await setup();try{
  layoutFixture(d);await w.WorkspaceNavigation.visit('/admin/users');layoutFixture(d);
  // Hold the HTML body: preview placement cannot depend on the next response.
  let resolve;pages.set('/admin/dashboard',new Promise(done=>resolve=done));
  const pending=w.WorkspaceNavigation.visit('/admin/dashboard');
  const preview=d.querySelector('.workspace-layout-preview');assert.ok(preview);
  const cards=[...preview.querySelectorAll('.workspace-layout-card')];assert.equal(cards.length,3);
  assert.deepEqual(cards.map(card=>[card.style.left,card.style.top,card.style.width,card.style.height]),[['24px','80px','420px','240px'],['464px','80px','412px','360px'],['24px','460px','852px','280px']]);
  assert.equal(preview.querySelectorAll('.app-loading-spinner').length,3);assert.equal(preview.querySelectorAll('.workspace-layout-card-header').length,3);
  assert.equal(preview.querySelector('input,a,script,[id]'),null);assert.equal(preview.textContent,'');assert.doesNotMatch(preview.outerHTML,/Private|Secret|\/private\/loan/);
  assert.equal(preview.getAttribute('aria-label'),'Loading workspace');assert.equal(d.querySelector('main').classList.contains('is-workspace-preview'),true);
  resolve(html('Dashboard'));await pending;assert.equal(d.querySelector('.workspace-layout-preview'),null);assert.equal(d.querySelector('h1').textContent,'Dashboard');
  assert.equal(requests.filter(r=>r.url.pathname==='/admin/dashboard').length,1);
 }finally{dom.window.close();}
});

test('failed warm navigation and resize restore the same original form nodes without replaying requests',async()=>{
 const {dom,w,d,pages,requests}=await setup();try{
  layoutFixture(d);await w.WorkspaceNavigation.visit('/admin/users');const {main}=layoutFixture(d);
  const input=d.querySelector('input');input.value='Retain edited draft';pages.delete('/admin/dashboard');
  const failed=w.WorkspaceNavigation.visit('/admin/dashboard');assert.ok(d.querySelector('.workspace-layout-preview'));await failed;
  assert.equal(d.querySelector('.workspace-layout-preview'),null);assert.equal(d.querySelector('input'),input);assert.equal(input.value,'Retain edited draft');assert.ok(d.querySelector('[role=alert]'));
  let resolve;pages.set('/admin/dashboard',new Promise(done=>resolve=done));const pending=w.WorkspaceNavigation.visit('/admin/dashboard');assert.ok(d.querySelector('.workspace-layout-preview'));
  Object.defineProperty(w,'innerWidth',{value:360,configurable:true});w.dispatchEvent(new w.Event('resize'));
  assert.equal(main.classList.contains('is-workspace-preview'),false);assert.ok(d.querySelector('.workspace-navigation-spinner .app-loading-spinner'));assert.equal(d.querySelector('input'),input);
  assert.equal(requests.filter(r=>r.url.pathname==='/admin/dashboard').length,2);
  await w.WorkspaceNavigation.visit('/admin/users');resolve(html('Dashboard'));await pending;
  assert.equal(d.querySelector('#workspace-navigation-feedback'),null);assert.equal(input.value,'Retain edited draft');
 }finally{dom.window.close();}
});

test('layout memory is bounded and logout clears previews',async()=>{
 const {dom,w,d,pages}=await setup();try{
  for(let index=0;index<13;index++){
   layoutFixture(d);pages.set('/admin/layout-'+index,html('Layout '+index));await w.WorkspaceNavigation.visit('/admin/layout-'+index);
  }
  layoutFixture(d);const oldest=w.WorkspaceNavigation.visit('/admin/dashboard');assert.equal(d.querySelector('.workspace-layout-preview'),null);await oldest;
  layoutFixture(d);d.dispatchEvent(new w.Event('session:logout'));
  const cleared=w.WorkspaceNavigation.visit('/admin/layout-12');assert.equal(d.querySelector('.workspace-layout-preview'),null);await cleared;
 }finally{dom.window.close();}
});

test('layout previews are exact-query and viewport specific and leave pending data indicators under their own owners',async()=>{
 const {dom,w,d,pages}=await setup();try{
  layoutFixture(d);await w.WorkspaceNavigation.visit('/admin/users');layoutFixture(d);
  pages.set('/admin/dashboard?changed=true',html('Other results'));
  const query=w.WorkspaceNavigation.visit('/admin/dashboard?changed=true');assert.ok(d.querySelector('.workspace-navigation-spinner .app-loading-spinner'));assert.equal(d.querySelector('.workspace-layout-preview'),null);await query;
  layoutFixture(d);d.documentElement.lang='sw';d.body.dataset.navigationLoading='Inapakia ukurasa';
  const language=w.WorkspaceNavigation.visit('/admin/users');assert.equal(d.querySelector('.workspace-layout-preview'),null);assert.equal(d.querySelector('[role=status]').getAttribute('aria-label'),'Inapakia ukurasa');await language;
  await w.WorkspaceNavigation.visit('/admin/dashboard?changed=true');
  layoutFixture(d);Object.defineProperty(w,'innerWidth',{value:360,configurable:true});
  const mobile=w.WorkspaceNavigation.visit('/admin/users');assert.equal(d.querySelector('.workspace-layout-preview'),null);await mobile;
  assert.equal(d.querySelector('.app-loading-spinner'),null);
 }finally{dom.window.close();}
});


test('main workspace creation links and programmatic subgroup actions load without replacing the document',async()=>{
 const {dom,w,d,pages}=await setup();try{
  pages.set('/finance/loan-records/new',html('Record loan application'));pages.set('/finance/accounts/groups/new?parentId=family',html('Create subgroup'));
  const header=d.querySelector('#shellTopNav'),sidebar=d.querySelector('#appSidebar'),document=d;
  const create=d.createElement('a');create.className='app-btn btn-launch';create.href='/finance/loan-records/new';create.textContent='Record loan application';d.querySelector('main').append(create);create.click();
  await until(()=>d.querySelector('h1').textContent==='Record loan application');assert.equal(w.document,document);assert.equal(d.querySelector('#shellTopNav'),header);
  await w.WorkspaceNavigation.navigate('/finance/accounts/groups/new?parentId=family');assert.equal(d.querySelector('h1').textContent,'Create subgroup');assert.equal(d.querySelector('#appSidebar'),sidebar);
  w.history.back();await until(()=>d.querySelector('h1').textContent==='Record loan application');
 }finally{dom.window.close();}
});

test('GET report generation serializes repeated criteria and the clicked submitter without a document reload',async()=>{
 const {dom,w,d,pages,requests}=await setup();try{
  const destination='/reports/run?branch=one&branch=two&through=2026-10-20&run=true';pages.set(destination,html('Generated report'));
  const form=d.createElement('form');form.action='/reports/run?stale=discard';form.method='get';form.innerHTML='<input name="branch" value="one"><input name="branch" value="two"><input name="through" value="2026-10-20"><button name="run" value="true">Generate</button>';d.querySelector('main').append(form);
  form.requestSubmit(form.querySelector('button'));await until(()=>d.querySelector('h1').textContent==='Generated report');
  assert.equal(requests.find(r=>r.url.pathname==='/reports/run').url.pathname+requests.find(r=>r.url.pathname==='/reports/run').url.search,destination);assert.equal(w.document,d);assert.equal(d.querySelector('#pageSubmitPreloader'),null);
 }finally{dom.window.close();}
});

test('existing local form handlers, downloads, print and modified links retain their request ownership',async()=>{
 const {dom,w,d,requests}=await setup();try{
  const form=d.createElement('form');form.action='/admin/users';form.addEventListener('submit',e=>e.preventDefault());d.querySelector('main').append(form);form.requestSubmit();
  assert.equal(requests.some(r=>r.url.pathname==='/admin/users'),false);
  const link=d.createElement('a');link.href='/finance/loans/id/pdf';assert.equal(w.WorkspaceNavigation.handles(link),false);link.href='/finance/loans/id/print';assert.equal(w.WorkspaceNavigation.handles(link),false);
  link.href='/admin/users';link.target='_blank';assert.equal(w.WorkspaceNavigation.handles(link),false);
  link.target='';link.href=d.location.href+'#section';assert.equal(w.WorkspaceNavigation.handles(link),false);
  form.method='post';form.removeAttribute('data-workspace-submit');assert.equal(w.WorkspaceNavigation.handlesForm(form),false);
 }finally{dom.window.close();}
});

test('explicit report POST preserves CSRF and request key, prevents duplicate sends and never replays on Back',async()=>{
 const {dom,w,d,requests}=await setup({holdNavigation:true});try{
  const form=d.createElement('form');form.action='/admin/users';form.method='post';form.dataset.workspaceSubmit='';form.innerHTML='<input name="_csrf" value="csrf-token"><input name="requestKey" value="once"><input name="from" value="2026-10-01"><button>Generate</button>';d.querySelector('main').append(form);
  form.requestSubmit();form.requestSubmit();const pending=requests.filter(r=>r.url.pathname==='/admin/users');assert.equal(pending.length,1);assert.equal(pending[0].options.method,'POST');assert.equal(pending[0].options.body.get('_csrf'),'csrf-token');assert.equal(pending[0].options.body.get('requestKey'),'once');
  pending[0].resolve({ok:true,url:'http://localhost/reports/generated/id',headers:{get:()=> 'text/html'},text:async()=>html('Generated report')});await until(()=>d.querySelector('h1').textContent==='Generated report');
  w.history.back();await until(()=>d.querySelector('h1').textContent==='Dashboard');assert.equal(requests.filter(r=>r.options.method==='POST').length,1);
 }finally{dom.window.close();}
});

test('failed report generation restores entered criteria and retries only after an explicit click',async()=>{
 const {dom,w,d,requests}=await setup({holdNavigation:true});try{
  const form=d.createElement('form');form.action='/admin/users';form.method='post';form.dataset.workspaceSubmit='';form.innerHTML='<input name="from" value="2026-10-01"><button>Generate</button>';d.querySelector('main').append(form);
  form.requestSubmit();requests.find(r=>r.url.pathname==='/admin/users').resolve({ok:false});await until(()=>d.querySelector('[role=alert] button'));
  assert.equal(form.elements.from.value,'2026-10-01');assert.equal(form.isConnected,true);assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(requests.filter(r=>r.options.method==='POST').length,1);
  d.querySelector('[role=alert] button').click();assert.equal(requests.filter(r=>r.options.method==='POST').length,2);requests.at(-1).resolve({ok:false});await until(()=>d.querySelector('[role=alert] button'));await new Promise(resolve=>setImmediate(resolve));
 }finally{dom.window.close();}
});


test('native protected POST feedback stays inside its submitter and restores only controls it disabled',async()=>{
 const {dom,w,d}=await setup();try{
  w.eval(read('ui-loading.js'));const form=d.querySelector('form');form.method='post';
  const button=form.querySelector('button'),restricted=d.createElement('button');restricted.type='submit';restricted.disabled=true;form.append(restricted);
  form.elements.name.value='Keep this draft';const event=new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:button});form.dispatchEvent(event);await new Promise(resolve=>setTimeout(resolve,5));
  assert.equal(event.defaultPrevented,false);assert.ok(button.querySelector('[data-ui-action-loading]'));assert.equal(button.disabled,true);
  assert.equal(d.body.hasAttribute('aria-busy'),false);assert.notEqual(d.querySelector('#shellTopNav').inert,true);assert.equal(form.elements.name.value,'Keep this draft');
  w.dispatchEvent(new w.PageTransitionEvent('pageshow',{persisted:true}));assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(button.disabled,false);assert.equal(restricted.disabled,true);
 }finally{dom.window.close();}
});

test('downloads use an actual local request spinner and clear it on both success and failure without a body lock',async()=>{
 const {dom,w,d}=await setup();try{
  w.eval(read('ui-loading.js'));const button=d.querySelector('#command'),calls=[],errors=[];
  w.fetch=(url,options)=>new Promise((resolve,reject)=>calls.push({url,options,resolve,reject}));w.URL.createObjectURL=()=> 'blob:fixture';w.URL.revokeObjectURL=()=>{};let downloads=0;w.HTMLAnchorElement.prototype.click=()=>downloads++;w.showToast=(type,message)=>errors.push(message);
  const ready=w.downloadExport('/report/pdf',button);assert.equal(calls.length,1);assert.equal(calls[0].options.credentials,'same-origin');assert.ok(button.querySelector('[data-ui-action-loading]'));
  assert.equal(d.body.hasAttribute('aria-busy'),false);assert.equal(d.querySelector('#pageSubmitPreloader'),null);
  calls[0].resolve({ok:true,headers:{get:()=>null},blob:async()=>new w.Blob(['pdf'],{type:'application/pdf'})});await ready;
  assert.equal(downloads,1);assert.equal(d.querySelector('.app-loading-spinner'),null);
  const failed=w.downloadExport('/report/pdf',button);calls[1].reject(Error('offline'));await failed;
  assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(errors.length,1);assert.equal(d.querySelector('input').value,'Original');
 }finally{dom.window.close();}
});


test('leaving during export cancels its request and clears feedback without an error toast in the next workspace',async()=>{
 const {dom,w,d}=await setup();try{
  w.eval(read('ui-loading.js'));let signal;const errors=[];w.showToast=(type,message)=>errors.push(message);
  w.fetch=(url,options)=>new Promise((resolve,reject)=>{signal=options.signal;signal.addEventListener('abort',()=>reject(new w.DOMException('Cancelled','AbortError')));});
  const exporting=w.downloadExport('/report/pdf',d.querySelector('#command'));assert.ok(d.querySelector('[data-ui-action-loading]'));
  w.dispatchEvent(new w.PageTransitionEvent('pagehide'));await exporting;
  assert.equal(signal.aborted,true);assert.equal(d.querySelector('[data-ui-action-loading]'),null);assert.equal(errors.length,0);assert.equal(d.body.hasAttribute('aria-busy'),false);
 }finally{dom.window.close();}
});
