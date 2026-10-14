const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

test('OTP validation can cancel a submission without locking the later verified submission',()=>{
  const dom=new JSDOM('<form data-loan-form><button type="submit">Disburse</button></form>',{runScripts:'outside-only'});
  const w=dom.window,form=w.document.querySelector('form');let verified=false;
  form.addEventListener('submit',event=>{if(!verified)event.preventDefault();});
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),false);
  verified=true;
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),true);
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),false);
  dom.window.close();
});

test('staff posting lookups use the scoped review endpoint and preserve the chosen account',async()=>{
  const dom=new JSDOM(`<form data-loan-form><div data-loan-choice data-category="money" data-choice-url="/disbursement/loan-applications/scoped-loan/posting-accounts/money"><select data-choice-select><option value="">Choose</option><option value="account" selected>Cash account</option></select><span data-choice-error data-search-label="Search accounts" data-error="Lookup failed"></span></div></form>`,{runScripts:'outside-only',url:'http://localhost/disbursement/loan-applications/scoped-loan'});
  const w=dom.window,input=w.document.createElement('input'),more=w.document.createElement('button');let requested;
  w.NeoSelect={search:()=>({input,more,setLoading:()=>{}})};w.AppSearch={bind:()=>({cancel:()=>{}})};
  w.fetch=async url=>{requested=url;return {ok:true,redirected:false,json:async()=>[{id:'account',label:'101 - Teller cash'}]};};
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(requested,'/disbursement/loan-applications/scoped-loan/posting-accounts/money?search=&page=0');
  assert.equal(w.document.querySelector('select').value,'account');assert.equal(w.document.querySelector('select').selectedOptions[0].textContent,'101 - Teller cash');
  dom.window.close();
});

function fixture() {
  const dom = new JSDOM(`<html lang="en"><form data-loan-form>
    <input name="_csrf" value="token"><select id="loanMode" name="loanMode"><option>CONFIGURED</option><option>CUSTOM</option></select>
    <div data-configured-product><select id="productId" name="productId"><option value="product">Business loan</option></select></div>
    <input id="principal" name="principal"><select id="tenureUnit" name="tenureUnit"><option value=""></option><option>DAYS</option><option>MONTHS</option><option>YEARS</option></select>
    <label data-tenure-number><input id="tenureValue" name="tenureValue"></label>
    <div data-custom-terms><select id="interestEntry" name="interestEntry"><option>PERCENTAGE</option><option>AMOUNT</option></select>
    <label data-custom-percentage><input id="interestPercent" name="interestPercent"></label>
    <label data-custom-percentage><select id="interestBasis" name="interestBasis"><option value="WHOLE_LOAN">For the whole loan</option><option value="ANNUAL">Annual rate</option></select></label>
    <label data-custom-amount><input id="interestAmount" name="interestAmount"></label><select id="customFrequency" name="customFrequency"><option>MONTHLY</option></select></div>
    <div data-interest-preview data-error="Check the loan terms" data-days="Days" data-months="Months" data-years="Years"><span class="loan-interest-spinner" hidden></span><output data-interest-value></output><p data-interest-description></p></div>
    <button name="action" value="save">Save</button><button disabled>Restricted</button>
  </form></html>`, {runScripts:'outside-only',url:'http://localhost/finance/loan-records/new'});
  const w=dom.window, timers=new Map(), requests=[];let next=0;
  w.setTimeout=fn=>{timers.set(++next,fn);return next;};w.clearTimeout=id=>timers.delete(id);
  w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  const set=(id,value)=>{w.document.getElementById(id).value=value;w.document.getElementById(id).dispatchEvent(new w.Event('change',{bubbles:true}));};
  const run=()=>{const fn=[...timers.values()].at(-1);timers.clear();return fn();};
  const respond=(index,interest,ok=true)=>requests[index].resolve({ok,redirected:false,json:async()=>ok?{interest,product:'Business loan',tenureUnit:'MONTHS',tenureValue:6}:{error:'Invalid loan terms'}});
  return {w,set,run,respond,requests,dom};
}

test('progressive tenure, custom modes and percentage basis send only active fields',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();const d=w.document;
  assert.equal(d.querySelector('[data-tenure-number]').hidden,true);
  set('tenureUnit','DAYS');assert.equal(d.getElementById('tenureValue').disabled,false);assert.equal(d.getElementById('tenureValue').max,'18250');
  set('loanMode','CUSTOM');set('interestBasis','ANNUAL');set('interestPercent','12');set('principal','1000');
  const calculation=run();assert.equal(d.querySelector('.loan-interest-spinner').hidden,false);assert.equal(d.querySelector('[data-interest-preview]').getAttribute('aria-busy'),'true');
  assert.equal(requests[0].options.body.get('productId'),null);assert.equal(requests[0].options.body.get('_csrf'),'token');assert.equal(requests[0].options.body.get('interestBasis'),'ANNUAL');
  respond(0,'60.00');await calculation;assert.equal(d.querySelector('[data-interest-value]').textContent,'60.00');assert.equal(d.querySelector('.loan-interest-spinner').hidden,true);
  set('interestEntry','AMOUNT');set('interestAmount','17.03');const amount=run();assert.equal(requests[1].options.body.get('interestPercent'),null);assert.equal(requests[1].options.body.get('interestAmount'),'17.03');respond(1,'17.03');await amount;
  assert.equal(d.querySelector('[data-interest-value]').textContent,'17.03');dom.window.close();
});

test('stale quotes cannot overwrite the latest amount and failures end the spinner',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();set('principal','1000');const first=run();set('principal','2000');const second=run();
  assert.equal(requests[0].options.signal.aborted,true);respond(0,'100.00');await first;assert.equal(w.document.querySelector('.loan-interest-spinner').hidden,false);
  respond(1,'200.00');await second;assert.equal(w.document.querySelector('[data-interest-value]').textContent,'200.00');
  set('principal','3000');const failed=run();respond(2,null,false);await failed;
  assert.equal(w.document.querySelector('[data-interest-description]').textContent,'Invalid loan terms');assert.equal(w.document.querySelector('.loan-interest-spinner').hidden,true);
  dom.window.close();
});

test('decimal interest strings retain exact cents when displayed',async()=>{
  const {w,set,run,respond,dom}=fixture();set('principal','1000');const pending=run();respond(0,'9007199254740991.01');await pending;
  assert.equal(w.document.querySelector('[data-interest-value]').textContent,'9,007,199,254,740,991.01');dom.window.close();
});
