const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');

function fixture(){
  const dom=new JSDOM(`<html lang="en"><form><input name="disbursementDate" value="2026-10-08"><input name="depositAmount"><input name="settlementSequence"><input name="settlementPrincipal"><input name="settlementInterest">
    <section data-topup-summary data-topup-posting data-quote-url="/disbursement/loan-applications/loan/top-up-quote" data-error="Unavailable" data-confirmation="Confirm" data-source-number="SOURCE">
    <span data-topup-date></span><span data-topup-principal></span><span data-topup-interest></span><span data-topup-excluded></span><span data-topup-total></span><span data-topup-cash></span><span data-topup-error></span><input name="settlementConfirmed" type="checkbox" checked></section></form>`,{url:'http://localhost/finance/loan-records/new',runScripts:'outside-only'});
  const w=dom.window,timers=new Map(),requests=[];let timer=0;
  w.setTimeout=callback=>{timers.set(++timer,callback);return timer;};w.clearTimeout=id=>timers.delete(id);
  w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-topup.js','utf8'));
  const fire=()=>{const callbacks=[...timers.values()];timers.clear();callbacks.forEach(callback=>callback());};
  const resolve=async(index,quote,ok=true)=>{requests[index].resolve({ok,redirected:false,json:async()=>quote});await new Promise(done=>setImmediate(done));};
  return {dom,w,form:w.document.querySelector('form'),requests,fire,resolve};
}
const quote={principal:'1000.00',interest:'20.00',excludedInterest:'20.00',total:'1020.00',cash:'480.00',sequence:1};

test('settlement preview preserves decimal cents beyond JavaScript number precision',async()=>{
  const f=fixture();f.fire();await f.resolve(0,{...quote,principal:'9999999999999999.99',cash:'9999999999999999.99'});
  assert.equal(f.form.elements.settlementPrincipal.value,'9999999999999999.99');
  assert.equal(f.form.elements.depositAmount.value,'9999999999999999.99');
  assert.equal(f.w.document.querySelector('[data-topup-cash]').textContent,'9,999,999,999,999,999.99');
  assert.equal(f.form.elements.settlementConfirmed.checked,false);f.dom.window.close();
});
test('changing the settlement date invalidates the accepted preview and ignores an older response',async()=>{
  const f=fixture();f.fire();f.form.elements.disbursementDate.value='2026-10-09';f.form.elements.disbursementDate.dispatchEvent(new f.w.Event('change'));f.fire();
  await f.resolve(1,{...quote,principal:'900.00',cash:'600.00',sequence:2});await f.resolve(0,quote);
  assert.equal(f.form.elements.settlementPrincipal.value,'900.00');assert.equal(f.form.elements.settlementSequence.value,'2');
  assert.equal(f.form.elements.depositAmount.value,'600.00');assert.equal(f.form.elements.settlementConfirmed.checked,false);f.dom.window.close();
});
test('failed settlement refresh cannot retain a previously usable posting sequence',async()=>{
  const f=fixture();f.fire();await f.resolve(0,quote);f.form.elements.settlementConfirmed.checked=true;
  f.form.elements.disbursementDate.value='2026-10-10';f.form.elements.disbursementDate.dispatchEvent(new f.w.Event('change'));f.fire();await f.resolve(1,{error:'Settlement unavailable'},false);
  assert.equal(f.form.elements.settlementSequence.value,'');assert.equal(f.form.elements.settlementConfirmed.checked,false);
  assert.equal(f.w.document.querySelector('[data-topup-error]').textContent,'Settlement unavailable');f.dom.window.close();
});
