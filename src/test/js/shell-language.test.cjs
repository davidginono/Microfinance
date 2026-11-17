const {test}=require('node:test');
const assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const {readFileSync}=require('node:fs');
test('language selection preserves current query and fragment, closes outside, and restores focus on Escape',()=>{
 const dom=new JSDOM('<details open><summary>EN</summary><form data-shell-language><input name="returnTo"><button>English</button></form></details><button id="outside">Other</button>',{url:'http://localhost/admin/settings-controls?section=loan#products',runScripts:'outside-only'});
 try{
  const w=dom.window,d=w.document;w.WorkspaceNavigation={onReady:callback=>callback()};w.eval(readFileSync('src/main/resources/static/js/shell-language.js','utf8'));
  const control=d.querySelector('details'),form=d.querySelector('form');form.addEventListener('submit',e=>e.preventDefault());form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
  assert.equal(form.elements.returnTo.value,'/admin/settings-controls?section=loan#products');
  d.querySelector('#outside').click();assert.equal(control.open,false);control.open=true;control.querySelector('button').focus();control.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));assert.equal(control.open,false);assert.equal(d.activeElement,control.querySelector('summary'));
 }finally{dom.window.close();}
});
