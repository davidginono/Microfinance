const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/dashboard-board.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
const labels='<template id="dashboard-labels" data-left="Left" data-right="Right" data-up="Up" data-down="Down" data-scope="user|institution|branch" data-icons="/icons/" data-reset="Reset" data-add="Add widgets" data-added="Added" data-add-item="Add" data-search="Find widgets" data-close="Close" data-empty="No widgets" data-overview="Overview" data-actions="Actions" data-move="Move" data-resize="Resize" data-preferences="Preferences" data-remove="Remove" data-move-earlier="Move earlier" data-move-later="Move later" data-width="Width" data-height="Height" data-short="Short" data-medium="Medium" data-tall="Tall" data-cancel="Cancel" data-apply="Apply"></template>';
const widget=(id,body='')=>`<section data-dashboard-widget="${id}" data-widget-width="2" data-widget-height="400" class="erp-panel"><div class="erp-panel-header"><h2>${id}</h2></div><div class="erp-panel-body">${body}</div></section>`;
const markup=()=>`<main class="shell-content-frame"><header class="erp-page-header"><h1>Dashboard</h1></header><div data-dashboard-board="test">${widget('Clients','<input id="draft" value="initial"><form method="post"><input name="_csrf" value="token"></form>')}${widget('Activity')}${widget('Operations')}</div></main>`;
async function setup(html=markup(),storage){const dom=new JSDOM(labels+html,{url:'http://localhost/admin/dashboard',runScripts:'outside-only',pretendToBeVisual:true});dom.window.innerWidth=1366;
 const w=dom.window;
 w.HTMLElement.prototype.getBoundingClientRect=function(){
  const width=w.innerWidth-100,columns=Number(w.document.querySelector('.dashboard-grid')?.style.getPropertyValue('--dashboard-columns'))||4;
  let left=0,top=0,height=Number.parseInt(this.style.minHeight)||1000,actualWidth=width;
  if(this.matches('[data-dashboard-widget]')){const column=Number.parseInt(this.style.gridColumn)||1,span=Number(this.style.gridColumn.split('span ')[1])||2;left=(column-1)*(width+20)/columns;top=(Number.parseInt(this.style.gridRow)||1)-1;actualWidth=span*(width+20)/columns-20;height=Number.parseInt(this.style.getPropertyValue('--widget-height'))||400;}
  return {left,top,width:actualWidth,height,right:left+actualWidth,bottom:top+height};
 };
 if(storage)storage(dom.window.localStorage);dom.window.eval(source);await settle();return {dom,w:dom.window,d:dom.window.document};}
const key=(d,w)=>Object.keys(w.localStorage).find(key=>key.startsWith('dashboard-layout:'));

for(const chartsFirst of [false,true])test('initial dashboard hides source cards and tables until board and charts are ready: '+(chartsFirst?'charts first':'board first'),async()=>{
 const css=readFileSync('src/main/resources/static/css/ui-loading.css','utf8'),charts=readFileSync('src/main/resources/static/js/dashboard-charts.js','utf8');
 const chart='<div data-dashboard-chart><table data-chart-source><thead><tr><th>Status</th><th>Applications</th></tr></thead><tbody><tr><th>Waiting</th><td>3</td></tr></tbody></table></div>';
 const html='<style>'+css+'</style>'+labels+'<main class="shell-content-frame"><header class="erp-page-header"><h1>Dashboard</h1></header><div data-dashboard-board="staff" aria-busy="true">'+widget('Overview','<a href="/authorized/queue">Review queue</a>')+widget('Applications',chart)+'</div></main>';
 const dom=new JSDOM(html,{url:'http://localhost/accountant/dashboard',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,d=w.document,root=d.querySelector('[data-dashboard-board]'),link=d.querySelector('a');
 assert.equal(w.getComputedStyle(link).visibility,'hidden');assert.equal(w.getComputedStyle(d.querySelector('[data-chart-source]')).visibility,'hidden');
 w.eval(chartsFirst?charts:source);await settle();
 assert.equal(root.hasAttribute('data-dashboard-ready'),false);assert.equal(root.getAttribute('aria-busy'),'true');assert.equal(w.getComputedStyle(link).visibility,'hidden');
 w.eval(chartsFirst?source:charts);await settle();
 assert.equal(root.dataset.dashboardReady,'true');assert.equal(root.getAttribute('aria-busy'),'false');assert.equal(w.getComputedStyle(link).visibility,'visible');
 assert.equal(d.querySelector('a'),link);assert.equal(d.querySelector('[data-chart-source]'),null);assert.ok(d.querySelector('.dashboard-chart svg'));assert.equal(d.querySelectorAll('.dashboard-grid').length,1);dom.window.close();
});

test('reordering and resizing keep native forms, unsaved values and accessible controls mounted',async()=>{
 const {dom,w,d}=await setup(),input=d.querySelector('#draft'),form=d.querySelector('form[method=post]');input.value='unsaved';
 const drag=d.querySelector('[data-dashboard-widget=Activity] .dashboard-drag');drag.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true,cancelable:true}));
 assert.equal(d.querySelector('.dashboard-grid').firstElementChild.dataset.dashboardWidget,'Activity');assert.equal(d.querySelector('#draft'),input);assert.equal(input.value,'unsaved');assert.equal(d.querySelector('form[method=post]'),form);
 const resize=d.querySelector('[data-dashboard-widget=Clients] .dashboard-resize');resize.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));resize.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true,cancelable:true}));
 const saved=JSON.parse(w.localStorage.getItem(key(d,w))).items.find(item=>item.id==='Clients');assert.equal(saved.width,3);assert.equal(saved.height,440);assert.equal(saved.hidden,false);assert.equal(JSON.stringify(saved).includes('unsaved'),false);dom.window.close();
});

