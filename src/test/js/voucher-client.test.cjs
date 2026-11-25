const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const read=name=>fs.readFileSync('src/main/resources/static/js/'+name,'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
const select=(attrs,label='Choose')=>`<select data-voucher-select ${attrs}><option value="">${label}</option></select>`;
const hidden=(name,value='')=>`<input type="hidden" name="${name}" value="${value}">`;
const account=(name,attrs,approval=false)=>`<span class="${approval?'approval-account':'voucher-account-choice'}">${hidden(name)}${approval?'<input data-account-label readonly hidden>':'<span data-account-label hidden></span>'}${select(attrs)}</span>`;
const splitControls=`<div data-manual-accounts data-split-editor><div data-split-list></div><div data-split-totals hidden><output data-debit-total></output><output data-credit-total></output><p data-balance-error hidden>Balance the transaction</p></div><button type="button" data-add-entry="DEBIT">Add debit</button><button type="button" data-add-entry="CREDIT">Add credit</button></div>`;
const response=data=>({ok:true,redirected:false,json:async()=>data});
async function setup(approval=false,fetcher,activityTypes=false,numbering=false,fresh=false,direct=false){
 const row=approval?`<tr data-editor-line><td><input type="checkbox" data-line-select></td><td data-line-number>1${hidden('rows[0].transactionId')}${hidden('rows[0].templateKey')}${hidden('rows[0].component','TOTAL')}</td><td><input name="rows[0].description"></td><td>${account('rows[0].debitAccountId','data-account-pick aria-label="Debit account"',true)}</td><td><input name="rows[0].amount" value="1.00"></td></tr>`:`<fieldset data-voucher-row><legend data-row-number>1</legend>${hidden('rows[0].transactionId')}${hidden('rows[0].templateKey')}${hidden('rows[0].component','TOTAL')}<span data-configured-transactions>${select('data-mapping data-other-only aria-label="Transaction"','Choose transaction')}</span><span data-mapping-label></span><div data-loan-only>${account('rows[0].loanId','data-loan-choice="loans" data-choice-for="rows[0].loanId" aria-label="Loan"')}</div>${hidden('rows[0].debitAccountId')}${hidden('rows[0].creditAccountId')}<input name="rows[0].description"><label data-transaction-amount><input name="rows[0].amount" value="1.00"></label>${splitControls}<button type="button" data-remove-row>Remove</button></fieldset>`;
 const html=approval?`<form data-approval-editor data-lookup-error="Lookup failed" data-client-url="/finance/approval/id/clients" data-account-url="/finance/approval/id/accounts">${account('clientId','data-client-pick aria-label="Client"',true)}<button data-editor-submit>Submit</button><button type="button" data-line-add>Add</button><table><tbody data-editor-lines>${row}</tbody></table></form>`:`<form data-voucher-form data-kind="RECEIPT" data-balance-error="Balance the transaction" data-debit-label="Debit" data-credit-label="Credit" data-amount-label="Amount (TZS)" data-remove-entry-label="Remove entry" data-choose-account="Choose an account" data-base="/finance/vouchers/receipts" data-lookup-error="Lookup failed"><select data-receipt-purpose name="receiptPurpose"><option>OTHER</option><option>LOAN_REPAYMENT</option></select><input name="reference"><input name="interestAccountId"><label data-other-only>Received from<input name="party" required></label><label data-loan-payer hidden>Received from<input name="loanPayer" disabled></label>${account('clientId','data-voucher-client data-choice-for="clientId" aria-label="Client"')}${account('moneyAccountId','data-account-for="moneyAccountId" data-money-only aria-label="Money account"')}<div data-voucher-rows>${row}</div><span data-row-status></span><button type="button" data-add-row>Add</button><output data-live-total></output></form>`;
 const dom=new JSDOM(`<body data-select-search="Search options" data-select-no-matches="No matches" data-select-load-more="Load more" data-shell-select="Choose" data-shell-no-options="No options" data-shell-loading="Loading">${html}</body>`,{url:'http://localhost',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,d=w.document,calls=[];
 if(!approval)d.querySelector('form').insertAdjacentHTML('afterbegin',hidden('equity','false')+'<label data-equity-only hidden><select name="equityChoice" data-equity-control><option value="">Choose type</option><option value="CONTRIBUTION">Capital added</option><option value="3:f82c2471-0267-4cc1-ba10-730c78ef5eb2">Profit set aside for operations</option></select></label><label data-equity-only hidden><textarea name="equityReason" data-equity-control></textarea></label>');
 if(numbering){const form=d.querySelector('form');Object.assign(form.dataset,{numberLoading:'Loading voucher number',numberError:'Could not generate voucher number',numberCsrfHeader:'X-CSRF-TOKEN',numberCsrfToken:'synthetic-token'});form.insertAdjacentHTML('beforeend',hidden('requestKey','synthetic-key')+'<input name="effectiveDate" type="date" value="2026-10-20"><span data-voucher-number><span class="app-loading-spinner" data-voucher-number-spinner data-loading-size="small" hidden></span><span data-voucher-number-value></span></span><button type="button" data-retry-voucher-number hidden>Retry</button>');}
 if(!approval){
  const activityRow=value=>`<div data-voucher-activity-row><input type="hidden" name="activityIds" data-activity-id value="${value}"><span data-activity-name>Activity</span><button type="button" data-remove-activity>Remove activity</button></div>`;
  const primary=select('data-voucher-type aria-label="Activities"').replace('</select>','<option value="OTHER">Manual entry</option><option value="EQUITY">Equity</option><option value="LOAN_REPAYMENT">Loan repayment</option></select>');
  d.querySelector('form').insertAdjacentHTML('afterbegin',`<div data-voucher-activities data-max-activities="50">
    <div data-voucher-activity-list>${activityTypes||direct?'':activityRow('activity-1')}</div>
    <template>${activityRow('')}</template>
    <div class="voucher-account-choice" data-voucher-primary-activity>${primary}</div>
    <div class="voucher-account-choice" data-voucher-extra-activity>${select('data-voucher-add-activity aria-label="Add activity"','Add activity')}</div>
  </div>`);
 }
 if(approval&&activityTypes){
  const form=d.querySelector('form'),row=d.querySelector('[data-editor-line]');
  Object.assign(form.dataset,{configuredVoucher:'true',transactionUrl:'/finance/approval/id/vouchers/item/transactions'});
  form.insertAdjacentHTML('afterbegin','<select data-approval-transaction disabled><option value="">Choose transaction</option></select>');
  row.querySelector('[name="rows[0].description"]').insertAdjacentHTML('afterend','<small data-mapping-label></small>');
  row.querySelector('.approval-account').parentElement.outerHTML='<td>'+hidden('rows[0].debitAccountId')+'<input readonly data-posting-side="debit"></td><td>'+hidden('rows[0].creditAccountId')+'<input readonly data-posting-side="credit"></td>';
 }
 if(fresh)d.querySelector('form').dataset.newVoucher='true';
 const clear=d.createElement('button');clear.type='button';clear.dataset.clientClear='';clear.textContent='Clear';d.querySelector('input[name=clientId]').parentElement.append(clear);
 w.HTMLElement.prototype.scrollIntoView=function(){};w.fetch=async(url,options)=>{calls.push(String(url));return fetcher?fetcher(new URL(url),options):response([{id:'client-1',name:'Synthetic client',number:'CL-001',label:'Synthetic client'}]);};
 w.eval(read('live-search.js'));w.eval(read('select-enhancer.js'));
 if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
 w.eval(read('voucher-selects.js'));w.eval(read('voucher-balance.js'));w.eval(read(approval?'voucher-approval.js':'accounting-vouchers.js'));await tick();
 const open=async control=>{await tick();control.closest('.neo-select').querySelector('button').click();await tick();};
 const options=()=>[...d.querySelectorAll('.neo-select--open')].flatMap(wrapper=>{const button=wrapper.querySelector('button');return [...d.querySelectorAll('#'+button.getAttribute('aria-controls')+' .neo-select-option')];});
 return {dom,w,d,calls,open,options};
}
test('voucher numbers show a pending spinner, retain the latest date result and recover after failures',async()=>{
 const pending=[];
 const {dom,w,d}=await setup(false,(url,options)=>new Promise((resolve,reject)=>pending.push({url,options,resolve,reject})),false,true);
 try{
  const output=d.querySelector('[data-voucher-number]'),value=d.querySelector('[data-voucher-number-value]'),spinner=d.querySelector('[data-voucher-number-spinner]'),date=d.querySelector('[name=effectiveDate]'),retry=d.querySelector('[data-retry-voucher-number]');
  const description=d.querySelector('[name="rows[0].description"]');description.value='Draft description';assert.equal(description.disabled,false);
  assert.equal(output.getAttribute('aria-busy'),'true');assert.equal(value.classList.contains('sr-only'),true);assert.equal(spinner.hidden,false);
  assert.equal(pending[0].options.method,'POST');assert.equal(pending[0].options.headers['X-CSRF-TOKEN'],'synthetic-token');assert.equal(pending[0].options.body.get('effectiveDate'),'2026-10-20');
  date.value='2026-10-21';date.dispatchEvent(new w.Event('change'));assert.equal(pending[0].options.signal.aborted,true);
  pending[1].resolve(response({number:'RV-2026-00000002'}));await tick();pending[0].resolve(response({number:'RV-2026-00000001'}));await tick();
  assert.equal(value.textContent,'RV-2026-00000002');assert.equal(value.classList.contains('sr-only'),false);assert.equal(spinner.hidden,true);assert.equal(output.getAttribute('aria-busy'),'false');
  date.value='2026-10-19';date.dispatchEvent(new w.Event('change'));pending[2].reject(Error('Unavailable'));await tick();
  assert.equal(value.textContent,'Could not generate voucher number');assert.equal(retry.hidden,false);assert.equal(spinner.hidden,true);
  retry.click();assert.equal(retry.hidden,true);assert.equal(spinner.hidden,false);pending[3].resolve(response({number:'RV-2026-00000003'}));await tick();assert.equal(value.textContent,'RV-2026-00000003');assert.equal(spinner.hidden,true);
  date.value='2026-10-22';date.dispatchEvent(new w.Event('change'));w.dispatchEvent(new w.PageTransitionEvent('pagehide'));
  assert.equal(pending[4].options.signal.aborted,true);assert.equal(spinner.hidden,true);assert.equal(retry.hidden,false);
  pending[4].resolve(response({number:'RV-2026-00000004'}));await tick();
  assert.equal(value.textContent,'Could not generate voucher number');assert.equal(pending.length,5);assert.equal(description.value,'Draft description');
 }finally{dom.window.close();}
});
for(const approval of [false,true])test(`${approval?'approval':'ordinary'} voucher client dropdown searches, binds and clears without submitting`,async()=>{
 const {dom,w,d,calls,open,options}=await setup(approval);try{
  let submits=0;d.querySelector('form').addEventListener('submit',event=>{event.preventDefault();submits++;});
  const control=d.querySelector(approval?'[data-client-pick]':'[data-voucher-client]');assert.ok(control.closest('.neo-select').querySelector('.neo-select-chevron'));
  await open(control);assert.match(calls[0],/\/clients\?search=&page=0$/);assert.equal(options()[0].textContent,'Synthetic client');options()[0].click();
  assert.equal(d.querySelector('[name=clientId]').value,'client-1');assert.equal(submits,0);
  if(approval)assert.equal(d.querySelector('[data-editor-submit]').disabled,true);
  else{const purpose=d.querySelector('[data-receipt-purpose]');purpose.value='LOAN_REPAYMENT';purpose.dispatchEvent(new w.Event('change'));assert.equal(d.querySelector('[name=clientId]').value,'client-1');await open(control);assert.match(calls.at(-1),/\/loan-choices\/clients\?/);w.refreshEnhancedSelects();purpose.value='OTHER';purpose.dispatchEvent(new w.Event('change'));}
  d.querySelector('[data-client-clear]').click();assert.equal(d.querySelector('[name=clientId]').value,'');assert.equal(control.value,'');
 }finally{dom.window.close();}
});
test('repayment hides Received from for individuals and makes row descriptions optional',async()=>{
 const clients=[
  {id:'individual',label:'Individual client',purpose:'PERSON'},
  {id:'organization',label:'Organization client',purpose:'ORGANIZATION'},
  {id:'group',label:'Joint group',purpose:'JOINT_GROUP'}
 ];
 const {dom,d,open,options}=await setup(false,url=>response(url.pathname.endsWith('/loan-choices/clients')?clients:[]),true);
 try{
  const form=d.querySelector('form'),activity=d.querySelector('[data-voucher-type]'),client=d.querySelector('[data-voucher-client]');
  const party=form.elements.party,payer=form.elements.loanPayer,description=form.elements['rows[0].description'];
  assert.equal(party.closest('label').hidden,false);
  assert.equal(description.required,true);
  await open(activity);options().find(o=>o.textContent==='Loan repayment').click();
  assert.equal(party.closest('label').hidden,true);
  assert.equal(party.disabled,true);
  assert.equal(payer.closest('label').hidden,true);
  assert.equal(payer.disabled,true);
  assert.equal(description.required,false);
  assert.equal(description.checkValidity(),true);
  await open(client);options().find(o=>o.textContent==='Organization client').click();
  assert.equal(payer.closest('label').hidden,false);
  assert.equal(payer.required,true);
  payer.value='Synthetic representative';
  await open(client);options().find(o=>o.textContent==='Joint group').click();
  assert.equal(payer.value,'');
  assert.equal(payer.required,true);
  await open(client);options().find(o=>o.textContent==='Individual client').click();
  assert.equal(payer.closest('label').hidden,true);
  assert.equal(payer.disabled,true);
  await open(activity);options().find(o=>o.textContent==='Manual entry').click();
  assert.equal(party.closest('label').hidden,false);
  assert.equal(description.required,true);
 }finally{dom.window.close();}
});
test('client changes invalidate loan choices, and new voucher rows bind their own dropdowns',async()=>{
 const {dom,w,d,open,options,calls}=await setup(false,url=>response(url.pathname.endsWith('/loans')?[{id:'loan-1',label:'Loan 1'}]:[{id:url.searchParams.get('search')?'client-2':'client-1',name:url.searchParams.get('search')?'Second client':'First client'}]));try{
  const purpose=d.querySelector('[data-receipt-purpose]');purpose.value='LOAN_REPAYMENT';purpose.dispatchEvent(new w.Event('change'));
  let loan=d.querySelector('[data-loan-choice]');assert.equal(loan.disabled,true);const client=d.querySelector('[data-voucher-client]');await open(client);options()[0].click();assert.equal(loan.disabled,false);
  await open(loan);assert.match(calls.at(-1),/clientId=client-1/);options()[0].click();assert.equal(d.querySelector('[name="rows[0].loanId"]').value,'loan-1');
  await open(client);const search=w.NeoSelect.search(client).input;search.value='Second';search.dispatchEvent(new w.Event('input',{bubbles:true}));search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));await tick();options()[0].click();assert.equal(loan.value,'');assert.equal(d.querySelector('[name="rows[0].loanId"]').value,'');
  d.querySelector('[data-add-row]').click();loan=d.querySelectorAll('[data-loan-choice]')[1];await open(loan);assert.match(calls.at(-1),/clientId=client-2/);options()[0].click();assert.equal(d.querySelector('[name="rows[1].loanId"]').value,'loan-1');
 }finally{dom.window.close();}
});
test('configured activity mappings retain their exact template/component and manual mode restores entries',async()=>{
 const mapping={transactionId:'transaction-1',templateKey:'template-1',transactionName:'Expense',component:'PRINCIPAL',activity:{id:'activity-1',name:'Expenses'},debit:{name:'Expense account'},credit:{name:'Cash'}};
 const {dom,d,open,options}=await setup(false,url=>response(url.pathname.endsWith('/activities')?[{id:'activity-1',name:'Expenses'}]:url.pathname.endsWith('/transactions')?[mapping]:[{id:'account-1',name:'Cash account',purpose:'CASH'},{id:'account-2',name:'Expense',purpose:'ORDINARY'}]),true);try{
  await open(d.querySelector('[data-voucher-type]'));options().find(o=>o.textContent==='Expenses').click();
  await open(d.querySelector('[data-money-only]'));assert.equal(options().length,1);options()[0].click();assert.equal(d.querySelector('[name=moneyAccountId]').value,'account-1');
  await open(d.querySelector('[data-mapping]'));options()[0].click();assert.equal(d.querySelector('[name="rows[0].transactionId"]').value,'transaction-1');assert.equal(d.querySelector('[name="rows[0].templateKey"]').value,'template-1');assert.equal(d.querySelector('[name="rows[0].component"]').value,'PRINCIPAL');assert.equal(d.querySelector('[data-manual-accounts]').hidden,true);
  d.querySelector('[data-remove-activity]').click();await open(d.querySelector('[data-voucher-type]'));options().find(o=>o.textContent==='Manual entry').click();assert.equal(d.querySelector('[name="rows[0].templateKey"]').value,'');assert.equal(d.querySelector('[name="rows[0].component"]').value,'TOTAL');assert.equal(d.querySelector('[data-manual-accounts]').hidden,false);
 }finally{dom.window.close();}
});
test('approval rows added during editing have independent account dropdowns',async()=>{
 const {dom,d,open,options}=await setup(true,()=>response([{id:'account-1',name:'Expense account'}]));try{
  d.querySelector('[data-line-add]').click();await open(d.querySelectorAll('[data-account-pick]')[1]);options()[0].click();assert.equal(d.querySelector('[name="rows[1].debitAccountId"]').value,'account-1');assert.equal(d.querySelector('[name="rows[0].debitAccountId"]').value,'');
 }finally{dom.window.close();}
});
test('voucher dropdowns reject stale searches and retain selection on empty or failed lookups',async()=>{
 let releaseOld;
 const {dom,w,d,open,options}=await setup(false,url=>{const query=url.searchParams.get('search');if(query==='old')return new Promise(resolve=>releaseOld=()=>resolve(response([{id:'stale',name:'Old result'}])));if(query==='fail')throw Error();return response(query?[]:[{id:'client-1',name:'First client'}]);});try{
  const client=d.querySelector('[data-voucher-client]');await open(client);options()[0].click();await open(client);const ui=w.NeoSelect.search(client);
  const search=async value=>{ui.input.value=value;ui.input.dispatchEvent(new w.Event('input',{bubbles:true}));ui.input.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));await tick();};
  await search('old');await search('new');releaseOld();await tick();assert.equal(options().length,0);assert.equal(client.value,'client-1');assert.equal(d.querySelector('[name=clientId]').value,'client-1');
  await search('fail');assert.equal(d.querySelector('.neo-select-menu [role=alert]').textContent,'Lookup failed');assert.equal(client.value,'client-1');
 }finally{dom.window.close();}
});
test('Load more uses bounded server pages and caps retained voucher options',async()=>{
 const {dom,w,d,open,options,calls}=await setup(false,url=>response(Array.from({length:26},(_,i)=>({id:'client-'+(Number(url.searchParams.get('page'))*25+i),name:'Client '+(Number(url.searchParams.get('page'))*25+i)}))));try{
  const client=d.querySelector('[data-voucher-client]');await open(client);assert.equal(options().length,25);const ui=w.NeoSelect.search(client);assert.equal(ui.more.hidden,false);
  for(let page=1;page<=5;page++){ui.more.click();await tick();assert.match(calls.at(-1),new RegExp('page='+page+'$'));assert.ok(client.options.length<=101);}
 }finally{dom.window.close();}
});

