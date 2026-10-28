const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/resources/static/js/resource-table.js', 'utf8');
const voucherSource = readFileSync('src/main/resources/static/js/accounting-vouchers.js', 'utf8');
const labels = `<template id="resource-table-labels" data-records="Records" data-actions="Actions" data-page-size="Page size" data-striped="Striped rows" data-sticky="Sticky first columns" data-none="None" data-first-column="First column" data-first-two-columns="First two columns" data-reorder="Move column" data-details="Details" data-show-details="Show details" data-panel-position="Panel position" data-bottom="Bottom" data-side="Side" data-resize="Resize details panel" data-select-record="Select a record" data-search-page="Search this page" data-no-matches="No matching records." data-refresh="Refresh" data-pagination="Pagination" data-page="Page" data-previous="Previous page" data-next="Next page" data-preferences="Preferences" data-columns="Visible columns" data-compact="Compact rows" data-wrap="Wrap lines" data-select="Select" data-select-first="Select first row" data-clear-selection="Clear row selection" data-view="View" data-close="Close" data-cancel="Cancel" data-confirm="Confirm"></template>`;
const table = (key, body, extra = '') => `<section class="erp-table-wrap" data-resource-table="${key}" ${extra}><div class="app-table-titlebar"><h2>Records</h2><div class="accounting-actions"><a class="app-btn btn-primary" href="/create">Create</a></div></div><form method="get"><input name="search"></form><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th>Reference</th><th>Amount</th><th>Status</th></tr></thead><tbody>${body}</tbody></table></div></section>`;
const row = '<tr><td>V001</td><td>100.00</td><td>Posted</td></tr>';
const loadingSource=readFileSync('src/main/resources/static/js/ui-loading.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));

test('legacy tables and dashboard summaries count displayed rows without adding table tools',async()=>{
    const markup=`<section class="erp-table-wrap"><div class="app-table-titlebar"><h2>Loan balances</h2></div><table><thead><tr><th>Reference</th><th>Amount</th><th>Status</th></tr></thead><tbody>${row}</tbody></table></section><section data-dashboard-widget="recent"><div class="erp-panel-header"><h2>Recent loans</h2></div><table><thead><tr><th>Reference</th><th>Amount</th><th>Status</th></tr></thead><tbody>${row}${row}</tbody></table></section>`;
    const {dom,window:w,document:d}=await setup(markup+`<section data-dashboard-widget="chart"><div class="erp-panel-header"><h2>Chart</h2></div><div data-dashboard-chart><table><thead><tr><th>Status</th></tr></thead><tbody><tr><td>Pending</td></tr></tbody></table></div></section>`);
    try {
        assert.deepEqual([...d.querySelectorAll('.app-table-count')].map(e=>e.textContent),['(1)','(2)']);
        assert.equal(d.querySelector('.resource-table-tools'),null);
        d.querySelector('[data-dashboard-widget] tbody tr').hidden=true;await settle();
        assert.equal(d.querySelector('[data-dashboard-widget] .app-table-count').textContent,'(1)');
        w.ResourceTable.init();assert.equal(d.querySelectorAll('.app-table-count').length,2);
    } finally {dom.window.close();}
});

test('title counters follow visible rows and preserve an authoritative server total',async()=>{
    const markup=table('row-count',row+row.replace('V001','V002')+'<tr><td colspan="3">No records</td></tr>').replace('<form method="get"><input name="search"></form>','');
    const {dom,window:w,document:d}=await setup(markup);
    try {
        const count=d.querySelector('.app-table-count'),search=d.querySelector('.resource-table-search input');
        assert.equal(count.textContent,'(2)');search.value='V002';search.dispatchEvent(new w.Event('input'));assert.equal(count.textContent,'(1)');
        search.value='missing';search.dispatchEvent(new w.Event('input'));assert.equal(count.textContent,'(0)');
        search.value='';search.dispatchEvent(new w.Event('input'));assert.equal(count.textContent,'(2)');
    } finally {dom.window.close();}
    const other=await setup(table('server-total',row).replace('<h2>Records</h2>','<h2>Records <span class="app-table-count">(90)</span></h2>'));
    try {assert.equal(other.document.querySelectorAll('.app-table-count').length,1);assert.equal(other.document.querySelector('.app-table-count').textContent,'(90)');}finally{other.dom.window.close();}
});

test('report exports retain their own form and use current table filters without issuing an HTML query',async()=>{
    const key='review-report';
    const exportForm=`<form action="/documents/reports/accountant-loans.pdf" method="get" data-table-toolbar-for="${key}" data-table-export-for="${key}"><input type="hidden" name="fromDate" value="2026-10-01"><input type="hidden" name="decisionFilter" value="ALL"><button>PDF</button><button data-print-action="true">Print</button></form>`;
    const markup=exportForm+table(key,row).replace('<form method="get"><input name="search"></form>','<form method="get" action="/accountant/reports"><label>From<input name="fromDate" type="date" value="2026-10-01"></label><label>Decision<select name="decisionFilter"><option>ALL</option><option>REJECTED</option></select></label><button>Apply</button></form>');
    const {dom,window:w,document:d}=await setup(markup);
    try {
        const query=d.querySelector('.resource-table-filters'),output=d.querySelector('[data-table-export-for]');
        assert.equal(output.parentElement.classList.contains('resource-table-actions'),true);
        assert.equal(query.closest('.resource-table-tools')!==null,true);
        query.elements.fromDate.value='2026-10-12';query.elements.decisionFilter.value='REJECTED';
        let observed,requests=0;w.fetch=async()=>{requests++;throw new Error('Export intercepted');};
        d.addEventListener('submit',event=>{observed=Object.fromEntries(new w.FormData(event.target));event.preventDefault();});
        output.dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:output.querySelector('button')}));
        assert.deepEqual(observed,{fromDate:'2026-10-12',decisionFilter:'REJECTED'});
        assert.equal(output.getAttribute('action'),'/documents/reports/accountant-loans.pdf');
        assert.equal(output.querySelector('[data-print-action]').form,output);assert.equal(requests,0);
        query.elements.fromDate.value='2026-10-15';
        d.addEventListener('click',event=>{if(event.target.hasAttribute('data-print-action')){observed=Object.fromEntries(new w.FormData(output));event.preventDefault();}});
        output.querySelector('[data-print-action]').click();
        assert.deepEqual(observed,{fromDate:'2026-10-15',decisionFilter:'REJECTED'});assert.equal(requests,0);
    } finally {dom.window.close();}
});

