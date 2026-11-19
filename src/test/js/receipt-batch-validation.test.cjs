const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/receipt-batches.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const settle=async()=>{for(let n=0;n<6;n++)await tick();};
async function setup(estimate,patch={},realWidgets=false){
  const messages=Object.fromEntries(readFileSync('src/main/resources/messages_en.properties','utf8').split(/\r?\n/).filter(line=>line.startsWith('batch.field.')).map(line=>{const split=line.indexOf('=');return [line.slice(6,split),line.slice(split+1)];}));Object.assign(messages,{saveFailed:'Could not save draft.',checking:'Checking receipt...',client:'Client',status:'Status','state.DRAFT':'Draft',receipts:'Receipts',total:'Total',posted:'Posted',ready:'Ready',failed:'Needs attention',remaining:'Remaining'});
  const dom=new JSDOM(`<body><h1>Repayment 1</h1><main data-receipt-batch="batch" data-can-edit="true" data-today="2026-10-19">
    <section class="erp-resource-section"><h2>Batch details</h2><dl class="erp-detail-facts" data-batch-summary></dl></section><div data-batch-error hidden></div>
    <section class="erp-table-wrap" data-resource-table="receipt-batch-entries" data-table-pagination-mode="external"><div class="app-table-titlebar"><h2>Receipts</h2><div class="accounting-actions"><button data-batch-refresh data-aws-table-refresh>Refresh</button><button data-batch-add></button><button data-batch-save></button></div></div><button data-batch-validate></button><button data-batch-post></button><button data-batch-stop></button><span data-save-status></span>
    <div class="erp-table-toolbar aws-filter-toolbar batch-defaults"><label>Date<input data-default-date value="2026-10-19"></label><label>Account<select data-default-account data-batch-choice="money"></select></label><label>Show<select data-batch-filter></select></label></div>
    <div class="erp-table-scroll batch-grid-scroll"><table class="erp-table" data-table-sort-mode="none"><thead><tr>${Array.from({length:8},(_,i)=>`<th data-table-identifier>Column ${i}</th>`).join('')}</tr></thead><tbody data-batch-rows></tbody></table></div></section><span data-batch-progress></span>
    <div data-batch-confirm hidden><div role="dialog"><h2 id="batch-confirm-title"></h2><p data-confirm-message></p><div class="app-modal-actions"><button data-confirm-cancel></button><button data-confirm-post></button></div></div></div>
    <div data-batch-messages>${Object.entries(messages).map(([key,value])=>`<span data-message="${key}">${value}</span>`).join('')}</div>
  </main></body>`,{url:'http://localhost/finance/batches/receipts/batch',runScripts:'outside-only'});
  const w=dom.window,d=w.document;let autosave;
  w.structuredClone=structuredClone;w.HTMLElement.prototype.scrollIntoView=function(){};
  const nativeTimeout=w.setTimeout.bind(w),nativeClear=w.clearTimeout.bind(w);
  w.setTimeout=(fn,ms)=>ms===800?(autosave=fn,-1):nativeTimeout(fn,ms);w.clearTimeout=id=>id===-1?(autosave=undefined):nativeClear(id);w.setInterval=()=>0;
  w.ResourceTable={setPage(){}};w.AppSearch={bind(){return {cancel(){}};}};
  w.NeoSelect={search(){return {input:d.createElement('input'),more:d.createElement('button'),setLoading(){}};}};
  if(realWidgets){w.requestAnimationFrame=fn=>nativeTimeout(fn,0);w.cancelAnimationFrame=nativeClear;d.body.append(d.querySelector('[data-batch-confirm]'));for(const file of ['live-search','select-enhancer','resource-table'])w.eval(readFileSync('src/main/resources/static/js/'+file+'.js','utf8'));}
  const item={id:'receipt',lineNo:1,version:0,state:'FAILED',clientLabel:'Client One',labels:{loan:'1'},failureKey:'voucher.error.date',failureMessage:'Choose a valid payment date.',form:{clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''}]}};
  Object.assign(item,patch);
  const response=(data,ok=true)=>({ok,redirected:false,headers:{get:()=> 'application/json'},json:async()=>data});
  w.fetch=async(url,options)=>{
    if(url.endsWith('/summary'))return response({batch:{state:'DRAFT',version:0},receipts:1,posted:0,ready:0,failed:1,total:'500.00'});
    if(url.includes('/items?'))return response({rows:[structuredClone(item)],hasNext:false});
    if(options?.method==='PUT')return response({...item,version:1});
    if(url.endsWith('/estimate'))return estimate(response);
    if(url.includes('/loan-context?'))return response([{outstandingPrincipal:'1000.00',duePrincipal:'100.00',dueInterest:'25.50',totalLoanOutstanding:'1025.50'}]);
    if(url.includes('/choices/'))return response([]);
    throw Error('Unexpected request '+url);
  };
  w.eval(readFileSync('src/main/resources/static/js/date-controls.js','utf8'));w.eval(source);if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));await settle();
  return {dom,w,d,async edit(value){const input=d.querySelector('input[type="number"]');input.value=value;input.dispatchEvent(new w.Event('input'));},async save(){const callback=autosave;autosave=undefined;callback();await settle();}};
}
const visible=d=>[...d.querySelectorAll('.batch-field-error:not([hidden])')];