test('new voucher starts with its activity placeholder and retains an explicit manual-account choice',async()=>{
 const {dom,d,open,options}=await setup(false,()=>response([]),true,false,true);try{
  const control=d.querySelector('[data-voucher-type]');assert.equal(control.value,'');await open(control);options().find(o=>o.textContent==='Manual entry').click();assert.equal(control.value,'OTHER');
  d.querySelector('[data-add-row]').click();assert.equal(control.value,'OTHER');assert.equal(d.querySelector('form').dataset.newVoucher,'false');
 }finally{dom.window.close();}
});
test('adding and removing activities preserves eligible mappings and rejects stale lookup results',async()=>{
 const activities=[{id:'activity-1',name:'Operations'},{id:'activity-2',name:'Fees'}];
 const mappings=activities.map((activity,i)=>({transactionId:'transaction-'+i,templateKey:'template-'+i,transactionName:'Income '+i,component:'TOTAL',activity,debit:{name:'Cash'},credit:{name:'Income'}}));
 let waitForLookup=false,finishLookup,oldSignal;
 const {dom,w,d,open,options,calls}=await setup(false,(url,request)=>{
  if(url.pathname.endsWith('/activities'))return response(activities);
  if(waitForLookup)return new Promise(resolve=>{finishLookup=()=>resolve(response(mappings));oldSignal=request.signal;});
  return response(mappings.filter(item=>url.searchParams.getAll('activityIds').includes(item.activity.id)));
 },true);try{
  const form=d.querySelector('form'),type=d.querySelector('[data-voucher-type]'),add=d.querySelector('[data-voucher-add-activity]');
  const ids=()=>new w.FormData(form).getAll('activityIds');
  const field=(index,suffix)=>form.elements.namedItem('rows['+index+'].'+suffix);
  await open(type);options().find(o=>o.textContent==='Operations').click();
  assert.deepEqual(ids(),['activity-1']);
  await open(d.querySelector('[data-mapping]'));assert.deepEqual(new URL(calls.at(-1)).searchParams.getAll('activityIds'),ids());options()[0].click();
  assert.equal(field(0,'transactionId').value,'transaction-0');
  await open(add);assert.deepEqual(options().map(o=>o.textContent),['Fees']);options()[0].click();
  assert.deepEqual(ids(),['activity-1','activity-2']);
  assert.equal(field(0,'transactionId').value,'transaction-0');
  d.querySelector('[data-add-row]').click();
  const second=d.querySelectorAll('[data-mapping]')[1];
  await open(second);assert.deepEqual(new URL(calls.at(-1)).searchParams.getAll('activityIds'),ids());
  options().find(o=>o.textContent.includes('Fees')).click();
  field(1,'amount').value='2.30';
  form.insertAdjacentHTML('beforeend','<section data-voucher-preview>Preview</section>');
  waitForLookup=true;await open(second);
  assert.ok(finishLookup);
  d.querySelector('[data-remove-activity]').click();assert.equal(oldSignal.aborted,true);
  waitForLookup=false;finishLookup();await tick();
  assert.deepEqual(ids(),['activity-2']);
  assert.equal(field(0,'transactionId').value,'');
  assert.equal(field(0,'description').value,'Income 0');
  assert.equal(field(0,'amount').value,'1.00');
  assert.equal(field(1,'transactionId').value,'transaction-1');
  assert.equal(field(1,'amount').value,'2.30');
  assert.equal(d.querySelector('[data-voucher-preview]').hidden,true);
  await open(d.querySelector('[data-mapping]'));assert.equal(options().length,1);assert.match(options()[0].textContent,/Fees/);
  d.querySelector('[data-remove-activity]').click();assert.deepEqual(ids(),[]);
  assert.equal(type.value,'');assert.equal(d.querySelector('[data-mapping]').disabled,true);
  await open(type);options().find(o=>o.textContent==='Loan repayment').click();
  assert.equal(form.elements.receiptPurpose.value,'LOAN_REPAYMENT');
 }finally{dom.window.close();}
});