test('remove, filtered palette, add and reset preserve widget nodes and restore default order',async()=>{
 const {dom,w,d}=await setup(),clients=d.querySelector('[data-dashboard-widget=Clients]');
 clients.querySelector('.dashboard-widget-controls button').click();clients.querySelector('.dashboard-widget-menu button:last-child').click();assert.equal(clients.hidden,true);
 d.querySelector('.dashboard-toolbar .btn-launch').click();const search=d.querySelector('.dashboard-palette input');search.value='clients';search.dispatchEvent(new w.Event('input'));
 const choices=[...d.querySelectorAll('.dashboard-palette-item')];assert.equal(choices.filter(item=>!item.hidden).length,1);choices[0].querySelector('button').click();assert.equal(clients.hidden,false);assert.equal(d.querySelector('[data-dashboard-widget=Clients]'),clients);
 d.querySelector('[data-dashboard-widget=Operations] .dashboard-drag').dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true,cancelable:true}));d.querySelector('.dashboard-toolbar .btn-neutral').click();
 assert.deepEqual([...d.querySelector('.dashboard-grid').children].map(n=>n.dataset.dashboardWidget),['Clients','Activity','Operations']);assert.equal(d.querySelectorAll('.dashboard-widget[hidden]').length,0);dom.window.close();
});

test('stored layouts restore only authorized widgets with clamped sizes and isolated user/branch keys',async()=>{
 const first=await setup();first.d.querySelector('.dashboard-resize').dispatchEvent(new first.w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));const savedKey=key(first.d,first.w);first.dom.window.close();
 const saved={version:1,items:[{id:'Secret',width:4,height:400,hidden:false},{id:'Activity',width:99,height:-2,hidden:true},{id:'Clients',width:3,height:560,hidden:false}]};
 const {dom,w,d}=await setup(markup(),store=>store.setItem(savedKey,JSON.stringify(saved)));
 assert.equal(d.querySelector('[data-dashboard-widget=Secret]'),null);const activity=d.querySelector('[data-dashboard-widget=Activity]');assert.equal(activity.hidden,true);assert.equal(activity.dataset.boardWidth,'6');assert.equal(activity.style.getPropertyValue('--widget-height'),'240px');
 d.querySelector('#dashboard-labels').dataset.scope='different user|institution|other branch';const root=d.querySelector('[data-dashboard-board]');root.replaceWith(root.cloneNode(true));await settle();d.querySelector('.dashboard-toolbar .btn-neutral').click();assert.notEqual(key(d,w),undefined);assert.equal(Object.keys(w.localStorage).length,2);dom.window.close();
});

