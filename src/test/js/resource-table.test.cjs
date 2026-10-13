const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/resources/static/js/resource-table.js', 'utf8');
const voucherSource = readFileSync('src/main/resources/static/js/accounting-vouchers.js', 'utf8');
const labels = `<template id="resource-table-labels" data-records="Records" data-actions="Actions" data-page-size="Page size" data-striped="Striped rows" data-sticky="Sticky first columns" data-none="None" data-first-column="First column" data-first-two-columns="First two columns" data-reorder="Move column" data-details="Details" data-show-details="Show details" data-panel-position="Panel position" data-bottom="Bottom" data-side="Side" data-resize="Resize details panel" data-select-record="Select a record" data-search-page="Search this page" data-no-matches="No matching records." data-refresh="Refresh" data-pagination="Pagination" data-page="Page" data-previous="Previous page" data-next="Next page" data-preferences="Preferences" data-columns="Visible columns" data-compact="Compact rows" data-wrap="Wrap lines" data-select="Select" data-select-first="Select first row" data-clear-selection="Clear row selection" data-view="View" data-close="Close" data-cancel="Cancel" data-confirm="Confirm"></template>`;
const table = (key, body, extra = '') => `<section class="erp-table-wrap" data-resource-table="${key}" ${extra}><div class="app-table-titlebar"><h2>Records</h2><div class="accounting-actions"><a class="app-btn btn-primary" href="/create">Create</a></div></div><form method="get"><input name="search"></form><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th>Reference</th><th>Amount</th><th>Status</th></tr></thead><tbody>${body}</tbody></table></div></section>`;
const row = '<tr><td>V001</td><td>100.00</td><td>Posted</td></tr>';
async function setup(html, integration = '') {
    const dom = new JSDOM(labels + html, {url:'http://localhost/finance/test', runScripts:'outside-only', pretendToBeVisual:true});
    const {window} = dom;
    window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new window.Event('close')); };
    if (integration) window.eval(integration);
    window.eval(readFileSync('src/main/resources/static/js/resource-split-view.js', 'utf8'));
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
    d.querySelectorAll('dialog .resource-table-column-options input')[1].click();
    [...d.querySelectorAll('dialog button')].find(x=>x.textContent==='Cancel').click();
    assert.equal(d.querySelector('thead th:nth-child(2)').hidden,false);
    assert.equal(d.documentElement.style.overflow,'');
    assert.equal(d.activeElement,gear);
    gear.click();
    assert.equal(d.querySelector('dialog .resource-table-column-options input').disabled,true);
    d.querySelectorAll('dialog .resource-table-column-options input')[1].click();
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

test('preferences reorder stable columns, stripe rows, pin columns and preserve late rows', async () => {
    const {dom, window:w, document:d}=await setup(table('ordering',row));
    d.querySelector('[aria-label="Preferences"]').click();
    const amount=d.querySelector('[data-column="1"] button');
    amount.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
    const option=text=>[...d.querySelectorAll('.resource-table-display-options label')].find(n=>n.textContent===text).querySelector('input');
    option('Striped rows').click();option('First two columns').click();option('Wrap lines').click();
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{cancelable:true}));
    assert.deepEqual([...d.querySelectorAll('thead th')].map(n=>n.textContent),['Reference','Status','Amount']);
    assert.deepEqual([...d.querySelectorAll('tbody td')].map(n=>n.textContent),['V001','Posted','100.00']);
    assert.equal(d.querySelectorAll('thead .resource-table-sticky').length,2);
    assert.equal(d.querySelector('[data-resource-table]').classList.contains('resource-table-striped'),true);
    assert.equal(d.querySelector('[data-resource-table]').classList.contains('resource-table-wrap-lines'),true);
    d.querySelector('tbody').insertAdjacentHTML('beforeend',row);
    await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.deepEqual([...d.querySelector('tbody tr:last-child').cells].map(n=>n.textContent),['V001','Posted','100.00']);
    assert.deepEqual(JSON.parse(w.localStorage.getItem('resource-table:v1:ordering')).order,[0,2,1]);
    dom.window.close();
});

test('server page size is available only on supported tables and survives filters and paging', async () => {
    const {dom,document:d}=await setup(table('sized',row,'data-table-page-size="30"')+'<div data-table-pagination data-page="0" data-table-for="sized"><a data-page-direction="next" href="?page=1&search=test">Next</a></div>');
    assert.equal(d.querySelector('.resource-table-filters input[name=size]').value,'30');
    const url=new URL(d.querySelector('[aria-label="Next page"]').href);
    assert.equal(url.searchParams.get('size'),'30');assert.equal(url.searchParams.get('search'),'test');
    d.querySelector('[aria-label="Preferences"]').click();
    assert.deepEqual([...d.querySelectorAll('input[name=pageSize]')].map(n=>n.value),['10','30','50']);
    assert.equal(d.querySelector('input[name=pageSize]:checked').value,'30');
    dom.window.close();
});

