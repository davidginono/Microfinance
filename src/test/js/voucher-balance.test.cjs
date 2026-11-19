const {test}=require('node:test'),assert=require('node:assert/strict'),{readFileSync}=require('node:fs'),{JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/voucher-balance.js','utf8');
const row=(n,debit,credit,approval=false)=>`<fieldset ${approval?'data-editor-line':'data-voucher-row'}><legend>Transaction ${n+1}</legend><input name="rows[${n}].amount" type="${approval?'number':'hidden'}" value="${debit}"><label>Debit<input name="rows[${n}].entries[0].side" type="hidden" value="DEBIT"><input name="rows[${n}].entries[0].amount" value="${debit}" aria-describedby="debit-hint"></label><label>Credit<input name="rows[${n}].entries[1].side" type="hidden" value="CREDIT"><input name="rows[${n}].entries[1].amount" value="${credit}"></label><output data-debit-total></output><output data-credit-total></output><p data-balance-error hidden>Match both totals to the transaction amount.</p></fieldset>`;
function setup(approval=false,html=row(0,'100.00','99.99',approval),kind='RECEIPT'){
 const dom=new JSDOM(`<form id="voucher" data-kind="${kind}" data-balance-error="Match both totals to the transaction amount.">${html}<div data-voucher-balance-error role="alert" hidden>Balance each transaction before posting.</div><button id="post" ${approval?'data-form-command="SUBMIT"':'name="action" value="post"'}>Post</button><button id="draft" formnovalidate>Save draft</button></form>`,{runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,form=d.querySelector('form');w.HTMLElement.prototype.scrollIntoView=function(){};w.eval(source);w.VoucherBalance.bind(form);
 form.addEventListener('input',()=>w.VoucherBalance.refresh(form));form.addEventListener('change',()=>w.VoucherBalance.refresh(form));
 const change=(name,value)=>{form.elements.namedItem(name).value=value;form.elements.namedItem(name).dispatchEvent(new w.Event('input',{bubbles:true}));};
 return {dom,w,d,form,change};
}
for(const approval of [false,true])for(const kind of ['RECEIPT','PAYMENT','JOURNAL'])test(`${kind} ${approval?'batch editor':'creation'} shows the balance error and only allows posting after correction`,()=>{
 const {dom,w,d,form,change}=setup(approval,undefined,kind);
 try{
  const debit=form.elements['rows[0].entries[0].amount'],post=d.querySelector('#post'),draft=d.querySelector('#draft');let submits=0;
  form.addEventListener('submit',event=>{event.preventDefault();submits++;});
  assert.equal(post.disabled,true);assert.equal(d.querySelector('p[data-balance-error]').hidden,false);assert.equal(d.querySelector('[data-voucher-balance-error]').hidden,false);
  assert.equal(d.querySelector('[data-debit-total]').value,'100.00');assert.equal(d.querySelector('[data-credit-total]').value,'99.99');
  assert.match(debit.getAttribute('aria-describedby'),/debit-hint voucher-balance-0/);
  const blocked=new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:post});form.dispatchEvent(blocked);
  assert.equal(blocked.defaultPrevented,true);assert.equal(submits,0);assert.equal(d.activeElement,debit);
  form.dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:draft}));assert.equal(submits,1);assert.equal(debit.value,'100.00');
  change('rows[0].entries[1].amount','100.00');assert.equal(post.disabled,false);assert.equal(debit.validationMessage,'');
  assert.equal(d.querySelector('p[data-balance-error]').hidden,true);assert.equal(d.querySelector('[data-voucher-balance-error]').hidden,true);assert.equal(debit.getAttribute('aria-describedby'),'debit-hint');
  post.click();assert.equal(submits,2);
 }finally{dom.window.close();}
});
test('offsetting differences in different transactions cannot unlock posting',()=>{
 const {dom,w,d,form,change}=setup(false,row(0,'100.00','99.99')+row(1,'99.99','100.00'));
 try{
  assert.equal(d.querySelector('#post').disabled,true);assert.equal([...d.querySelectorAll('p[data-balance-error]')].filter(e=>!e.hidden).length,2);
  change('rows[0].entries[1].amount','100.00');assert.equal(d.querySelector('#post').disabled,true);
  change('rows[1].entries[0].amount','100.00');assert.equal(d.querySelector('#post').disabled,false);
  assert.equal(form.elements['rows[1].amount'].value,'100.00');
 }finally{dom.window.close();}
});
test('approval editing also requires both totals to match the transaction amount',()=>{
 const {dom,w,d,change}=setup(true,row(0,'100.00','100.00',true));
 try{change('rows[0].amount','99.99');assert.equal(d.querySelector('#post').disabled,true);assert.equal(d.querySelector('p[data-balance-error]').hidden,false);change('rows[0].amount','100.00');assert.equal(d.querySelector('#post').disabled,false);}finally{dom.window.close();}
});
test('exact large amounts and split decimals retain every cent',()=>{
 const {dom,w,d,form,change}=setup(false,row(0,'9999999999999999.99','9999999999999999.98'));
 try{
  assert.equal(d.querySelector('#post').disabled,true);change('rows[0].entries[1].amount','9999999999999999.99');assert.equal(d.querySelector('#post').disabled,false);
  assert.equal(form.elements['rows[0].amount'].value,'9999999999999999.99');
  form.querySelector('fieldset').insertAdjacentHTML('beforeend','<input name="rows[0].entries[2].side" type="hidden" value="DEBIT"><input name="rows[0].entries[2].amount" value="0.20">');
  change('rows[0].entries[0].amount','0.10');change('rows[0].entries[1].amount','0.30');assert.equal(d.querySelector('#post').disabled,false);assert.equal(form.elements['rows[0].amount'].value,'0.30');
 }finally{dom.window.close();}
});
test('pending actions and permission-disabled controls stay disabled after correction',()=>{
 const {dom,w,d,form,change}=setup();
 try{
  form.setAttribute('aria-busy','true');change('rows[0].entries[1].amount','100.00');assert.equal(d.querySelector('#post').disabled,true);
  form.removeAttribute('aria-busy');w.VoucherBalance.refresh(form);assert.equal(d.querySelector('#post').disabled,false);
  d.querySelector('#post').disabled=true;change('rows[0].entries[1].amount','99.00');change('rows[0].entries[1].amount','100.00');assert.equal(d.querySelector('#post').disabled,true);
 }finally{dom.window.close();}
});
test('untouched blank entries keep ordinary required-field validation without a premature balance warning',()=>{
 const {dom,d,form}=setup(false,row(0,'',''));
 try{assert.equal(d.querySelector('p[data-balance-error]').hidden,true);assert.equal(d.querySelector('[data-voucher-balance-error]').hidden,true);assert.equal(form.elements['rows[0].entries[0].amount'].validationMessage,'');}finally{dom.window.close();}
});
test('workspace reinitialization retains error ownership and still clears the native posting restriction',()=>{
 const {dom,w,d,form,change}=setup();
 try{w.eval(source);w.VoucherBalance.bind(form);change('rows[0].entries[1].amount','100.00');assert.equal(d.querySelector('#post').disabled,false);assert.equal(form.elements['rows[0].entries[0].amount'].validationMessage,'');}finally{dom.window.close();}
});