test('hydrating a replacement dashboard removes old palette/toolbar handlers and initializes new widgets',async()=>{
 const {dom,w,d}=await setup();const root=d.querySelector('[data-dashboard-board]'),replacement=d.createElement('div');replacement.dataset.dashboardBoard='test';replacement.innerHTML=widget('Fresh','<input value="new">');root.replaceWith(replacement);await settle();
 assert.equal(d.querySelectorAll('.dashboard-toolbar').length,1);assert.equal(d.querySelectorAll('.dashboard-palette').length,1);assert.equal(d.querySelectorAll('.dashboard-grid').length,1);assert.equal(d.querySelector('.dashboard-grid').firstElementChild.dataset.dashboardWidget,'Fresh');d.querySelector('.dashboard-toolbar .btn-launch').click();assert.equal(d.querySelector('.dashboard-palette').hidden,false);dom.window.close();
});

const pointer=(w,target,type,x,y,id=1)=>{const ev=new w.Event(type,{bubbles:true,cancelable:true});Object.assign(ev,{button:0,isPrimary:true,pointerId:id,clientX:x,clientY:y});target.dispatchEvent(ev);};
const positions=d=>Object.fromEntries([...d.querySelectorAll('.dashboard-grid>[data-dashboard-widget]:not([hidden])')].map(n=>[n.dataset.dashboardWidget,{x:Number.parseInt(n.style.gridColumn)-1,y:Number.parseInt(n.style.gridRow)-1,width:Number(n.style.gridColumn.split('span ')[1]),height:Number.parseInt(n.style.getPropertyValue('--widget-height'))}]));
const noOverlap=d=>{const p=Object.values(positions(d));for(let i=0;i<p.length;i++)for(let j=i+1;j<p.length;j++)assert.ok(p[i].x+p[i].width<=p[j].x || p[j].x+p[j].width<=p[i].x || p[i].y+p[i].height+20<=p[j].y || p[j].y+p[j].height+20<=p[i].y);};

test('resize pushes neighbors, snaps to the grid, persists on release and rolls back cancellation',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const handle=d.querySelector('.dashboard-resize'),root=d.querySelector('[data-dashboard-widget=Clients]'),before=positions(d);
 pointer(w,handle,'pointerdown',100,100);pointer(w,w,'pointermove',420,180);assert.equal(root.dataset.boardWidth,'3');assert.equal(root.style.getPropertyValue('--widget-height'),'480px');noOverlap(d);
 pointer(w,handle,'pointercancel',420,180);assert.deepEqual(positions(d),before);assert.equal(root.dataset.boardWidth,'2');assert.equal(key(d,w),undefined);
 pointer(w,handle,'pointerdown',100,100);pointer(w,w,'pointermove',420,180);pointer(w,w,'pointerup',420,180);noOverlap(d);const stored=w.localStorage.getItem(key(d,w));assert.ok(stored);
 pointer(w,handle,'pointerdown',100,100);pointer(w,w,'pointermove',1000,500);d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.equal(w.localStorage.getItem(key(d,w)),stored);assert.equal(root.dataset.boardWidth,'3');assert.equal(root.style.getPropertyValue('--widget-height'),'480px');
});

test('two dimensional dragging swaps equal widgets and preserves native fields; invalid drops and Escape restore positions',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const input=d.querySelector('#draft');input.value='unsaved';const a=d.querySelector('[data-dashboard-widget=Clients]'),handle=a.querySelector('.dashboard-drag'),before=positions(d),step=(1266+20)/4;
 pointer(w,handle,'pointerdown',20,20);pointer(w,w,'pointermove',step*2+20,20);assert.ok(d.querySelector('.dashboard-drop-preview'));noOverlap(d);pointer(w,w,'pointerup',step*2+20,20);
 assert.equal(positions(d).Clients.x,2);assert.equal(positions(d).Activity.x,0);assert.equal(d.querySelector('#draft'),input);assert.equal(input.value,'unsaved');assert.equal(positions(d).Clients.height,before.Clients.height);
 const saved=positions(d);pointer(w,handle,'pointerdown',step*2+20,20);pointer(w,w,'pointermove',20,600);d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));pointer(w,w,'pointerup',20,600);assert.deepEqual(positions(d),saved);assert.equal(d.querySelector('.dashboard-drop-preview'),null);
 pointer(w,handle,'pointerdown',step*2+20,20);pointer(w,w,'pointermove',-50,50);pointer(w,w,'pointerup',-50,50);assert.deepEqual(positions(d),saved);
});

