const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/webapp/WEB-INF/jsp/fragments/loan-product-editor-script.jspf','utf8').replace(/^<script>\s*/,'').replace(/\s*<\/script>\s*$/,'');
function setup(query='',error=false){
  const keys=['identity','eligibility','pricing','workflow','accounts','evidence'];
  const section=key=>`<section data-product-config-step="${key}"><input name="${key}" value="retained"></section>`;
  const dom=new JSDOM(`<main data-product-config-workspace><nav data-product-config-tabs>${keys.map(key=>`<button data-product-config-nav="${key}"><strong>${key}</strong></button>`).join('')}</nav><form data-product-config-form>${keys.filter(key=>key!=='workflow').map(section).join('')}<div class="product-config-actionbar"><button type="button" data-product-config-previous>Previous</button><button type="button" data-product-config-next>Next</button></div></form><section data-product-config-step="workflow"><form data-product-approval-form><input name="group" value="CREDIT_OFFICER">${error?'<p role="alert">Choose a group</p>':''}</form></section></main>`,{runScripts:'outside-only',url:'http://localhost/admin/loan-products/1'+query});
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
