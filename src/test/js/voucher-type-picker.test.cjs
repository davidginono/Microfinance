const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const source=fs.readFileSync('src/main/resources/static/js/voucher-approval.js','utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
async function setup(){
 const dom=new JSDOM(`<form method="get" action="/finance/approval/configuration" data-voucher-type-picker data-current-type="RECEIPT">${['RECEIPT','PAYMENT','JOURNAL'].map(type=>`<label><input type="radio" name="type" value="${type}" ${type==='RECEIPT'?'checked':''}>${type}</label>`).join('')}</form><form class="accounting-create-form"><input name="minimum" value="0"></form><div id="accounting-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div>`,{runScripts:'outside-only',url:'http://localhost/finance/approval/configuration?type=RECEIPT'});
 const w=dom.window,requests=[];
 w.WorkspaceNavigation={navigate(url){const resume=()=>requests.push(url);if(w.document.dispatchEvent(new w.CustomEvent('workspace:before-navigate',{cancelable:true,detail:{resume,url}})))resume();}};
 w.eval(fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js','utf8'));w.eval(source);await tick();return {dom,w,d:w.document,requests};
}
function select(w,d,type){const input=d.querySelector(`[value="${type}"]`);input.checked=true;input.dispatchEvent(new w.Event('change',{bubbles:true}));}
test('choosing a card loads its rules through workspace navigation without a Show command',async()=>{
 const {dom,w,d,requests}=await setup();select(w,d,'PAYMENT');assert.deepEqual(requests,['http://localhost/finance/approval/configuration?type=PAYMENT']);
 assert.equal(d.querySelector('[data-voucher-type-picker] input:checked').value,'RECEIPT');select(w,d,'RECEIPT');assert.equal(requests.length,1);
 assert.equal(d.querySelector('[data-voucher-type-picker]').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true})),false);dom.window.close();
});
test('Continue editing retains the original card and unsaved rules; Discard resumes the selected destination',async()=>{
 const {dom,w,d,requests}=await setup(),amount=d.querySelector('[name=minimum]');amount.value='250000';amount.dispatchEvent(new w.Event('input',{bubbles:true}));
 select(w,d,'JOURNAL');assert.deepEqual(requests,[]);assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);d.querySelector('[data-accounting-keep]').click();
 assert.equal(d.querySelector('[data-voucher-type-picker] input:checked').value,'RECEIPT');assert.equal(amount.value,'250000');
 select(w,d,'PAYMENT');d.querySelector('[data-accounting-discard]').click();assert.deepEqual(requests,['http://localhost/finance/approval/configuration?type=PAYMENT']);dom.window.close();
});
