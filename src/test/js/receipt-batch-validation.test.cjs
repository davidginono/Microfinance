const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/receipt-batches.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const settle=async()=>{for(let n=0;n<6;n++)await tick();};
async function setup(estimate,patch={},realWidgets=false,initialState='DRAFT'){
  const messages=Object.fromEntries(readFileSync('src/main/resources/messages_en.properties','utf8').split(/\r?\n/).filter(line=>/^batch\.(?:field\.|(?:balances|viewBalances|totalLoanOutstanding|principalOutstanding|interestDue|chargesOutstanding)=)/.test(line)).map(line=>{const split=line.indexOf('=');return [line.slice(6,split),line.slice(split+1)];}));Object.assign(messages,{saveFailed:'Could not save draft.',checking:'Checking receipt...',client:'Client',status:'Status','state.DRAFT':'Draft',receipts:'Receipts',total:'Total',posted:'Posted',ready:'Ready',failed:'Needs attention',remaining:'Remaining',select:'Select',selected:'Selected','state.POSTED':'Posted','state.READY':'Ready','state.POSTING':'Posting','state.COMPLETED':'Completed'});
  const dom=new JSDOM(`<body><h1>Repayment 1</h1><main data-receipt-batch="batch" data-can-edit="true" data-today="2026-10-19">
    <section class="erp-resource-section"><h2>Batch details</h2><dl class="erp-detail-facts" data-batch-summary></dl></section><div data-batch-error hidden></div>
    <section class="erp-table-wrap" data-resource-table="receipt-batch-entries" data-table-pagination-mode="external" data-table-selection="single"><div class="app-table-titlebar"><h2>Receipts</h2><div class="accounting-actions"><button data-batch-refresh data-aws-table-refresh>Refresh</button><button data-table-command data-batch-view disabled>View</button><button data-batch-add></button><button data-batch-save></button></div></div><button data-batch-validate></button><button data-batch-post></button><button data-batch-stop></button><span data-save-status></span>
    <div class="erp-table-toolbar aws-filter-toolbar batch-defaults"><label>Date<input data-default-date value="2026-10-19"></label><label>Account<select data-default-account data-batch-choice="money"></select></label><label>Show<select data-batch-filter></select></label></div>
    <span data-batch-selection></span><div class="erp-table-scroll batch-grid-scroll"><table class="erp-table" data-table-sort-mode="none"><thead><tr>${Array.from({length:9},(_,i)=>`<th data-table-identifier>Column ${i}</th>`).join('')}</tr></thead><tbody data-batch-rows></tbody></table></div></section><span data-batch-progress></span><a hidden data-batch-completed href="/finance/batches/receipts/batch/posted"></a>
    <div data-batch-confirm hidden><div role="dialog"><h2 id="batch-confirm-title"></h2><p data-confirm-message></p><div class="app-modal-actions"><button data-confirm-cancel></button><button data-confirm-post></button></div></div></div>
    <div data-batch-messages>${Object.entries(messages).map(([key,value])=>`<span data-message="${key}">${value}</span>`).join('')}</div>
  </main></body>`,{url:'http://localhost/finance/batches/receipts/batch',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window,d=w.document;let autosave, poll;
  w.structuredClone=structuredClone;w.HTMLElement.prototype.scrollIntoView=function(){};
  const nativeTimeout=w.setTimeout.bind(w),nativeClear=w.clearTimeout.bind(w);
  w.setTimeout=(fn,ms)=>ms===800?(autosave=fn,-1):nativeTimeout(fn,ms);w.clearTimeout=id=>id===-1?(autosave=undefined):nativeClear(id);w.setInterval=fn=>{poll=fn;return 0;};
  w.ResourceTable={setPage(){}};w.AppSearch={bind(){return {cancel(){}};}};
  w.NeoSelect={search(){return {input:d.createElement('input'),more:d.createElement('button'),setLoading(){}};}};
  w.eval(readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));
  if(realWidgets){w.requestAnimationFrame=fn=>nativeTimeout(fn,0);w.cancelAnimationFrame=nativeClear;d.body.append(d.querySelector('[data-batch-confirm]'));for(const file of ['live-search','select-enhancer','resource-table'])w.eval(readFileSync('src/main/resources/static/js/'+file+'.js','utf8'));}
  const item={id:'receipt',lineNo:1,version:0,state:'FAILED',clientLabel:'Client One',labels:{loan:'1'},failureKey:'voucher.error.date',failureMessage:'Choose a valid payment date.',form:{clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''}]}};
  Object.assign(item,patch);
  const response=(data,ok=true)=>({ok,redirected:false,headers:{get:()=> 'application/json'},json:async()=>data});
  w.fetch=async(url,options)=>{
    if(url.endsWith('/summary'))return response({batch:{state:initialState,version:0},receipts:1,posted:0,ready:0,failed:1,total:'500.00',remainingTotal:'500.00'});
    if(url.includes('/items?'))return response({rows:[structuredClone(item)],hasNext:false});
    if(options?.method==='PUT')return response({...item,version:1});
    if(url.endsWith('/estimate'))return estimate(response);
    if(url.includes('/loan-context?'))return response([{outstandingPrincipal:'1000.00',duePrincipal:'100.00',dueInterest:'25.50',totalLoanOutstanding:'1025.50'}]);
    if(url.includes('/choices/'))return response([]);
    throw Error('Unexpected request '+url);
  };
  w.eval(readFileSync('src/main/resources/static/js/date-controls.js','utf8'));w.eval(source);if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));await settle();
  return {dom,w,d,poll,response,async edit(value){const input=d.querySelector('input[type="number"]');input.value=value;input.dispatchEvent(new w.Event('input'));},async save(){const callback=autosave;autosave=undefined;callback();await settle();}};
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

