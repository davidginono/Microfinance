const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/console-resource-details.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
async function setup(markup,query=''){
 const dom=new JSDOM('<body data-resource-details="true"><template id="console-resource-labels" data-copy="Copy" data-copied="Copied" data-sections="Details sections"></template><main><div class="shell-content-frame">'+markup+'</div></main></body>',{url:'http://localhost/details'+query,runScripts:'dangerously',pretendToBeVisual:true});
 dom.window.fetch=()=>{throw Error('Tab switching must not fetch or navigate');};dom.window.eval(source);await settle();return {dom,w:dom.window,d:dom.window.document};
}
const sections='<section class="accounting-section"><h2>Summary</h2><dl class="erp-detail-facts"><div><dt>Loan</dt><dd>L001</dd></div></dl></section><section id="schedule" class="erp-table-wrap"><h2>Schedule</h2><input value="draft"><form method="post"><input name="_csrf" value="token"></form></section><section id="history" class="erp-table-wrap"><h2>History</h2><a href="/receipt">Receipt</a></section>';

test('loan cards copy only selected identifiers while keeping facts and resource links intact',async()=>{
 const {dom,w,d}=await setup('<div data-copy-policy="explicit"><dl class="erp-detail-facts"><div><dt>Loan ID</dt><dd data-copyable>L001</dd></div><div><dt>Principal</dt><dd>2,000,000.00</dd></div><div><dt>Due date</dt><dd>13/11/2026</dd></div><div><dt>Client</dt><dd><a href="/client/1">Client name</a></dd></div><div><dt>Status</dt><dd><span data-resource-status="SUBMITTED">Waiting for review</span></dd></div></dl></div>');
 try{
  assert.equal(d.querySelectorAll('.console-copy').length,1);assert.equal(d.querySelector('.console-copy').getAttribute('aria-label'),'Copy: Loan ID');
  assert.equal(d.querySelector('a').getAttribute('href'),'/client/1');assert.equal(d.querySelector('a').classList.contains('console-resource-link'),true);
  assert.match(d.body.textContent,/2,000,000.00/);assert.match(d.body.textContent,/13\/11\/2026/);
  w.ConsoleResourceDetails.init();assert.equal(d.querySelectorAll('.console-copy').length,1);
 }finally{dom.window.close();}
});

test('hub details keep every section visible and native forms intact despite an old tab URL',async()=>{
 const {dom,w,d}=await setup('<div class="accounting-workspace" data-resource-details-page="hub">'+sections+'</div>','?detailTab=history&historyPage=3');
 try {
  assert.equal(d.querySelector('[role=tablist]'),null);assert.equal(d.querySelector('[role=tabpanel]'),null);
  for(const section of d.querySelectorAll('.accounting-workspace > section'))assert.equal(section.hidden,false);
  assert.deepEqual([...d.querySelectorAll('.accounting-workspace > section h2')].map(h=>h.textContent),['Summary','Schedule','History']);
  const form=d.querySelector('form'),input=d.querySelector('#schedule > input');input.value='unsaved';
  w.ConsoleResourceDetails.init();input.dispatchEvent(new w.Event('invalid',{bubbles:false}));
  assert.equal(d.querySelector('form'),form);assert.equal(form.elements._csrf.value,'token');assert.equal(input.value,'unsaved');
  assert.equal(w.location.search,'?detailTab=history&historyPage=3');assert.equal(w.sessionStorage.length,0);
  assert.ok(d.querySelector('.console-copy'));assert.equal(d.querySelector('#history a').getAttribute('href'),'/receipt');
 }finally{dom.window.close();}
});

