const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
function fixture(){const dom=new JSDOM(`<html lang="en"><meta name="app-context-path" content="/lms"><form><select name="productId"><option value="product" selected>Product</option></select><section data-charge-preview><tbody></tbody><table><tbody data-charge-rows></tbody></table><span data-charge-total></span><span data-charge-deductions></span><span data-charge-separate></span><span data-charge-net></span><p data-charge-error></p><a data-charge-policy-link hidden></a></section></form>`,{url:'http://localhost',runScripts:'outside-only'});dom.window.eval(fs.readFileSync('src/main/resources/static/js/loan-charges.js','utf8'));return dom;}
const plan={total:'30.01',deductions:'10.01',separate:'20.00',netCash:'969.99',error:'',terms:[{kind:'Insurance <unsafe>',basis:'Rate',basisValue:'1%',amount:'10.01',timing:'Deduct'}]};
test('server charge amounts preserve cents and render hostile text safely',()=>{const dom=fixture(),w=dom.window,form=w.document.querySelector('form');form.dispatchEvent(new w.CustomEvent('loan:quote',{detail:{charges:{...plan,total:'9999999999999999.99'}}}));assert.equal(w.document.querySelector('[data-charge-total]').textContent,'9,999,999,999,999,999.99');assert.equal(w.document.querySelector('[data-charge-deductions]').textContent,'10.01');assert.equal(w.document.querySelector('[data-charge-net]').textContent,'969.99');assert.equal(w.document.querySelector('[data-charge-rows]').rows.length,1);assert.equal(w.document.querySelector('unsafe'),null);assert.match(w.document.querySelector('[data-charge-policy-link]').href,/lms\/finance/);dom.window.close();});
test('pending and failed quotations clear stale amounts and policy links',()=>{const dom=fixture(),w=dom.window,form=w.document.querySelector('form');form.dispatchEvent(new w.CustomEvent('loan:quote',{detail:{charges:plan}}));form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));assert.equal(w.document.querySelector('[data-charge-rows]').rows.length,0);assert.equal(w.document.querySelector('[data-charge-policy-link]').hidden,true);form.dispatchEvent(new w.CustomEvent('loan:quote-error',{detail:'Unavailable'}));assert.equal(w.document.querySelector('[data-charge-error]').textContent,'Unavailable');assert.equal(w.document.querySelector('[data-charge-preview]').getAttribute('aria-busy'),'false');dom.window.close();});

function manualFixture(){
  const dom=new JSDOM(`<form><select name="loanMode"><option>CONFIGURED</option><option>CUSTOM</option></select><section data-charge-preview>
  <p data-manual-charge-help hidden></p><div data-configured-charge-summary><span data-charge-net></span></div>
  <details data-management-charge-inputs><summary>Custom fees</summary><input name="customApplicationCharge" data-recorded-fee="application"><input name="customProcessingCharge" data-recorded-fee="processing"><p data-configured-charge-help></p></details>
  <div data-insurance-fields hidden><input name="insurancePremium" data-recorded-fee="insurance"><input name="insuranceEvidence"></div>
  <table><tbody data-charge-rows></tbody></table><p data-charge-error></p></section></form>`,{url:'http://localhost',runScripts:'outside-only'});
  dom.window.eval(fs.readFileSync('src/main/resources/static/js/loan-charges.js','utf8'));return dom;
}
test('customised fee entry is available before a quote and offers no deduction preview',()=>{
  const dom=manualFixture(),w=dom.window,d=w.document,form=d.querySelector('form'),mode=form.elements.loanMode;
  mode.value='CUSTOM';mode.dispatchEvent(new w.Event('change',{bubbles:true}));
  assert.equal(d.querySelector('[data-management-charge-inputs]').open,true);
  assert.equal(d.querySelector('[data-management-charge-inputs] summary').hidden,true);
  assert.equal(d.querySelector('[data-insurance-fields]').hidden,false);
  for(const name of ['customApplicationCharge','customProcessingCharge','insurancePremium']){assert.equal(form.elements[name].disabled,false);assert.equal(form.elements[name].required,true);}
  assert.equal(d.querySelector('[data-configured-charge-summary]').hidden,true);
  form.dispatchEvent(new w.CustomEvent('loan:quote',{detail:{charges:{...plan,insuranceRequired:false}}}));
  assert.equal(form.elements.insurancePremium.disabled,false);
  assert.equal(d.querySelector('[data-insurance-fields]').hidden,false);
  form.elements.customApplicationCharge.value='25';form.elements.insurancePremium.value='7.65';
  mode.value='CONFIGURED';mode.dispatchEvent(new w.Event('change',{bubbles:true}));
  assert.equal(form.elements.customApplicationCharge.value,'');assert.equal(form.elements.insurancePremium.value,'');
  assert.equal(form.elements.customApplicationCharge.required,false);assert.equal(d.querySelector('[data-insurance-fields]').hidden,true);
  assert.equal(d.querySelector('[data-configured-charge-summary]').hidden,false);dom.window.close();
});
test('recorded manual fees survive quote updates and insurance evidence is required only for positive insurance',()=>{
  const dom=manualFixture(),w=dom.window,d=w.document,form=d.querySelector('form');
  form.elements.loanMode.value='CUSTOM';form.elements.loanMode.dispatchEvent(new w.Event('change',{bubbles:true}));
  form.elements.customApplicationCharge.value='3.21';form.elements.customProcessingCharge.value='6.78';form.elements.insurancePremium.value='7.65';
  form.dispatchEvent(new w.CustomEvent('loan:quote',{detail:{charges:{...plan,insuranceRequired:true}}}));
  assert.equal(form.elements.customApplicationCharge.value,'3.21');assert.equal(form.elements.insurancePremium.value,'7.65');
  assert.equal(form.elements.insuranceEvidence.required,true);
  form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));assert.equal(form.elements.customProcessingCharge.value,'6.78');
  dom.window.close();
});


test('charge requests load the table and individual amounts while preserving editable inputs',async()=>{
 const dom=manualFixture(),w=dom.window,d=w.document,form=d.querySelector('form');try{
  d.body.dataset.shellLoading='Loading';w.eval(fs.readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));
  form.elements.loanMode.value='CUSTOM';form.elements.loanMode.dispatchEvent(new w.Event('change',{bubbles:true}));form.elements.customApplicationCharge.value='12.34';
  form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));
  assert.equal(d.querySelectorAll('.app-loading-spinner').length,2);assert.ok(d.querySelector('table .app-loading-spinner'));
  assert.equal(d.querySelector('[data-charge-net] .app-loading-spinner').dataset.loadingSize,'small');
  d.querySelectorAll('.app-loading-status').forEach(status=>assert.equal(status.textContent,''));
  assert.equal(form.elements.customApplicationCharge.value,'12.34');
  form.dispatchEvent(new w.CustomEvent('loan:quote',{detail:{charges:plan}}));
  assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(d.querySelector('[data-charge-net]').textContent,'969.99');
  form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));w.dispatchEvent(new w.Event('pagehide'));
  assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(form.elements.customApplicationCharge.value,'12.34');
 }finally{dom.window.close();}
});
