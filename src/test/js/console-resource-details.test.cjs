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
