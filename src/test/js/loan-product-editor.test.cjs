const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/webapp/WEB-INF/jsp/fragments/loan-product-editor-script.jspf','utf8').replace(/^<script>\s*/,'').replace(/\s*<\/script>\s*$/,'');
function setup(query='',error=false,guarantorDraft=''){
  const keys=['identity','eligibility','pricing','workflow','accounts','evidence'];
  const section=key=>`<section data-product-config-step="${key}"><input name="${key}" value="retained"></section>`;
  const dom=new JSDOM(`<main data-product-config-workspace><nav data-product-config-tabs>${keys.map(key=>`<button data-product-config-nav="${key}"><strong>${key}</strong></button>`).join('')}</nav><form id="product" class="accounting-create-form" data-product-config-form>${keys.filter(key=>key!=='workflow').map(section).join('')}${guarantorDraft}<div class="product-config-actionbar"><span hidden data-product-config-save-state></span><button type="button" data-product-config-previous>Previous</button><button type="button" data-product-config-next>Next</button></div></form><section data-product-config-step="workflow"><label>Number of guarantors<input form="product" name="guarantorsRequired" type="number" min="0" max="15" required value="2"></label><button type="submit" form="product">Save product</button><form data-product-approval-form><input name="group" value="CREDIT_OFFICER">${error?'<p role="alert">Choose a group</p>':''}</form></section></main>`,{runScripts:'outside-only',url:'http://localhost/admin/loan-products/1'+query});
  dom.window.scrollTo=()=>{};dom.window.requestAnimationFrame=fn=>fn();dom.window.eval(source);return dom;
}
test('product tabs follow displayed order and keep approval in its own form',()=>{
  const dom=setup(),d=dom.window.document,w=dom.window;
  try{
    const selected=()=>d.querySelector('[role="tab"][aria-selected="true"]').dataset.productConfigNav;
    d.querySelector('[data-product-config-nav="pricing"]').click();
    d.querySelector('[data-product-config-next]').click();assert.equal(selected(),'workflow');
    assert.equal(d.querySelector('.product-config-actionbar').hidden,true);
    assert.equal(d.querySelector('[data-product-approval-form]').closest('[data-product-config-form]'),null);
    d.querySelector('[data-product-config-nav="workflow"]').dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));
    assert.equal(selected(),'accounts');assert.equal(d.querySelector('.product-config-actionbar').hidden,false);
    assert.equal(d.querySelector('[name="pricing"]').value,'retained');
    d.querySelector('[name="identity"]').dispatchEvent(new w.Event('invalid'));
    assert.equal(selected(),'identity');
  }finally{w.close();}
});

test('workflow guarantor count belongs to the product save, retains its bounds and reveals errors from another tab',()=>{
  const dom=setup(),w=dom.window,d=w.document;try{
    const product=d.querySelector('[data-product-config-form]'),approval=d.querySelector('[data-product-approval-form]'),count=product.elements.guarantorsRequired;
    assert.equal(count.closest('[data-product-config-step]').dataset.productConfigStep,'workflow');
    assert.equal(new w.FormData(product).get('guarantorsRequired'),'2');assert.equal(new w.FormData(approval).has('guarantorsRequired'),false);
    count.value='3';count.dispatchEvent(new w.Event('input',{bubbles:true}));assert.equal(d.querySelector('[data-product-config-save-state]').hidden,false);
    assert.equal(new w.FormData(product).get('guarantorsRequired'),'3');
    for(const invalid of ['','-1','16']){d.querySelector('[data-product-config-nav="evidence"]').click();count.value=invalid;assert.equal(product.checkValidity(),false);assert.equal(d.querySelector('[aria-selected="true"]').dataset.productConfigNav,'workflow');}
    count.value='0';assert.equal(product.checkValidity(),true);assert.equal(d.querySelector('[data-product-config-step="workflow"] button[type="submit"]').form,product);
  }finally{w.close();}
});

test('failed product saves restore an externally associated guarantor count and reveal its workflow error',()=>{
  const dom=setup('','', '<div data-loan-product-form-draft><span data-loan-product-form-value="guarantorsRequired">4</span></div>');
  try{const d=dom.window.document,count=d.querySelector('[name="guarantorsRequired"]');assert.equal(count.value,'4');count.classList.add('field-error-input');d.querySelector('[data-product-config-form]').dispatchEvent(new dom.window.Event('submit',{cancelable:true}));count.dispatchEvent(new dom.window.Event('invalid'));assert.equal(d.querySelector('[aria-selected="true"]').dataset.productConfigNav,'workflow');}finally{dom.window.close();}
});