test('approval transaction picker targets only the selected line and keeps its amount',async()=>{
 const mapping={transactionId:'transaction-1',templateKey:'template-1',transactionName:'Income',component:'TOTAL',activity:{name:'Operations'},debit:{code:'101',name:'Cash'},credit:{code:'201',name:'Income'}};
 const {dom,d,open,options,calls}=await setup(true,()=>response([mapping]),true);try{
  const chooser=d.querySelector('[data-approval-transaction]');
  assert.equal(chooser.disabled,true);
  d.querySelector('[data-line-add]').click();
  const form=d.querySelector('form');
  form.elements['rows[1].amount'].value='22.47';
  d.querySelectorAll('[data-line-select]')[1].click();assert.equal(chooser.disabled,false);
  await open(chooser);assert.match(calls.at(-1),/approval\/id\/vouchers\/item\/transactions/);options()[0].click();
  assert.equal(form.elements['rows[0].transactionId'].value,'');
  assert.equal(form.elements['rows[1].transactionId'].value,'transaction-1');
  assert.equal(form.elements['rows[1].description'].value,'Income');
  assert.equal(form.elements['rows[1].amount'].value,'22.47');
  assert.equal(d.querySelectorAll('[data-posting-side="credit"]')[1].value,'201 · Income');
  d.querySelectorAll('[data-line-select]')[1].click();assert.equal(chooser.disabled,true);
 }finally{dom.window.close();}
});