test('grouped financial totals retain their columns and heading order', async () => {
    const {dom,document:d}=await setup(table('totals',row+'<tr><td colspan="2">Total</td><td>100.00</td></tr>'));
    d.querySelector('[aria-label="Preferences"]').click();
    assert.equal([...d.querySelectorAll('.resource-table-column-options input')].every(input=>input.disabled),true);
    assert.equal([...d.querySelectorAll('.resource-table-drag')].every(button=>button.disabled),true);
    assert.equal(d.querySelectorAll('input[name=pageSize]').length,0);
    dom.window.close();
});

test('split details follow selection, stay dismissed, clear stale data and support resizing', async () => {
    const markup=table('split','<tr><td>A001</td><td>100</td><td><a class="app-btn" href="/record/1">View</a></td></tr>','data-resource-split').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom,window:w,document:d}=await setup(markup);
    const panel=d.querySelector('.resource-split-panel');assert.equal(panel.hidden,true);
    d.querySelector('tbody input').click();await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(panel.hidden,false);assert.equal(panel.querySelector('h2').textContent,'A001');
    assert.equal(panel.querySelectorAll('dd').length,2);assert.equal(panel.querySelector('.resource-split-content a'),null);
    assert.equal(d.querySelector('.resource-table-record-actions a').getAttribute('href'),'/record/1');
    panel.querySelector('[aria-label="Close"]').click();
    d.querySelector('tbody input').click();d.querySelector('tbody input').click();
    await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(panel.hidden,true);
    d.querySelector('[aria-label="Show details"]').click();assert.equal(panel.hidden,false);
    const resize=panel.querySelector('[role=separator]');resize.dispatchEvent(new w.KeyboardEvent('keydown',{key:'End',bubbles:true}));assert.equal(resize.getAttribute('aria-valuenow'),resize.getAttribute('aria-valuemax'));
    const pos=panel.querySelector('select');pos.value='side';pos.dispatchEvent(new w.Event('change'));assert.equal(d.querySelector('.resource-split-layout').classList.contains('resource-split-side'),true);
    d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));
    await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(panel.querySelectorAll('dd').length,0);assert.equal(panel.querySelector('.resource-split-content').textContent,'Select a record');
    dom.window.close();
});

test('preferences preserve editable cells and never clear form checkbox values', async () => {
    const {dom, window:w, document:d} = await setup(table('editor',row));
    d.querySelector('[aria-label="Preferences"]').click();
    d.querySelectorAll('dialog .resource-table-column-options input')[1].click();
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(d.querySelector('tbody td:nth-child(2)').hidden,true);
    d.querySelector('tbody').innerHTML='<tr><td><input type="checkbox" name="include" checked></td><td><input name="amount" value="100"></td><td>Draft</td></tr>';
    await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(d.querySelector('tbody td:nth-child(2)').hidden,false);
    d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));
    assert.equal(d.querySelector('[name="include"]').checked,true);
    d.querySelector('[aria-label="Preferences"]').click();
    const amountChoice=d.querySelectorAll('dialog .resource-table-column-options input')[1];
    assert.equal(amountChoice.checked,true);
    assert.equal(amountChoice.disabled,true);
    d.querySelector('dialog form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(d.querySelector('[name="include"]').checked,true);
    dom.window.close();
});

test('dragging the bottom divider exchanges table space and stops at pointer release or cancellation', async () => {
    const markup=table('drag','<tr><td>A001</td><td>100</td><td><a class="app-btn" href="/record/1">View</a></td></tr>','data-resource-split').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom,window:w,document:d}=await setup(markup);
    d.querySelector('tbody input').click();await new Promise(resolve=>w.setTimeout(resolve,0));
    const layout=d.querySelector('.resource-split-layout'), scroller=layout.querySelector('.erp-table-scroll'), handle=d.querySelector('[role=separator]');
    const pointer=(target,type,y,id=1)=>{const event=new w.Event(type,{bubbles:true,cancelable:true});Object.assign(event,{button:0,pointerId:id,clientY:y,clientX:500});target.dispatchEvent(event);};
    const initialHeight=parseFloat(w.getComputedStyle(scroller).height), total=initialHeight+320;
    pointer(handle,'pointerdown',400);pointer(w,'pointermove',320);
    assert.equal(Number(handle.getAttribute('aria-valuenow')),400);
    assert.ok(Math.abs(parseFloat(w.getComputedStyle(scroller).height)-(initialHeight-80))<.01);
    assert.ok(Math.abs(parseFloat(scroller.style.height)+Number(handle.getAttribute('aria-valuenow'))-total)<.01);
    pointer(w,'pointerup',320);pointer(w,'pointermove',250);
    assert.equal(Number(handle.getAttribute('aria-valuenow')),400);
    assert.equal(layout.classList.contains('resource-split-dragging'),false);
    assert.equal(JSON.parse(w.localStorage.getItem('resource-split:v1:drag')).size,400);
    pointer(handle,'pointerdown',320);pointer(w,'pointermove',370);
    assert.equal(Number(handle.getAttribute('aria-valuenow')),350);
    pointer(w,'pointercancel',370);pointer(w,'pointermove',420);
    assert.equal(Number(handle.getAttribute('aria-valuenow')),350);
    d.querySelector('.resource-split-panel [aria-label="Close"]').click();
    assert.equal(scroller.style.height,'');
    dom.window.close();
});

