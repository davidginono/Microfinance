const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/dashboard-board.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
const labels='<template id="dashboard-labels" data-scope="user|institution|branch" data-icons="/icons/" data-reset="Reset" data-add="Add widgets" data-added="Added" data-add-item="Add" data-search="Find widgets" data-close="Close" data-empty="No widgets" data-overview="Overview" data-actions="Actions" data-move="Move" data-resize="Resize" data-preferences="Preferences" data-remove="Remove" data-move-earlier="Move earlier" data-move-later="Move later" data-width="Width" data-height="Height" data-short="Short" data-medium="Medium" data-tall="Tall" data-cancel="Cancel" data-apply="Apply"></template>';
const widget=(id,body='')=>`<section data-dashboard-widget="${id}" data-widget-width="2" data-widget-height="400" class="erp-panel"><div class="erp-panel-header"><h2>${id}</h2></div><div class="erp-panel-body">${body}</div></section>`;
const markup=()=>`<main class="shell-content-frame"><header class="erp-page-header"><h1>Dashboard</h1></header><div data-dashboard-board="test">${widget('Clients','<input id="draft" value="initial"><form method="post"><input name="_csrf" value="token"></form>')}${widget('Activity')}${widget('Operations')}</div></main>`;
async function setup(html=markup(),storage){const dom=new JSDOM(labels+html,{url:'http://localhost/admin/dashboard',runScripts:'outside-only',pretendToBeVisual:true});if(storage)storage(dom.window.localStorage);dom.window.eval(source);await settle();return {dom,w:dom.window,d:dom.window.document};}
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
 const drag=d.querySelector('[data-dashboard-widget=Activity] .dashboard-drag');drag.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true,cancelable:true}));
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
 assert.equal(d.querySelector('[data-dashboard-widget=Secret]'),null);const activity=d.querySelector('[data-dashboard-widget=Activity]');assert.equal(activity.hidden,true);assert.equal(activity.dataset.boardWidth,'4');assert.equal(activity.style.getPropertyValue('--widget-height'),'240px');
 d.querySelector('#dashboard-labels').dataset.scope='different user|institution|other branch';const root=d.querySelector('[data-dashboard-board]');root.replaceWith(root.cloneNode(true));await settle();d.querySelector('.dashboard-toolbar .btn-neutral').click();assert.notEqual(key(d,w),undefined);assert.equal(Object.keys(w.localStorage).length,2);dom.window.close();
});

test('hydrating a replacement dashboard removes old palette/toolbar handlers and initializes new widgets',async()=>{
 const {dom,w,d}=await setup();const root=d.querySelector('[data-dashboard-board]'),replacement=d.createElement('div');replacement.dataset.dashboardBoard='test';replacement.innerHTML=widget('Fresh','<input value="new">');root.replaceWith(replacement);await settle();
 assert.equal(d.querySelectorAll('.dashboard-toolbar').length,1);assert.equal(d.querySelectorAll('.dashboard-palette').length,1);assert.equal(d.querySelectorAll('.dashboard-grid').length,1);assert.equal(d.querySelector('.dashboard-grid').firstElementChild.dataset.dashboardWidget,'Fresh');d.querySelector('.dashboard-toolbar .btn-launch').click();assert.equal(d.querySelector('.dashboard-palette').hidden,false);dom.window.close();
});

test('pointer resizing clamps geometry and stops on cancellation',async()=>{
 const {dom,w,d}=await setup(),handle=d.querySelector('.dashboard-resize'),root=d.querySelector('[data-dashboard-widget=Clients]'),grid=d.querySelector('.dashboard-grid');grid.getBoundingClientRect=()=>({width:1000});
 const pointer=(target,type,x,y)=>{const e=new w.Event(type,{bubbles:true,cancelable:true});Object.assign(e,{button:0,isPrimary:true,pointerId:1,clientX:x,clientY:y});target.dispatchEvent(e);};
 pointer(handle,'pointerdown',100,100);pointer(w,'pointermove',350,180);assert.equal(root.dataset.boardWidth,'3');assert.equal(root.style.getPropertyValue('--widget-height'),'480px');pointer(w,'pointercancel',350,180);pointer(w,'pointermove',1000,1000);assert.equal(root.dataset.boardWidth,'3');assert.equal(root.style.getPropertyValue('--widget-height'),'480px');dom.window.close();
});

test('narrow-screen palette isolates the background and restores focus and scroll styles',async()=>{const {dom,w,d}=await setup();w.innerWidth=360;d.body.style.overflow='auto';const main=d.querySelector('main'),add=d.querySelector('.dashboard-toolbar .btn-launch');add.click();assert.equal(main.inert,true);assert.equal(d.querySelector('.dashboard-palette').getAttribute('aria-modal'),'true');assert.equal(d.body.style.overflow,'hidden');d.querySelector('.dashboard-palette').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));assert.equal(main.inert,false);assert.equal(d.body.style.overflow,'auto');assert.equal(d.activeElement,add);dom.window.close();});

test('existing record links can reveal an intentionally hidden widget without replacing its content',async()=>{const {dom,w,d}=await setup(),clients=d.querySelector('[data-dashboard-widget=Clients]');clients.id='client-summary';clients.querySelector('.dashboard-widget-controls button').click();clients.querySelector('.dashboard-widget-menu button:last-child').click();const link=d.createElement('a');link.href='#client-summary';link.textContent='Show summary';d.querySelector('[data-dashboard-widget=Activity] .erp-panel-body').append(link);link.addEventListener('click',event=>event.preventDefault());link.click();assert.equal(clients.hidden,false);assert.equal(d.querySelector('#draft').value,'initial');dom.window.close();});

test('staff and borrower summary cards keep their native actions and reporting period when framed as a widget',async()=>{const html='<main class="shell-content-frame"><header class="erp-page-header"><h1>Staff</h1></header><div data-dashboard-board="staff"><section class="erp-stat-grid" data-dashboard-widget="overview" data-widget-title="Overview" data-widget-width="4" data-widget-height="360"><button id="summary-action" class="erp-stat-card"><span class="erp-stat-value">12</span><span class="erp-stat-footer">2026</span></button><a class="erp-stat-card" href="/authorized/details">Record</a></section></div></main>';const {dom,w,d}=await setup(html);let clicks=0;const button=d.querySelector('#summary-action');button.addEventListener('click',()=>clicks++);button.click();assert.equal(clicks,1);assert.equal(d.querySelector('.dashboard-widget-body.erp-stat-grid').querySelector('#summary-action'),button);assert.equal(d.querySelector('.erp-stat-footer').textContent,'2026');assert.equal(d.querySelector('.erp-stat-card[href]').getAttribute('href'),'/authorized/details');dom.window.close();});
