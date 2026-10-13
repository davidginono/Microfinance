const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/resources/static/js/resource-table.js', 'utf8');
const voucherSource = readFileSync('src/main/resources/static/js/accounting-vouchers.js', 'utf8');
const labels = `<template id="resource-table-labels" data-records="Records" data-search-page="Search this page" data-no-matches="No matching records." data-refresh="Refresh" data-pagination="Pagination" data-page="Page" data-previous="Previous page" data-next="Next page" data-preferences="Preferences" data-columns="Visible columns" data-compact="Compact rows" data-wrap="Wrap lines" data-select="Select" data-select-first="Select first row" data-clear-selection="Clear row selection" data-view="View" data-close="Close" data-cancel="Cancel" data-confirm="Confirm"></template>`;
const table = (key, body, extra = '') => `<section class="erp-table-wrap" data-resource-table="${key}" ${extra}><div class="app-table-titlebar"><h2>Records</h2><div class="accounting-actions"><a class="app-btn btn-primary" href="/create">Create</a></div></div><form method="get"><input name="search"></form><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th>Reference</th><th>Amount</th><th>Status</th></tr></thead><tbody>${body}</tbody></table></div></section>`;
const row = '<tr><td>V001</td><td>100.00</td><td>Posted</td></tr>';
async function setup(html, integration = '') {
    const dom = new JSDOM(labels + html, {url:'http://localhost/finance/test', runScripts:'outside-only', pretendToBeVisual:true});
    const {window} = dom;
    window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new window.Event('close')); };
    if (integration) window.eval(integration);
    window.eval(source);
    if (window.document.readyState !== 'complete') await new Promise(resolve => window.addEventListener('load', resolve, {once:true}));
    return {dom, window, document:window.document};
}
test('pager keeps scoped URLs and tools outside the horizontal viewport', async () => {
    const {dom, document:d} = await setup(table('receipts', row) + '<div data-table-pagination data-page="2" data-table-for="receipts"><a data-page-direction="previous" href="?page=1&search=V001">Previous</a><a data-page-direction="next" href="?page=3&search=V001">Next</a></div>');
    assert.equal(d.querySelector('.resource-table-page-number').textContent, '3');
    assert.equal(d.querySelector('[aria-label="Next page"]').getAttribute('href'), '?page=3&search=V001');
    assert.equal(d.querySelector('.erp-table-scroll .resource-table-tools'), null);
    assert.equal(d.querySelector('.resource-table-tools form input').name, 'search');
    assert.equal(d.querySelector('[data-table-pagination]'), null);
    assert.equal(d.querySelector('.resource-table-search'), null);
    dom.window.close();
});

test('tables without filters get bounded search, no-match recovery and dynamic-row filtering', async () => {
    const markup=table('search',row+'<tr><td>V002</td><td>200.00</td><td>Reversed</td></tr>').replace('<form method="get"><input name="search"></form>','');
    const {dom, window:w, document:d}=await setup(markup);
    const search=d.querySelector('.resource-table-search input');
    assert.equal(search.placeholder,'Search this page');
    assert.equal(search.closest('.erp-table-scroll'),null);
    search.value='reVERsed'; search.dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('tbody tr').hidden,true);
    assert.equal(d.querySelector('tbody tr:nth-child(2)').hidden,false);
    search.value='missing'; search.dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);
    assert.equal(d.querySelector('.resource-table-no-matches td').colSpan,3);
    d.querySelector('tbody').insertAdjacentHTML('beforeend','<tr><td>Missing</td><td>300.00</td><td>Posted</td></tr>');
    await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(d.querySelector('.resource-table-no-matches').hidden,true);
    assert.equal(d.querySelector('tbody tr:last-child').hidden,false);
    search.value=''; search.dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelectorAll('tbody tr:not([hidden])').length,3);
    dom.window.close();
});