test('server-normalized status changes update the reference filter label and input mode without replacing controls',async()=>{
    const markup=table('status-query',row,'data-table-sync-query="true"').replace('<input name="search">','<label><span data-table-filter-label="search">Application ID</span><input name="search" placeholder="Application number" inputmode="numeric"></label>');
    const {dom,window:w,document:d}=await setup(markup);
    try {
        const input=d.querySelector('[name="search"]'),label=d.querySelector('[data-table-filter-label]');
        w.fetch=async()=>({ok:true,redirected:false,text:async()=>markup.replace('Application ID','Loan ID').replace('Application number','Loan number').replace('inputmode="numeric"','inputmode="text"')});
        d.querySelector('[data-resource-refresh]').click();await settle();await settle();
        assert.equal(d.querySelector('[name="search"]'),input);assert.equal(label.textContent,'Loan ID');
        assert.equal(input.placeholder,'Loan number');assert.equal(input.getAttribute('inputmode'),'text');
    } finally {dom.window.close();}
});

test('hydrated tables retain their own refresh request instead of fetching placeholder HTML',async()=>{
    const {dom,window:w,document:d}=await setup(table('hydrated',row),loadingSource);
    let hydrated=0,requests=0;
    d.addEventListener('resource-table:refresh',event=>{event.preventDefault();hydrated++;},true);
    w.fetch=async()=>{requests++;throw new Error('Wrong refresh owner');};
    const refresh=d.querySelector('[data-resource-refresh]');refresh.click();await settle();
    assert.equal(hydrated,1);assert.equal(requests,0);assert.equal(d.querySelector('tbody tr').textContent,'V001100.00Posted');
    assert.equal(w.ResourceTable.refresh(refresh),true);assert.equal(hydrated,2);
    assert.equal(w.ResourceTable.refresh(d.body),false);
    dom.window.close();
});

test('a no-match search remains usable after replacing server rows',async()=>{
    const markup=table('search-refresh',row).replace('<form method="get"><input name="search"></form>','');
    const {dom,window:w,document:d}=await setup(markup,loadingSource);
    const search=d.querySelector('.resource-table-search input');
    search.value='missing';search.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);
    w.fetch=async()=>({ok:true,redirected:false,text:async()=>markup.replace('V001','V002')});
    d.querySelector('[data-resource-refresh]').click();await settle();await settle();
    assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);
    search.value='V002';search.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelector('.resource-table-no-matches').hidden,true);
    assert.equal(d.querySelector('tbody tr').hidden,false);
    dom.window.close();
});

test('sort and refresh replace only server rows, retain filters and use a canvas spinner',async()=>{
    const markup=table('async',row).replace('<h2>Records</h2>','<h2>Records</h2><span class="admin-register-count">Showing 1 of 1</span>').replace('<input name="search">','<input name="search" value="cash"><select name="sort"><option value="newest" selected>Newest</option><option value="oldest">Oldest</option></select>')
        .replace('<th>Amount</th>','<th data-table-sort-asc="oldest" data-table-sort-desc="newest">Date</th>');
    const {dom,window:w,document:d}=await setup('<p id="page">Page</p>'+markup,loadingSource);
    const requests=[];w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));
    const region=d.querySelector('section'),form=d.querySelector('form'),page=d.querySelector('#page'),body=d.querySelector('tbody');
    d.querySelector('[data-table-sort]').click();
    assert.equal(requests.length,1);assert.equal(new URL(requests[0].url).searchParams.get('sort'),'oldest');
    assert.equal(d.querySelector('.erp-table-scroll .app-loading-row')!==null,true);assert.equal(d.querySelector('.resource-table-tools .app-loading-spinner'),null);
    assert.equal([...body.rows].find(row=>row.textContent.includes('V001')).hidden,true);
    requests[0].resolve({ok:true,redirected:false,text:async()=>markup.replace('Showing 1 of 1','Showing 1 of 2').replace('V001','V002').replace('value="newest" selected','value="newest"').replace('value="oldest"','value="oldest" selected')});await settle();await settle();
    assert.equal(d.querySelector('.admin-register-count').textContent,'Showing 1 of 2');
    assert.equal(d.querySelector('tbody'),body);assert.equal(d.querySelector('form'),form);assert.equal(d.querySelector('#page'),page);
    assert.equal(body.textContent.includes('V002'),true);assert.equal(d.querySelector('th[aria-sort=ascending]')!==null,true);assert.equal(d.querySelector('.app-loading-spinner'),null);
    form.elements.search.value='unsaved filter';d.querySelector('[data-resource-refresh]').click();
    assert.equal(new URL(requests[1].url).searchParams.get('sort'),'oldest');assert.equal(form.elements.search.value,'unsaved filter');
    requests[1].resolve({ok:true,redirected:false,text:async()=>markup.replace('V001','V003')});await settle();await settle();
    assert.equal(form.elements.search.value,'unsaved filter');assert.equal(body.textContent.includes('V003'),true);assert.equal(d.querySelector('#page'),page);dom.window.close();
});

test('a newer table request cancels older results and failed authentication retains the canvas',async()=>{
    const markup=table('race',row);const {dom,window:w,document:d}=await setup(markup,loadingSource);
    const requests=[];w.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));const refresh=d.querySelector('[data-resource-refresh]');
    refresh.click();refresh.click();assert.equal(requests[0].options.signal.aborted,true);
    requests[1].resolve({ok:true,redirected:false,text:async()=>markup.replace('V001','Latest')});await settle();await settle();
    requests[0].resolve({ok:true,redirected:false,text:async()=>markup.replace('V001','Stale')});await settle();await settle();
    assert.equal(d.querySelector('tbody').textContent.includes('Latest'),true);assert.equal(d.querySelector('tbody').textContent.includes('Stale'),false);
    const record=d.querySelector('tbody tr');refresh.click();requests[2].resolve({ok:true,redirected:true,text:async()=>'<form>Login</form>'});await settle();await settle();
    assert.equal(record.isConnected,true);assert.equal(record.hidden,false);assert.equal(d.querySelector('.resource-table-load-error [role=alert]')!==null,true);
    assert.equal(d.querySelector('.app-loading-spinner'),null);dom.window.close();
});

