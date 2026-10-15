const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
async function setup(html){
    const dom=new JSDOM('<body data-shell-loading="Loading">'+html+'</body>',{url:'http://localhost/app/dashboard',runScripts:'outside-only',pretendToBeVisual:true});
    dom.window.eval(readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));
    if(dom.window.document.readyState!=='complete')await new Promise(resolve=>dom.window.addEventListener('load',resolve,{once:true}));
    return {dom,w:dom.window,d:dom.window.document};
}
test('table loading preserves native rows and tools, and uses a centered status row in the canvas',async()=>{
    const {dom,w,d}=await setup('<section class="erp-table-wrap"><div class="resource-table-tools"><input name="query" value="record"></div><table><thead><tr><th>ID</th><th>Value</th></tr></thead><tbody><tr id="record"><td>L001</td><td><button id="view">View</button></td></tr></tbody></table></section>');
    const region=d.querySelector('section'),record=d.querySelector('#record'),view=d.querySelector('#view');let clicks=0;view.onclick=()=>clicks++;
    w.AppLoading.begin(region);w.AppLoading.begin(region);
    assert.equal(d.querySelectorAll('.app-loading-row').length,1);assert.equal(d.querySelector('.app-loading-row td').colSpan,2);
    assert.equal(d.querySelector('.resource-table-tools [role=status]'),null);assert.equal(region.getAttribute('aria-busy'),'true');assert.equal(record.hidden,true);
    view.click();assert.equal(clicks,1);assert.equal(d.querySelector('input').value,'record');
    w.AppLoading.end(region);assert.equal(d.querySelector('#record'),record);assert.equal(d.querySelector('[role=status]'),null);assert.equal(region.hasAttribute('aria-busy'),false);dom.window.close();
});
test('initial empty placeholders recover on failure but cannot reappear after real results replace them',async()=>{
    const {dom,w,d}=await setup('<section class="erp-table-wrap"><table><thead><tr><th>ID</th><th>Value</th></tr></thead><tbody><tr id="empty"><td colspan="2">No records</td></tr></tbody></table></section>');
    const region=d.querySelector('section'),empty=d.querySelector('#empty');w.AppLoading.begin(region);assert.equal(empty.hidden,true);
    w.AppLoading.end(region);assert.equal(empty.hidden,false);
    w.AppLoading.begin(region);d.querySelector('tbody').innerHTML='<tr><td>L001</td><td>Ready</td></tr>';w.AppLoading.end(region);
    assert.equal(d.querySelector('#empty'),null);assert.equal(d.querySelector('tbody').textContent,'L001Ready');assert.equal(region.classList.contains('is-loading'),false);dom.window.close();
});
test('sidebar and dashboard regions never acquire component spinners',async()=>{
    const {dom,w,d}=await setup('<nav id="appSidebar"><a href="/app">Navigation</a></nav><section id="card" data-ui-loading-region aria-busy="true">Summary</section>');
    w.AppLoading.begin(d.querySelector('a'),{table:false});w.AppLoading.begin(d.querySelector('#card'),{table:false});
    assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(d.querySelector('.app-loading-status'),null);
    assert.equal(w.AppLoading.status('Loading').textContent,'Loading');dom.window.close();
});

test('table search cannot hide a pending spinner or announce no matches before the request settles',async()=>{
    const {dom,w,d}=await setup('<template id="resource-table-labels" data-records="Records" data-refresh="Refresh" data-search-page="Search this page" data-no-matches="No matching records"></template><section class="erp-table-wrap" data-resource-table="pending-search"><table><thead><tr><th>ID</th></tr></thead><tbody><tr><td>L001</td></tr></tbody></table></section>');
    w.eval(readFileSync('src/main/resources/static/js/resource-table.js','utf8'));w.ResourceTable.init();
    const region=d.querySelector('section');w.AppLoading.begin(region);const input=region.querySelector('input[type=search]');input.value='missing';input.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelector('.app-loading-row').hidden,false);assert.equal(d.querySelector('.resource-table-no-matches'),null);
    w.AppLoading.end(region);await new Promise(resolve=>setTimeout(resolve,0));assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);dom.window.close();
});
