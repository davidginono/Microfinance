const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/resources/static/js/resource-table.js','utf8');
const labels = '<template id="resource-table-labels" data-records="Records" data-refresh="Refresh" data-actions="Actions" data-search-page="Search" data-preferences="Preferences"></template>';
const rows = '<div class="erp-table-scroll"><table><thead><tr><th>Code</th><th>Name</th></tr></thead><tbody><tr><td>1</td><td>Main</td></tr></tbody></table></div>';
async function setup(html) {
    const dom = new JSDOM(labels+html,{url:'http://localhost/register',runScripts:'outside-only',pretendToBeVisual:true});
    const w=dom.window; w.eval(source);
    if(w.document.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
    return {dom,w,d:w.document};
}
test('referenced page titles appear only inside the generated table header and retain their accessible ID',async()=>{
    const {dom,w,d}=await setup('<nav class="erp-page-path">Admin / Notifications</nav><div class="erp-page-header"><div><h1 id="notifications-title" class="erp-page-title">Notifications</h1></div></div><section data-resource-table="notifications" data-table-title-id="notifications-title" aria-labelledby="notifications-title">'+rows+'</section>');
    try {
        assert.equal(d.querySelector('h1'),null);assert.equal(d.querySelector('.erp-page-header'),null);
        assert.equal(d.querySelector('.erp-page-path').textContent,'Admin / Notifications');
        const heading=d.querySelector('.app-table-titlebar h2');assert.equal(heading.id,'notifications-title');
        assert.equal(heading.hasAttribute('data-workspace-title'),true);assert.match(heading.textContent,/Notifications/);
        assert.equal(d.querySelector('[data-resource-table]').getAttribute('aria-labelledby'),heading.id);
        assert.equal(d.querySelectorAll('#notifications-title').length,1);
        const actions=d.querySelector('.resource-table-actions');w.ResourceTable.init();assert.equal(d.querySelector('.resource-table-actions'),actions);
    }finally{dom.window.close();}
});
test('authored matching table headings keep their title, authoritative count, controls and breadcrumbs',async()=>{
    const {dom,d}=await setup('<div class="erp-page-header"><nav class="erp-page-path">Users</nav><h1 class="erp-page-title" id="users-title">Users &amp; Roles</h1><button id="independent">Help</button></div><section data-resource-table="users"><div class="app-table-titlebar"><h2>Users &amp; Roles <span class="app-table-count">(90)</span></h2><div class="app-table-toolbar"><a id="create" class="app-btn" href="/create">Create</a></div></div>'+rows+'</section>');
    try {
        assert.equal(d.querySelector('h1'),null);assert.equal(d.querySelector('h2').id,'users-title');
        assert.equal(d.querySelector('.app-table-count').textContent,'(90)');
        assert.ok(d.getElementById('independent'));assert.ok(d.querySelector('.erp-page-path'));
        assert.equal(d.getElementById('create').closest('.resource-table-actions')!==null,true);
        assert.equal(d.querySelectorAll('.resource-table-tools').length,1);
    }finally{dom.window.close();}
});
test('distinct detail titles, dashboard headings and dialog titles are retained',async()=>{
    const {dom,d}=await setup('<h1 class="erp-page-title">Client details</h1><section data-resource-table="loans"><div class="app-table-titlebar"><h2>Loans</h2></div>'+rows+'</section><section data-dashboard-widget="summary"><h1>Loans</h1><section data-resource-table="dashboard"><div class="app-table-titlebar"><h2>Loans</h2></div>'+rows+'</section></section><div role="dialog"><h1 class="erp-page-title" id="dialog-title">Definitions</h1><section data-resource-table="dialog" data-table-title-id="dialog-title">'+rows+'</section></div>');
    try {assert.equal(d.querySelectorAll('h1').length,3);}finally{dom.window.close();}
});
test('legacy table summaries also retire repeated page headings without adding table tools',async()=>{
    const {dom,d}=await setup('<h1 class="erp-page-title">Branches</h1><section class="erp-table-wrap"><div class="app-table-titlebar"><h2>Branches</h2></div>'+rows+'</section>');
    try {assert.equal(d.querySelector('h1'),null);assert.equal(d.querySelectorAll('h2').length,1);assert.equal(d.querySelector('.app-table-count').textContent,'(1)');assert.equal(d.querySelector('.resource-table-tools'),null);}finally{dom.window.close();}
});