test('cash account dropdown sends the purpose filter to the server before paging',async()=>{
 const {dom,d,calls,open}=await setup(false,()=>response([{id:'cash',code:'111001',name:'Cash',purpose:'CASH'}]));
 try{await open(d.querySelector('[data-money-only]'));const url=new URL(calls.find(url=>url.includes('/accounts')));assert.equal(url.searchParams.get('purpose'),'MONEY');assert.equal(url.searchParams.get('page'),'0');}finally{dom.window.close();}
});

for(const kind of ['RECEIPT','PAYMENT','JOURNAL'])test(`${kind} direct entries have adjacent amounts, derived totals and no transaction selector`,async()=>{
 const {dom,w,d}=await setup(false,undefined,false,false,false,true);try{
  const form=d.querySelector('form'),row=d.querySelector('[data-voucher-row]');form.dataset.kind=kind;
  const entries=()=>[...row.querySelectorAll('[data-split-entry]')],amount=n=>entries()[n].querySelector('input[name$=".amount"]'),change=(input,value)=>{input.value=value;input.dispatchEvent(new w.Event('input',{bubbles:true}));};
  const total=form.elements['rows[0].amount'];form.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(entries().length,2);assert.equal(total.type,'hidden');assert.equal(total.required,false);assert.equal(d.querySelector('[data-transaction-amount]').hidden,true);assert.equal(d.querySelector('[data-configured-transactions]').hidden,true);assert.equal(d.querySelector('[data-mapping]').disabled,true);
  change(amount(0),'0.10');change(amount(1),'0.30');row.querySelector('[data-add-entry="DEBIT"]').click();change(amount(2),'0.20');
  assert.equal(total.value,'0.30');assert.equal(amount(0).validationMessage,'');assert.equal(row.querySelector('[data-balance-error]').hidden,true);assert.equal(d.querySelector('[data-live-total]').value,'0.30');
  assert.equal(amount(1).readOnly,false);assert.equal(amount(2).required,true);assert.equal(entries()[2].querySelector('[data-remove-entry]').getAttribute('aria-label'),'Remove entry');
  const expected=kind==='RECEIPT'?['MONEY','OTHER','MONEY']:kind==='PAYMENT'?['OTHER','MONEY','OTHER']:['','',''];assert.deepEqual(entries().map(e=>e.querySelector('select').dataset.purpose),expected);
  entries()[2].querySelector('[data-remove-entry]').click();assert.equal(amount(0).value,'0.10');assert.equal(amount(0).validationMessage,'Balance the transaction');assert.equal(total.value,'0.10');
  change(amount(0),'0.30');assert.equal(amount(0).validationMessage,'');
  change(amount(0),'9999999999999999.99');change(amount(1),'9999999999999999.99');assert.equal(total.value,'9999999999999999.99');assert.equal(amount(0).validationMessage,'');
  row.querySelector('[data-add-entry="CREDIT"]').click();change(amount(1),'9999999999999999.98');change(amount(2),'0.01');assert.equal(amount(0).validationMessage,'');change(amount(2),'0.02');assert.equal(amount(0).validationMessage,'Balance the transaction');
  d.querySelector('[data-add-row]').click();const second=d.querySelectorAll('[data-voucher-row]')[1];assert.equal(second.querySelectorAll('[data-split-entry]').length,2);assert.equal(second.querySelector('input[name$=".amount"]').type,'hidden');
  assert.equal(second.querySelector('input[name$=".side"]').name,'rows[1].entries[0].side');row.querySelector('[data-remove-row]').click();assert.equal(second.querySelector('input[name$=".side"]').name,'rows[0].entries[0].side');
 }finally{dom.window.close();}
});
test('direct and configured modes keep the amount formatter and submitted total in sync',async()=>{
 const {dom,w,d,open,options}=await setup(false,url=>response(url.pathname.endsWith('/activities')?[{id:'activity-1',name:'Operations'}]:[]),true,false,false,true);try{
  w.eval(read('amount-inputs.js'));await tick();const form=d.querySelector('form'),row=d.querySelector('[data-voucher-row]'),total=form.elements['rows[0].amount'];
  assert.equal(total.type,'hidden');assert.equal(row.querySelectorAll('[data-amount-display]').length,2);
  const displays=row.querySelectorAll('[data-amount-display]');displays[0].value='1,200';displays[0].dispatchEvent(new w.Event('input',{bubbles:true}));displays[1].value='1,200';displays[1].dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(total.value,'1200.00');assert.equal(total.validity.valid,true);assert.equal(new w.FormData(form).get('rows[0].amount'),'1200.00');
  const type=d.querySelector('[data-voucher-type]');await open(type);options().find(o=>o.textContent==='Operations').click();await tick();
  assert.equal(row.querySelectorAll('[data-split-entry]').length,0);assert.equal(row.querySelector('[data-transaction-amount]').hidden,false);assert.equal(total.type,'number');assert.equal(row.querySelectorAll('[data-amount-display]').length,1);assert.equal(total.value,'1200.00');assert.equal(row.querySelector('[data-configured-transactions]').hidden,false);
  d.querySelector('[data-remove-activity]').click();await open(type);options().find(o=>o.textContent==='Manual entry').click();await tick();
  assert.equal(total.type,'hidden');assert.equal(total.required,false);assert.equal(row.querySelector('[data-transaction-amount]').hidden,true);assert.equal(row.querySelectorAll('[data-split-entry]').length,2);assert.equal(new w.FormData(form).get('rows[0].amount'),'1200.00');assert.equal(row.querySelector('[data-mapping]').disabled,true);
 }finally{dom.window.close();}
});

