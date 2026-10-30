const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');
const source=fs.readFileSync('src/main/resources/static/js/shell.js','utf8');
function setup(){
 const dom=new JSDOM('<body class="aws-console"><button id="open">Open</button><main><div id="modal" class="app-modal-overlay hidden" aria-hidden="true"><section class="app-modal-panel" role="dialog" aria-labelledby="title" tabindex="-1"><header class="app-modal-header"><h2 id="title">Create staff</h2><button class="app-modal-close">Close</button></header><form class="app-modal-body"><input name="name" value="Keep this"><div class="app-modal-actions"><button type="button" id="cancel">Cancel</button></div></form></section></div></main></body>',{url:'http://localhost/admin/users',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document;w.matchMedia=()=>({matches:false,addEventListener(){}});
 w.WorkspaceNavigation={onReady:()=>{}};
 w.sessionStorage.setItem('Saccos:open-modal',JSON.stringify({path:'/admin/users',modal:{type:'id',value:'modal'},at:Date.now()}));
 w.eval(source);
 const modal=d.querySelector('#modal'),open=d.querySelector('#open'),close=modal.querySelector('.app-modal-close');
 open.onclick=()=>{modal.classList.remove('hidden');modal.classList.add('is-open');modal.setAttribute('aria-hidden','false');};
 close.onclick=()=>{modal.classList.add('hidden');modal.classList.remove('is-open');modal.setAttribute('aria-hidden','true');};
 for(const control of d.querySelectorAll('button,input'))control.getClientRects=()=>[{width:20,height:20}];
 return {dom,w,d,modal,open,close};
}
const tick=()=>new Promise(resolve=>setTimeout(resolve,30));
test('stale modal restoration is discarded and overlays mount under body',()=>{
 const {dom,w,d,modal}=setup();try{
  assert.equal(modal.parentElement,d.body);assert.equal(modal.classList.contains('is-open'),false);
  assert.equal(w.sessionStorage.getItem('Saccos:open-modal'),null);
 }finally{dom.window.close();}
});
test('keyboard focus stays inside the open dialog and returns to its trigger on close',async()=>{
 const {dom,w,d,modal,open,close}=setup();try{
  open.focus();open.click();await tick();assert.equal(d.activeElement,modal.querySelector('input'));
  d.querySelector('#cancel').focus();d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));assert.equal(d.activeElement,close);
  d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Tab',shiftKey:true,bubbles:true,cancelable:true}));assert.equal(d.activeElement,d.querySelector('#cancel'));
  d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));await tick();
  assert.equal(modal.classList.contains('is-open'),false);assert.equal(d.activeElement,open);
 }finally{dom.window.close();}
});
test('Escape cannot dismiss an in-flight command or lose its entered values',async()=>{
 const {dom,w,d,modal,open}=setup();try{
  open.click();await tick();modal.querySelector('form').setAttribute('aria-busy','true');
  d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
  assert.equal(modal.classList.contains('is-open'),true);assert.equal(modal.querySelector('input').value,'Keep this');
 }finally{dom.window.close();}
});

test('nested native dialogs and body-mounted select menus retain their keyboard ownership',async()=>{
 const {dom,w,d,modal,open}=setup();try{
  open.click();await tick();
  const menu=d.createElement('div');menu.dataset.neoSelectMenu='true';menu.innerHTML='<input type="search">';d.body.append(menu);
  let menuKeys=0;menu.addEventListener('keydown',event=>{menuKeys++;event.preventDefault();});
  for(const key of ['Escape','Tab'])menu.querySelector('input').dispatchEvent(new w.KeyboardEvent('keydown',{key,bubbles:true,cancelable:true}));
  assert.equal(menuKeys,2);assert.equal(modal.classList.contains('is-open'),true);
  menu.remove();const dialog=d.createElement('dialog');dialog.setAttribute('open','');dialog.innerHTML='<button>Keep editing</button>';d.body.append(dialog);
  let workspaceEscapes=0;d.addEventListener('keydown',event=>{if(event.key==='Escape')workspaceEscapes++;});
  const escape=new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});dialog.querySelector('button').dispatchEvent(escape);
  assert.equal(escape.defaultPrevented,false);assert.equal(workspaceEscapes,0);assert.equal(modal.classList.contains('is-open'),true);
 }finally{dom.window.close();}
});
test('successful page exit closes dialogs and they stay closed on history restoration',async()=>{
 const {dom,w,modal,open}=setup();try{
  open.click();await tick();w.dispatchEvent(new w.PageTransitionEvent('pagehide'));await tick();
  w.dispatchEvent(new w.PageTransitionEvent('pageshow',{persisted:true}));await tick();
  assert.equal(modal.classList.contains('is-open'),false);assert.equal(w.sessionStorage.getItem('Saccos:open-modal'),null);
 }finally{dom.window.close();}
});
