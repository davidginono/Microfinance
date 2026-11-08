const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
function fixture(mode='CONFIGURED',allowed=true){
 let card=fs.readFileSync('src/main/webapp/WEB-INF/jsp/accounting/loans/charges-form.jspf','utf8')
  .replace(/<%@[^]*?%>/g,'').replace(/<sec:authorize[^]*?\/>/g,'').replace('${canSetCharges}',String(allowed))
  .replace(/<spring:message code="([^"]+)"\s*\/>/g,(_,code)=>({'charges.title':'Charges','charges.application':'Application fee','charges.processing':'Processing fee','charges.insurance':'Insurance'}[code]||code))
  .replace(/<form:hidden path="([^"]+)"([^]*?)\/>/g,'<input type="hidden" name="$1" $2>')
  .replace(/<form:input path="([^"]+)"([^]*?)\/>/g,'<input name="$1" $2>').replace(/cssClass=/g,'class=');
 const dom=new JSDOM(`<html lang="en"><form><select name="loanMode"><option>CONFIGURED</option><option>CUSTOM</option></select>${card}<p data-charge-error hidden></p></form></html>`,{url:'http://localhost',runScripts:'outside-only'});
 dom.window.document.querySelector('[name="loanMode"]').value=mode;
 dom.window.eval(fs.readFileSync('src/main/resources/static/js/loan-charges.js','utf8'));return dom;
}
const plan={error:'',supported:true,terms:[{code:'APPLICATION',percentage:'',amount:'15000.00'},{code:'PROCESSING',percentage:'2',amount:'40000.00'},{code:'INSURANCE',percentage:'1.5',amount:'30000.00'}]};
function quote(dom,p=plan){dom.window.document.querySelector('form').dispatchEvent(new dom.window.CustomEvent('loan:quote',{detail:{charges:p}}));}
function edit(dom,name,value,type='input'){const input=dom.window.document.querySelector(`[name="${name}"]`);input.value=value;input.dispatchEvent(new dom.window.Event(type,{bubbles:true}));return input;}
test('configured card contains only named charge fields with rates and exact server amounts',()=>{
 const dom=fixture(),d=dom.window.document;quote(dom);
 assert.equal(d.querySelectorAll('[data-charge-preview] input:not([type="hidden"])').length,5);
 assert.equal(d.querySelector('[data-charge-preview]').querySelector('p,table,details,a'),null);
 assert.equal(d.querySelector('[name="customProcessingPercent"]').value,'2');assert.equal(d.querySelector('[name="insurancePremium"]').value,'30000.00');
 d.querySelectorAll('[data-charge-amount],[data-charge-percentage]').forEach(input=>{assert.equal(input.readOnly,true);assert.equal(input.required,false);});
 quote(dom,{...plan,terms:[{code:'APPLICATION',percentage:'',amount:'9999999999999999.99'}]});assert.equal(d.querySelector('[name="customApplicationCharge"]').value,'9999999999999999.99');dom.window.close();
});
test('custom percentage entry calculates the amount while preserving the edited percentage',()=>{
 const dom=fixture('CUSTOM'),d=dom.window.document;edit(dom,'customProcessingPercent','1.25');
 assert.equal(d.querySelector('[name="customProcessingBasis"]').value,'PERCENTAGE');assert.equal(d.querySelector('[name="customProcessingPercent"]').required,true);
 assert.equal(d.querySelector('[name="customProcessingCharge"]').required,false);
 quote(dom,{...plan,terms:[{code:'PROCESSING',percentage:'1.25',amount:'25000.00'}]});assert.equal(d.querySelector('[name="customProcessingPercent"]').value,'1.25');assert.equal(d.querySelector('[name="customProcessingCharge"]').value,'25000.00');dom.window.close();
});
test('custom amount entry calculates its percentage and supports changing entry direction',()=>{
 const dom=fixture('CUSTOM'),d=dom.window.document;edit(dom,'insurancePremium','7.65','change');
 quote(dom,{...plan,terms:[{code:'INSURANCE',percentage:'0.765',amount:'7.65'}]});assert.equal(d.querySelector('[name="customInsurancePercent"]').value,'0.765');assert.equal(d.querySelector('[name="insurancePremium"]').value,'7.65');
 edit(dom,'customInsurancePercent','0.5');quote(dom,{...plan,terms:[{code:'INSURANCE',percentage:'0.5',amount:'5.00'}]});assert.equal(d.querySelector('[name="insurancePremium"]').value,'5.00');dom.window.close();
});
test('mode changes clear prior charges and preserve the custom-charge permission gate',()=>{
 const dom=fixture(),d=dom.window.document;quote(dom);edit(dom,'loanMode','CUSTOM','change');assert.equal(d.querySelector('[name="customApplicationCharge"]').value,'');assert.equal(d.querySelector('[name="customApplicationCharge"]').readOnly,false);
 edit(dom,'customApplicationCharge','10');edit(dom,'loanMode','CONFIGURED','change');assert.equal(d.querySelector('[name="customApplicationCharge"]').value,'');assert.equal(d.querySelector('[name="customApplicationCharge"]').readOnly,true);dom.window.close();
 const denied=fixture('CUSTOM',false);denied.window.document.querySelectorAll('[data-charge-amount],[data-charge-percentage]').forEach(input=>assert.equal(input.readOnly,true));denied.window.close();
});
test('pending and failed quotes preserve custom source values, clear derived values, and keep feedback outside the card',()=>{
 const dom=fixture('CUSTOM'),w=dom.window,d=w.document,form=d.querySelector('form');edit(dom,'customProcessingPercent','1.25');quote(dom);form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));
 assert.equal(d.querySelector('[name="customProcessingPercent"]').value,'1.25');assert.equal(d.querySelector('[name="customProcessingCharge"]').value,'');
 form.dispatchEvent(new w.CustomEvent('loan:quote-error',{detail:'<unsafe> unavailable'}));assert.equal(d.querySelector('[data-charge-error]').textContent,'<unsafe> unavailable');assert.equal(d.querySelector('unsafe'),null);assert.equal(d.querySelector('[data-charge-preview] [role="alert"]'),null);dom.window.close();
});
test('grouped amount controls keep editable input values during pending requests and release the spinner',()=>{
 const dom=fixture('CUSTOM'),w=dom.window,d=w.document,form=d.querySelector('form');w.eval(fs.readFileSync('src/main/resources/static/js/amount-inputs.js','utf8'));d.dispatchEvent(new w.Event('DOMContentLoaded'));
 w.eval(fs.readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));
 const native=d.querySelector('[name="customApplicationCharge"]'),display=native.closest('.erp-amount-control').querySelector('[data-amount-display]');display.value='12,345.67';display.dispatchEvent(new w.Event('input',{bubbles:true}));
 form.dispatchEvent(new w.CustomEvent('loan:quote-pending'));assert.equal(native.value,'12345.67');assert.equal(display.value,'12,345.67');assert.equal(d.querySelectorAll('.app-loading-spinner').length,1);
 form.dispatchEvent(new w.Event('loan:quote-settled'));assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(native.value,'12345.67');dom.window.close();
});
