const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/receipt-batches.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const settle=async()=>{for(let n=0;n<6;n++)await tick();};
async function setup(estimate){
  const messages={checking:'Checking receipt...',client:'Client',status:'Status','state.DRAFT':'Draft',receipts:'Receipts',total:'Total',posted:'Posted',ready:'Ready',failed:'Needs attention',remaining:'Remaining'};
  const dom=new JSDOM(`<body><h1>Repayment 1</h1><main data-receipt-batch="batch" data-can-edit="true" data-today="2026-10-19">
    <section class="erp-resource-section"><h2>Batch details</h2><dl class="erp-detail-facts" data-batch-summary></dl></section><div data-batch-error hidden></div>
    <section data-resource-table><button data-batch-add></button><button data-batch-save></button><button data-batch-validate></button><button data-batch-post></button><button data-batch-stop></button><span data-save-status></span>
    <input data-default-date value="2026-10-19"><select data-default-account></select><select data-batch-filter></select>
    <section class="batch-validation"><h3>Receipt validation</h3><div data-batch-validation></div></section>
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
  const item={id:'receipt',lineNo:1,version:0,state:'FAILED',clientLabel:'Client One',labels:{loan:'1'},failureKey:'error',failureMessage:'Choose a valid payment date.',form:{clientId:'client',effectiveDate:'2026-10-19',moneyAccountId:'cash',reference:'RV-009',rows:[{loanId:'loan',amount:'500.00',description:''}]}};
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
  w.eval(source);if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));await settle();
  return {dom,w,d,async edit(value){const input=d.querySelector('input[type="number"]');input.value=value;input.dispatchEvent(new w.Event('input'));},async save(){const callback=autosave;autosave=undefined;callback();await settle();}};
}
test('batch status is framed and validation messages stay outside the status cell and scroll viewport',async()=>{
  const {dom,d}=await setup(()=>{});try{
    assert.equal(d.querySelector('[data-batch-state]'),null);
    assert.equal(d.querySelector('[data-batch-summary]').closest('.erp-resource-section').querySelector('h2').textContent,'Batch details');
    assert.equal(d.querySelector('[data-batch-summary] dd').textContent,'Draft');
    const issue=d.querySelector('.batch-issue');assert.equal(issue.textContent,'Choose a valid payment date.');assert.equal(issue.closest('td,.batch-grid-scroll'),null);
    assert.equal(issue.previousElementSibling.textContent,'Client One · RV-009');assert.equal(d.querySelector('[aria-describedby="'+issue.id+'"]').tagName,'TR');
    assert.equal(d.querySelector('.batch-row td:last-child').textContent,'state.FAILED');
  }finally{dom.window.close();}
});
test('live checking and failed estimates use the wide validation area without moving entered values',async()=>{
  let finish;const {dom,d,edit,save}=await setup(response=>new Promise(resolve=>finish=data=>resolve(response(data,false))));try{
    await edit('600');assert.equal(d.querySelector('.batch-issue').hidden,true);await save();
    assert.equal(d.querySelector('.batch-issue').textContent,'Checking receipt...');assert.equal(d.querySelector('.batch-validation-entry').dataset.state,'checking');
    finish({message:'The repayment exceeds the remaining debt.'});await settle();
    assert.equal(d.querySelector('.batch-issue').textContent,'The repayment exceeds the remaining debt.');assert.equal(d.querySelector('.batch-validation-entry').dataset.state,'error');
    assert.equal(d.querySelector('input[type="number"]').value,'600');assert.equal(d.querySelector('.batch-row td:last-child').textContent,'Draft');
  }finally{dom.window.close();}
});
test('editing while an older estimate is pending prevents its stale error returning',async()=>{
  let finish;const {dom,d,edit,save}=await setup(response=>new Promise(resolve=>finish=()=>resolve(response({message:'Old validation error'},false))));try{
    await edit('600');await save();await edit('200');finish();await settle();
    assert.notEqual(d.querySelector('.batch-issue').textContent,'Old validation error');assert.equal(d.querySelector('input[type="number"]').value,'200');
  }finally{dom.window.close();}
});