test('batch editor initializes its real account selector and loads summary and receipt rows',async()=>{
  const {dom,d}=await setup(response=>Promise.resolve(response({lines:[]})),{},true);
  try {
    assert.match(d.querySelector('[data-batch-summary]').textContent,/Draft/);
    assert.equal(d.querySelectorAll('[data-batch-rows] > .batch-row').length,1);
    assert.equal(d.querySelector('[data-default-account]').dataset.neoEnhanced,'true');
    assert.equal(d.querySelector('[data-batch-confirm]').parentElement,d.body);
    assert.equal(d.querySelector('[data-batch-add]').disabled,false);
  }finally{dom.window.close();}
});

test('confirmation still opens and cancels after the shared shell mounts its modal on the body',async()=>{
  const {dom,w,d,edit}=await setup(response=>Promise.resolve(response({lines:[]})),{},true);
  try {
    await edit('75.00');
    d.querySelector('[data-resource-table]').dispatchEvent(new w.CustomEvent('resource-table:refresh',{cancelable:true}));
    const modal=d.querySelector('[data-batch-confirm]');
    assert.equal(modal.hidden,false);
    assert.equal(modal.querySelector('[role="dialog"]').contains(d.activeElement),true);
    modal.querySelector('[data-confirm-cancel]').click();
    assert.equal(modal.hidden,true);
    assert.equal(d.querySelector('input[type="number"]').value,'75.00');
  }finally{w.close();}
});