test('guarantor edits outside the product form keep unsaved-change protection during page initialization',async()=>{
  const dom=setup(),w=dom.window,d=w.document;try{
    d.body.insertAdjacentHTML('beforeend','<div id="accounting-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-accounting-keep>Keep editing</button><button data-accounting-discard>Discard</button></div></div></div>');
    w.eval(readFileSync('src/main/resources/static/js/accounting-form-ux.js','utf8'));
    const count=d.querySelector('[name="guarantorsRequired"]');count.value='3';count.dispatchEvent(new w.Event('input',{bubbles:true}));
    w.dispatchEvent(new w.Event('pageshow'));await new Promise(resolve=>setImmediate(resolve));
    const leave=new w.CustomEvent('workspace:before-navigate',{cancelable:true,detail:{resume(){}}});d.dispatchEvent(leave);assert.equal(leave.defaultPrevented,true);assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);assert.equal(count.value,'3');
  }finally{w.close();}
});
test('workflow return links and server errors reopen the approval form',()=>{
  for(const [query,error] of [['?tab=workflow',false],['',true]]){
    const dom=setup(query,error);
    try{assert.equal(dom.window.document.querySelector('[aria-selected="true"]').dataset.productConfigNav,'workflow');assert.equal(dom.window.document.querySelector('[name="group"]').value,'CREDIT_OFFICER');}
    finally{dom.window.close();}
  }
});

test('charge return links reopen the account mappings and keep product values',()=>{
  const dom=setup('?tab=accounts');
  try{
    assert.equal(dom.window.document.querySelector('[aria-selected="true"]').dataset.productConfigNav,'accounts');
    assert.equal(dom.window.document.querySelector('[name="pricing"]').value,'retained');
  }finally{dom.window.close();}
});
function documentForm(draft='') {
  const dom=new JSDOM(`<form><input type="number" name="guarantorsRequired" value="1"><input type="checkbox" name="applicantAttachmentRequired" value="true" data-required-attachments-toggle><p data-product-guarantor-note>Signed guarantor papers</p><div data-required-attachments-panel><button type="button" data-add-required-attachment>Add</button><div data-required-attachments-list><div data-required-attachment-row><input type="hidden" name="requiredAttachmentId" value="retained-id"><label>Name<input name="requiredAttachmentName" value="Signed papers"></label><label>MB<input name="requiredAttachmentMaxSizeMb" type="number" value="5"></label><button type="button" data-remove-required-attachment>Remove</button></div></div><p data-required-attachments-empty>Empty</p></div>${draft}</form>`,{runScripts:'outside-only',url:'http://localhost/admin'});
  dom.window.eval(source);return dom;
}
test('optional attachments do not submit hidden required names; toggling retains values and IDs',()=>{
  const dom=documentForm(),w=dom.window,d=w.document;try{
    const toggle=d.querySelector('[data-required-attachments-toggle]'),panel=d.querySelector('[data-required-attachments-panel]');
    assert.equal(panel.hidden,true);assert.equal(d.querySelector('[data-product-guarantor-note]').hidden,true);
    assert.equal(new w.FormData(d.querySelector('form')).has('requiredAttachmentName'),false);
    toggle.click();assert.equal(panel.hidden,false);assert.equal(d.querySelector('[data-product-guarantor-note]').hidden,false);
    assert.equal(new w.FormData(d.querySelector('form')).get('requiredAttachmentId'),'retained-id');
    toggle.click();toggle.click();assert.equal(d.querySelector('[name="requiredAttachmentName"]').value,'Signed papers');
    d.querySelector('[name="guarantorsRequired"]').value='0';d.querySelector('[name="guarantorsRequired"]').dispatchEvent(new w.Event('input'));assert.equal(d.querySelector('[data-product-guarantor-note]').hidden,true);
  }finally{w.close();}
});
test('document rows add and remove accessibly, stay bounded and require complete names only when enabled',()=>{
  const dom=documentForm(),w=dom.window,d=w.document;try{
    d.querySelector('[data-required-attachments-toggle]').click();const add=d.querySelector('[data-add-required-attachment]');
    add.click();assert.equal(d.activeElement.name,'requiredAttachmentName');assert.equal(d.activeElement.required,true);
    assert.equal(d.querySelectorAll('[data-required-attachment-row]').length,2);
    for(let i=0;i<12;i++)add.click();assert.equal(d.querySelectorAll('[data-required-attachment-row]').length,10);assert.equal(add.disabled,true);
    d.querySelector('[data-remove-required-attachment]').click();assert.equal(add.disabled,false);
    while(d.querySelector('[data-remove-required-attachment]'))d.querySelector('[data-remove-required-attachment]').click();
    assert.equal(d.querySelector('[data-required-attachments-empty]').hidden,false);assert.equal(d.activeElement,add);
    assert.equal(new w.FormData(d.querySelector('form')).has('requiredAttachmentName'),false);
  }finally{w.close();}
});
test('failed save restores an intentionally empty requirement list without reviving removed rows',()=>{
  const dom=documentForm('<div data-loan-product-form-draft><span data-loan-product-form-value="documentRowsSubmitted">true</span><span data-loan-product-form-value="applicantAttachmentRequired">true</span></div>');
  try{const d=dom.window.document;assert.equal(d.querySelector('[data-required-attachments-panel]').hidden,false);assert.equal(d.querySelectorAll('[data-required-attachment-row]').length,0);}finally{dom.window.close();}
});