test('workspace command columns retain native forms, permissions and record targets in Actions', async () => {
    const html=`<section data-resource-table="workspace"><table class="erp-table"><thead><tr><th>Name</th><th data-table-actions-header>Details</th><th data-table-actions-header>Actions</th></tr></thead><tbody><tr><td>First</td><td><a class="app-btn" href="/first">Open</a></td><td><form id="native-command" method="post" action="/first/decision"><input type="hidden" name="_csrf" value="original"><button type="button" id="approve">Approve</button></form><button disabled>Restricted</button><p>Policy restriction</p></td></tr><tr><td>Second</td><td><a class="app-btn" href="/second">Open</a></td><td><button disabled>Restricted</button></td></tr></tbody></table></section>`;
    const {dom,window:w,document:d}=await setup(html);
    assert.equal(d.querySelectorAll('thead th').length,2);assert.equal(d.querySelector('thead [data-table-actions-header]'),null);
    d.querySelector('tbody input').click();
    const form=d.querySelector('#native-command'),button=d.querySelector('#approve');let commands=0;button.addEventListener('click',()=>commands++);
    assert.equal(d.querySelectorAll('.resource-table-record-actions > button,.resource-table-record-actions > a').length,0);
    assert.equal(d.querySelector('.resource-table-menu > a').textContent,'Open');
    assert.equal(d.querySelectorAll('.resource-table-menu #approve').length,1);
    assert.equal([...d.querySelectorAll('.resource-table-menu button')].filter(control=>control.textContent==='Approve').length,1);
    assert.equal(form.closest('[role=menu]')!==null,true);assert.equal(form.action,'http://localhost/first/decision');assert.equal(form.querySelector('input').value,'original');
    assert.equal(d.querySelector('.resource-table-command-context').hidden,false);
    d.querySelector('.resource-table-action-menu > button').click();button.click();assert.equal(commands,1);
    d.querySelectorAll('tbody input')[1].click();assert.equal(d.querySelector('.resource-table-record-actions a').getAttribute('href'),'/second');assert.equal(d.querySelector('.resource-table-action-menu > button').disabled,false);
    assert.equal(d.querySelectorAll('.resource-table-record-actions > button,.resource-table-record-actions > a').length,0);
    d.querySelector('tbody input').click();assert.equal(d.querySelector('#native-command'),form);assert.equal(d.querySelector('#approve'),button);
    d.querySelector('.resource-table-search input').dispatchEvent(new w.Event('input'));assert.equal(d.querySelector('tbody input').checked,false);dom.window.close();
});

test('late hydrated rows receive selection and replacement clears old record commands', async () => {
    const html=`<section data-resource-table="hydrated"><table><thead><tr><th>Reference</th><th>Amount</th><th data-table-actions-header>Commands</th></tr></thead><tbody><tr><td colspan="3">Loading</td></tr></tbody></table></section>`;
    const {dom,window:w,document:d}=await setup(html);const body=d.querySelector('tbody');
    body.innerHTML='<tr><td>L001</td><td>100</td><td><button type="button" id="schedule">Schedule</button></td></tr>';
    await new Promise(resolve=>w.setTimeout(resolve,0));d.querySelector('tbody input').click();
    assert.equal(d.querySelector('.resource-table-menu #schedule')!==null,true);
    body.innerHTML='<tr><td>L002</td><td>200</td><td><a class="app-btn" href="/second">Open</a></td></tr>';
    await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(d.querySelector('#schedule'),null);assert.equal(d.querySelector('tbody input').checked,false);
    d.querySelector('tbody input').click();assert.equal(d.querySelector('.resource-table-record-actions a').getAttribute('href'),'/second');dom.window.close();
});

test('explicit workspace headings, GET filters and independent POST commands move with their envelopes', async () => {
    const html=`<h2 data-table-heading-for="external">Review queue</h2><form method="get" action="/queue" data-table-filter-for="external"><input name="searchId" value="L001"><input type="hidden" name="filter" value="pending"></form><form id="mark-read" method="post" action="/notifications/mark-all-read" data-table-toolbar-for="external"><input type="hidden" name="_csrf" value="token"><button>Mark read</button></form>${table('external',row).replace('<div class="app-table-titlebar"><h2>Records</h2><div class="accounting-actions"><a class="app-btn btn-primary" href="/create">Create</a></div></div>','').replace('<form method="get"><input name="search"></form>','')}`;
    const {dom,document:d}=await setup(html);
    assert.equal(d.querySelector('.app-table-titlebar h2').textContent,'Review queue (1)');assert.equal(d.querySelector('.resource-table-tools [name=searchId]').value,'L001');assert.equal(d.querySelector('.resource-table-tools [name=filter]').value,'pending');
    assert.equal(d.querySelector('.resource-table-actions #mark-read').action,'http://localhost/notifications/mark-all-read');assert.equal(d.querySelector('#mark-read input').value,'token');assert.equal(d.querySelector('.erp-table-scroll form'),null);dom.window.close();
});

test('existing bounded client search remains functional under the shared table component', async () => {
    const html=table('bounded',row+'<tr><td>V002</td><td>200</td><td>Reversed</td></tr>').replace('<form method="get"><input name="search"></form>','<div class="aws-filter-toolbar"><input type="search" data-aws-table-search></div>');
    const {dom,window:w,document:d}=await setup(html);const search=d.querySelector('[data-aws-table-search]');search.value='reversed';search.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelector('tbody tr').hidden,true);assert.equal(d.querySelector('tbody tr:nth-child(2)').hidden,false);dom.window.close();
});

test('preferences use the staff register server sizes without duplicate size fields', async () => {
    const html=table('staff-sizes',row,'data-table-page-size="25" data-table-page-size-options="25 50 100"').replace('<input name="search">','<input name="search"><select name="size"><option value="25">25</option><option value="50">50</option><option value="100">100</option></select>');
    const {dom,document:d}=await setup(html);assert.equal(d.querySelectorAll('.resource-table-tools [name=size]').length,1);d.querySelector('[aria-label="Preferences"]').click();
    assert.deepEqual([...d.querySelectorAll('input[name=pageSize]')].map(input=>input.value),['25','50','100']);assert.equal(d.querySelector('input[name=pageSize]:checked').value,'25');d.querySelector('dialog button[title=Close]').click();dom.window.close();
});

