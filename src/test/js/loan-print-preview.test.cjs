const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/loan-print-preview.js','utf8');
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function setup(response){
 const dom=new JSDOM('<main><a href="/finance/loan-records/loan/print" data-print-action="false" data-loan-print-preview>Print</a><input value="unsaved"><dialog id="loanPrintPreview"><div data-preview-body><div data-preview-error hidden>Error <button data-preview-retry>Refresh</button></div><iframe data-preview-frame hidden></iframe></div><button data-preview-close>Close</button><button data-preview-print hidden>Print</button></dialog></main>',{url:'http://localhost/finance/loan-records/loan',runScripts:'outside-only'});
 const w=dom.window,d=w.document,dialog=d.querySelector('dialog');let requests=[],revoked=[];
 dialog.showModal=()=>{dialog.open=true;};dialog.close=()=>{dialog.open=false;dialog.dispatchEvent(new w.Event('close'));};
 w.WorkspaceNavigation={onReady:fn=>fn()};w.fetch=async(url,options)=>{requests.push({url,options});return response();};
 w.URL.createObjectURL=()=> 'blob:http://localhost/loan-preview';w.URL.revokeObjectURL=url=>revoked.push(url);
 w.eval(source);return {dom,w,d,dialog,requests,revoked};
}
test('PDF preview stays on the loan page and clears private content and scroll locks on close',async()=>{
 const {dom,w,d,dialog,requests,revoked}=setup(()=>({ok:true,blob:async()=>({type:'application/pdf'})}));
 try{
  d.body.style.overflow='auto';d.querySelector('a').click();await settle();
  const frame=d.querySelector('iframe');frame.dispatchEvent(new w.Event('load'));await settle();
  assert.equal(dialog.parentElement,d.body);assert.equal(dialog.open,true);assert.equal(frame.hidden,false);
  assert.equal(frame.src,'blob:http://localhost/loan-preview#view=FitH');assert.equal(w.location.pathname,'/finance/loan-records/loan');
  assert.equal(requests[0].options.credentials,'same-origin');assert.equal(requests[0].options.redirect,'error');
  d.querySelector('[data-preview-close]').click();assert.equal(frame.hasAttribute('src'),false);assert.deepEqual(revoked,['blob:http://localhost/loan-preview']);
  assert.equal(d.body.style.overflow,'auto');assert.equal(d.querySelector('input').value,'unsaved');assert.equal(d.activeElement,d.querySelector('a'));
 }finally{dom.window.close();}
});
test('failed preview stays local and refresh retries without navigating or replaying a financial command',async()=>{
 let fail=true;const {dom,w,d,requests}=setup(()=>({ok:!fail,blob:async()=>({type:'application/pdf'})}));
 try{
  d.querySelector('a').click();await settle();assert.equal(d.querySelector('[data-preview-error]').hidden,false);
  fail=false;d.querySelector('[data-preview-retry]').click();await settle();d.querySelector('iframe').dispatchEvent(new w.Event('load'));await settle();
  assert.equal(d.querySelector('[data-preview-error]').hidden,true);assert.equal(requests.length,2);assert.ok(requests.every(r=>!r.options.method));
  assert.equal(w.location.pathname,'/finance/loan-records/loan');d.querySelector('dialog').close();
 }finally{dom.window.close();}
});
test('page exit aborts a pending preview and removes its mounted dialog',async()=>{
 const {dom,w,d,requests}=setup(()=>new Promise(()=>{}));
 try{
  d.querySelector('a').click();await settle();w.dispatchEvent(new w.PageTransitionEvent('pagehide',{persisted:false}));
  assert.equal(requests[0].options.signal.aborted,true);assert.equal(d.querySelector('dialog'),null);assert.equal(d.body.style.overflow,'');
 }finally{dom.window.close();}
});