test('grouped client sections stay together without multiplying tabs and reveal nested review errors',async()=>{
 const summary=sections.slice(0,sections.indexOf('<section id="schedule"'));
 const card=title=>'<section class="erp-resource-section"><h2>'+title+'</h2><p>Recorded evidence</p></section>';
 const group=(id,title,content)=>'<div id="'+id+'" data-detail-panel><h2 class="sr-only">'+title+'</h2>'+content+'</div>';
 const markup=summary+group('client-profile','Profile',['Recorded profile','Privacy notice','Bio data','Contact details','Spouse details'].map(card).join(''))
  +group('client-business','Business',card('Business details')+card('Bank details'))+group('client-documents','Documents',card('Documents'))
  +group('client-review','Review',card('Review')+card('Review')+'<form data-detail-panel><section class="erp-resource-section"><h2>Review</h2><input name="reason" value="draft" required><input type="hidden" name="_csrf" value="token"></section></form>'+card('History'));
 const {dom,w,d}=await setup(markup,'?detailTab=client-business');
 try {
  assert.deepEqual([...d.querySelectorAll('[role=tab]')].map(tab=>tab.textContent),['Profile','Business','Documents','Review']);
  assert.equal(d.querySelector('#client-business').hidden,false);assert.equal(d.querySelector('#client-business').querySelectorAll('section').length,2);
  const input=d.querySelector('[name=reason]'),form=input.form;input.value='Unsaved review';
  d.querySelector('[aria-controls=client-review]').click();d.querySelector('[aria-controls=client-profile]').click();
  input.dispatchEvent(new w.Event('invalid',{bubbles:false}));
  assert.equal(d.querySelector('#client-review').hidden,false);assert.equal(d.querySelector('[aria-controls=client-review]').getAttribute('aria-selected'),'true');
  assert.equal(input.form,form);assert.equal(input.value,'Unsaved review');assert.equal(form.elements._csrf.value,'token');
  assert.equal(w.sessionStorage.getItem('workspace-detail-tab:v1:/details'),'client-review');
  d.querySelector('[aria-controls=client-review]').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Home',bubbles:true}));
  assert.equal(d.activeElement,d.querySelector('[aria-controls=client-profile]'));assert.equal(d.querySelector('#client-profile').querySelectorAll('section').length,5);
 } finally {dom.window.close();}
});

test('server flashbars dismiss with keyboard focus recovery without removing contextual warnings',async()=>{
 const {dom,w,d}=await setup('<div data-workspace-flashbar><div data-app-toast data-message-type="success" role="status"><p>Profile saved.</p><button type="button" data-feedback-dismiss aria-label="Funga taarifa">×</button></div></div><h1>My profile</h1><section><p data-message-type="warning">Correct your address.</p><input value="unsaved"></section>');
 try {
  w.ConsoleResourceDetails.init();w.ConsoleResourceDetails.init();
  const dismiss=d.querySelector('[data-feedback-dismiss]');dismiss.focus();dismiss.click();
  assert.equal(d.querySelector('[data-app-toast]'),null);
  assert.equal(d.activeElement,d.querySelector('h1'));
  assert.equal(d.querySelector('[data-message-type="warning"]').textContent,'Correct your address.');
  assert.equal(d.querySelector('input').value,'unsaved');
  const replacement=d.createElement('div');replacement.dataset.workspaceFlashbar='';replacement.innerHTML='<div data-app-toast data-message-type="info"><p>No changes.</p><button type="button" data-feedback-dismiss>Dismiss</button></div>';d.querySelector('main').prepend(replacement);
  await settle();await settle();replacement.querySelector('button').click();assert.equal(replacement.children.length,0);
 } finally {dom.window.close();}
});

test('shared alerts preserve native recovery actions and hidden and empty states',async()=>{
 const {dom,w,d}=await setup('<p class="aws-inline-notice aws-inline-notice--warning" hidden>Permission required</p><div class="app-loading-error" role="alert">Retry failed <button>Retry</button></div><div class="accounting-error-summary" role="alert"></div><label>Email <span class="coa-error">Invalid email</span></label>');
 try {
  const warning=d.querySelector('.aws-inline-notice'),failure=d.querySelector('.app-loading-error'),button=failure.querySelector('button');let calls=0;button.addEventListener('click',()=>calls++);
  assert.equal(warning.hidden,true);assert.equal(warning.dataset.messageType,'warning');assert.equal(warning.getAttribute('role'),'status');
  assert.equal(failure.dataset.messageType,'error');assert.equal(failure.getAttribute('role'),'alert');assert.equal(failure.querySelector('svg').getAttribute('aria-hidden'),'true');
  button.click();assert.equal(calls,1);assert.equal(d.querySelector('.accounting-error-summary').childNodes.length,0);assert.equal(d.querySelector('label .console-message'),null);
  w.ConsoleResourceDetails.init();assert.equal(failure.querySelectorAll('.console-message-icon').length,1);assert.equal(failure.querySelector('button'),button);
  failure.textContent='Still failed';await settle();await settle();assert.equal(failure.querySelectorAll('.console-message-icon').length,1);assert.match(failure.textContent,/Still failed/);
 } finally {dom.window.close();}
});

