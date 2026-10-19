const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

test('shared confirmation does not lock the accepted teller submission',()=>{
  const dom=new JSDOM('<form data-loan-form><button type="submit">Disburse</button></form>',{runScripts:'outside-only'});
  const w=dom.window,form=w.document.querySelector('form');let confirmed=false;
  w.document.addEventListener('submit',event=>{if(!confirmed)event.preventDefault();});
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),false);
  confirmed=true;
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),true);
  assert.equal(form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),false);
  dom.window.close();
});

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
    <div data-configured-product><select id="productId" name="productId"><option value="product" data-tenure-unit="MONTHS" data-minimum-amount="1000.00" data-maximum-amount="9007199254740991.01" data-minimum-tenure="3" data-maximum-tenure="24">Business loan</option><option value="daily-product" data-tenure-unit="DAYS" data-minimum-amount="500.00" data-maximum-amount="5000.00" data-minimum-tenure="7" data-maximum-tenure="30">Day loan</option><option value="yearly-product" data-tenure-unit="YEARS">Year loan</option></select></div>
    <input id="principal" name="principal"><small data-product-amount-limits data-unavailable="Unavailable" hidden><span data-limit-min></span> to <span data-limit-max></span></small><label data-tenure-type><select id="tenureUnit" name="tenureUnit"><option value=""></option><option>DAYS</option><option>MONTHS</option><option>YEARS</option></select></label>
    <label data-tenure-number><span data-tenure-label data-empty="Number of" data-days="Number of days" data-months="Number of months" data-years="Number of years"></span><input id="tenureValue" name="tenureValue"><small data-product-tenure-limits hidden><span data-limit-min></span> to <span data-limit-max></span> <span data-limit-unit></span></small></label>
    <div data-custom-terms><select id="customMethod" name="customMethod"><option value="FLAT_RATE">Flat rate</option><option value="REDUCING_BALANCE">Reducing balance</option></select><select id="interestEntry" name="interestEntry"><option value="PERCENTAGE">PERCENTAGE</option><option value="AMOUNT">AMOUNT</option></select>
    <label data-custom-percentage><input id="interestPercent" name="interestPercent"></label>
    <label data-custom-percentage><select id="interestBasis" name="interestBasis"><option value="WHOLE_LOAN">For the whole loan</option><option value="ANNUAL">Annual rate</option></select><small data-reducing-terms-note hidden>Reducing balance uses an annual percentage. For whole-loan interest, select Flat rate.</small></label>
    <label data-custom-amount><input id="interestAmount" name="interestAmount"></label><select id="customFrequency" name="customFrequency"><option>MONTHLY</option></select></div>
    <div data-interest-preview data-error="Check the loan terms" data-days="Days" data-months="Months" data-years="Years"><span class="loan-interest-spinner" hidden></span><output data-interest-value></output><p data-interest-description></p></div>
    <section data-schedule-preview hidden><span data-schedule-summary data-monthly="Monthly" data-weekly="Weekly" data-monthly-period="Month {0}" data-weekly-period="Week {0}"></span><table><tbody data-schedule-rows></tbody></table></section>
    <button name="action" value="save">Save</button><button disabled>Restricted</button>
  </form></html>`, {runScripts:'outside-only',url:'http://localhost/finance/loan-records/new'});
  const w=dom.window, timers=new Map(), requests=[];let next=0;
  w.setTimeout=fn=>{timers.set(++next,fn);return next;};w.clearTimeout=id=>timers.delete(id);
  w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  const set=(id,value)=>{w.document.getElementById(id).value=value;w.document.getElementById(id).dispatchEvent(new w.Event('change',{bubbles:true}));};
  const run=()=>{const fn=[...timers.values()].at(-1);timers.clear();return fn();};
  const respond=(index,interest,ok=true,schedule=[],method='FLAT_RATE',frequency='MONTHLY')=>requests[index].resolve({ok,redirected:false,json:async()=>ok?{interest,method,product:'Business loan',tenureUnit:'MONTHS',tenureValue:6,frequency,schedule}:{error:'Invalid loan terms'}});
  return {w,set,run,respond,requests,dom};
}

test('progressive tenure, custom modes and percentage basis send only active fields',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();const d=w.document;
  assert.equal(d.querySelector('[data-tenure-type]').hidden,true);
  set('loanMode','CUSTOM');set('tenureUnit','DAYS');set('tenureValue','14');assert.equal(d.getElementById('tenureValue').disabled,false);assert.equal(d.getElementById('tenureValue').max,'18250');
  set('loanMode','CUSTOM');set('interestBasis','ANNUAL');set('interestPercent','12');set('principal','1000');
  const calculation=run();assert.equal(d.querySelector('.loan-interest-spinner').hidden,false);assert.equal(d.querySelector('[data-interest-preview]').getAttribute('aria-busy'),'true');
  assert.equal(requests[0].options.body.get('productId'),null);assert.equal(requests[0].options.body.get('_csrf'),'token');assert.equal(requests[0].options.body.get('interestBasis'),'ANNUAL');
  respond(0,'60.00');await calculation;assert.equal(d.querySelector('[data-interest-value]').textContent,'60.00');assert.equal(d.querySelector('.loan-interest-spinner').hidden,true);
  set('interestEntry','AMOUNT');set('interestAmount','17.03');const amount=run();assert.equal(requests[1].options.body.get('interestPercent'),null);assert.equal(requests[1].options.body.get('interestAmount'),'17.03');respond(1,'17.03');await amount;
  assert.equal(d.querySelector('[data-interest-value]').textContent,'17.03');dom.window.close();
});

test('stale quotes cannot overwrite the latest amount and failures end the spinner',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();set('tenureUnit','MONTHS');set('tenureValue','6');set('principal','1000');const first=run();set('principal','2000');const second=run();
  assert.equal(requests[0].options.signal.aborted,true);respond(0,'100.00');await first;assert.equal(w.document.querySelector('.loan-interest-spinner').hidden,false);
  respond(1,'200.00');await second;assert.equal(w.document.querySelector('[data-interest-value]').textContent,'200.00');
  set('principal','3000');const failed=run();respond(2,null,false);await failed;
  assert.equal(w.document.querySelector('[data-interest-description]').textContent,'Invalid loan terms');assert.equal(w.document.querySelector('.loan-interest-spinner').hidden,true);
  dom.window.close();
});

test('decimal interest strings retain exact cents when displayed',async()=>{
  const {w,set,run,respond,dom}=fixture();set('tenureUnit','MONTHS');set('tenureValue','6');set('principal','1000');const pending=run();respond(0,'9007199254740991.01');await pending;
  assert.equal(w.document.querySelector('[data-interest-value]').textContent,'9,007,199,254,740,991.01');dom.window.close();
});

test('schedule uses server rows, preserves cents, and clears stale estimates on changes and errors',async()=>{
  const {w,set,run,respond,dom}=fixture();const d=w.document,preview=d.querySelector('[data-schedule-preview]');
  set('tenureUnit','MONTHS');set('tenureValue','6');set('principal','1000');const pending=run();
  assert.equal(preview.hidden,true);
  respond(0,'17.03',true,[{number:1,principal:'9007199254740991.01',interest:'17.03',amount:'9007199254741008.04',beginningBalance:'9007199254741008.04',endingBalance:'0.00'}]);await pending;
  assert.equal(preview.hidden,false);
  assert.equal(d.querySelector('[data-schedule-summary]').textContent,'Monthly · 6 Months');
  assert.deepEqual([...d.querySelectorAll('[data-schedule-rows] td')].map(cell=>cell.textContent),['1','Month 1','9,007,199,254,741,008.04','9,007,199,254,741,008.04','9,007,199,254,740,991.01','17.03','0.00']);
  set('tenureValue','12');assert.equal(preview.hidden,true);assert.equal(d.querySelector('[data-schedule-rows]').children.length,0);
  const failed=run();respond(1,null,false);await failed;assert.equal(preview.hidden,true);
  dom.window.close();
});

test('incomplete configuration does not request a misleading default schedule',()=>{
  const {set,requests,dom}=fixture();set('principal','1000');set('loanMode','CUSTOM');
  assert.equal(requests.length,0);assert.equal(dom.window.document.querySelector('[data-schedule-preview]').hidden,true);
  dom.window.close();
});

test('weekly estimates use localized repayment periods and principal-plus-interest balances',async()=>{
  const {w,set,run,respond,dom}=fixture();set('productId','daily-product');set('tenureValue','9');set('principal','10000');
  w.document.querySelector('[data-schedule-summary]').dataset.weeklyPeriod='Wiki {0}';
  const pending=run();respond(0,'183.45',true,[
    {number:1,beginningBalance:'10183.45',amount:'5091.73',principal:'4969.68',interest:'122.05',endingBalance:'5091.72'},
    {number:2,beginningBalance:'5091.72',amount:'5091.72',principal:'5030.32',interest:'61.40',endingBalance:'0.00'}
  ],'REDUCING_BALANCE','WEEKLY');await pending;
  const rows=[...w.document.querySelectorAll('[data-schedule-rows] tr')].map(row=>[...row.cells].map(cell=>cell.textContent));
  assert.deepEqual(rows,[['1','Wiki 1','10,183.45','5,091.73','4,969.68','122.05','5,091.72'],['2','Wiki 2','5,091.72','5,091.72','5,030.32','61.40','0.00']]);
  dom.window.close();
});

test('reducing balance accepts either percentage basis and configured loans hide custom terms',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();const d=w.document;
  set('loanMode','CUSTOM');set('tenureUnit','MONTHS');set('tenureValue','6');set('interestPercent','12');set('principal','1000');set('customMethod','REDUCING_BALANCE');
  assert.equal(d.getElementById('interestBasis').value,'WHOLE_LOAN');assert.equal(d.getElementById('interestEntry').value,'PERCENTAGE');
  assert.equal(d.querySelector('#interestEntry option[value="AMOUNT"]').disabled,true);
  assert.equal(d.querySelector('#interestBasis option[value="WHOLE_LOAN"]').disabled,false);
  assert.equal(d.querySelector('[data-reducing-terms-note]').hidden,false);
  const reducing=run();assert.equal(requests[0].options.body.get('customMethod'),'REDUCING_BALANCE');assert.equal(requests[0].options.body.get('interestBasis'),'WHOLE_LOAN');respond(0,'71.16',true,[],'REDUCING_BALANCE');await reducing;
  set('interestBasis','ANNUAL');assert.equal(d.getElementById('interestBasis').value,'ANNUAL');assert.equal(d.querySelector('[data-reducing-terms-note]').hidden,true);
  set('interestBasis','WHOLE_LOAN');assert.equal(d.getElementById('interestBasis').value,'WHOLE_LOAN');
  set('customMethod','FLAT_RATE');assert.equal(d.querySelector('#interestEntry option[value="AMOUNT"]').disabled,false);
  assert.equal(d.querySelector('#interestBasis option[value="WHOLE_LOAN"]').disabled,false);
  assert.equal(d.querySelector('[data-reducing-terms-note]').hidden,true);
  set('interestBasis','WHOLE_LOAN');const whole=run();assert.equal(requests[1].options.body.get('interestBasis'),'WHOLE_LOAN');respond(1,'120.00');await whole;
  set('loanMode','CONFIGURED');const configured=run();assert.equal(requests[2].options.body.get('customMethod'),null);respond(2,'50.00');await configured;
  assert.equal(d.querySelector('[data-custom-terms]').hidden,true);
  assert.equal(d.querySelector('[data-interest-value]').textContent,'50.00');dom.window.close();
});

test('tenure labels follow the selected unit in both loan modes and use localized text',()=>{
  const {w,set,dom}=fixture();const d=w.document,label=d.querySelector('[data-tenure-label]');
  for(const mode of ['CONFIGURED','CUSTOM']) {
    set('loanMode',mode);
    for(const [unit,text] of [['DAYS','Number of days'],['MONTHS','Number of months'],['YEARS','Number of years']]) {
      if(mode==='CONFIGURED')set('productId',unit==='DAYS'?'daily-product':unit==='YEARS'?'yearly-product':'product');
      else set('tenureUnit',unit);
      assert.equal(label.textContent,text);
      assert.equal(d.querySelector('[data-tenure-number]').hidden,false);
    }
  }
  label.dataset.days='Idadi ya siku';set('tenureUnit','DAYS');assert.equal(label.textContent,'Idadi ya siku');
  set('tenureUnit','');assert.equal(d.querySelector('[data-tenure-number]').hidden,true);
  dom.window.close();
});


test('configured products supply hidden tenure units while customised loans keep the selector',async()=>{
  const {w,set,run,respond,requests,dom}=fixture();const d=w.document,unit=d.getElementById('tenureUnit'),box=d.querySelector('[data-tenure-type]');
  assert.equal(box.hidden,true);assert.equal(unit.disabled,false);assert.equal(unit.required,false);
  set('tenureValue','6');set('principal','1000');const monthly=run();
  assert.equal(requests[0].options.body.get('tenureUnit'),'MONTHS');respond(0,'60.00');await monthly;
  set('productId','daily-product');assert.equal(unit.value,'DAYS');assert.equal(box.hidden,true);
  assert.equal(d.getElementById('tenureValue').value,'');assert.equal(d.querySelector('[data-tenure-label]').textContent,'Number of days');
  set('tenureValue','14');const daily=run();assert.equal(requests[1].options.body.get('tenureUnit'),'DAYS');respond(1,'12.00');await daily;
  set('loanMode','CUSTOM');assert.equal(box.hidden,false);assert.equal(unit.required,true);
  set('tenureUnit','YEARS');assert.equal(d.querySelector('[data-tenure-label]').textContent,'Number of years');
  set('loanMode','CONFIGURED');assert.equal(box.hidden,true);assert.equal(unit.value,'DAYS');
  set('productId','');assert.equal(unit.value,'');assert.equal(d.querySelector('[data-tenure-number]').hidden,true);
  dom.window.close();
});


test('configured amount and tenure limits follow product selection and hide for customised or empty selections',()=>{
  const {w,set,dom}=fixture(),d=w.document;
  const amount=d.querySelector('[data-product-amount-limits]'),tenure=d.querySelector('[data-product-tenure-limits]');
  assert.equal(amount.hidden,false);assert.equal(amount.textContent,'1,000.00 to 9,007,199,254,740,991.01');
  assert.equal(tenure.textContent,'3 to 24 Months');
  set('productId','daily-product');
  assert.equal(amount.textContent,'500.00 to 5,000.00');assert.equal(tenure.textContent,'7 to 30 Days');
  d.querySelector('[data-interest-preview]').dataset.days='Siku';set('tenureValue','10');
  assert.equal(tenure.textContent,'7 to 30 Siku');
  set('loanMode','CUSTOM');assert.equal(amount.hidden,true);assert.equal(tenure.hidden,true);
  set('loanMode','CONFIGURED');assert.equal(amount.hidden,false);assert.equal(tenure.hidden,false);
  set('productId','');assert.equal(amount.hidden,true);assert.equal(tenure.hidden,true);
  dom.window.close();
});

test('product lookup preserves exact decimal limits and updates a retained selection with the same tenure unit',async()=>{
  const dom=new JSDOM(`<div data-loan-choice data-category="products"><select data-choice-select><option value="product" data-tenure-unit="MONTHS" selected>Selected product</option></select><span data-choice-error data-search-label="Search products" data-error="Lookup failed"></span></div>`,{runScripts:'outside-only',url:'http://localhost/finance/loan-records/new'});
  const w=dom.window,input=w.document.createElement('input'),more=w.document.createElement('button'),select=w.document.querySelector('select');let changes=0;
  select.addEventListener('change',()=>changes++);
  w.NeoSelect={search:()=>({input,more,setLoading:()=>{}})};w.AppSearch={bind:()=>({cancel:()=>{}})};
  w.fetch=async()=>({ok:true,redirected:false,json:async()=>[{id:'product',label:'Business loan',tenureUnit:'MONTHS',minimumAmount:'1000.00',maximumAmount:'9007199254740991.01',minimumTenure:3,maximumTenure:24}]});
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording.js','utf8'));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(select.value,'product');assert.equal(select.selectedOptions[0].dataset.maximumAmount,'9007199254740991.01');
  assert.equal(select.selectedOptions[0].dataset.minimumTenure,'3');assert.equal(changes,1);
  dom.window.close();
});
