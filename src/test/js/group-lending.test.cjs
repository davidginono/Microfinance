const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/group-lending.js','utf8');
const rows=id=>'<tr data-group-row="'+id+'" data-target="/clients/'+id+'"><td><input type="checkbox"></td><td data-group-identity><a href="/clients/'+id+'">'+id+'</a></td><td class="name">Client '+id+'</td></tr>';
function setup(direct=true){
 const dom=new JSDOM('<section data-group-register '+(direct?'data-group-open-on-row':'')+'><button data-group-command="view">Open</button><span data-group-selection-status></span><form method="get"><input name="search"></form><table><tbody>'+rows('A')+rows('B')+'</tbody></table></section>',{url:'http://localhost/clients',runScripts:'dangerously'});
 const w=dom.window,d=w.document,calls=[],ready=[];
 w.WorkspaceNavigation={onReady:fn=>ready.push(fn),navigate:url=>calls.push(url)};
 w.eval(source);ready.forEach(fn=>fn());
 const click=(node,options={})=>node.dispatchEvent(new w.MouseEvent('click',{bubbles:true,cancelable:true,button:0,...options}));
 return {dom,w,d,calls,ready,click};
}
test('onboarding row body opens the existing full-details target immediately',()=>{
 const {dom,d,calls,click}=setup();
 try{click(d.querySelector('.name'));assert.deepEqual(calls,['/clients/A']);assert.equal(d.querySelector('input[type=checkbox]').checked,false);}
 finally{dom.window.close();}
});
test('checkboxes and the selection cell retain the selected-record action flow',()=>{
 const {dom,d,calls,click}=setup();
 try{
  const checkbox=d.querySelector('input[type=checkbox]'),open=d.querySelector('[data-group-command]');
  checkbox.click();assert.equal(checkbox.checked,true);assert.equal(open.disabled,false);assert.equal(calls.length,0);
  click(checkbox.closest('td'));assert.equal(checkbox.checked,false);assert.equal(open.disabled,true);
  checkbox.click();open.click();assert.deepEqual(calls,['/clients/A']);
 }finally{dom.window.close();}
});
test('existing group tables keep row selection and initialization never binds twice',()=>{
 const {dom,d,calls,ready,click}=setup(false);
 try{ready.forEach(fn=>fn());click(d.querySelector('.name'));assert.equal(d.querySelector('input[type=checkbox]').checked,true);assert.equal(calls.length,0);d.querySelector('[data-group-command]').click();assert.deepEqual(calls,['/clients/A']);}
 finally{dom.window.close();}
 const direct=setup();
 try{direct.ready.forEach(fn=>fn());direct.click(direct.d.querySelector('.name'));assert.deepEqual(direct.calls,['/clients/A']);}
 finally{direct.dom.window.close();}
});
test('native ID links, modified clicks and text selection do not trigger row navigation',()=>{
 const {dom,w,d,calls,click}=setup();
 try{
  const link=d.querySelector('a');link.addEventListener('click',e=>e.preventDefault());click(link);click(d.querySelector('.name'),{ctrlKey:true});
  const range=d.createRange();range.selectNodeContents(d.querySelector('.name'));w.getSelection().addRange(range);click(d.querySelector('.name'));
  assert.equal(calls.length,0);assert.equal(d.querySelector('input[type=checkbox]').checked,false);assert.equal(link.getAttribute('href'),'/clients/A');
 }finally{dom.window.close();}
});
test('refreshed rows open their own target and filtering clears prior selection',()=>{
 const {dom,w,d,calls,click}=setup();
 try{
  d.querySelector('input[type=checkbox]').click();d.querySelector('input[name=search]').dispatchEvent(new w.Event('input',{bubbles:true}));assert.equal(d.querySelector('[data-group-command]').disabled,true);
  d.querySelector('tbody').innerHTML=rows('C');d.querySelector('section').dispatchEvent(new w.Event('resource-table:updated'));click(d.querySelector('.name'));assert.deepEqual(calls,['/clients/C']);
 }finally{dom.window.close();}
});