test('page notices and legacy feedback get severity icons without decorating fields or progress',async()=>{
 const {dom,w,d}=await setup('<p class="loan-success">Loan saved.</p><div class="coa-success">Account saved.</div><p class="repayment-error" role="alert">Payment failed.</p><p id="information" role="status">Read this update.</p><p data-message-type="warning" role="alert">Check the account.</p><div class="bg-emerald-50">Settings saved.</div><div class="bg-rose-50">Try again.</div><div class="bg-amber-50">Missing history.</div><p class="erp-field-error" role="alert">Amount is required.</p><span class="kyc-contact-result" role="status">Phone checked.</span><span data-batch-progress role="status">Saving 2 of 3</span><span class="app-loading-spinner" role="status">Loading</span><p class="sr-only" role="status">Row selected</p>');
 try{
  for(const [selector,kind] of [['.loan-success','success'],['.coa-success','success'],['.repayment-error','error'],['#information','info'],['[data-message-type="warning"]','warning'],['.bg-emerald-50','success'],['.bg-rose-50','error'],['.bg-amber-50','warning']]){
   const notice=d.querySelector(selector);assert.equal(notice.querySelector('.console-message-icon').dataset.kind,kind);assert.equal(notice.querySelector('svg').getAttribute('aria-hidden'),'true');
  }
  for(const selector of ['.erp-field-error','.kyc-contact-result','[data-batch-progress]','.app-loading-spinner','.sr-only'])assert.equal(d.querySelector(selector).querySelector('.console-message-icon'),null);
  w.ConsoleResourceDetails.init();assert.equal(d.querySelector('.loan-success').querySelectorAll('.console-message-icon').length,1);
 }finally{dom.window.close();}
});

test('body-mounted modal messages and in-place severity changes stay synchronized',async()=>{
 const {dom,w,d}=await setup('<p id="changing" class="loan-success">Saved.</p><div id="empty" role="status"></div>');
 try{
  const modal=d.createElement('div');modal.innerHTML='<section role="dialog"><p data-message-type="error" role="alert">Retry <button>Try again</button></p></section>';d.body.append(modal);
  await settle();await settle();
  const notice=modal.querySelector('p'),button=notice.querySelector('button');assert.equal(notice.querySelector('.console-message-icon').dataset.kind,'error');assert.equal(notice.querySelector('button'),button);
  notice.dataset.messageType='warning';await settle();await settle();assert.equal(notice.querySelector('.console-message-icon').dataset.kind,'warning');assert.equal(notice.querySelectorAll('.console-message-icon').length,1);
  const changing=d.querySelector('#changing');changing.classList.remove('loan-success');changing.classList.add('repayment-error');changing.setAttribute('role','alert');await settle();await settle();assert.equal(changing.querySelector('.console-message-icon').dataset.kind,'error');
  const empty=d.querySelector('#empty');assert.equal(empty.querySelector('svg'),null);const text=d.createTextNode('');empty.append(text);await settle();text.data='New information';await settle();await settle();assert.equal(empty.querySelector('.console-message-icon').dataset.kind,'info');assert.equal(empty.textContent,'New information');
 }finally{dom.window.close();}
});

