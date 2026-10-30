const test = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/webapp/WEB-INF/jsp/fragments/loan-product-workflow-script.jspf','utf8').replace(/^<script>\s*/, '').replace(/\s*<\/script>\s*$/, '');

function setup(initial = '') {
    const dom = new JSDOM(`<html style="overflow:clip"><body style="overflow:auto">${initial}
        <button id="opener" data-product-modal-open="create-product">Add loan product</button>
        <div class="app-modal-overlay hidden" data-product-modal="create-product"><div class="app-modal-panel"><div class="app-modal-scroll">
        <div class="app-modal-header"><h2>Create Loan Product</h2><button id="close" data-product-modal-close="create-product">Close</button></div>
        <form class="app-modal-body"><input id="name"><div class="app-modal-actions"><button type="button" data-product-modal-close="create-product">Cancel</button><button id="create" type="submit">Create</button></div></form>
        </div></div></div></body></html>`, {runScripts:'outside-only',url:'http://localhost/admin/settings-controls'});
    dom.window.HTMLElement.prototype.getClientRects = function() {return this.closest('.hidden,[hidden]') ? [] : [{}];};
    dom.window.eval(source);
    return dom;
}

test('product modal locks both scroll roots and restores original values and focus on close', () => {
    const dom=setup(), d=dom.window.document;
    d.querySelector('#opener').click();
    const modal=d.querySelector('[data-product-modal]');
    assert.equal(modal.parentNode,d.body);
    assert.equal(modal.getAttribute('aria-modal'),null);
    assert.equal(modal.querySelector('.app-modal-panel').getAttribute('aria-modal'),'true');
    assert.equal(modal.querySelector('.app-modal-panel').getAttribute('role'),'dialog');
    assert.equal(d.documentElement.style.overflow,'hidden');
    assert.equal(d.body.style.overflow,'hidden');
    d.querySelector('#close').click();
    assert.equal(d.documentElement.style.overflow,'clip');
    assert.equal(d.body.style.overflow,'auto');
    assert.equal(d.activeElement,d.querySelector('#opener'));
    assert.equal(modal.getAttribute('aria-hidden'),'true');
    dom.window.close();
});

test('product modal contains keyboard focus and Escape uses the existing close control', () => {
    const dom=setup(), w=dom.window, d=w.document;
    d.querySelector('#opener').click();
    d.querySelector('#create').focus();
    d.querySelector('#create').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));
    assert.equal(d.activeElement,d.querySelector('#close'));
    d.querySelector('#close').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Tab',shiftKey:true,bubbles:true,cancelable:true}));
    assert.equal(d.activeElement,d.querySelector('#create'));
    let clicks=0;d.querySelector('#close').addEventListener('click',()=>clicks++);
    d.querySelector('#create').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
    assert.equal(clicks,1);
    assert.equal(d.body.style.overflow,'auto');
    dom.window.close();
});

test('server-reopened product modal acquires and releases the same scroll lock', () => {
    const dom=setup('<div hidden data-open-product-modal="create-product"></div>'), d=dom.window.document;
    assert.equal(d.documentElement.style.overflow,'hidden');
    d.querySelector('#close').click();
    assert.equal(d.documentElement.style.overflow,'clip');
    dom.window.close();
});

test('closing a dialog reopened by shared navigation removes its background lock', () => {
    const dom=setup(), d=dom.window.document;
    const modal=d.querySelector('[data-product-modal]');
    modal.classList.remove('hidden');modal.classList.add('is-open');
    d.body.classList.add('overflow-hidden');
    d.querySelector('#close').click();
    assert.equal(d.body.classList.contains('overflow-hidden'),false);
    assert.equal(d.documentElement.style.overflow,'clip');
    dom.window.close();
});

test('focused product tabs retain native values, support keyboard navigation and reveal invalid sections',()=>{
    const dom=new JSDOM(`<div data-product-config-workspace><nav data-product-config-tabs role="tablist">
      <button data-product-config-nav="identity"><strong>Identity</strong></button><button data-product-config-nav="pricing"><strong>Pricing</strong></button></nav>
      <form data-product-config-form><section data-product-config-step="identity"><div data-product-config-panel><input name="productName" value="Existing product"></div></section>
      <section data-product-config-step="pricing"><div data-product-config-panel hidden><input name="annualInterestPercent" value="12"></div></section></form></div>`,{runScripts:'outside-only',url:'http://localhost/admin/settings-controls/loan-products/test/edit',pretendToBeVisual:true});
    try{
        const w=dom.window,d=w.document;w.scrollTo=()=>{};w.eval(source);
        const tabs=d.querySelectorAll('[role=tab]'),form=d.querySelector('form'),name=form.elements.productName,rate=form.elements.annualInterestPercent;
        name.value='Edited product';tabs[0].dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));
        assert.equal(tabs[1].getAttribute('aria-selected'),'true');assert.equal(d.activeElement,tabs[1]);
        assert.equal(d.querySelector('[data-product-config-step=identity]').hidden,true);assert.equal(rate.closest('section').hidden,false);
        assert.equal(form.elements.productName,name);assert.equal(name.value,'Edited product');
        tabs[1].dispatchEvent(new w.KeyboardEvent('keydown',{key:'Home',bubbles:true,cancelable:true}));
        rate.dispatchEvent(new w.Event('invalid',{bubbles:false,cancelable:true}));assert.equal(rate.closest('section').hidden,false);
        assert.equal(d.querySelector('form'),form);assert.equal(tabs[1].getAttribute('aria-controls'),rate.closest('section').id);
    }finally{dom.window.close();}
});