test('side divider follows horizontal dragging and falls back to vertical resizing on mobile', async () => {
    const markup=table('side-drag','<tr><td>A001</td><td>100</td><td><a class="app-btn" href="/record/1">View</a></td></tr>','data-resource-split').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom,window:w,document:d}=await setup(markup,'window.innerWidth=1366;window.matchMedia=()=>({get matches(){return window.innerWidth>=1100;},addEventListener(){}});');
    d.querySelector('tbody input').click();await new Promise(resolve=>w.setTimeout(resolve,0));
    const handle=d.querySelector('[role=separator]'), position=d.querySelector('.resource-split-panel select'), scroller=d.querySelector('.erp-table-scroll');
    position.value='side';position.dispatchEvent(new w.Event('change'));
    const pointer=(target,type,x,y,id=1)=>{const event=new w.Event(type,{bubbles:true,cancelable:true});Object.assign(event,{button:0,pointerId:id,clientX:x,clientY:y});target.dispatchEvent(event);};
    pointer(handle,'pointerdown',900,400);pointer(w,'pointermove',800,100);
    assert.equal(handle.getAttribute('aria-orientation'),'vertical');
    assert.equal(handle.getAttribute('aria-valuenow'),'420');assert.equal(scroller.style.height,'');
    pointer(w,'pointermove',700,100,2);assert.equal(handle.getAttribute('aria-valuenow'),'420');
    pointer(w,'pointerup',800,100);
    w.innerWidth=360;w.dispatchEvent(new w.Event('resize'));
    assert.equal(handle.getAttribute('aria-orientation'),'horizontal');assert.notEqual(scroller.style.height,'');
    pointer(handle,'pointerdown',300,400);pointer(w,'pointermove',300,450);pointer(w,'pointerup',300,450);
    assert.equal(handle.getAttribute('aria-valuenow'),'370');
    dom.window.close();
});


test('Actions menu preserves live forms and follows selection, keyboard and reset states', async () => {
    const markup=table('commands',row).replace('<a class="app-btn btn-primary"', '<form data-table-command method="post" id="status"><input type="hidden" name="_csrf" value="token"><button class="app-btn btn-neutral" id="deactivate" disabled>Deactivate</button></form><button data-table-command class="app-btn btn-neutral" id="print" disabled>Print</button><a class="app-btn btn-primary"');
    const {dom,window:w,document:d}=await setup(markup);
    const trigger=d.querySelector('.resource-table-action-menu>button'),menu=d.querySelector('[role=menu]'),command=d.querySelector('#deactivate');
    assert.equal(trigger.disabled,true);assert.equal(menu.hidden,true);
    assert.equal(menu.querySelector('[name=_csrf]').value,'token');
    let submissions=0;d.querySelector('#status').addEventListener('submit',event=>{event.preventDefault();submissions++;});
    command.disabled=false;d.querySelector('#print').disabled=false;await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(trigger.disabled,false);trigger.click();assert.equal(trigger.getAttribute('aria-expanded'),'true');assert.equal(d.activeElement,command);
    command.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement,d.querySelector('#print'));
    d.activeElement.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.equal(menu.hidden,true);assert.equal(d.activeElement,trigger);
    trigger.click();command.click();await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(submissions,1);assert.equal(menu.hidden,true);
    trigger.click();d.querySelector('[data-resource-table]').dispatchEvent(new w.CustomEvent('resource-table:reset'));assert.equal(menu.hidden,true);
    command.disabled=true;d.querySelector('#print').disabled=true;await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(trigger.disabled,true);
    assert.equal(d.querySelector('.resource-table-menu .btn-primary'),null);dom.window.close();
});

test('sort links preserve server queries, expose direction and clear selected rows', async () => {
    const markup=table('sorting','<tr><td><input type="checkbox" checked></td><td>Alice</td><td>Active</td></tr>','data-table-selection="single"').replace('<th>Amount</th>','<th aria-sort="descending"><a data-table-sort href="?search=Alice&state=ACTIVE&activityId=parent&size=50&sort=name&direction=asc">Name</a></th>');
    const {dom,document:d}=await setup(markup);const link=d.querySelector('.resource-table-sort');
    assert.equal(link.href,'http://localhost/finance/test?search=Alice&state=ACTIVE&activityId=parent&size=50&sort=name&direction=asc');
    assert.equal(link.closest('th').getAttribute('aria-sort'),'descending');assert.equal(link.querySelector('svg').getAttribute('viewBox'),'0 0 16 16');
    d.querySelector('tbody input').checked=true;
    link.addEventListener('click',event=>event.preventDefault());link.click();assert.equal(d.querySelector('tbody input').checked,false);
    assert.equal(d.querySelectorAll('.resource-table-sort').length,1);dom.window.close();
});