test('resource IDs retain native same-tab navigation and only real new-tab links get an external hint',async()=>{
 const {dom,w,d}=await setup('<dl class="erp-detail-facts"><div><dt>Loan</dt><dd><a class="resource-table-id-link" href="/loan/1">L001</a></dd></div></dl><a id="policy" href="https://example.org/policy" target="_blank" rel="help">Policy</a><a id="download" href="/receipt/pdf" target="_blank" download>PDF</a>');
 const loan=d.querySelector('.resource-table-id-link'),policy=d.querySelector('#policy');
 assert.equal(loan.target,'');assert.equal(loan.querySelector('svg'),null);assert.equal(loan.getAttribute('href'),'/loan/1');
 assert.equal(policy.querySelectorAll('.console-external-icon').length,1);assert.match(policy.textContent,/opens in a new tab/);assert.equal(policy.relList.contains('help'),true);assert.equal(policy.relList.contains('noopener'),true);
 w.ConsoleResourceDetails.init();assert.equal(policy.querySelectorAll('.console-external-icon').length,1);assert.equal(policy.querySelectorAll('.sr-only').length,1);
 assert.equal(d.querySelector('#download svg'),null);dom.window.close();
});
test('tabs preserve native forms, edited values, query context and summary without a document request',async()=>{
 const {dom,w,d}=await setup(sections,'?historyPage=3');const input=d.querySelector('#schedule input'),form=d.querySelector('form');input.value='unsaved';let submits=0;form.addEventListener('submit',()=>submits++);
 const queryChanges=[];d.addEventListener('resource-table:query',()=>queryChanges.push(new URL(w.location.href).searchParams.get('detailTab')));const buttons=d.querySelectorAll('[role=tab]');assert.equal(buttons.length,2);buttons[1].click();await settle();assert.equal(d.querySelector('#history').hidden,false);assert.equal(d.querySelector('#schedule').hidden,true);buttons[0].click();
 assert.equal(d.querySelector('#schedule input'),input);assert.equal(input.value,'unsaved');assert.equal(d.querySelector('form'),form);assert.equal(submits,0);assert.deepEqual(queryChanges,['history','schedule']);assert.equal(new URL(w.location.href).searchParams.get('historyPage'),'3');assert.equal(d.querySelector('.erp-detail-facts').closest('section').hidden,false);dom.window.close();
});
test('keyboard selection, initial requested panel and invalid control reveal the correct panel',async()=>{
 const {dom,w,d}=await setup(sections,'?detailTab=history');const tabs=d.querySelectorAll('[role=tab]');assert.equal(tabs[1].getAttribute('aria-selected'),'true');tabs[1].dispatchEvent(new w.KeyboardEvent('keydown',{key:'Home',bubbles:true}));assert.equal(d.activeElement,tabs[0]);
 tabs[1].click();d.querySelector('#schedule input').dispatchEvent(new w.Event('invalid',{bubbles:false}));assert.equal(d.querySelector('#schedule').hidden,false);assert.equal(tabs[0].tabIndex,0);assert.equal(tabs[1].tabIndex,-1);dom.window.close();
});
test('prerequisite notices stay visible and the single related section gets a tab',async()=>{
 const {dom,d}=await setup('<section data-detail-persistent class="accounting-section" id="notice"><h2>Link accounts</h2><form><button>Link</button></form></section>'+sections.replace(/<section id="history"[\s\S]*/,''));
 assert.equal(d.querySelectorAll('[role=tab]').length,1);assert.equal(d.querySelector('#notice').closest('[role=tabpanel]'),null);assert.equal(d.querySelector('#notice').hidden,false);dom.window.close();
});
test('status icons follow actual state, preserve labels and refresh after replacing rows',async()=>{
 const {dom,w,d}=await setup('<section class="erp-panel"><h2>States</h2><span class="voucher-status"><span data-resource-status="DISBURSED">Imetolewa</span></span><span data-resource-status="PENDING">Pending</span><span data-resource-status="REJECTED">Rejected</span></section>');
 assert.equal(d.querySelector('[data-resource-status=DISBURSED]').dataset.statusType,'success');assert.equal(d.querySelector('[data-resource-status=DISBURSED]').textContent,'Imetolewa');assert.equal(d.querySelector('[data-resource-status=PENDING]').dataset.statusType,'pending');assert.equal(d.querySelector('[data-resource-status=REJECTED]').dataset.statusType,'error');
 const replacement=d.createElement('span');replacement.dataset.resourceStatus='REVERSED';replacement.textContent='Reversed';d.querySelector('section').append(replacement);await settle();assert.equal(replacement.dataset.statusType,'stopped');w.ConsoleResourceDetails.init();assert.equal(replacement.querySelectorAll('svg').length,1);dom.window.close();
});
test('copy has a localized accessible name, copies the value once and preserves resource links',async()=>{
 const {dom,w,d}=await setup('<section class="accounting-section"><h2>Details</h2><dl class="erp-detail-facts"><div><dt>Client</dt><dd><a href="/clients/1" target="_blank">CL001</a></dd></div><div><dt>Status</dt><dd><span data-resource-status="ACTIVE">Active</span></dd></div></dl></section>');let copied=[];Object.defineProperty(w.navigator,'clipboard',{value:{writeText:async text=>copied.push(text)}});
 const button=d.querySelector('button.console-copy');assert.equal(button.getAttribute('aria-label'),'Copy: Client');button.click();await settle();assert.deepEqual(copied,['CL001']);assert.equal(d.querySelector('#console-copy-feedback').textContent,'Copied');assert.equal(d.querySelector('a').getAttribute('href'),'/clients/1');assert.equal(d.querySelector('a').querySelectorAll('svg').length,1);assert.equal(d.querySelectorAll('.console-copy').length,1);w.ConsoleResourceDetails.init();assert.equal(d.querySelectorAll('.console-copy').length,1);dom.window.close();
});
test('detail values remove template indentation and retain intentional line breaks',async()=>{
 const {dom,d}=await setup('<dl class="erp-detail-facts"><div><dt>Address</dt><dd>\n    First line\nSecond line\n  </dd></div></dl>');
 try {
  assert.equal(d.querySelector('.console-detail-value').textContent,'First line\nSecond line');
  assert.equal(d.querySelector('.console-copy').nextElementSibling,d.querySelector('.console-detail-value'));
 } finally {dom.window.close();}
});

