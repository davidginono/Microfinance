const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {JSDOM}=require('jsdom');
const read=name=>fs.readFileSync('src/main/resources/static/js/'+name,'utf8');
const discard='<div id="accounting-discard-modal" hidden><div role="dialog"><button data-accounting-keep>Close</button><div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div>';
function fixture(content){
 const dom=new JSDOM(content+discard,{url:'http://localhost/admin/platform-settings/email/edit',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,d=w.document,visits=[];
 w.WorkspaceNavigation={onReady:callback=>callback(),navigate:url=>{visits.push(url);return Promise.resolve(true);}};
 w.eval(read('platform-form-ux.js'));w.eval(read('accounting-form-ux.js'));
 return {dom,w,d,visits};
}
const edit='<form action="/admin/platform-settings/email" method="post" data-unchanged-url="/admin/platform-settings?unchanged=email#email"><input name="host" value="smtp.example.test"><input type="password" name="password"><input type="hidden" name="_csrf" value="csrf"><button type="submit">Save changes</button></form>';
test('a destination success notice initializes without a URL fragment',()=>{
 const {dom,d}=fixture('<div data-workspace-flashbar><div data-app-toast role="status">Institution created</div></div>');
 try{assert.equal(d.querySelector('[data-app-toast]').textContent,'Institution created');}finally{dom.window.close();}
});
test('unchanged page edit returns with informational context; a changed edit reaches its original submit owner',async()=>{
 const {dom,w,d,visits}=fixture(edit);
 try{
  const form=d.querySelector('form'),button=form.querySelector('button');
  const submit=()=>{const event=new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:button});form.dispatchEvent(event);return event;};
  assert.equal(submit().defaultPrevented,true);assert.deepEqual(visits,['/admin/platform-settings?unchanged=email#email']);
  await Promise.resolve();form.elements.password.value='New synthetic value';form.elements.password.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(submit().defaultPrevented,false);assert.equal(visits.length,1);assert.equal(form.elements._csrf.value,'csrf');
 }finally{dom.window.close();}
});
test('a restored server draft cannot be mistaken for an unchanged saved configuration',()=>{
 const {dom,w,d,visits}=fixture(edit.replace('</form>','<div data-workspace-form-error>SMTP host is required</div></form>'));
 try{
  const form=d.querySelector('form'),event=new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:form.querySelector('button')});form.dispatchEvent(event);
  assert.equal(event.defaultPrevented,false);assert.deepEqual(visits,[]);
 }finally{dom.window.close();}
});
test('platform opt-in preserves native, external and existing submission owners',()=>{
 const {dom,d}=fixture(edit+'<form action="/logout" method="post"></form><form action="https://example.test/admin/save" method="post"></form><form action="/admin/native" method="post" data-native-navigation></form><form action="/admin/report" method="post" data-workspace-submit></form>');
 try{assert.deepEqual([...d.forms].map(form=>form.getAttribute('data-workspace-submit')),['recover',null,null,null,'']);}
 finally{dom.window.close();}
});
test('top-up settings use single selection, preserve links and clear commands when rows refresh',()=>{
 const {dom,w,d,visits}=fixture('<section data-platform-topup-register><button data-platform-topup-edit disabled>Edit</button><table><tbody>'+['ONE','TWO'].map(id=>`<tr data-platform-topup-row data-edit-url="/admin/platform-settings/loan-top-up/${id}/edit"><td><input type="checkbox" name="platform-topup-selection"></td><td><a href="/admin/saccos/${id}">${id}</a><span>${id}</span></td></tr>`).join('')+'</tbody></table></section>');
 try{
  const command=d.querySelector('button'),rows=d.querySelectorAll('tr'),inputs=d.querySelectorAll('input'),register=d.querySelector('section');
  rows[0].querySelector('span').click();assert.equal(inputs[0].checked,true);assert.equal(command.disabled,false);
  rows[1].querySelector('span').click();assert.equal(inputs[0].checked,false);assert.equal(inputs[1].checked,true);
  command.click();assert.deepEqual(visits,['/admin/platform-settings/loan-top-up/TWO/edit']);
  rows[1].querySelector('span').click();assert.equal(command.disabled,true);
  inputs[0].click();register.dispatchEvent(new w.Event('resource-table:updated'));assert.equal(command.disabled,true);assert.equal(inputs[0].checked,false);
 }finally{dom.window.close();}
});
