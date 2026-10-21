const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const read=name=>fs.readFileSync('src/main/resources/static/js/'+name,'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
const select=(attrs,label='Choose')=>`<select data-voucher-select ${attrs}><option value="">${label}</option></select>`;
const hidden=(name,value='')=>`<input type="hidden" name="${name}" value="${value}">`;
const account=(name,attrs,approval=false)=>`<span class="${approval?'approval-account':'voucher-account-choice'}">${hidden(name)}${approval?'<input data-account-label readonly hidden>':'<span data-account-label hidden></span>'}${select(attrs)}</span>`;
const response=data=>({ok:true,redirected:false,json:async()=>data});
async function setup(approval=false,fetcher,activityTypes=false,numbering=false){
 const row=approval?`<tr data-editor-line><td><input type="checkbox" data-line-select></td><td data-line-number>1${hidden('rows[0].transactionId')}${hidden('rows[0].templateKey')}${hidden('rows[0].component','TOTAL')}</td><td><input name="rows[0].description"></td><td>${account('rows[0].debitAccountId','data-account-pick aria-label="Debit account"',true)}</td><td><input name="rows[0].amount" value="1.00"></td></tr>`:`<fieldset data-voucher-row><legend data-row-number>1</legend>${hidden('rows[0].transactionId')}${hidden('rows[0].templateKey')}${hidden('rows[0].component','TOTAL')}${select('data-mapping data-other-only aria-label="Transaction"','Choose transaction')}<button type="button" data-manual data-other-only>Manual</button><span data-mapping-label></span><div data-loan-only>${account('rows[0].loanId','data-loan-choice="loans" data-choice-for="rows[0].loanId" aria-label="Loan"')}</div><div data-manual-accounts>${account('rows[0].creditAccountId','data-account-for="rows[0].creditAccountId" aria-label="Credit account"')}</div><input name="rows[0].description"><input name="rows[0].amount" value="1.00"><button type="button" data-remove-row>Remove</button></fieldset>`;
 const html=approval?`<form data-approval-editor data-lookup-error="Lookup failed" data-client-url="/finance/approval/id/clients" data-account-url="/finance/approval/id/accounts">${account('clientId','data-client-pick aria-label="Client"',true)}<button data-editor-submit>Submit</button><button type="button" data-line-add>Add</button><table><tbody data-editor-lines>${row}</tbody></table></form>`:`<form data-voucher-form data-base="/finance/vouchers/receipts" data-lookup-error="Lookup failed"><select data-receipt-purpose name="receiptPurpose"><option>OTHER</option><option>LOAN_REPAYMENT</option></select><input name="reference"><input name="interestAccountId">${account('clientId','data-voucher-client data-choice-for="clientId" aria-label="Client"')}${account('moneyAccountId','data-account-for="moneyAccountId" data-money-only aria-label="Money account"')}<div data-voucher-rows>${row}</div><span data-row-status></span><button type="button" data-add-row>Add</button><output data-live-total></output></form>`;
 const dom=new JSDOM(`<body data-select-search="Search options" data-select-no-matches="No matches" data-select-load-more="Load more" data-shell-select="Choose" data-shell-no-options="No options" data-shell-loading="Loading">${html}</body>`,{url:'http://localhost',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,d=w.document,calls=[];
 if(numbering){const form=d.querySelector('form');Object.assign(form.dataset,{numberLoading:'Loading voucher number',numberError:'Could not generate voucher number',numberCsrfHeader:'X-CSRF-TOKEN',numberCsrfToken:'synthetic-token'});form.insertAdjacentHTML('beforeend',hidden('requestKey','synthetic-key')+'<input name="effectiveDate" type="date" value="2026-10-20"><span data-voucher-number><span class="voucher-number-spinner"></span><span data-voucher-number-value></span></span><button type="button" data-retry-voucher-number hidden>Retry</button>');}
 if(activityTypes){const purpose=d.querySelector('[data-receipt-purpose]');purpose.replaceWith(Object.assign(d.createElement('input'),{name:'receiptPurpose',type:'hidden',value:'OTHER'}));d.querySelector('[name=receiptPurpose]').dataset.receiptPurpose='';const holder=d.createElement('div');holder.className='voucher-account-choice';holder.innerHTML=hidden('activityId')+'<span data-account-label hidden></span>'+select('data-voucher-type aria-label="Receipt type"').replace('</select>','<option value="OTHER">Manual accounts</option><option value="LOAN_REPAYMENT">Loan repayment</option></select>');d.querySelector('form').prepend(holder);}
 const clear=d.createElement('button');clear.type='button';clear.dataset.clientClear='';clear.textContent='Clear';d.querySelector('input[name=clientId]').parentElement.append(clear);
 w.HTMLElement.prototype.scrollIntoView=function(){};w.fetch=async(url,options)=>{calls.push(String(url));return fetcher?fetcher(new URL(url),options):response([{id:'client-1',name:'Synthetic client',number:'CL-001',label:'Synthetic client'}]);};
 w.eval(read('live-search.js'));w.eval(read('select-enhancer.js'));
 if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
 w.eval(read('voucher-selects.js'));w.eval(read(approval?'voucher-approval.js':'accounting-vouchers.js'));await tick();
 const open=async control=>{control.closest('.neo-select').querySelector('button').click();await tick();};
 const options=()=>[...d.querySelectorAll('.neo-select--open')].flatMap(wrapper=>{const button=wrapper.querySelector('button');return [...d.querySelectorAll('#'+button.getAttribute('aria-controls')+' .neo-select-option')];});
 return {dom,w,d,calls,open,options};
}
test('voucher numbers show a pending spinner, retain the latest date result and recover after failures',async()=>{
 const pending=[];
 const {dom,w,d}=await setup(false,(url,options)=>new Promise((resolve,reject)=>pending.push({url,options,resolve,reject})),false,true);
 try{
  const output=d.querySelector('[data-voucher-number]'),value=d.querySelector('[data-voucher-number-value]'),spinner=d.querySelector('.voucher-number-spinner'),date=d.querySelector('[name=effectiveDate]'),retry=d.querySelector('[data-retry-voucher-number]');
  assert.equal(output.getAttribute('aria-busy'),'true');assert.equal(value.classList.contains('sr-only'),true);assert.equal(spinner.hidden,false);
  assert.equal(pending[0].options.method,'POST');assert.equal(pending[0].options.headers['X-CSRF-TOKEN'],'synthetic-token');assert.equal(pending[0].options.body.get('effectiveDate'),'2026-10-20');
  date.value='2026-10-21';date.dispatchEvent(new w.Event('change'));assert.equal(pending[0].options.signal.aborted,true);
  pending[1].resolve(response({number:'RV-2026-00000002'}));await tick();pending[0].resolve(response({number:'RV-2026-00000001'}));await tick();
  assert.equal(value.textContent,'RV-2026-00000002');assert.equal(value.classList.contains('sr-only'),false);assert.equal(spinner.hidden,true);assert.equal(output.getAttribute('aria-busy'),'false');
  date.value='2026-10-19';date.dispatchEvent(new w.Event('change'));pending[2].reject(Error('Unavailable'));await tick();
  assert.equal(value.textContent,'Could not generate voucher number');assert.equal(retry.hidden,false);assert.equal(spinner.hidden,true);
  retry.click();assert.equal(retry.hidden,true);assert.equal(spinner.hidden,false);pending[3].resolve(response({number:'RV-2026-00000003'}));await tick();assert.equal(value.textContent,'RV-2026-00000003');assert.equal(spinner.hidden,true);
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
test('client changes invalidate loan choices, and new voucher rows bind their own dropdowns',async()=>{
 const {dom,w,d,open,options,calls}=await setup(false,url=>response(url.pathname.endsWith('/loans')?[{id:'loan-1',label:'Loan 1'}]:[{id:url.searchParams.get('search')?'client-2':'client-1',name:url.searchParams.get('search')?'Second client':'First client'}]));try{
  const purpose=d.querySelector('[data-receipt-purpose]');purpose.value='LOAN_REPAYMENT';purpose.dispatchEvent(new w.Event('change'));
  let loan=d.querySelector('[data-loan-choice]');assert.equal(loan.disabled,true);const client=d.querySelector('[data-voucher-client]');await open(client);options()[0].click();assert.equal(loan.disabled,false);
  await open(loan);assert.match(calls.at(-1),/clientId=client-1/);options()[0].click();assert.equal(d.querySelector('[name="rows[0].loanId"]').value,'loan-1');
  await open(client);const search=w.NeoSelect.search(client).input;search.value='Second';search.dispatchEvent(new w.Event('input',{bubbles:true}));search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));await tick();options()[0].click();assert.equal(loan.value,'');assert.equal(d.querySelector('[name="rows[0].loanId"]').value,'');
  d.querySelector('[data-add-row]').click();loan=d.querySelectorAll('[data-loan-choice]')[1];await open(loan);assert.match(calls.at(-1),/clientId=client-2/);options()[0].click();assert.equal(d.querySelector('[name="rows[1].loanId"]').value,'loan-1');
 }finally{dom.window.close();}
});
test('transaction mappings retain their exact template/component and manual mode restores account selectors',async()=>{
 const mapping={transactionId:'transaction-1',templateKey:'template-1',transactionName:'Expense',component:'PRINCIPAL',debit:{name:'Expense account'},credit:{name:'Cash'}};
 const {dom,d,open,options}=await setup(false,url=>response(url.pathname.endsWith('/transactions')?[mapping]:[{id:'account-1',name:'Cash account',purpose:'CASH'},{id:'account-2',name:'Expense',purpose:'ORDINARY'}]));try{
  await open(d.querySelector('[data-money-only]'));assert.equal(options().length,1);options()[0].click();assert.equal(d.querySelector('[name=moneyAccountId]').value,'account-1');
  await open(d.querySelector('[data-mapping]'));options()[0].click();assert.equal(d.querySelector('[name="rows[0].transactionId"]').value,'transaction-1');assert.equal(d.querySelector('[name="rows[0].templateKey"]').value,'template-1');assert.equal(d.querySelector('[name="rows[0].component"]').value,'PRINCIPAL');assert.equal(d.querySelector('[data-manual-accounts]').hidden,true);
  d.querySelector('[data-manual]').click();assert.equal(d.querySelector('[name="rows[0].templateKey"]').value,'');assert.equal(d.querySelector('[name="rows[0].component"]').value,'TOTAL');assert.equal(d.querySelector('[data-manual-accounts]').hidden,false);
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

test('configured receipt types filter transactions, reset old mappings and preserve entered amounts',async()=>{
 const mapping={transactionId:'transaction-1',templateKey:'template-1',transactionName:'Income',component:'TOTAL',debit:{name:'Cash'},credit:{name:'Income'}};
 const {dom,w,d,open,options,calls}=await setup(false,url=>response(url.pathname.endsWith('/activities')?[{id:'activity-1',name:'Operations'},{id:'activity-2',name:'Fees'}]:url.pathname.endsWith('/transactions')?[mapping]:[]),true);try{
  let submits=0;d.querySelector('form').addEventListener('submit',e=>{submits++;e.preventDefault();});
  const type=d.querySelector('[data-voucher-type]');await open(type);assert.match(calls.at(-1),/\/activities\?/);assert.deepEqual(options().map(o=>o.textContent),['Manual accounts','Loan repayment','Operations','Fees']);options().find(o=>o.textContent==='Operations').click();
  assert.equal(d.querySelector('[name=activityId]').value,'activity-1');assert.equal(d.querySelector('[name=receiptPurpose]').value,'OTHER');assert.equal(d.querySelector('[data-manual-accounts]').hidden,true);
  await open(d.querySelector('[data-mapping]'));assert.match(calls.at(-1),/activityId=activity-1/);options()[0].click();assert.equal(d.querySelector('[name="rows[0].transactionId"]').value,'transaction-1');assert.equal(d.querySelector('[data-manual]').hidden,true);
  await open(type);options().find(o=>o.textContent==='Fees').click();assert.equal(d.querySelector('[name=activityId]').value,'activity-2');assert.equal(d.querySelector('[name="rows[0].transactionId"]').value,'');assert.equal(d.querySelector('[name="rows[0].amount"]').value,'1.00');assert.equal(d.querySelector('[name="rows[0].description"]').value,'Income');
  d.querySelector('[data-add-row]').click();await open(d.querySelectorAll('[data-mapping]')[1]);assert.match(calls.at(-1),/activityId=activity-2/);
  await open(type);options().find(o=>o.textContent==='Loan repayment').click();assert.equal(d.querySelector('[name=activityId]').value,'');assert.equal(d.querySelector('[name=receiptPurpose]').value,'LOAN_REPAYMENT');assert.equal(d.querySelector('[data-mapping]').disabled,true);
  await open(type);options().find(o=>o.textContent==='Manual accounts').click();assert.equal(d.querySelector('[name=activityId]').value,'');assert.equal(d.querySelector('[data-manual-accounts]').hidden,false);assert.equal(submits,0);
 }finally{dom.window.close();}
});