test('dragging into empty cells keeps the requested row and column after reload',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const handle=d.querySelector('.dashboard-drag'),step=1286/4;
 pointer(w,handle,'pointerdown',20,20);pointer(w,w,'pointermove',step+20,880);pointer(w,w,'pointerup',step+20,880);assert.equal(positions(d).Clients.x,1);assert.equal(positions(d).Clients.y,860);noOverlap(d);
 const savedKey=key(d,w),saved=w.localStorage.getItem(savedKey),restored=await setup(markup(),store=>store.setItem(savedKey,saved));t.after(()=>restored.dom.window.close());assert.deepEqual(positions(restored.d),positions(d));
});

test('responsive layouts use six/four/two/one columns and restore desktop positions and widths',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const handle=d.querySelector('[data-dashboard-widget=Activity] .dashboard-drag');handle.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}));const desktop=positions(d);
 for(const [width,count] of [[1920,6],[1366,4],[768,2],[360,1]]){w.innerWidth=width;w.dispatchEvent(new w.Event('resize'));assert.equal(Number(d.querySelector('.dashboard-grid').style.getPropertyValue('--dashboard-columns'))||4,count);noOverlap(d);for(const p of Object.values(positions(d)))assert.ok(p.x+p.width<=count);}
 w.innerWidth=1366;w.dispatchEvent(new w.Event('resize'));assert.deepEqual(positions(d),desktop);
});

test('single click direction controls support moving and resizing without dragging',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const activity=d.querySelector('[data-dashboard-widget=Activity]');activity.querySelector('.dashboard-drag').click();const panel=activity.querySelector('.dashboard-direction-controls');assert.equal(panel.hidden,false);panel.querySelector('[aria-label="Move Left"]').click();assert.equal(positions(d).Activity.x,1);panel.querySelector('[aria-label="Move Left"]').click();assert.equal(positions(d).Activity.x,0);panel.querySelector('.app-btn').click();assert.equal(panel.hidden,true);
 activity.querySelector('.dashboard-resize').click();panel.querySelector('[aria-label="Resize Right"]').click();assert.equal(activity.dataset.boardWidth,'3');noOverlap(d);
});

test('palette drag adds a removed widget to a chosen cell including an empty board',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());for(const e of d.querySelectorAll('[data-dashboard-widget]')){e.querySelector('.dashboard-widget-controls button').click();e.querySelector('.dashboard-widget-menu button:last-child').click();}
 d.querySelector('.dashboard-toolbar .btn-launch').click();const paletteHandle=d.querySelector('.dashboard-palette-item .dashboard-drag');pointer(w,paletteHandle,'pointerdown',1300,100);pointer(w,w,'pointermove',650,40);pointer(w,w,'pointerup',650,40);assert.equal(d.querySelector('[data-dashboard-widget=Clients]').hidden,false);assert.equal(positions(d).Clients.x,2);assert.equal(positions(d).Clients.y,40);assert.equal(d.querySelectorAll('.dashboard-palette-item:not([hidden])').length,2);noOverlap(d);
});

test('narrow-screen palette isolates the background and restores focus and scroll styles',async()=>{const {dom,w,d}=await setup();w.innerWidth=360;d.body.style.overflow='auto';const main=d.querySelector('main'),add=d.querySelector('.dashboard-toolbar .btn-launch');add.click();assert.equal(main.inert,true);assert.equal(d.querySelector('.dashboard-palette').getAttribute('aria-modal'),'true');assert.equal(d.body.style.overflow,'hidden');d.querySelector('.dashboard-palette').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));assert.equal(main.inert,false);assert.equal(d.body.style.overflow,'auto');assert.equal(d.activeElement,add);dom.window.close();});

test('existing record links can reveal an intentionally hidden widget without replacing its content',async()=>{const {dom,w,d}=await setup(),clients=d.querySelector('[data-dashboard-widget=Clients]');clients.id='client-summary';clients.querySelector('.dashboard-widget-controls button').click();clients.querySelector('.dashboard-widget-menu button:last-child').click();const link=d.createElement('a');link.href='#client-summary';link.textContent='Show summary';d.querySelector('[data-dashboard-widget=Activity] .erp-panel-body').append(link);link.addEventListener('click',event=>event.preventDefault());link.click();assert.equal(clients.hidden,false);assert.equal(d.querySelector('#draft').value,'initial');dom.window.close();});

