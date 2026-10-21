const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/receipt-batches.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const settle=async()=>{for(let n=0;n<6;n++)await tick();};
async function setup(estimate,patch={}){
  const messages=Object.fromEntries(readFileSync('src/main/resources/messages_en.properties','utf8').split(/\r?\n/).filter(line=>line.startsWith('batch.field.')).map(line=>{const split=line.indexOf('=');return [line.slice(6,split),line.slice(split+1)];}));Object.assign(messages,{saveFailed:'Could not save draft.',checking:'Checking receipt...',client:'Client',status:'Status','state.DRAFT':'Draft',receipts:'Receipts',total:'Total',posted:'Posted',ready:'Ready',failed:'Needs attention',remaining:'Remaining'});
  const dom=new JSDOM(`<body><h1>Repayment 1</h1><main data-receipt-batch="batch" data-can-edit="true" data-today="2026-10-19">
    <section class="erp-resource-section"><h2>Batch details</h2><dl class="erp-detail-facts" data-batch-summary></dl></section><div data-batch-error hidden></div>
    <section data-resource-table><button data-batch-add></button><button data-batch-save></button><button data-batch-validate></button><button data-batch-post></button><button data-batch-stop></button><span data-save-status></span>
    <input data-default-date value="2026-10-19"><select data-default-account></select><select data-batch-filter></select>
    <div class="batch-grid-scroll"><table><tbody data-batch-rows></tbody></table></div></section><span data-batch-progress></span>
    <div data-batch-confirm hidden><div role="dialog"><h2 id="batch-confirm-title"></h2><p data-confirm-message></p><div class="app-modal-actions"><button data-confirm-cancel></button><button data-confirm-post></button></div></div></div>
    <div data-batch-messages>${Object.entries(messages).map(([key,value])=>`<span data-message="${key}">${value}</span>`).join('')}</div>
  </main></body>`,{url:'http://localhost/finance/batches/receipts/batch',runScripts:'outside-only'});
  const w=dom.window,d=w.document;let autosave;
  w.structuredClone=structuredClone;w.HTMLElement.prototype.scrollIntoView=function(){};
  const nativeTimeout=w.setTimeout.bind(w),nativeClear=w.clearTimeout.bind(w);
  w.setTimeout=(fn,ms)=>ms===800?(autosave=fn,-1):nativeTimeout(fn,ms);w.clearTimeout=id=>id===-1?(autosave=undefined):nativeClear(id);w.setInterval=()=>0;
  w.ResourceTable={setPage(){}};w.AppSearch={bind(){return {cancel(){}};}};
  w.NeoSelect={search(){return {input:d.createElement('input'),more:d.createElement('button'),setLoading(){}};}};
  const item={id:'receipt',lineNo:1,version:0,state:'FAILED',clientLabel:'Client One',labels:{loan:'1'},failureKey:'voucher.error.date',failureMessage:'Choose a valid payment date.',form:{clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''}]}};
  Object.assign(item,patch);
  const response=(data,ok=true)=>({ok,redirected:false,headers:{get:()=> 'application/json'},json:async()=>data});
  w.fetch=async(url,options)=>{
    if(url.endsWith('/summary'))return response({batch:{state:'DRAFT',version:0},receipts:1,posted:0,ready:0,failed:1,total:'500.00'});
    if(url.includes('/items?'))return response({rows:[structuredClone(item)],hasNext:false});
    if(options?.method==='PUT')return response({...item,version:1});
    if(url.endsWith('/estimate'))return estimate(response);
    if(url.includes('/loan-context?'))return response([{outstandingPrincipal:'1000.00',duePrincipal:'0.00',dueInterest:'0.00'}]);
    if(url.includes('/choices/'))return response([]);
    throw Error('Unexpected request '+url);
  };
  w.eval(readFileSync('src/main/resources/static/js/date-controls.js','utf8'));w.eval(source);if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));await settle();
  return {dom,w,d,async edit(value){const input=d.querySelector('input[type="number"]');input.value=value;input.dispatchEvent(new w.Event('input'));},async save(){const callback=autosave;autosave=undefined;callback();await settle();}};
}
const visible=d=>[...d.querySelectorAll('.batch-field-error:not([hidden])')];
test('retained errors are short, field-specific and accessible under the date control',async()=>{
  const {dom,d}=await setup(()=>{});try{
    assert.equal(d.querySelector('[data-batch-state]'),null);
    assert.equal(d.querySelector('[data-batch-summary]').closest('.erp-resource-section').querySelector('h2').textContent,'Batch details');
    assert.equal(d.querySelector('[data-batch-summary] dd').textContent,'Draft');
    assert.equal(d.querySelector('.batch-validation'),null);
    const [issue]=visible(d);assert.equal(issue.textContent,'Before disbursement or last payment.');
    assert.equal(issue.closest('td').dataset.label,'date');
    const date=d.querySelector('[data-date-display]');assert.equal(date.getAttribute('aria-describedby'),issue.id);assert.equal(date.getAttribute('aria-invalid'),'true');
    assert.equal(d.querySelector('input[type="number"]').hasAttribute('aria-invalid'),false);
    assert.equal(d.querySelector('.batch-row td:last-child').textContent,'state.FAILED');
  }finally{dom.window.close();}
});
test('live checks and overpayments appear below the amount without moving entered values',async()=>{
  let finish;const {dom,d,edit,save}=await setup(response=>new Promise(resolve=>finish=data=>resolve(response(data,false))));try{
    await edit('600');assert.equal(visible(d).length,0);await save();
    let [issue]=visible(d);assert.equal(issue.textContent,'Checking...');assert.equal(issue.closest('td').dataset.label,'amount');
    assert.equal(d.querySelector('input[type="number"]').hasAttribute('aria-invalid'),false);
    finish({code:'repayment.error.advance',message:'The repayment exceeds the remaining debt.'});await settle();
    [issue]=visible(d);assert.equal(issue.textContent,'Amount exceeds unpaid loan debt.');assert.equal(issue.dataset.state,'error');
    assert.equal(d.querySelector('input[type="number"]').value,'600');assert.equal(d.querySelector('input[type="number"]').getAttribute('aria-invalid'),'true');
    assert.equal(d.querySelector('.batch-row td:last-child').textContent,'Draft');
  }finally{dom.window.close();}
});
test('editing while an older estimate is pending prevents its stale error returning',async()=>{
  let finish;const {dom,d,edit,save}=await setup(response=>new Promise(resolve=>finish=()=>resolve(response({code:'repayment.error.advance',message:'Old validation error'},false))));try{
    await edit('600');await save();await edit('200');finish();await settle();
    assert.equal(visible(d).some(issue=>issue.textContent==='Amount exceeds unpaid loan debt.'),false);assert.equal(d.querySelector('input[type="number"]').value,'200');
  }finally{dom.window.close();}
});
test('loan account errors sit below Loan ID rather than the status or amount',async()=>{
  const {dom,d}=await setup(()=>{},{failureKey:'recording.error.loanAccountsMissing'});try{
    const [issue]=visible(d);assert.equal(issue.textContent,'This existing loan needs its configured product accounts linked.');assert.equal(issue.closest('td').dataset.label,'loan');
    assert.equal(d.querySelector('.batch-row td:last-child .batch-field-error'),null);
  }finally{dom.window.close();}
});