test('detail tabs survive a POST redirect and reload without retaining form values or leaking into another record',async()=>{
 const {dom,w,d}=await setup(sections);const scope=d.querySelector('.shell-content-frame');w.WorkspaceNavigation={onReady:callback=>callback()};
 try{
  d.querySelector('[aria-controls=history]').click();d.querySelector('#schedule form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
  assert.equal(w.sessionStorage.getItem('workspace-detail-tab:v1:/details'),'history');
  // A server redirect strips the detailTab parameter and replaces the resource markup.
  w.history.replaceState({},'', '/details');delete scope.dataset.detailsReady;scope.innerHTML=sections;w.eval(source);await settle();
  assert.equal(d.querySelector('#history').hidden,false);assert.equal(d.querySelector('[aria-controls=history]').getAttribute('aria-selected'),'true');
  assert.equal(d.querySelector('#schedule input').value,'draft');
  // An explicit destination takes priority over remembered presentation state.
  w.history.replaceState({},'', '/details?detailTab=schedule');delete scope.dataset.detailsReady;scope.innerHTML=sections;w.eval(source);await settle();assert.equal(d.querySelector('#schedule').hidden,false);
  w.history.replaceState({},'', '/another-record');delete scope.dataset.detailsReady;scope.innerHTML=sections;w.eval(source);await settle();assert.equal(d.querySelector('#schedule').hidden,false);
 }finally{dom.window.close();}
});
test('unavailable remembered tabs fall back safely and real errors reveal their own panel',async()=>{
 const {dom,w,d}=await setup(sections);const scope=d.querySelector('.shell-content-frame');w.WorkspaceNavigation={onReady:callback=>callback()};
 try{
  w.sessionStorage.setItem('workspace-detail-tab:v1:/details','removed-tab');delete scope.dataset.detailsReady;scope.innerHTML=sections;w.eval(source);await settle();assert.equal(d.querySelector('#schedule').hidden,false);
  w.sessionStorage.setItem('workspace-detail-tab:v1:/details','history');delete scope.dataset.detailsReady;scope.innerHTML=sections.replace('value="draft"','value="bad" aria-invalid="true"');w.eval(source);await settle();assert.equal(d.querySelector('#schedule').hidden,false);
 }finally{dom.window.close();}
});