test('staff and borrower summary cards keep their native actions and reporting period when framed as a widget',async()=>{const html='<main class="shell-content-frame"><header class="erp-page-header"><h1>Staff</h1></header><div data-dashboard-board="staff"><section class="erp-stat-grid" data-dashboard-widget="overview" data-widget-title="Overview" data-widget-width="4" data-widget-height="360"><button id="summary-action" class="erp-stat-card"><span class="erp-stat-value">12</span><span class="erp-stat-footer">2026</span></button><a class="erp-stat-card" href="/authorized/details">Record</a></section></div></main>';const {dom,w,d}=await setup(html);let clicks=0;const button=d.querySelector('#summary-action');button.addEventListener('click',()=>clicks++);button.click();assert.equal(clicks,1);assert.equal(d.querySelector('.dashboard-widget-body.erp-stat-grid').querySelector('#summary-action'),button);assert.equal(d.querySelector('.erp-stat-footer').textContent,'2026');assert.equal(d.querySelector('.erp-stat-card[href]').getAttribute('href'),'/authorized/details');dom.window.close();});


test('multiple smaller widgets swap into the space vacated by a larger widget',async t=>{
 const html=markup().replace('data-dashboard-widget="Activity" data-widget-width="2"','data-dashboard-widget="Activity" data-widget-width="1"').replace('data-dashboard-widget="Operations" data-widget-width="2"','data-dashboard-widget="Operations" data-widget-width="1"');
 const {dom,w,d}=await setup(html);t.after(()=>dom.window.close());const handle=d.querySelector('[data-dashboard-widget=Clients] .dashboard-drag');
 pointer(w,handle,'pointerdown',20,20);pointer(w,w,'pointermove',663,20);pointer(w,w,'pointerup',663,20);const p=positions(d);assert.equal(p.Clients.x,2);assert.equal(p.Activity.x,0);assert.equal(p.Operations.x,1);assert.equal(p.Activity.y,0);assert.equal(p.Operations.y,0);noOverlap(d);
});

test('mobile resize at a width limit retains the preferred desktop width',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const widget=d.querySelector('[data-dashboard-widget=Clients]'),handle=widget.querySelector('.dashboard-resize');w.innerWidth=360;w.dispatchEvent(new w.Event('resize'));
 handle.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}));assert.equal(widget.dataset.boardWidth,'2');
 pointer(w,handle,'pointerdown',300,400);pointer(w,w,'pointermove',100,400);pointer(w,w,'pointerup',100,400);assert.equal(widget.dataset.boardWidth,'2');w.innerWidth=1366;w.dispatchEvent(new w.Event('resize'));assert.equal(positions(d).Clients.width,2);
});

test('resizing at the right edge keeps the widget position, and window blur cancels changes',async t=>{
 const {dom,w,d}=await setup();t.after(()=>dom.window.close());const widget=d.querySelector('[data-dashboard-widget=Activity]'),handle=widget.querySelector('.dashboard-resize'),before=positions(d);
 pointer(w,handle,'pointerdown',1200,400);pointer(w,w,'pointermove',1600,480);assert.equal(widget.dataset.boardWidth,'2');assert.equal(positions(d).Activity.x,2);w.dispatchEvent(new w.Event('blur'));assert.deepEqual(positions(d),before);assert.equal(key(d,w),undefined);
});

test('invalid stored coordinates cannot create off-grid widgets or excessive blank space',async t=>{
 const first=await setup();first.d.querySelector('.dashboard-resize').dispatchEvent(new first.w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));const savedKey=key(first.d,first.w);first.dom.window.close();
 const saved={version:2,items:[null,{id:'Clients',width:2,height:400,positions:{4:{x:3,y:0}}},{id:'Activity',width:2,height:400,positions:{4:{x:-1,y:1e12}}},{id:'Operations',width:2,height:400,positions:{4:{x:0,y:0}}}]};
 const {dom,w,d}=await setup(markup(),store=>store.setItem(savedKey,JSON.stringify(saved)));t.after(()=>dom.window.close());noOverlap(d);for(const p of Object.values(positions(d))){assert.ok(p.x>=0 && p.x+p.width<=4);assert.ok(p.y<=840);}
});