test('posted receipts use the selected row View action and show plain status',async()=>{
  const {w,d}=await setup(()=>{},{state:'POSTED',receiptId:'posted-receipt',failureKey:null},true,'COMPLETED');
  try {
    const row=d.querySelector('.batch-row'), checkbox=row.querySelector('[data-batch-select]');
    const view=d.querySelector('[data-batch-view]');
    const destinations=[];
    w.WorkspaceNavigation={navigate:url=>destinations.push(url)};
    assert.equal(row.lastElementChild.textContent,'Posted');
    assert.equal(row.lastElementChild.querySelector('a'),null);
    assert.equal(view.disabled,true);
    row.lastElementChild.click();
    assert.equal(checkbox.checked,true);
    assert.equal(row.classList.contains('resource-table-selected'),true);
    assert.equal(view.disabled,false);
    view.click();
    assert.deepEqual(destinations,['/finance/vouchers/receipts/posted-receipt']);
    d.querySelector('[data-resource-table]').dispatchEvent(new w.CustomEvent('resource-table:reset'));
    assert.equal(view.disabled,true);
    assert.equal(checkbox.checked,false);
  }finally{w.close();}
});

test('posting shows progress and returns to the register only after completed posting',async()=>{
  for(const outcome of ['COMPLETED','PARTIAL']){
    const {w,d,poll,response}=await setup(()=>{},{state:'READY',failureKey:null},true,'READY');
    try{
      const post=d.querySelector('[data-batch-post]'), confirm=d.querySelector('[data-confirm-post]');
      const original=w.fetch, states=state=>({batch:{state,version:1},receipts:1,posted:state==='COMPLETED'?1:0,ready:1,failed:0,total:'500.00',remainingTotal:'500.00'});
      let finishPost, state='POSTING', redirects=0;
      w.fetch=(url,options)=>{
        if(url.endsWith('/post?version=0'))return new Promise(resolve=>{finishPost=()=>resolve(response(states(state)));});
        if(url.endsWith('/summary'))return Promise.resolve(response(states(state)));
        return original(url,options);
      };
      d.querySelector('[data-batch-completed]').addEventListener('click',event=>{event.preventDefault();redirects++;});
      post.click();confirm.click();await settle();
      assert.equal(confirm.classList.contains('is-action-loading'),true);
      assert.equal(post.classList.contains('is-action-loading'),true);
      finishPost();await settle();
      assert.equal(confirm.classList.contains('is-action-loading'),false);
      assert.equal(post.classList.contains('is-action-loading'),true);
      assert.equal(redirects,0);
      state=outcome;await poll();await poll();
      assert.equal(redirects,outcome==='COMPLETED'?1:0);
      if(outcome==='PARTIAL')assert.equal(post.classList.contains('is-action-loading'),false);
    }finally{w.close();}
  }
});