test('replaced calculator sections release old observers and initialize exactly one set of tools', async () => {
    const {dom,window:w,document:d}=await setup('<div id="calculator">'+table('calculation',row)+'</div>');
    const container=d.querySelector('#calculator'),old=container.querySelector('[data-resource-table]'),body=old.querySelector('tbody');
    container.innerHTML=table('calculation',row);w.ResourceTable.init(container);w.ResourceTable.init(container);
    assert.equal(container.querySelectorAll('.resource-table-tools').length,1);assert.equal(container.querySelectorAll('.resource-table-preferences-button').length,1);
    body.insertAdjacentHTML('beforeend',row);await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(body.lastChild.cells[0].style.getPropertyValue('--sticky-left'),'');dom.window.close();
});

test('opening an existing record modal from Actions keeps focus in the modal', async () => {
    const html=table('modal-command','<tr><td>R001</td><td>100</td><td><button type="button" id="open-modal">Review</button></td></tr>').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>')+'<div id="modal" hidden><input aria-label="Review reason"></div>';
    const {dom,window:w,document:d}=await setup(html);d.querySelector('tbody input').click();
    const input=d.querySelector('#modal input');d.querySelector('#open-modal').addEventListener('click',()=>{d.querySelector('#modal').hidden=false;input.focus();});
    d.querySelector('.resource-table-action-menu > button').click();d.querySelector('#open-modal').click();await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(d.activeElement,input);dom.window.close();
});
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
test('bounded detail tables omit tools and retain all columns and record commands', async () => {
    const markup=table('loan-documents','<tr><td>Proof.pdf</td><td>1 KB</td><td><a class="app-btn" href="/proof" data-download-action="true">Download</a></td></tr>','data-table-tools="none" data-aws-no-refresh="true" data-resource-split')
        .replace('<form method="get"><input name="search"></form>','').replace('<th>Status</th>','<th data-table-actions-header></th>');
    const saved='localStorage.setItem("resource-table:v1:loan-documents",JSON.stringify({hidden:[1],order:[2,1,0]}));';
    const {dom,document:d}=await setup(markup+table('next-detail',row,'data-table-tools="none"'),saved);
    assert.equal(d.querySelector('.resource-table-tools'),null);
    assert.equal(d.querySelector('[type="search"],.resource-table-pagination,.resource-table-preferences-button'),null);
    assert.equal(d.querySelector('.app-table-titlebar h2').textContent,'Records (1)');
    assert.equal(d.querySelector('.resource-split-layout'),null);
    assert.equal(d.querySelector('[data-resource-table="next-detail"]').dataset.resourceTableReady,'true');
    assert.equal([...d.querySelectorAll('tbody td')].every(cell=>!cell.hidden),true);
    assert.equal(d.querySelector('tbody td:last-child').textContent,'1 KB');
    d.querySelector('tbody input[type="checkbox"]').click();
    assert.equal(d.querySelector('.resource-table-actions a[data-download-action]').getAttribute('href'),'/proof');
    dom.window.close();
});
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

test('JSON editors use the shared pager without replacing drafts or fetching HTML', async () => {
    const {dom,window:w,document:d}=await setup(table('receipt-editor','<tr><td><input value="draft receipt"></td><td>100.00</td><td>Draft</td></tr>','data-table-pagination-mode="external"'));
    const region=d.querySelector('section'),input=d.querySelector('tbody input');
    let requests=0,refreshes=0,edits=0;const pages=[];
    w.fetch=async()=>{requests++;throw new Error('Editor owns JSON loading');};
    input.addEventListener('input',()=>edits++);
    region.addEventListener('resource-table:page',event=>pages.push(event.detail.page));
    region.addEventListener('resource-table:refresh',()=>refreshes++);
    w.ResourceTable.setPage(region,{page:0,hasNext:true,loading:false});
    const next=d.querySelector('[aria-label="Next page"]');
    assert.equal(d.querySelector('[aria-label="Previous page"]').disabled,true);
    assert.equal(next.disabled,false);next.focus();
    w.ResourceTable.setPage(region,{page:0,hasNext:true,loading:false});
    assert.equal(d.activeElement,next);next.click();assert.deepEqual(pages,[1]);
    w.ResourceTable.setPage(region,{page:1,hasNext:false,loading:false});
    assert.equal(d.querySelector('.resource-table-page-number').textContent,'2');
    assert.equal(d.querySelector('[aria-label="Next page"]').disabled,true);
    d.querySelector('[aria-label="Previous page"]').click();assert.deepEqual(pages,[1,0]);
    w.ResourceTable.setPage(region,{page:1,hasNext:true,loading:true});
    assert.equal([...d.querySelectorAll('.resource-table-page')].every(button=>button.disabled),true);
    d.querySelector('[data-resource-refresh]').click();await settle();
    assert.equal(refreshes,1);assert.equal(requests,0);
    assert.equal(d.querySelector('tbody input'),input);assert.equal(input.value,'draft receipt');
    input.dispatchEvent(new w.Event('input'));assert.equal(edits,1);
    dom.window.close();
});