test('fallback search clears record commands and preserves empty states and export forms', async () => {
    const markup=table('legacySearch','<tr><td>A001</td><td>100</td><td><a class="app-btn" href="/records/1">View</a></td></tr>').replace('<form method="get"><input name="search"></form>','<form method="post" id="export"><input type="hidden" name="_csrf" value="token"></form>').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom, window:w, document:d}=await setup(markup);
    d.querySelector('tbody input').click();
    const search=d.querySelector('.resource-table-search input'); search.value='none'; search.dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('.resource-table-record-actions a'),null);
    assert.equal(d.querySelector('tbody input').checked,false);
    assert.equal(d.querySelector('thead input').disabled,true);
    assert.equal(d.querySelector('#export').parentElement.dataset.resourceTable,'legacySearch');
    dom.window.close();
    const empty=await setup(table('emptySearch','<tr><td colspan="3">No posting template saved</td></tr>').replace('<form method="get"><input name="search"></form>',''));
    empty.document.querySelector('input[type="search"]').value='none';
    empty.document.querySelector('input[type="search"]').dispatchEvent(new empty.window.Event('input',{bubbles:true}));
    assert.equal(empty.document.querySelector('tbody tr').hidden,false);
    assert.equal(empty.document.querySelector('.resource-table-no-matches'),null);
    empty.dom.window.close();
});