test('failed table refresh preserves receipt values and recovers through the shared refresh control',async()=>{
  const {dom,w,d}=await setup(response=>Promise.resolve(response({lines:[]})),{},true);
  try {
    const fetch=w.fetch,amount=d.querySelector('input[type="number"]');
    w.fetch=async()=>{throw Error('Connection lost');};
    d.querySelector('[data-resource-table]').dispatchEvent(new w.CustomEvent('resource-table:refresh',{cancelable:true}));await settle();
    assert.equal(d.querySelector('[data-batch-error]').textContent,'Connection lost');
    assert.equal(d.querySelector('input[type="number"]'),amount);
    assert.match(d.querySelector('[data-batch-summary]').textContent,/Draft/);
    w.fetch=fetch;
    d.querySelector('[data-resource-table]').dispatchEvent(new w.CustomEvent('resource-table:refresh',{cancelable:true}));await settle();
    assert.equal(d.querySelector('[data-batch-error]').hidden,true);
    assert.equal(d.querySelector('input[type="number"]').value,'500.00');
  }finally{w.close();}
});
test('balances show only the outstanding amounts and interest due, including a genuine zero',async()=>{
  const line={loanId:'loan',loanNumber:'LN-001',outstandingPrincipal:'1000.00',duePrincipal:'0.00',dueInterest:'0.00',totalLoanOutstanding:'1000.00',principal:'50.00',interest:'0.00',principalBalance:'950.00'};
  const {dom,d,edit,save}=await setup(response=>Promise.resolve(response({policyVersion:1,allocationOrder:'INTEREST_FIRST',moneyAccount:'Cash',lines:[line]})));
  try{
    const balance=()=>[...d.querySelectorAll('.batch-row .batch-balance > *')].map(node=>node.textContent);
    assert.deepEqual(balance(),['totalLoanOutstanding','1,025.50','principalOutstanding','1,000.00','interestDue','25.50']);
    await edit('50.00');await save();
    assert.deepEqual(balance(),['totalLoanOutstanding','1,000.00','principalOutstanding','1,000.00','interestDue','0.00']);
    assert.equal(d.querySelector('.batch-row').cells.length,8);
  }finally{dom.window.close();}
});
test('missing loan balances remain unavailable after the payment date changes',async()=>{
  const {dom,w,d}=await setup(()=>{});
  try{
    const original=w.fetch;
    w.fetch=async(url,options)=>url.includes('date=2026-10-18')
      ? {ok:true,headers:{get:()=> 'application/json'},json:async()=>[{}]}
      : original(url,options);
    const date=d.querySelector('.batch-row input[type="date"]');
    date.value='2026-10-18';date.dispatchEvent(new w.Event('change'));await settle();
    assert.deepEqual([...d.querySelectorAll('.batch-balance dd')].map(node=>node.textContent),['—','—','—']);
  }finally{dom.window.close();}
});
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
    assert.equal(d.querySelector('.batch-row td:last-child .batch-field-error:not([hidden])'),null);
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
    assert.deepEqual(visible(d).map(issue=>issue.textContent).sort(),['Select receiving account.','Enter payment amount.'].sort());
    assert.equal(d.querySelector('.batch-detail-row').hidden,true);assert.equal(d.querySelector('.batch-expand').getAttribute('aria-expanded'),'false');
    assert.equal(d.querySelector('[aria-label="receivingAccount"]').closest('tr').className,'batch-row');
    assert.equal(new Set([...d.querySelectorAll('.batch-field-error')].map(issue=>issue.id)).size,d.querySelectorAll('.batch-field-error').length);
  }finally{dom.window.close();}
});
test('clearing a receipt account uses the batch default while explicit overrides remain selected',async()=>{
  const {dom,w,d,save}=await setup(response=>Promise.resolve(response({policyVersion:1,allocationOrder:'INTEREST_FIRST',moneyAccount:'Bank',lines:[]})));
  try{
    const defaults=d.querySelector('[data-default-account]');defaults.add(new w.Option('Bank','bank'));defaults.value='bank';
    const original=w.fetch,calls=[];w.fetch=async(url,options)=>{if(options?.method==='PUT')calls.push(JSON.parse(options.body).form);return original(url,options);};
    const account=d.querySelector('[aria-label="receivingAccount"]');account.add(new w.Option('Mobile money','mobile'));account.value='mobile';account.dispatchEvent(new w.Event('change'));await save();
    assert.equal(calls.at(-1).moneyAccountId,'mobile');assert.equal(account.value,'mobile');
    account.value='';account.dispatchEvent(new w.Event('change'));await save();
    assert.equal(calls.at(-1).moneyAccountId,'bank');assert.equal(account.value,'bank');assert.equal(account.selectedOptions[0].textContent,'Bank');
    assert.equal(Object.hasOwn(calls.at(-1),'reference'),false);assert.equal(calls.at(-1).rows[0].amount,'500.00');
  }finally{dom.window.close();}
});
test('choosing a batch default fills an empty receipt account without replacing a saved account',async()=>{
  for(const current of [null,'cash']){
    const form={clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:current,reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''}]};
    const {dom,w,d}=await setup(()=>{},{form});try{
      const defaults=d.querySelector('[data-default-account]');defaults.add(new w.Option('Bank','bank'));defaults.value='bank';defaults.dispatchEvent(new w.Event('change'));
      await settle();
      assert.equal(d.querySelector('[aria-label="receivingAccount"]').value,current || 'bank');
    }finally{dom.window.close();}
  }
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