test('compact density is the default while an explicit saved comfortable preference survives', async () => {
    const fresh=await setup(table('fresh-density',row));
    assert.equal(fresh.document.querySelector('section').classList.contains('resource-table-compact'),true);
    fresh.dom.window.close();
    const saved=await setup(table('saved-density',row), `localStorage.setItem('resource-table:v1:saved-density',JSON.stringify({hidden:[],compact:false}));`);
    assert.equal(saved.document.querySelector('section').classList.contains('resource-table-compact'),false);
    saved.document.querySelector('[aria-label="Preferences"]').click();
    const compact=[...saved.document.querySelectorAll('.resource-table-option')].find(option=>option.textContent==='Compact rows');
    assert.equal(compact.querySelector('input').checked,false);
    saved.dom.window.close();
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

test('selected loan View, PDF, Print and top-up commands share one menu and retain controller rules', async () => {
    const commands = '<button data-table-command data-selection-action="details" disabled>View</button><button data-table-command data-selection-action="pdf" disabled>Download PDF</button><button data-table-command data-selection-action="print" disabled>Print</button><button data-table-command data-selection-action="topup" disabled>Top up</button>';
    const rows = '<tr data-voucher-id="first" data-topup-eligible="true"><td><input type="checkbox"></td><td><a class="resource-table-id-link" href="/loans/first">L001</a></td><td>100</td></tr><tr data-voucher-id="second" data-topup-eligible="false"><td><input type="checkbox"></td><td><a class="resource-table-id-link" href="/loans/second">L002</a></td><td>200</td></tr>';
    const markup = '<div data-voucher-register data-base="/loans">'+table('loans',rows,'data-table-selection="single"').replace('<a class="app-btn btn-primary"',commands+'<a class="app-btn btn-primary"')+'<span data-selection-status data-empty="" data-selected="Selected"></span></div>';
    const {dom,window:w,document:d}=await setup(markup,voucherSource);
    const menu=d.querySelector('[role=menu]'),trigger=d.querySelector('[aria-haspopup=menu]'),view=d.querySelector('[data-selection-action=details]');
    assert.equal(d.querySelectorAll('[role=menu]').length,1);
    assert.equal(d.querySelectorAll('.resource-table-actions > [data-selection-action]').length,0);
    assert.deepEqual([...menu.querySelectorAll('[role=menuitem]')].map(item=>item.textContent),['View','Download PDF','Print','Top up']);
    assert.equal(trigger.disabled,true);assert.equal(menu.hidden,true);
    assert.equal(d.querySelector('.resource-table-actions > a').getAttribute('href'),'/create');
    assert.equal(d.querySelector('tbody .resource-table-id-link').getAttribute('href'),'/loans/first');
    d.querySelector('thead input').click();await settle();assert.equal(trigger.disabled,false);
    trigger.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
    assert.equal(menu.hidden,false);assert.equal(d.activeElement,view);
    const printed=[];w.printExport=url=>printed.push(url);
    d.querySelector('[data-selection-action=print]').click();await settle();
    assert.deepEqual(printed,['/loans/first/print']);assert.equal(menu.hidden,true);
    d.querySelectorAll('tbody input')[1].click();await settle();
    assert.equal(d.querySelector('[data-selection-action=topup]').disabled,true);
    trigger.click();d.querySelector('[data-selection-action=print]').click();await settle();
    assert.deepEqual(printed,['/loans/first/print','/loans/second/print']);
    trigger.click();d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));await settle();
    assert.equal(menu.hidden,true);assert.equal(trigger.disabled,true);assert.equal(view.disabled,true);
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

test('desktop split starts empty, follows selection, stays closed after dismissal and clears stale facts', async () => {
    const markup=table('split','<tr><td>A001</td><td>100</td><td><a class="app-btn" href="/record/1">View</a></td></tr>','data-resource-split').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
    const {dom,window:w,document:d}=await setup(markup);
    const panel=d.querySelector('.resource-split-panel');assert.equal(panel.hidden,false);
    assert.equal(panel.querySelector('h2').textContent,'Select a record');
    assert.equal(panel.querySelectorAll('dd').length,0);
    d.querySelector('tbody input').click();await new Promise(resolve=>w.setTimeout(resolve,0));
    assert.equal(panel.hidden,false);
    assert.equal(panel.hidden,false);assert.equal(panel.querySelector('h2').textContent,'Details');
    assert.equal(panel.querySelector('dd').textContent,'A001');
    assert.equal(panel.querySelectorAll('dd').length,2);assert.equal(panel.querySelector('.resource-split-content a'),null);
    assert.equal(d.querySelector('.resource-table-record-actions a').getAttribute('href'),'/record/1');
    panel.querySelector('[aria-label="Close"]').click();
    d.querySelector('tbody input').click();d.querySelector('tbody input').click();
    await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(panel.hidden,true);
    d.querySelector('[aria-label="Show details"]').click();assert.equal(panel.hidden,false);
    const resize=panel.querySelector('[role=separator]');resize.dispatchEvent(new w.KeyboardEvent('keydown',{key:'End',bubbles:true}));assert.equal(resize.getAttribute('aria-valuenow'),resize.getAttribute('aria-valuemax'));
    const pos=panel.querySelector('select');pos.value='side';pos.dispatchEvent(new w.Event('change'));assert.equal(d.querySelector('.resource-split-layout').classList.contains('resource-split-side'),true);
    d.querySelector('.resource-table-filters input').dispatchEvent(new w.Event('input',{bubbles:true}));
    await new Promise(resolve=>w.setTimeout(resolve,0));assert.equal(panel.querySelectorAll('dd').length,0);assert.equal(panel.querySelector('h2').textContent,'Select a record');
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
    d.querySelector('[aria-label="Show details"]').click();
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
    d.querySelector('[aria-label="Show details"]').click();
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

test('Actions toggles closed when pointer focus returns from a menu item to its trigger', async () => {
    const markup=table('toggle',row).replace('<a class="app-btn btn-primary"','<button type="button" data-table-command>Deactivate</button><a class="app-btn btn-primary"');
    const {dom,window:w,document:d}=await setup(markup);
    const trigger=d.querySelector('.resource-table-action-menu>button'),menu=d.querySelector('[role=menu]');
    trigger.click();assert.equal(menu.hidden,false);assert.equal(d.activeElement,menu.querySelector('button'));
    // Native focus events can run a microtask checkpoint before the new target gains focus.
    const queue=w.queueMicrotask;w.queueMicrotask=callback=>callback();
    trigger.focus();w.queueMicrotask=queue;
    trigger.click();await settle();
    assert.equal(menu.hidden,true);assert.equal(trigger.getAttribute('aria-expanded'),'false');
    trigger.click();assert.equal(menu.hidden,false);
    d.querySelector('.resource-table-filters input').focus();await settle();
    assert.equal(menu.hidden,true);assert.equal(trigger.getAttribute('aria-expanded'),'false');
    dom.window.close();
});

test('Actions skips restricted and hidden options and blocks their handlers until eligible', async () => {
    const markup=table('conditions',row).replace('<a class="app-btn btn-primary"','<button type="button" data-table-command id="view">View</button><button type="button" data-table-command id="restricted" disabled>Top up</button><a class="app-btn" data-table-command id="restricted-link" href="/decision" aria-disabled="true">Review</a><div data-table-command hidden id="hidden-command"><button type="button" id="hidden-option">Hidden action</button></div><a class="app-btn btn-primary"');
    const {dom,window:w,document:d}=await setup(markup);
    const trigger=d.querySelector('[aria-haspopup=menu]'),menu=d.querySelector('[role=menu]'),view=d.querySelector('#view'),restricted=d.querySelector('#restricted'),link=d.querySelector('#restricted-link'),hidden=d.querySelector('#hidden-command');
    let activations=0;[restricted,link,d.querySelector('#hidden-option')].forEach(control=>control.addEventListener('click',event=>{event.preventDefault();activations++;}));
    trigger.click();assert.equal(d.activeElement,view);
    view.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement,view);
    restricted.dispatchEvent(new w.MouseEvent('click',{bubbles:true,cancelable:true}));link.click();d.querySelector('#hidden-option').click();
    assert.equal(activations,0);assert.equal(menu.hidden,false);assert.equal(restricted.disabled,true);assert.equal(link.getAttribute('aria-disabled'),'true');
    restricted.disabled=false;await settle();
    view.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement,restricted);
    restricted.click();await settle();assert.equal(activations,1);assert.equal(menu.hidden,true);
    restricted.disabled=true;link.setAttribute('aria-disabled','false');hidden.hidden=false;await settle();trigger.click();
    view.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement,link);
    link.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement,d.querySelector('#hidden-option'));
    hidden.hidden=true;await settle();assert.equal(d.activeElement,view);
    view.disabled=true;link.setAttribute('aria-disabled','true');await settle();
    assert.equal(trigger.disabled,true);assert.equal(menu.hidden,true);
    dom.window.close();
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

test('register sort headings bind actual date and amount options with AWS caret states', async () => {
    const markup=table('register-sort',row).replace('<input name="search">','<input name="search" value="cash"><input name="from" value="2026-10-01"><input name="productId" value="product"><select name="sort"><option value="newest" selected>Newest</option><option value="oldest">Oldest</option><option value="amount">Amount</option></select>')
        .replace('<th>Reference</th>','<th data-table-sort-asc="oldest" data-table-sort-desc="newest">Date</th>')
        .replace('<th>Amount</th>','<th data-table-sort-desc="amount">Amount</th>');
    const {dom,document:d}=await setup(markup);
    const links=[...d.querySelectorAll('.resource-table-sort')];assert.equal(links.length,2);
    assert.equal(links[0].closest('th').getAttribute('aria-sort'),'descending');
    assert.equal(links[1].closest('th').getAttribute('aria-sort'),'none');
    for(const link of links) {
        const url=new URL(link.href);assert.equal(url.searchParams.get('search'),'cash');assert.equal(url.searchParams.get('from'),'2026-10-01');assert.equal(url.searchParams.get('productId'),'product');assert.equal(url.searchParams.get('page'),'0');
        assert.equal(link.querySelector('svg path').getAttribute('d'),'M8 11L12 5H4L8 11Z');assert.equal(link.querySelector('svg').getAttribute('fill'),'none');
    }
    assert.equal(new URL(links[0].href).searchParams.get('sort'),'oldest');assert.equal(new URL(links[1].href).searchParams.get('sort'),'amount');
    assert.equal(d.querySelector('th:last-child svg'),null);dom.window.close();
});

test('every data column with a server sort contract receives an AWS caret and scoped link', async () => {
    const markup=table('all-column-sort',row).replace('<input name="search">','<input name="search" value="client"><select name="sort"><option value="newest" selected>Newest</option></select>')
        .replace('<th>Reference</th>','<th data-table-sort-asc="number_asc" data-table-sort-desc="number_desc">Reference</th>')
        .replace('<th>Amount</th>','<th data-table-sort-asc="amount_asc" data-table-sort-desc="amount">Amount</th>')
        .replace('<th>Status</th>','<th data-table-sort-asc="status_asc" data-table-sort-desc="status_desc">Status</th>');
    const {dom,document:d}=await setup(markup);const headings=[...d.querySelectorAll('thead th')];
    assert.equal(d.querySelectorAll('.resource-table-sort').length,headings.length);
    for(const heading of headings){
        assert.equal(heading.getAttribute('aria-sort'),'none');
        const link=heading.querySelector('.resource-table-sort'),url=new URL(link.href);
        assert.equal(url.searchParams.get('search'),'client');assert.equal(url.searchParams.get('page'),'0');
        assert.equal(link.querySelector('svg path').getAttribute('d'),'M8 11L12 5H4L8 11Z');
    }
    dom.window.close();
});

test('separate direction headers retain scoped filters and size and toggle ascending', async () => {
    const markup=table('chart-sort',row,'data-table-page-size="50"').replace('<input name="search">','<input name="search" value="Bank"><input type="hidden" name="parentId" value="group"><input type="hidden" name="sort" value="code"><input type="hidden" name="direction" value="asc">')
        .replace('<th>Reference</th>','<th data-table-sort-key="code" data-table-sort-asc="asc" data-table-sort-desc="desc">Code</th>')
        .replace('<th>Amount</th>','<th data-table-sort-key="name" data-table-sort-asc="asc" data-table-sort-desc="desc">Name</th>');
    const {dom,document:d}=await setup(markup);const links=[...d.querySelectorAll('.resource-table-sort')];
    assert.equal(links[0].closest('th').getAttribute('aria-sort'),'ascending');assert.equal(links[1].closest('th').getAttribute('aria-sort'),'none');
    const active=new URL(links[0].href),other=new URL(links[1].href);
    assert.equal(active.searchParams.get('direction'),'desc');assert.equal(other.searchParams.get('sort'),'name');assert.equal(other.searchParams.get('direction'),'asc');
    for(const url of [active,other]){assert.equal(url.searchParams.get('size'),'50');assert.equal(url.searchParams.get('parentId'),'group');assert.equal(url.searchParams.get('search'),'Bank');assert.equal(url.searchParams.get('page'),'0');}
    dom.window.close();
});


test('server column arrows toggle, retain filters and ordering through pagination and filter submission', async () => {
    const markup=table('columns',row).replace('<th>Reference</th>','<th data-table-order="number">Reference</th>')
        .replace('<input name="search">','<input name="search" value="cash"><input name="state" value="ACTIVE">')
        + '<div data-table-pagination data-page="0" data-table-for="columns"><a data-page-direction="next" href="?page=1&search=cash&state=ACTIVE">Next</a></div>';
    const {dom,window:w,document:d}=await setup(markup,loadingSource);
    const requests=[];w.fetch=async url=>{requests.push(new URL(url));return {ok:true,redirected:false,text:async()=>markup};};
    const header=d.querySelector('th'),link=header.querySelector('a');
    assert.equal(header.getAttribute('aria-sort'),'none');assert.equal(header.querySelectorAll('svg').length,1);
    link.click();await settle();await settle();
    assert.equal(requests[0].searchParams.get('tableSort[columns]'),'number');
    assert.equal(requests[0].searchParams.get('tableDirection[columns]'),'asc');
    assert.equal(requests[0].searchParams.get('page'),'0');assert.equal(requests[0].searchParams.get('state'),'ACTIVE');
    assert.equal(header.getAttribute('aria-sort'),'ascending');
    assert.equal(new URL(d.querySelector('[aria-label="Next page"]').href).searchParams.get('tableSort[columns]'),'number');
    link.click();await settle();await settle();
    assert.equal(requests[1].searchParams.get('tableDirection[columns]'),'desc');assert.equal(header.getAttribute('aria-sort'),'descending');
    d.querySelector('[name=search]').value='bank';d.querySelector('form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    await settle();await settle();
    assert.equal(requests[2].searchParams.get('tableSort[columns]'),'number');
    assert.equal(requests[2].searchParams.get('tableDirection[columns]'),'desc');assert.equal(requests[2].searchParams.get('search'),'bank');
    assert.equal(header.querySelectorAll('svg').length,1);dom.window.close();
});


test('complete detail tables sort exact amounts and displayed dates while retaining native row nodes', async () => {
    const rows=[['A','9,000.00','02/01/2026'],['B','-10.50','31/12/2025'],['C','9007199254740992.01','01/02/2026'],['D','9007199254740992.00','01/01/2026']]
        .map(([id,amount,date])=>`<tr><td><a href="/${id}">${id}</a></td><td>${amount}</td><td>${date}</td></tr>`).join('');
    const markup=table('complete-detail',rows).replace('<table class="erp-table">','<table class="erp-table" data-table-sort-mode="local">');
    const {dom,window:w,document:d}=await setup(markup);
    const body=d.querySelector('tbody'),first=body.rows[0],link=first.querySelector('a');let activations=0;
    link.addEventListener('click',event=>{event.preventDefault();activations++;});
    const buttons=[...d.querySelectorAll('[data-table-local-sort]')];assert.equal(buttons.length,3);
    const order=()=>[...body.rows].map(row=>row.cells[0].textContent);
    buttons[1].click();assert.deepEqual(order(),['B','A','D','C']);
    buttons[1].click();assert.deepEqual(order(),['C','D','A','B']);
    buttons[2].click();assert.deepEqual(order(),['B','D','A','C']);
    assert.equal(first.querySelector('a'),link);link.click();assert.equal(activations,1);
    body.insertAdjacentHTML('beforeend','<tr><td>E</td><td>0.00</td><td>01/01/2025</td></tr>');await settle();
    assert.deepEqual(order(),['E','B','D','A','C']);assert.equal(d.querySelector('th:nth-child(3)').getAttribute('aria-sort'),'ascending');
    w.ResourceTable.init();assert.equal(d.querySelectorAll('[data-table-local-sort]').length,3);dom.window.close();
});

test('ordinary registers and editable tables never acquire page-only sorting controls',async()=>{
    const {dom,document:d}=await setup(table('register',row)+table('editor','<tr><td><input value="draft"></td><td>10</td><td>Draft</td></tr>','data-table-pagination-mode="external"'));
    assert.equal(d.querySelector('[data-table-local-sort]'),null);dom.window.close();
});

test('two tables retain independent sorts and reset their own page counters',async()=>{
    const markup=table('history',row).replace('<th>Reference</th>','<th data-table-order="reference">Reference</th>')
        +table('schedule',row,'data-table-page-param="schedulePage"').replace('<th>Reference</th>','<th data-table-order="number">Number</th>');
    const {dom,window:w,document:d}=await setup(markup,loadingSource);
    const requests=[];w.fetch=async url=>{requests.push(new URL(url));return {ok:true,redirected:false,text:async()=>markup};};
    d.querySelector('[data-resource-table="history"] [data-table-sort]').click();await settle();await settle();
    d.querySelector('[data-resource-table="schedule"] [data-table-sort]').click();await settle();await settle();
    assert.equal(requests[1].searchParams.get('tableSort[history]'),'reference');
    assert.equal(requests[1].searchParams.get('tableSort[schedule]'),'number');
    assert.equal(requests[1].searchParams.get('schedulePage'),'0');
    assert.equal(d.querySelectorAll('th[aria-sort="ascending"]').length,2);
    d.querySelector('[data-resource-table="history"] [data-table-sort]').click();await settle();await settle();
    assert.equal(requests[2].searchParams.get('tableDirection[history]'),'desc');
    assert.equal(requests[2].searchParams.get('tableSort[schedule]'),'number');
    dom.window.close();
});

test('a server response can change a conditional reference heading without leaving its old sort key',async()=>{
    const markup=table('conditional',row).replace('<th>Reference</th>','<th data-table-order="number">Application number</th>');
    const {dom,window:w,document:d}=await setup(markup,loadingSource);
    const requests=[];w.fetch=async url=>{requests.push(new URL(url));return {ok:true,redirected:false,text:async()=>markup.replace('data-table-order="number"','data-table-order="loan"').replace('Application number','Loan ID')};};
    const header=d.querySelector('th');header.querySelector('a').click();await settle();await settle();
    assert.equal(header.dataset.tableOrder,'loan');assert.equal(header.textContent,'Loan ID');assert.equal(header.getAttribute('aria-sort'),'none');
    header.querySelector('a').click();await settle();await settle();
    assert.equal(requests[1].searchParams.get('tableSort[conditional]'),'loan');
    assert.equal(header.querySelectorAll('svg').length,1);dom.window.close();
});


test('mobile split starts closed and its divider only consumes keys for the current orientation',async()=>{
 const markup=table('mobile-preview','<tr><td>L001</td><td>100</td><td><a class="app-btn" href="/loan/1">View</a></td></tr>','data-resource-split').replace('<th>Status</th>','<th data-table-actions-header>Actions</th>');
 const {dom,window:w,document:d}=await setup(markup,'window.innerWidth=360;');
 const panel=d.querySelector('.resource-split-panel');assert.equal(panel.hidden,true);
 d.querySelector('tbody input').click();await settle();assert.equal(panel.hidden,true);
 d.querySelector('[aria-label="Show details"]').click();assert.equal(panel.hidden,false);
 const handle=panel.querySelector('[role=separator]'),before=handle.getAttribute('aria-valuenow');
 const right=new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true});handle.dispatchEvent(right);
 assert.equal(right.defaultPrevented,false);assert.equal(handle.getAttribute('aria-valuenow'),before);
 handle.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true,cancelable:true}));
 assert.equal(Number(handle.getAttribute('aria-valuenow')),Number(before)+20);dom.window.close();
});