test('approval totals count transactions once when debit and credit entry amounts are present',async()=>{
 const {dom,w,d}=await setup(true);
 try{
  const form=d.querySelector('form'),row=d.querySelector('[data-editor-line]');row.insertAdjacentHTML('beforeend','<td><input name="rows[0].entries[0].amount" value="0.60"><input name="rows[0].entries[1].amount" value="0.40"><input name="rows[0].entries[2].amount" value="1.00"></td>');
  form.insertAdjacentHTML('beforeend','<output data-editor-total></output>');form.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(d.querySelector('[data-editor-total]').textContent,'1.00');
 }finally{dom.window.close();}
});

test('cash-family Other-purpose choices remain selectable without changing their purpose',async()=>{
 const {dom,d,open,options}=await setup(false,()=>response([{id:'family-bank',code:'111001',name:'Family bank',purpose:'OTHER',moneyEligible:true}]));
 try{await open(d.querySelector('[data-money-only]'));const option=options().find(o=>o.textContent.includes('Family bank'));assert.ok(option);option.click();assert.equal(d.querySelector('[name=moneyAccountId]').value,'family-bank');}finally{dom.window.close();}
});
for(const kind of ['RECEIPT','PAYMENT','JOURNAL'])test(`${kind} Equity activity reveals required movement fields and keeps them through row changes`,async()=>{
 const {dom,w,d,open,options}=await setup(false,()=>response([]),true,false,true);
 try{
  const form=d.querySelector('form'),type=d.querySelector('[data-voucher-type]');form.dataset.kind=kind;
  await open(type);options().find(o=>o.textContent==='Equity').click();
  assert.equal(form.elements.equity.value,'true');assert.deepEqual(new w.FormData(form).getAll('activityIds'),[]);assert.equal(form.elements.receiptPurpose.value,'OTHER');
  assert.equal(d.querySelector('[data-equity-only]').hidden,false);assert.equal(form.elements.equityChoice.required,true);assert.equal(form.elements.equityReason.disabled,false);
  assert.equal(d.querySelector('[data-manual-accounts]').hidden,false);assert.equal(d.querySelectorAll('[data-split-entry]').length,2);assert.equal(d.querySelector('[data-configured-transactions]').hidden,true);
  form.elements.equityChoice.value='3:f82c2471-0267-4cc1-ba10-730c78ef5eb2';form.elements.equityReason.value='Owner adds capital';d.querySelector('[data-add-row]').click();
  assert.equal(type.value,'EQUITY');assert.equal(new w.FormData(form).get('equityChoice'),'3:f82c2471-0267-4cc1-ba10-730c78ef5eb2');assert.equal(form.elements.equityReason.value,'Owner adds capital');
  await open(type);options().find(o=>o.textContent==='Manual entry').click();
  assert.equal(form.elements.equity.value,'false');assert.equal(d.querySelector('[data-equity-only]').hidden,true);assert.equal(form.elements.equityChoice.disabled,true);assert.equal(new w.FormData(form).has('equityReason'),false);
  await open(type);options().find(o=>o.textContent==='Equity').click();assert.equal(form.elements.equityReason.value,'Owner adds capital');assert.equal(type.value,'EQUITY');
 }finally{dom.window.close();}
});
