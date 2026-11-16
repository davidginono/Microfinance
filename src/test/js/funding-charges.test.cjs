const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const source=fs.readFileSync('src/main/resources/static/js/funding-charges.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const quote={cash:'1487500.00',deductions:'12500.00',separate:'25000.00'};
function setup(){
 const dom=new JSDOM(`<body><form data-funding-quote-url="/disbursement/loan-applications/loan/funding-quote" data-funding-error="Try again">
 <input name="_csrf" value="fixture"><input type="date" name="disbursementDate" value="2026-11-16"><input type="number" name="depositAmount" value="1500000.00" data-amount-input="true" readonly>
 <button type="button" data-funding-picker-toggle aria-expanded="false">Deduct</button><fieldset class="funding-charge-picker" hidden><label><input name="deductChargeIds" type="checkbox" value="application">Application</label><label><input name="deductChargeIds" type="checkbox" value="insurance">Insurance</label></fieldset>
 <dd data-funding-deductions></dd><dd data-funding-separate></dd><dd data-funding-cash></dd><input type="checkbox" data-funding-confirm checked required><p data-funding-error hidden></p><button data-funding-retry hidden type="button">Try again</button><button data-funding-submit>Pay</button></form></body>`,{url:'http://localhost',runScripts:'outside-only'});
 const w=dom.window,requests=[];w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));w.eval(source);
 return {dom,w,d:w.document,requests,async resolve(index,body=quote,ok=true){requests[index].resolve({ok,redirected:false,json:async()=>body});await tick();}};
}
test('deduction selection closes the picker and updates exact net cash without changing principal or transport',async()=>{
 const f=setup(),{w,d}=f;await f.resolve(0,{cash:'1500000.00',deductions:'0.00',separate:'37500.00'});
 d.querySelector('[data-funding-picker-toggle]').click();const choice=d.querySelector('[name="deductChargeIds"]');choice.checked=true;choice.dispatchEvent(new w.Event('change',{bubbles:true}));
 assert.equal(d.querySelector('.funding-charge-picker').hidden,true);assert.equal(d.activeElement,d.querySelector('[data-funding-picker-toggle]'));
 assert.equal(d.querySelector('[data-funding-submit]').disabled,true);assert.equal(d.querySelector('[name="depositAmount"]').value,'');
 assert.deepEqual(f.requests[1].url.searchParams.getAll('deductChargeIds'),['application']);await f.resolve(1);
 assert.equal(d.querySelector('[name="depositAmount"]').value,'1487500.00');assert.equal(d.querySelector('[data-funding-cash]').textContent,'1,487,500.00');
 assert.equal(d.querySelector('[data-funding-submit]').disabled,false);assert.equal(d.querySelector('[data-funding-confirm]').checked,false);
 assert.deepEqual(new w.FormData(d.querySelector('form')).getAll('deductChargeIds'),['application']);assert.equal(new w.FormData(d.querySelector('form')).get('_csrf'),'fixture');f.dom.window.close();
});
test('stale responses cannot restore an earlier cash quote and failures retain choices with local retry',async()=>{
 const f=setup(),{w,d}=f;const date=d.querySelector('[name="disbursementDate"]');date.value='2026-11-17';date.dispatchEvent(new w.Event('change'));
 await f.resolve(1,{error:'Check the payment date'},false);await f.resolve(0);
 assert.equal(d.querySelector('[name="depositAmount"]').value,'');assert.equal(d.querySelector('[data-funding-submit]').disabled,true);
 assert.equal(d.querySelector('p[data-funding-error]').textContent,'Check the payment date');assert.equal(d.querySelector('[data-funding-retry]').hidden,false);
 d.querySelector('[data-funding-retry]').click();await f.resolve(2,{...quote,cash:'9999999999999999.99'});
 assert.equal(d.querySelector('[data-funding-cash]').textContent,'9,999,999,999,999,999.99');f.dom.window.close();
});
test('Escape closes the picker and restores its trigger without submitting the financial form',async()=>{
 const f=setup(),{w,d}=f;await f.resolve(0);d.querySelector('[data-funding-picker-toggle]').click();
 const event=new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});d.querySelector('.funding-charge-picker input').dispatchEvent(event);
 assert.equal(event.defaultPrevented,true);assert.equal(d.querySelector('.funding-charge-picker').hidden,true);assert.equal(d.activeElement,d.querySelector('[data-funding-picker-toggle]'));f.dom.window.close();
});