test('failed table queries retry the attempted filter without replacing native rows or controls',async()=>{
 const {dom,window:w,document:d}=await setup(table('retry',row),loadingSource);
 const input=d.querySelector('input[name=search]'),original=d.querySelector('tbody tr');input.value='filtered';
 const requests=[];w.fetch=async(url,options)=>{requests.push({url,options});if(requests.length===1)throw Error('offline');return {ok:true,redirected:false,text:async()=>table('retry','<tr><td>V002</td><td>200</td><td>Posted</td></tr>')};};
 d.querySelector('form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await settle();
 assert.equal(d.querySelector('tbody tr:not(.resource-table-load-error)'),original);assert.equal(original.hidden,false);
 const retry=d.querySelector('.resource-table-retry');assert.ok(retry);assert.equal(d.querySelector('.app-loading-row'),null);
 retry.click();await settle();await settle();
 assert.equal(requests.length,2);assert.equal(new URL(requests[1].url).searchParams.get('search'),'filtered');assert.equal(requests[1].options.cache,'no-store');
 assert.equal(d.querySelector('input[name=search]'),input);assert.equal(input.value,'filtered');assert.equal(d.querySelector('.resource-table-load-error'),null);assert.match(d.querySelector('tbody').textContent,/V002/);dom.window.close();
});

test('page exit aborts table requests and discards their late responses',async()=>{
 const {dom,window:w,document:d}=await setup(table('leave',row),loadingSource);
 let complete,signal;w.fetch=(_,options)=>{signal=options.signal;return new Promise(resolve=>complete=resolve);};
 d.querySelector('[data-resource-refresh]').click();assert.equal(d.querySelectorAll('.app-loading-row').length,1);
 w.dispatchEvent(new w.Event('pagehide'));assert.equal(signal.aborted,true);assert.equal(d.querySelector('.app-loading-row'),null);
 complete({ok:true,redirected:false,text:async()=>table('leave','<tr><td>STALE</td><td>0</td><td>Posted</td></tr>')});await settle();await settle();
 assert.match(d.querySelector('tbody').textContent,/V001/);assert.doesNotMatch(d.querySelector('tbody').textContent,/STALE/);assert.equal(d.querySelector('.resource-table-load-error'),null);dom.window.close();
});

test('approval status cards refresh counts and active groups while preserving the mounted shell and filters',async()=>{
    const approvalSource=readFileSync('src/main/resources/static/js/voucher-approval.js','utf8');
    const cards=`<div data-table-query-state-for="voucher-approval-register"><section class="approval-tile"><a href="/finance/test?category=AGE_0_7" data-table-query-for="voucher-approval-register" data-table-query-param="category"><strong data-table-query-value="AGE_0_7">1</strong> Incomplete</a></section><section class="approval-tile"><a href="/finance/test?category=READY" data-table-query-for="voucher-approval-register" data-table-query-param="category"><strong data-table-query-value="READY">2</strong> Ready</a></section></div>`;
    const markup=`<div data-approval-register>${cards}${table('voucher-approval-register',row,'data-table-sync-query="true"').replace('<input name="search">','<input name="search" value="cash"><input name="type" value="JOURNAL"><input name="days" value="30"><input name="sort" value="number_asc"><select name="category"><option value=""></option><option value="AGE_0_7">Incomplete</option><option value="READY">Ready</option></select>')}</div>`;
    const {dom,window:w,document:d}=await setup(markup,approvalSource);
    try{
        const page=d.documentElement,region=d.querySelector('[data-resource-table]'),form=d.querySelector('form'),card=d.querySelector('.approval-tile'),search=d.querySelector('[name=search]');
        let requested;
        w.fetch=async(url)=>{requested=new URL(url);return {ok:true,redirected:false,text:async()=>markup.replace('value="AGE_0_7">Incomplete','value="AGE_0_7" selected>Incomplete').replace('>1</strong>','>7</strong>').replace('V001','V009')};};
        card.querySelector('a').click();await settle();await settle();
        assert.equal(d.documentElement,page);assert.equal(d.querySelector('[data-resource-table]'),region);assert.equal(d.querySelector('form'),form);assert.equal(d.querySelector('[name=search]'),search);
        for(const [key,value] of Object.entries({category:'AGE_0_7',search:'cash',type:'JOURNAL',days:'30',sort:'number_asc',page:'0'}))assert.equal(requested.searchParams.get(key),value);
        assert.equal(card.querySelector('strong').textContent,'7');assert.equal(card.classList.contains('is-active'),true);assert.equal(card.querySelector('a').getAttribute('aria-current'),'page');assert.equal(d.querySelectorAll('.approval-tile.is-active').length,1);
        assert.match(d.querySelector('tbody').textContent,/V009/);
    }finally{dom.window.close();}
});