test('validate and stop show progress until the worker leaves its running state',async()=>{
  const {w,d,poll,response}=await setup(()=>{},{},true);
  try{
    const validate=d.querySelector('[data-batch-validate]'), stop=d.querySelector('[data-batch-stop]');
    const original=w.fetch, stages={};
    let state='DRAFT', cancelRequested=false;
    const summary=()=>({batch:{state,version:0,cancelRequested},receipts:1,posted:0,ready:0,failed:0,total:'500.00'});
    w.fetch=(url,options)=>{
      if(url.endsWith('/validate?version=0') || url.endsWith('/stop')){
        const command=url.includes('/validate?')?'validate':'stop';
        return new Promise(resolve=>{stages[command]=()=>resolve(response(summary()));});
      }
      if(url.endsWith('/summary'))return Promise.resolve(response(summary()));
      return original(url,options);
    };
    validate.click();await settle();
    assert.equal(validate.classList.contains('is-action-loading'),true);
    state='VALIDATING';stages.validate();await settle();
    assert.equal(validate.classList.contains('is-action-loading'),true);
    assert.equal(stop.disabled,false);
    stop.click();await settle();
    assert.equal(stop.classList.contains('is-action-loading'),true);
    cancelRequested=true;stages.stop();await settle();
    assert.equal(stop.classList.contains('is-action-loading'),true);
    assert.equal(stop.disabled,true);
    state='CANCELLED';await poll();
    assert.equal(validate.classList.contains('is-action-loading'),false);
    assert.equal(stop.classList.contains('is-action-loading'),false);
    assert.equal(stop.hidden,true);
  }finally{w.close();}
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
test('the balance dropdown shows only unpaid amounts and preserves the compact row',async()=>{
  const line={loanId:'loan',loanNumber:'LN-001',outstandingPrincipal:'1000.00',duePrincipal:'0.00',dueInterest:'0.00',chargesOutstanding:'25.00',totalLoanOutstanding:'1025.00',principal:'25.00',interest:'0.00',chargesPaid:'25.00',feesRecovered:'10.00',principalBalance:'975.00'};
  const {dom,w,d,edit,save}=await setup(response=>Promise.resolve(response({policyVersion:1,allocationOrder:'INTEREST_FIRST',moneyAccount:'Cash',lines:[line]})),{},true);
  try{
    const dropdown=()=>d.querySelector('.batch-row select.batch-balance');
    const options=()=>[...dropdown().options].map(option=>option.textContent);
    assert.deepEqual(options(),['View balances','Total outstanding · 1,025.50','Outstanding principal · 1,000.00','Interest due · 25.50']);
    await edit('50.00');await save();
    assert.deepEqual(options(),['View balances','Total outstanding · 1,025.00','Outstanding principal · 1,000.00','Interest due · 0.00','Fees due · 25.00']);
    const select=dropdown();
    assert.equal(select.dataset.neoEnhanced,undefined);
    assert.equal(select.getAttribute('aria-label'),'Balances (TZS)');
    assert.equal(select.name,'');
    select.value='chargesOutstanding';
    select.dispatchEvent(new w.Event('change',{bubbles:true}));
    assert.equal(select.selectedOptions[0].textContent,'View balances');
    assert.equal(d.querySelector('input[type="number"]').value,'50.00');
    assert.equal(d.querySelector('.batch-row').cells.length,9);
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
    assert.deepEqual([...d.querySelectorAll('.batch-balance option')].slice(1).map(option=>option.textContent),[
      'Total outstanding · —','Outstanding principal · —','Interest due · —'
    ]);
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
    assert.equal(d.querySelector('.batch-detail-row'),null);
    assert.equal(d.querySelector('.batch-remove').textContent,'−');
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
test('older multi-loan receipts show their stored total and status without an editable detail panel',async()=>{
  const form={clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00'},{loanId:'loan2',amount:null}]};
  const {dom,d}=await setup(()=>{},{failureKey:'voucher.error.amount',failureMessage:'Enter the missing amount.',form,total:'500.00',labels:{loan:'LN-001',loan2:'LN-002'}});try{
    assert.equal(d.querySelector('[data-batch-rows]').rows.length,1);
    assert.equal(d.querySelector('.batch-loan-list').textContent,'LN-001, LN-002');
    assert.equal(d.querySelector('.batch-row').cells[5].textContent,'500.00');
    assert.equal(d.querySelector('input[type="number"]'),null);
    assert.equal(d.querySelector('.batch-row select').disabled,true);
    assert.equal(d.querySelector('[data-batch-view]').disabled,true);
    assert.equal(visible(d)[0].textContent,'Enter the missing amount.');
    assert.equal(d.querySelector('.batch-remove').disabled,false);
  }finally{dom.window.close();}
});
test('minus removes the newly added receipt after its first save and leaves the other row',async()=>{
  const {dom,w,d}=await setup(()=>{}, {}, true);
  try{
    const original=w.fetch, deletes=[];
    let finishSave, newUrl;
    w.fetch=async(url,options)=>{
      if(options?.method==='PUT'){
        newUrl=url;
        assert.equal(JSON.parse(options.body).form.rows.length,1);
        return new Promise(resolve=>{finishSave=()=>resolve({ok:true,headers:{get:()=> 'application/json'},json:async()=>({version:3,labels:{}})});});
      }
      if(options?.method==='DELETE'){
        deletes.push(url);
        return {ok:true,headers:{get:()=> 'application/json'},json:async()=>({deleted:true})};
      }
      return original(url,options);
    };
    d.querySelector('[data-batch-add]').click();
    await settle();
    const rows=d.querySelectorAll('.batch-row');
    assert.equal(rows.length,2);
    assert.equal(rows[1].querySelectorAll('input[type="number"]').length,1);
    rows[1].querySelector('.batch-remove').click();
    await settle();
    assert.deepEqual(deletes,[]);
    finishSave();
    await settle();
    assert.deepEqual(deletes,[newUrl + '?version=3']);
    assert.deepEqual([...d.querySelectorAll('.batch-row')].map(row=>row.dataset.item),['receipt']);
  }finally{dom.window.close();}
});
test('a rejected removal keeps the draft and re-enables its minus button',async()=>{
  const {dom,w,d}=await setup(()=>{});
  try{
    const original=w.fetch;
    w.fetch=async(url,options)=>options?.method==='DELETE'
      ? {ok:false,headers:{get:()=> 'application/json'},json:async()=>({message:'Refresh before removing.'})}
      : original(url,options);
    const remove=d.querySelector('.batch-remove');
    remove.click();
    await settle();
    assert.equal(d.querySelector('.batch-remove'),remove);
    assert.equal(remove.disabled,false);
    assert.equal(d.querySelector('input[type="number"]').value,'500.00');
    assert.equal(d.querySelector('[data-batch-error]').textContent,'Refresh before removing.');
  }finally{dom.window.close();}
});
test('posted receipts stay read-only and their balances remain available',async()=>{
  const preview={lines:[{loanId:'loan',outstandingPrincipal:'1000.00',dueInterest:'0.00',totalLoanOutstanding:'1000.00'}]};
  const {dom,d}=await setup(()=>{},{state:'POSTED',failureKey:null,preview});
  try{
    assert.equal(d.querySelector('.batch-remove').disabled,true);
    assert.equal(d.querySelector('.batch-row input[type="number"]').disabled,true);
    assert.equal(d.querySelector('.batch-balance').disabled,false);
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
