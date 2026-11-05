const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
async function setup({readonly=false,client='current-client'}={}) {
 const dom=new JSDOM(`<body><main><form data-contact-check-url="/finance/clients/onboarding/contact-check" data-contact-client-id="${client}"><input name="_csrf" value="token"><div data-contact-fields data-contact-phone-error="Phone format" data-contact-email-error="Email format" data-contact-exists="Already registered" data-contact-available="Available" data-contact-checking="Checking" data-contact-unavailable="Could not check" data-contact-retry="Retry"><label data-contact-field="phone">Phone<input id="phone" name="profile.phone" required ${readonly?'readonly':''} aria-describedby="phone-hint"><small id="phone-hint">Example: 255712345678</small><span data-contact-result role="status" hidden></span></label><label data-contact-field="email">Email<input id="email" name="profile.email" type="email" aria-describedby="email-hint"><small id="email-hint">Example: name@example.com</small><span data-contact-result role="status" hidden></span></label></div><button type="submit">Save</button></form><button id="elsewhere">Elsewhere</button></main></body>`,{url:'http://localhost/clients/new',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,requests=[],timers=new Map(),ready=[];let serial=0;
 w.setTimeout=(fn,delay)=>{const id=++serial;timers.set(id,{fn,delay});return id;};w.clearTimeout=id=>timers.delete(id);
 w.WorkspaceNavigation={onReady:fn=>ready.push(fn)};
 w.fetch=(url,options)=>new Promise((resolve,reject)=>requests.push({url,options,resolve,reject}));
 for(const path of ['form-validation.js','console-resource-details.js','client-onboarding.js'])w.eval(readFileSync('src/main/resources/static/js/'+path,'utf8'));
 ready.forEach(fn=>fn());await tick();
 const phone=d.querySelector('#phone'),email=d.querySelector('#email'),form=d.querySelector('form');
 const input=(control,value)=>{control.value=value;control.dispatchEvent(new w.Event('input',{bubbles:true}));};
 const advance=async(delay=450)=>{for(const [id,timer] of [...timers])if(timer.delay===delay){timers.delete(id);timer.fn();}await tick();};
 const respond=async(index,data,ok=true,type='application/json')=>{requests[index].resolve({ok,headers:{get:()=>type},json:async()=>data});await tick();await tick();};
 return {dom,w,d,phone,email,form,requests,ready,input,advance,respond};
}
test('typing pauses before one private POST with CSRF and the scoped exclusion',async()=>{
 const f=await setup();try {
  assert.equal(f.requests.length,0);f.ready.forEach(fn=>fn());
  const csrf=f.form.querySelector('[name=_csrf]');f.form.append(csrf.cloneNode(true));
  for(const value of ['0','0712','0712345678'])f.input(f.phone,value);
  assert.equal(f.requests.length,0);await f.advance();assert.equal(f.requests.length,1);
  const request=f.requests[0];assert.equal(request.options.method,'POST');assert.equal(request.options.body.get('_csrf'),'token');assert.equal(request.options.body.get('clientId'),'current-client');assert.equal(request.options.body.get('value'),'0712345678');assert.equal(request.url.includes('0712345678'),false);
  assert.ok(f.d.querySelector('.app-loading-spinner'));await f.respond(0,{valid:true,exists:false});
  assert.equal(f.d.querySelector('[data-contact-result]').textContent,'Available');assert.equal(f.d.querySelector('[data-contact-result]').dataset.statusType,'success');assert.ok(f.d.querySelector('[data-contact-result] svg'));assert.equal(f.phone.value,'0712345678');
 }finally{f.dom.window.close();}
});
test('format and duplicate errors use shared validation without moving focus or losing hints',async()=>{
 const f=await setup();try {
  f.d.querySelector('#elsewhere').focus();f.input(f.phone,'123');await f.advance();assert.equal(f.requests.length,0);assert.equal(f.phone.validationMessage,'Phone format');assert.equal(f.d.activeElement.id,'elsewhere');assert.match(f.phone.getAttribute('aria-describedby'),/phone-hint/);
  f.input(f.phone,'255712345678');assert.equal(f.phone.validationMessage,'');await f.advance();await f.respond(0,{valid:true,exists:true});
  assert.equal(f.phone.getAttribute('aria-invalid'),'true');assert.equal(f.phone.validationMessage,'Already registered');assert.equal(f.d.querySelectorAll('.erp-field-error').length,1);assert.equal(f.d.activeElement.id,'elsewhere');
  f.input(f.phone,'255712345679');assert.equal(f.phone.getAttribute('aria-invalid'),null);await f.advance();await f.respond(1,{valid:true,exists:false});assert.equal(f.phone.validity.valid,true);
 }finally{f.dom.window.close();}
});
test('stale requests cannot overwrite the latest value and IME input waits for completion',async()=>{
 const f=await setup();try {
  f.input(f.email,'first@example.com');await f.advance();f.input(f.email,'second@example.com');assert.equal(f.requests[0].options.signal.aborted,true);await f.advance();
  await f.respond(1,{valid:true,exists:false});await f.respond(0,{valid:true,exists:true});assert.equal(f.email.validity.valid,true);assert.equal(f.email.closest('label').querySelector('[data-contact-result]').textContent,'Available');
  f.email.dispatchEvent(new f.w.Event('compositionstart'));f.input(f.email,'third@example.com');await f.advance();assert.equal(f.requests.length,2);f.email.dispatchEvent(new f.w.Event('compositionend'));await f.advance();assert.equal(f.requests.length,3);
 }finally{f.dom.window.close();}
});
test('failed checks show Retry rather than availability and preserve the native save controls',async()=>{
 const f=await setup();try {
  f.input(f.phone,'0712345678');await f.advance();await f.respond(0,null,false);const retry=f.d.querySelector('[data-contact-result] button');assert.equal(retry.type,'button');assert.equal(retry.textContent,'Retry');assert.equal(f.d.querySelector('[data-contact-result]').dataset.statusType,'warning');assert.equal(f.form.querySelector('[type=submit]').disabled,false);
  retry.click();assert.equal(f.requests.length,2);await f.respond(1,{valid:true,exists:false});assert.equal(f.d.querySelector('[data-contact-result] button'),null);
  f.input(f.email,'name@example.com');await f.advance();f.form.remove();f.d.dispatchEvent(new f.w.Event('workspace:navigated'));assert.equal(f.requests[2].options.signal.aborted,true);
 }finally{f.dom.window.close();}
});
test('empty optional fields, readonly contact values and resets do not issue checks',async()=>{
 const f=await setup({readonly:true,client:''});try {
  f.input(f.phone,'0712345678');f.input(f.email,'');await f.advance();assert.equal(f.requests.length,0);
  f.input(f.email,'bad@');await f.advance();assert.equal(f.email.validationMessage,'Email format');assert.equal(f.requests.length,0);
  f.input(f.email,'name@example.com');await f.advance();assert.equal(f.requests[0].options.body.has('clientId'),false);f.form.reset();await tick();assert.equal(f.requests[0].options.signal.aborted,true);assert.equal(f.email.validationMessage,'');
 }finally{f.dom.window.close();}
});
test('a timed-out check offers retry without reporting that the value is available',async()=>{
 const f=await setup();try {
  f.input(f.email,'name@example.com');await f.advance();await f.advance(8000);assert.equal(f.requests[0].options.signal.aborted,true);
  f.requests[0].reject(Object.assign(new Error('Aborted'),{name:'AbortError'}));await tick();await tick();
  const result=f.email.closest('label').querySelector('[data-contact-result]');assert.equal(result.dataset.statusType,'warning');assert.match(result.textContent,/Could not check/);assert.ok(result.querySelector('button'));assert.equal(result.querySelector('.app-loading-spinner'),null);
 }finally{f.dom.window.close();}
});
