const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const source=fs.readFileSync('src/main/resources/static/js/amount-inputs.js','utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
async function setup(markup=''){
 const dom=new JSDOM(`<body><span id="amount-input-labels" data-invalid="Enter a valid amount."></span><form><label for="amount">Amount</label><input id="amount" name="amount" type="number" data-amount-input="true" min="0" max="9999999999999999.99" step="0.01" value="100000.50" required><input name="rate" type="number" value="12.5"><input name="count" type="number" value="3">${markup}<button>Save</button></form></body>`,{url:'http://localhost',runScripts:'outside-only'});
 dom.window.eval(source);await tick();return dom;
}
function enter(w,input,value){input.value=value;input.dispatchEvent(new w.Event('input',{bubbles:true}));}
test('grouped display preserves exact decimal transport, field identity and plain values for calculations',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,n=d.querySelector('#amount'),display=d.querySelector('[data-amount-display]'),form=n.form;
 assert.equal(display.value,'100,000.50');assert.equal(n.value,'100000.50');assert.equal(d.querySelector('label').htmlFor,display.id);
 const seen=[];form.addEventListener('input',e=>seen.push([e.target.name,e.target.value]));
 enter(w,display,'9999999999999999.99');assert.equal(display.value,'9,999,999,999,999,999.99');assert.equal(n.value,'9999999999999999.99');
 assert.deepEqual(seen,[['amount','9999999999999999.99']]);assert.equal(new w.FormData(form).get('amount'),'9999999999999999.99');assert.equal(new w.FormData(form).getAll('amount').length,1);
 assert.equal(d.querySelectorAll('[data-amount-display]').length,1);assert.equal(form.elements.rate.value,'12.5');assert.equal(form.elements.count.value,'3');dom.window.close();
});
test('typing, pasted separators, fractional zeros and the decimal-point editing state keep their value',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,n=d.querySelector('#amount'),display=d.querySelector('[data-amount-display]');
 for(const [value,formatted,raw] of [['100000','100,000','100000'],['1,234.50','1,234.50','1234.50'],['1234.','1,234.','1234'],['0.00','0.00','0.00'],['','', '']]){
  enter(w,display,value);assert.equal(display.value,formatted);assert.equal(n.value,raw);
 }
 n.value='1250000.25';assert.equal(display.value,'1,250,000.25');n.value='0';assert.equal(display.value,'0');dom.window.close();
});
test('required, min, max, step and malformed amounts block submission and focus the visible input',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,n=d.querySelector('#amount'),display=d.querySelector('[data-amount-display]');
 n.min='1';n.max='1000';await tick();
 for(const value of ['', '-1','1001','1.001','12abc','1.2.3',',']){enter(w,display,value);assert.equal(n.form.checkValidity(),false,value);}
 enter(w,display,'1000.50');assert.equal(n.validity.rangeOverflow,true);n.max='2000';assert.equal(n.form.checkValidity(),true);
 n.focus();assert.equal(d.activeElement,display);dom.window.close();
});
test('disabled, read-only, bounds, aria errors and reset remain synchronized',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,n=d.querySelector('#amount'),display=d.querySelector('[data-amount-display]');
 n.disabled=true;n.readOnly=true;n.setAttribute('aria-describedby','amount.errors');await tick();assert.equal(display.disabled,true);assert.equal(display.readOnly,true);assert.equal(display.getAttribute('aria-describedby'),'amount.errors');
 n.disabled=false;n.readOnly=false;enter(w,display,'oops');assert.equal(display.validity.customError,true);n.form.reset();await tick();assert.equal(display.value,'100,000.50');assert.equal(display.validity.valid,true);dom.window.close();
});
test('cloned and AJAX-inserted transaction rows receive one formatter and submit only their own native fields',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,form=d.querySelector('form'),clone=d.querySelector('.erp-amount-control').cloneNode(true);
 clone.querySelector('input[type=number]').name='rows[1].amount';clone.querySelector('input[type=number]').id='second';form.append(clone);
 form.insertAdjacentHTML('beforeend','<input name="minimum" type="number" data-amount-input="true" value="250000" step="0.01">');await tick();
 assert.equal(clone.querySelectorAll('[data-amount-display]').length,1);assert.equal(clone.querySelector('[data-amount-display]').value,'100,000.50');
 enter(w,clone.querySelector('[data-amount-display]'),'750000.01');assert.equal(new w.FormData(form).get('rows[1].amount'),'750000.01');
 assert.equal(d.querySelector('[name=minimum]').parentElement.querySelector('[data-amount-display]').value,'250,000');
 clone.remove();await tick();form.append(clone);await tick();assert.equal(clone.querySelectorAll('[data-amount-display]').length,1);dom.window.close();
});
test('shared field errors refer to the native amount and render after the whole amount control',async()=>{
 const dom=await setup(),w=dom.window,d=w.document,n=d.querySelector('#amount'),display=d.querySelector('[data-amount-display]');
 w.eval(fs.readFileSync('src/main/resources/static/js/form-validation.js','utf8'));await tick();
 enter(w,display,'bad');display.dispatchEvent(new w.FocusEvent('focusout',{bubbles:true}));
 const error=d.querySelector('.erp-field-error');assert.ok(error);assert.equal(error.previousElementSibling,n.parentElement);assert.equal(n.getAttribute('aria-invalid'),'true');assert.equal(display.getAttribute('aria-invalid'),'true');
 enter(w,display,'250000');assert.equal(d.querySelector('.erp-field-error'),null);assert.equal(n.getAttribute('aria-invalid'),null);dom.window.close();
});
test('formatting does not dirty a financial form; edits retain the existing discard protection',async()=>{
 const dom=new JSDOM('<body><form class="accounting-create-form"><input type="number" name="amount" data-amount-input="true" value="100000" step="0.01"></form><a href="#leave">Leave</a><div id="accounting-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-accounting-keep>Keep editing</button><button data-accounting-discard>Discard</button></div></div></div></body>',{runScripts:'outside-only',url:'http://localhost'});
 const w=dom.window,d=w.document;
 w.eval(fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js','utf8'));w.eval(source);await tick();
 const navigate=()=>d.dispatchEvent(new w.CustomEvent('workspace:before-navigate',{cancelable:true,detail:{resume(){}}}));
 assert.equal(navigate(),true);
 enter(w,d.querySelector('[data-amount-display]'),'250000');
 assert.equal(navigate(),false);
 assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);d.querySelector('[data-accounting-keep]').click();
 assert.equal(d.querySelector('[name=amount]').value,'250000');assert.equal(d.querySelector('[data-amount-display]').value,'250,000');dom.window.close();
});