test('configured account linking retries the saved receipt estimate without changing entered amounts or posting money',async()=>{
  const {dom,w,d}=await setup(response=>Promise.resolve(response({policyVersion:1,allocationOrder:'INTEREST_FIRST',moneyAccount:'cash',lines:[]})),{failureKey:'recording.error.loanAccountsMissing'});
  try{
    const original=w.fetch,calls=[];w.fetch=async(url,options)=>{calls.push({url,options});if(url.endsWith('/link-accounts'))return {ok:true,headers:{get:()=> 'application/json'},json:async()=>({version:0})};return original(url,options);};
    const action=[...d.querySelectorAll('.batch-row button')].find(button=>button.textContent==='linkAccounts');assert.ok(action);assert.equal(action.hidden,false);action.click();await settle();
    const link=calls.find(call=>call.url.endsWith('/link-accounts'));assert.equal(link.options.method,'POST');assert.equal(JSON.parse(link.options.body).expectedVersion,0);
    assert.ok(calls.some(call=>call.url.endsWith('/estimate')));assert.equal(calls.some(call=>call.url.endsWith('/post')),false);assert.equal(visible(d).length,0);assert.equal(d.querySelector('input[type="number"]').value,'500.00');
  }finally{dom.window.close();}
});
test('required fields have independent messages and reveal a missing receiving account',async()=>{
  const form={clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:null,reference:'',rows:[{loanId:'loan',amount:null,description:''}]};
  const {dom,d}=await setup(()=>{},{failureKey:'voucher.error.text',form});try{
    assert.deepEqual(visible(d).map(issue=>issue.textContent).sort(),['Select receiving account.','Enter payment amount.','Enter collection reference.'].sort());
    assert.equal(d.querySelector('.batch-detail-row').hidden,false);assert.equal(d.querySelector('.batch-expand').getAttribute('aria-expanded'),'true');
    assert.equal(new Set([...d.querySelectorAll('.batch-field-error')].map(issue=>issue.id)).size,d.querySelectorAll('.batch-field-error').length);
  }finally{dom.window.close();}
});
test('multi-loan errors identify only the incomplete transaction and open its details',async()=>{
  const form={clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''},{loanId:'loan2',amount:null,description:''}]};
  const {dom,d}=await setup(()=>{},{failureKey:'voucher.error.amount',form});try{
    const [issue]=visible(d);assert.equal(issue.textContent,'Enter payment amount.');assert.equal(issue.id,'receipt-receipt-amount-1');
    assert.equal(issue.closest('.batch-detail-row').hidden,false);assert.equal(d.querySelectorAll('input[type="number"][aria-invalid="true"]').length,1);
    assert.equal(d.querySelector('input[type="number"]').value,'500.00');
  }finally{dom.window.close();}
});
test('amount, date and client errors identify the actual constraint',async()=>{
  const cases=[
    {code:'voucher.error.amount',amount:'0.00',text:'Amount must be above zero.'},
    {code:'voucher.error.amount',amount:'12.345',text:'Use digits and up to 2 decimals.'},
    {code:'voucher.error.date',date:'2026-10-20',text:'Payment date is in the future.'},
    {code:'voucher.error.period',text:'Payment period is closed.'},
    {code:'repayment.error.adjustmentDate',text:'Before the latest loan adjustment.'},
    {code:'voucher.error.duplicate',text:'Collection reference already used.'},
    {code:'voucher.error.loanClient',text:'Client does not own this loan.'}
  ];
  for(const c of cases){
    const form={clientId:'client',effectiveDate:c.date || '2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:c.amount || '500.00',description:''}]};
    const {dom,d}=await setup(()=>{},{failureKey:c.code,form});try{assert.deepEqual(visible(d).map(issue=>issue.textContent),[c.text]);}finally{dom.window.close();}
  }
});

test('a successful draft retry clears its obsolete save warning',async()=>{
  const {dom,w,d,edit,save}=await setup(response=>Promise.resolve(response({policyVersion:1,allocationOrder:'INTEREST_FIRST',moneyAccount:'cash',lines:[]})));try{
    const original=w.fetch;let failed=false;
    w.fetch=async(url,options)=>{if(options?.method==='PUT' && !failed){failed=true;throw Error('Temporary failure');}return original(url,options);};
    await edit('600');await save();assert.equal(d.querySelector('[data-batch-error]').hidden,false);
    d.querySelector('[data-batch-save]').click();await settle();assert.equal(d.querySelector('[data-batch-error]').hidden,true);
    assert.equal(d.querySelector('input[type="number"]').value,'600');
  }finally{dom.window.close();}
});
