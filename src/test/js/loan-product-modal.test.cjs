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
    assert.equal(modal.getAttribute('aria-modal'),'true');
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