test('search clears financial statement selection through its existing controller', async () => {
    const markup=`<div data-statement-register>${table('statements','<tr data-statement-id="1"><td><input type="checkbox"></td><td>2026-10-01</td><td>Finalized</td></tr>','data-table-selection="single"').replace('<form method="get"><input name="search"></form>','<form id="statement-export" method="post"></form>')}<button data-statement-view disabled>View</button><button data-statement-export disabled>PDF</button><span data-statement-selection data-empty="" data-selected="Selected"></span></div>`;
    const {dom, window:w, document:d}=await setup(markup,readFileSync('src/main/resources/static/js/financial-statements.js','utf8'));
    d.querySelector('tbody input').click();
    assert.equal(d.querySelector('[data-statement-view]').disabled,false);
    const search=d.querySelector('.resource-table-search input'); search.value='missing'; search.dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('[data-statement-view]').disabled,true);
    assert.equal(d.querySelector('[data-statement-export]').disabled,true);
    assert.equal(d.querySelector('#statement-export').hasAttribute('action'),false);
    dom.window.close();
});
test('a shared server pager supplies every associated table without losing a link', async () => {
    const {dom, document:d} = await setup(table('balances',row) + table('matches',row) + '<div data-table-pagination data-page="4" data-table-for="balances matches"><a data-page-direction="previous" href="?page=3&asOf=2026-10-13">Previous</a></div>');
    assert.equal(d.querySelectorAll('a.resource-table-page').length,2);
    assert.deepEqual([...d.querySelectorAll('.resource-table-page-number')].map(x=>x.textContent), ['5','5']);
    assert.equal(d.querySelectorAll('button.resource-table-page:disabled').length,2);
    dom.window.close();
});
test('empty result shows page one, disabled arrows and a usable gear', async () => {
    const {dom, document:d} = await setup(table('empty','<tr><td colspan="3">No records</td></tr>'));
    assert.equal(d.querySelector('.resource-table-page-number').textContent,'1');
    assert.equal(d.querySelectorAll('.resource-table-page:disabled').length,2);
    assert.equal(d.querySelector('[aria-label="Preferences"]').disabled,false);
    dom.window.close();
});
test('legacy commands retain original form, CSRF and exact selected-record target', async () => {
    const markup = table('legacy','<tr><td>A001</td><td>100</td><td><form method="post" action="/records/1/reverse"><input type="hidden" name="_csrf" value="test-token"><input name="reason"><button>Reverse</button></form></td></tr><tr><td>A002</td><td>200</td><td><a class="app-btn" href="/records/2">View</a></td></tr>').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom, window:w, document:d} = await setup(markup);
    assert.equal(d.querySelector('[data-table-actions-header]'),null);
    d.querySelector('tbody input[type="checkbox"]').click();
    const form = d.querySelector('.resource-table-record-actions form');
    assert.equal(form.getAttribute('action'),'/records/1/reverse');
    assert.equal(form.querySelector('[name="_csrf"]').value,'test-token');
    form.querySelector('[name="reason"]').value='Keep draft';
    d.querySelectorAll('tbody input[type="checkbox"]')[1].click();
    assert.equal(d.querySelector('.resource-table-record-actions a').getAttribute('href'),'/records/2');
    assert.equal(d.querySelectorAll('tbody input:checked').length,1);
    d.querySelector('tbody input[type="checkbox"]').click();
    assert.equal(d.querySelector('.resource-table-record-actions form'),form);
    assert.equal(form.querySelector('[name="reason"]').value,'Keep draft');
    d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelectorAll('tbody input:checked').length,0);
    assert.equal(d.querySelector('.resource-table-record-actions form'),null);
    assert.ok([...d.querySelectorAll('.resource-table-record-actions button')].every(x=>x.disabled));
    dom.window.close();
});
test('header selection cooperates with existing voucher command rules', async () => {
    const markup = `<div data-voucher-register data-base="/vouchers">${table('vouchers','<tr data-voucher-id="1"><td><input type="checkbox"></td><td><a>V001</a></td><td>100</td></tr>', 'data-table-selection="single"')}<button data-selection-action="details" disabled>View</button><span data-selection-status data-empty="" data-selected="Selected"></span></div>`;
    const {dom, window:w, document:d} = await setup(markup,voucherSource);
    d.querySelector('thead input').click();
    assert.equal(d.querySelector('[data-selection-action]').disabled,false);
    assert.equal(d.querySelectorAll('tbody input:checked').length,1);
    d.querySelector('thead input').click();
    assert.equal(d.querySelector('[data-selection-action]').disabled,true);
    d.querySelector('thead input').click();
    w.dispatchEvent(new w.Event('pageshow'));
    assert.equal(d.querySelector('[data-selection-action]').disabled,true);
    dom.window.close();
});
test('preferences cancel does not change columns; confirm persists display settings', async () => {
    const {dom, window:w, document:d} = await setup(table('prefs',row));
    const gear=d.querySelector('[aria-label="Preferences"]'); gear.click();
    assert.equal(d.querySelector('dialog').parentElement,d.body);
    assert.equal(d.documentElement.style.overflow,'hidden');
    d.querySelectorAll('dialog fieldset input')[1].click();
    [...d.querySelectorAll('dialog button')].find(x=>x.textContent==='Cancel').click();
    assert.equal(d.querySelector('thead th:nth-child(2)').hidden,false);
    assert.equal(d.documentElement.style.overflow,'');
    assert.equal(d.activeElement,gear);
    gear.click();
    assert.equal(d.querySelector('dialog fieldset input').disabled,true);
    d.querySelectorAll('dialog fieldset input')[1].click();
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(d.querySelector('thead th:nth-child(2)').hidden,true);
    assert.equal(d.querySelector('tbody td:nth-child(2)').hidden,true);
    assert.deepEqual(JSON.parse(w.localStorage.getItem('resource-table:v1:prefs')).hidden,[1]);
    d.querySelector('tbody').insertAdjacentHTML('beforeend',row);
    await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(d.querySelector('tbody tr:last-child td:nth-child(2)').hidden,true);
    dom.window.close();
});
test('unmarked tables are untouched', async () => {
    const {dom, document:d} = await setup(table('other',row).replace('data-resource-table="other"',''));
    assert.equal(d.querySelector('.resource-table-tools'),null);
    dom.window.close();
});

test('preferences preserve editable cells and never clear form checkbox values', async () => {
    const {dom, window:w, document:d} = await setup(table('editor',row));
    d.querySelector('[aria-label="Preferences"]').click();
    d.querySelectorAll('dialog fieldset input')[1].click();
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(d.querySelector('tbody td:nth-child(2)').hidden,true);
    d.querySelector('tbody').innerHTML='<tr><td><input type="checkbox" name="include" checked></td><td><input name="amount" value="100"></td><td>Draft</td></tr>';
    await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(d.querySelector('tbody td:nth-child(2)').hidden,false);
    d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('[name="include"]').checked,true);
    d.querySelector('[aria-label="Preferences"]').click();
    const amountChoice=d.querySelectorAll('dialog fieldset input')[1];
    assert.equal(amountChoice.checked,true);
    assert.equal(amountChoice.disabled,true);
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(d.querySelector('[name="include"]').checked,true);
    dom.window.close();
});