test('full-page product creation restores submitted fields without opening or locking a modal',()=>{
    const dom=new JSDOM(`<form class="admin-product-create"><input name="productName" value=""><input type="checkbox" name="boardReviewRequired" value="true">
      <div hidden data-loan-product-form-draft><span data-loan-product-form-value="productName">Restored business loan</span><span data-loan-product-form-value="boardReviewRequired">true</span></div></form>`,{runScripts:'outside-only',url:'http://localhost/admin/settings-controls?section=loan&task=create'});
    try{
        const w=dom.window,d=w.document;w.eval(source);
        assert.equal(d.querySelector('[name=productName]').value,'Restored business loan');assert.equal(d.querySelector('[name=boardReviewRequired]').checked,true);
        assert.equal(d.querySelector('[role=dialog]'),null);assert.equal(d.body.style.overflow,'');assert.equal(d.documentElement.style.overflow,'');
    }finally{dom.window.close();}
});

function multiplierForm(initialPercent = '300', draft = '') {
    const fragment = readFileSync('src/main/webapp/WEB-INF/jsp/fragments/loan-product-eligibility-fields.jspf', 'utf8');
    const percent = fragment.match(/<input name="maxLoanSavingsPercent"[^>]*>/)[0].replace(/\$\{[^}]*\}/g, initialPercent);
    const multiplier = fragment.match(/<input[^>]*data-savings-multiplier[^>]*>/)[0].replace(/\$\{[^}]*\}/g, '3');
    const dom = new JSDOM(`<form data-product-workflow-builder="true">${percent}
        <input type="checkbox" data-savings-limit-check checked>
        <label data-savings-ratio-field>Loan amount limit ${multiplier}</label>
        <input type="checkbox" data-active-loan-toggle><div data-active-loan-field><input data-active-loan-limit></div>
        <input type="checkbox" data-workflow-committee><div data-workflow-warnings></div><div data-workflow-preview-runtime></div>
        ${draft}</form>`, {runScripts:'outside-only', url:'http://localhost/admin/settings-controls'});
    dom.window.eval(source);
    return dom;
}

test('typed income multipliers submit arbitrary decimals and reject empty or out-of-range input', () => {
    const dom = multiplierForm(), w = dom.window, d = w.document;
    try {
        const field = d.querySelector('[data-savings-multiplier]'), percent = d.querySelector('[data-savings-percent]');
        assert.equal(field.type, 'number');
        assert.equal(field.value, '3');
        for (const [value, expectedPercent, valid] of [['3.2755','327.55',true], ['','',false], ['10.1','1010.00',false], ['-0.1','-10.00',false], ['0','0.00',true], ['10','1000.00',true]]) {
            field.value = value;
            field.dispatchEvent(new w.Event('input', {bubbles:true}));
            assert.equal(percent.value, expectedPercent);
            assert.equal(field.checkValidity(), valid);
        }
    } finally {dom.window.close();}
});

test('income multiplier retains saved and recovered values through reset, history and eligibility toggles', async () => {
    const dom = multiplierForm('300', '<div data-loan-product-form-draft hidden><span data-loan-product-form-value="maxLoanSavingsPercent">412.5</span></div>');
    const w = dom.window, d = w.document;
    try {
        const field = d.querySelector('[data-savings-multiplier]'), percent = d.querySelector('[data-savings-percent]');
        assert.equal(field.value, '4.125');
        field.value = '2.75'; field.dispatchEvent(new w.Event('input', {bubbles:true}));
        w.dispatchEvent(new w.Event('pageshow'));
        assert.equal(field.value, '2.75'); assert.equal(percent.value, '275.00');
        const toggle = d.querySelector('[data-savings-limit-check]');
        toggle.checked = false; toggle.dispatchEvent(new w.Event('change', {bubbles:true}));
        assert.equal(field.disabled, true); assert.equal(field.closest('label').hidden, true);
        toggle.checked = true; toggle.dispatchEvent(new w.Event('change', {bubbles:true}));
        assert.equal(field.disabled, false); assert.equal(field.value, '2.75');
        d.querySelector('form').reset();
        await new Promise(resolve => w.setTimeout(resolve, 0));
        assert.equal(field.value, '4.125'); assert.equal(percent.value, '412.5');
    } finally {dom.window.close();}
});
