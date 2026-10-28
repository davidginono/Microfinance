const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const source=fs.readFileSync('src/main/resources/static/js/voucher-approval.js','utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
const settle=async()=>{for(let i=0;i<4;i++)await tick();};
const picker=type=>`<form action="/finance/approval/configuration" data-voucher-type-picker data-current-type="${type}">${['RECEIPT','PAYMENT','JOURNAL'].map(t=>`<label><input type="radio" name="type" value="${t}" ${t===type?'checked':''}>${t}</label>`).join('')}</form>`;
const rules=type=>`<form class="accounting-create-form" data-approval-rules data-voucher-form><input type="hidden" name="type" value="${type}"><input type="hidden" name="_csrf" value="token-${type}"><fieldset><div data-rule-list><section data-rule><h2>Rule <span data-rule-number>1</span><button data-rule-remove type="button">Remove rule</button></h2><input name="rules[0].minimum" value="${type==='PAYMENT'?'50000':'0'}"><div data-steps><div data-step><span data-step-number>1</span><select name="rules[0].stages[0].role"><option>MANAGER</option></select><input name="rules[0].stages[0].approvals" value="1"><button data-step-remove type="button">Remove step</button></div></div><button data-step-add type="button">Add step</button></section></div><button data-rule-add type="button">Add rule</button></fieldset><button>Save</button></form>`;
const response=type=>({ok:true,redirected:false,headers:{get:()=> 'text/html'},text:async()=>picker(type)+rules(type)});
async function setup(){
 const dom=new JSDOM(`<body data-navigation-error="Rules could not load" data-navigation-retry="Retry"><nav id="shell">Workspace</nav>${picker('RECEIPT')}${rules('RECEIPT')}<div id="accounting-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div></body>`,{runScripts:'outside-only',url:'http://localhost/finance/approval/configuration?type=RECEIPT'});
 const w=dom.window,requests=[];
 w.WorkspaceNavigation={navigate(){assert.fail('Must not reload the workspace');}};
 w.fetch=(url,options)=>{requests.push({url,options});return Promise.resolve(response(new URL(url).searchParams.get('type')));};
 w.eval(fs.readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));w.eval(fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js','utf8'));w.eval(source);await settle();return {dom,w,d:w.document,requests};
}
function select(w,d,type){const input=d.querySelector(`[data-voucher-type-picker] [value="${type}"]`);input.checked=true;input.dispatchEvent(new w.Event('change',{bubbles:true}));}
test('selection replaces only rule contents, keeps shell/cards/form nodes and refreshes CSRF and row controls',async()=>{
 const {dom,w,d,requests}=await setup(),shell=d.querySelector('#shell'),cards=d.querySelector('[data-voucher-type-picker]'),form=d.querySelector('[data-approval-rules]');
 select(w,d,'PAYMENT');await settle();assert.equal(requests.length,1);assert.equal(requests[0].options.credentials,'same-origin');
 assert.equal(d.querySelector('#shell'),shell);assert.equal(d.querySelector('[data-voucher-type-picker]'),cards);assert.equal(d.querySelector('[data-approval-rules]'),form);
 assert.equal(form.elements.type.value,'PAYMENT');assert.equal(form.elements._csrf.value,'token-PAYMENT');assert.equal(form.elements['rules[0].minimum'].value,'50000');
 assert.equal(cards.querySelector(':checked').value,'PAYMENT');assert.equal(w.location.search,'?type=PAYMENT');assert.equal(form.inert,false);
 form.querySelector('[data-step-add]').click();assert.equal(form.querySelectorAll('[data-step]').length,2);dom.window.close();
});
test('Continue editing keeps edits; Discard loads rules and the next edit is protected again',async()=>{
 const {dom,w,d,requests}=await setup();let amount=d.querySelector('[name="rules[0].minimum"]');amount.value='250000';amount.dispatchEvent(new w.Event('input',{bubbles:true}));
 select(w,d,'JOURNAL');assert.equal(requests.length,0);d.querySelector('[data-accounting-keep]').click();assert.equal(amount.value,'250000');assert.equal(d.querySelector('[data-voucher-type-picker] :checked').value,'RECEIPT');
 select(w,d,'PAYMENT');d.querySelector('[data-accounting-discard]').click();await settle();assert.equal(requests.length,1);
 amount=d.querySelector('[name="rules[0].minimum"]');amount.value='75000';amount.dispatchEvent(new w.Event('input',{bubbles:true}));select(w,d,'JOURNAL');assert.equal(requests.length,1);assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);dom.window.close();
});
test('rapid selections reject stale responses and show a local spinner while the workspace stays mounted',async()=>{
 const {dom,w,d}=await setup(),pending=[];w.fetch=(url,options)=>new Promise(resolve=>pending.push({url,options,resolve}));
 select(w,d,'PAYMENT');assert.ok(d.querySelector('[data-approval-rules] [data-ui-loading-indicator]'));assert.equal(d.querySelector('[data-approval-rules]').inert,true);
 select(w,d,'JOURNAL');assert.equal(pending[0].options.signal.aborted,true);pending[1].resolve(response('JOURNAL'));await settle();pending[0].resolve(response('PAYMENT'));await settle();
 assert.equal(d.querySelector('[data-approval-rules]').elements.type.value,'JOURNAL');assert.equal(d.querySelector('[data-ui-loading-indicator]'),null);dom.window.close();
});
test('failed or redirected loads preserve native rules and selection, with local Retry',async()=>{
 const {dom,w,d}=await setup(),amount=d.querySelector('[name="rules[0].minimum"]');w.fetch=async()=>({...response('PAYMENT'),redirected:true});select(w,d,'PAYMENT');await settle();
 assert.equal(d.querySelector('[name="rules[0].minimum"]'),amount);assert.equal(d.querySelector('[data-voucher-type-picker] :checked').value,'RECEIPT');assert.equal(w.location.search,'?type=RECEIPT');assert.ok(d.querySelector('[data-ui-loading-error]'));
 w.fetch=async()=>response('PAYMENT');d.querySelector('[data-ui-loading-error] button').click();await settle();assert.equal(d.querySelector('[data-approval-rules]').elements.type.value,'PAYMENT');dom.window.close();
});
test('returning to the current card cancels a pending request and keeps the original rule nodes',async()=>{
 const {dom,w,d}=await setup(),amount=d.querySelector('[name="rules[0].minimum"]');let complete,signal;
 w.fetch=(url,options)=>{signal=options.signal;return new Promise(resolve=>complete=resolve);};
 select(w,d,'PAYMENT');select(w,d,'RECEIPT');assert.equal(signal.aborted,true);assert.equal(d.querySelector('[name="rules[0].minimum"]'),amount);assert.equal(d.querySelector('[data-approval-rules]').inert,false);
 complete(response('PAYMENT'));await settle();assert.equal(d.querySelector('[data-approval-rules]').elements.type.value,'RECEIPT');dom.window.close();
});
test('a failed request after Discard still protects preserved edits, including when Retry is clicked',async()=>{
 const {dom,w,d,requests}=await setup(),amount=d.querySelector('[name="rules[0].minimum"]');amount.value='250000';amount.dispatchEvent(new w.Event('input',{bubbles:true}));
 w.fetch=async()=>{throw new Error('offline');};select(w,d,'PAYMENT');d.querySelector('[data-accounting-discard]').click();await settle();assert.equal(amount.value,'250000');
 d.querySelector('[data-ui-loading-error] button').click();assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);assert.equal(requests.length,0);d.querySelector('[data-accounting-keep]').click();assert.equal(amount.value,'250000');dom.window.close();
});
