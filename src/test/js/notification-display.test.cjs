const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const read=file=>readFileSync('src/main/resources/static/js/'+file,'utf8');
function setup(){
 const dom=new JSDOM('<body class="aws-console" data-dismiss-notification="Funga"><div id="appToastContainer"></div><main><div data-workspace-flashbar></div></main><div id="modal" class="app-modal-overlay" aria-hidden="false"><section class="app-modal-panel"><header class="app-modal-header">Edit</header><form><input value="keep"><button id="save">Save</button></form></section></div></body>',{url:'http://localhost/admin/users',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,ready=[];
 w.matchMedia=()=>({matches:false,addEventListener(){}});
 w.WorkspaceNavigation={onReady:callback=>ready.push(callback)};
 w.eval(read('shell.js'));w.eval(read('console-resource-details.js'));
 return {dom,w,d};
}
test('every severity uses the shared surface, semantic icon, accessible role and localized dismissal',()=>{
 const {dom,w,d}=setup();
 try{
  for(const [type,icon] of [['success','status-positive'],['error','status-negative'],['warning','status-warning'],['info','status-info']]){
   const {element}=w.showToast(type,'Message '+type,{placement:'page'});
   assert.equal(element.dataset.messageType,type);assert.equal(element.classList.contains('console-message'),true);
   assert.equal(element.getAttribute('role'),type==='error'?'alert':'status');
   assert.equal(element.querySelector('.console-message-icon').dataset.kind,type);
   assert.equal(element.querySelector('button').getAttribute('aria-label'),'Funga');
   assert.equal(element.querySelector('[data-toast-text]').textContent,'Message '+type);
   assert.equal(element.querySelector('.console-message-icon').outerHTML.includes(w.ConsoleResourceDetails.icon(icon).innerHTML),true);
  }
 }finally{dom.window.close();}
});
test('default saved feedback stays in the workspace and message text is escaped',()=>{
 const {dom,w,d}=setup();
 try{
  const {element}=w.showToast('success','<img src=x onerror=alert(1)>');
  assert.equal(element.parentElement,d.querySelector('[data-workspace-flashbar]'));
  assert.equal(element.querySelector('img'),null);
  assert.equal(element.querySelector('[data-toast-text]').textContent,'<img src=x onerror=alert(1)>');
  assert.equal(element.style.getPropertyValue('--toast-depth'),'');
 }finally{dom.window.close();}
});
test('modal feedback stays between its header and live form while explicit workspace feedback stays outside',()=>{
 const {dom,w,d}=setup();
 try{
  const save=d.querySelector('#save'),form=save.form;save.focus();
  const {element}=w.showToast('error','Try again',{contextElement:save});
  const container=d.querySelector('[data-modal-toast-container]');
  assert.equal(element.parentElement,container);assert.equal(container.previousElementSibling.tagName,'HEADER');
  assert.equal(container.nextElementSibling,form);assert.equal(form.querySelector('input').value,'keep');
  assert.equal(w.showToast('success','Saved',{placement:'workspace'}).element.parentElement,d.querySelector('[data-workspace-flashbar]'));
 }finally{dom.window.close();}
});
test('dismissal promotes queued notices without losing messages or dismissing them automatically',()=>{
 const {dom,w,d}=setup(),timers=[];
 try{
  w.setTimeout=callback=>{timers.push(callback);return timers.length;};
  const notices=Array.from({length:4},(_,i)=>w.showToast('info','Notice '+i));
  assert.equal(d.querySelectorAll('[data-app-toast]').length,3);assert.equal(notices[3].element.isConnected,false);
  assert.equal(timers.length,0);
  notices[0].element.querySelector('button').click();timers.shift()();
  assert.equal(notices[0].element.isConnected,false);assert.equal(notices[3].element.isConnected,true);
  assert.deepEqual([...d.querySelectorAll('[data-toast-text]')].map(node=>node.textContent),['Notice 1','Notice 2','Notice 3']);
 }finally{dom.window.close();}
});

test('scroll-wrapped modal feedback stays below its header and reuses one notification region',()=>{
 const {dom,w,d}=setup();
 try{
  const panel=d.querySelector('.app-modal-panel'),scroll=d.createElement('div');scroll.className='app-modal-scroll';
  scroll.append(...panel.children);panel.append(scroll);
  const save=d.querySelector('#save');
  w.showToast('error','Try again',{contextElement:save});
  w.showToast('info','Values retained',{contextElement:save});
  const container=d.querySelector('[data-modal-toast-container]');
  assert.equal(container.parentElement,scroll);assert.equal(container.previousElementSibling.tagName,'HEADER');
  assert.equal(container.nextElementSibling,save.form);assert.equal(d.querySelectorAll('[data-modal-toast-container]').length,1);
  assert.equal(container.querySelectorAll('[data-app-toast]').length,2);
 }finally{dom.window.close();}
});
