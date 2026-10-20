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
    const {dom,w,d}=await setup('<nav id="appSidebar"><a href="/app">Navigation</a></nav><section id="card" aria-busy="true">Summary</section>');
    w.AppLoading.begin(d.querySelector('a'),{table:false});w.AppLoading.begin(d.querySelector('#card'),{table:false});
    assert.equal(d.querySelector('.app-loading-spinner'),null);assert.equal(d.querySelector('.app-loading-status'),null);
    assert.equal(w.AppLoading.status('Loading').textContent,'Loading');dom.window.close();
});

test('opted-in async previews keep native controls and clear local feedback on completion and restoration',async()=>{
    const {dom,w,d}=await setup('<section data-ui-loading-region><h2>Quote</h2><input value="draft"><p id="value">—</p></section>');
    const region=d.querySelector('section'),input=d.querySelector('input');input.value='edited';
    region.setAttribute('aria-busy','true');await new Promise(resolve=>setTimeout(resolve,0));
    assert.equal(region.querySelectorAll('.app-loading-spinner').length,1);
    assert.equal(region.querySelector('h2').nextElementSibling.getAttribute('role'),'status');
    assert.equal(region.querySelector('input'),input);assert.equal(input.value,'edited');
    region.setAttribute('aria-busy','false');await new Promise(resolve=>setTimeout(resolve,0));
    assert.equal(region.querySelector('[role=status]'),null);
    region.setAttribute('aria-busy','true');await new Promise(resolve=>setTimeout(resolve,0));
    w.dispatchEvent(new w.Event('pagehide'));
    assert.equal(region.querySelector('[role=status]'),null);assert.equal(region.getAttribute('aria-busy'),'false');
    dom.window.close();
});

test('table search cannot hide a pending spinner or announce no matches before the request settles',async()=>{
    const {dom,w,d}=await setup('<template id="resource-table-labels" data-records="Records" data-refresh="Refresh" data-search-page="Search this page" data-no-matches="No matching records"></template><section class="erp-table-wrap" data-resource-table="pending-search"><table><thead><tr><th>ID</th></tr></thead><tbody><tr><td>L001</td></tr></tbody></table></section>');
    w.eval(readFileSync('src/main/resources/static/js/resource-table.js','utf8'));w.ResourceTable.init();
    const region=d.querySelector('section');w.AppLoading.begin(region);const input=region.querySelector('input[type=search]');input.value='missing';input.dispatchEvent(new w.Event('input'));
    assert.equal(d.querySelector('.app-loading-row').hidden,false);assert.equal(d.querySelector('.resource-table-no-matches'),null);
    w.AppLoading.end(region);await new Promise(resolve=>setTimeout(resolve,0));assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);dom.window.close();
});

test('independent section failures clear only their spinner and retry without replacing entered controls',async()=>{
    const {dom,w,d}=await setup('<section id="one" data-ui-loading-region><h2>Values</h2><input name="amount" value="12.34"></section><section id="two" data-ui-loading-region><h2>Other values</h2></section>');
    try{
        d.body.dataset.navigationRetry='Retry';const one=d.querySelector('#one'),two=d.querySelector('#two'),input=one.querySelector('input');input.value='56.78';
        w.AppLoading.begin(one,{table:false});w.AppLoading.begin(two,{table:false});
        w.AppLoading.fail(one,{label:'Unable to load',retry:()=>w.AppLoading.begin(one,{table:false})});
        assert.equal(one.querySelector('.app-loading-spinner'),null);assert.equal(two.querySelectorAll('.app-loading-spinner').length,1);
        assert.equal(one.querySelector('[role=alert]').textContent,'Unable to loadRetry');one.querySelector('[role=alert] button').click();
        assert.equal(one.querySelector('[role=alert]'),null);assert.equal(one.querySelectorAll('.app-loading-spinner').length,1);
        assert.equal(one.querySelector('input'),input);assert.equal(input.value,'56.78');w.AppLoading.end(one);w.AppLoading.end(two);
        assert.equal(d.querySelector('.app-loading-spinner'),null);
    }finally{dom.window.close();}
});

test('value and chart loaders have silent visual labels, distinct sizes and restore ready native content on cancellation',async()=>{
 const {dom,w,d}=await setup('<section><dd data-ui-loading-region>42</dd><div id="chart" data-ui-loading-region><button>Series</button></div></section>');
 try{
  const value=d.querySelector('dd'),chart=d.querySelector('#chart'),button=chart.querySelector('button');
  w.AppLoading.begin(value,{table:false,replace:true,size:'small',label:'Loading values'});
  w.AppLoading.begin(chart,{table:false,replace:true,size:'large',label:'Loading chart'});
  for(const target of [value,chart]){assert.equal(target.querySelector('[role=status]').textContent,'');assert.ok(target.querySelector(':scope > span[hidden]'));}
  assert.equal(value.querySelector('.app-loading-spinner').dataset.loadingSize,'small');assert.equal(chart.querySelector('.app-loading-spinner').dataset.loadingSize,'large');
  assert.equal(value.querySelector('[role=status]').getAttribute('aria-label'),'Loading values');
  value.textContent='73';w.AppLoading.end(value);assert.equal(value.textContent,'73');assert.equal(chart.querySelectorAll('.app-loading-spinner').length,1);
  w.AppLoading.end(chart);assert.equal(chart.querySelector('button'),button);assert.equal(button.closest('[hidden]'),null);
  assert.equal(d.querySelector('.app-loading-spinner'),null);
 }finally{dom.window.close();}
});


test('button request feedback stays inside its button and clears independently on completion or page exit',async()=>{
 const {dom,w,d}=await setup('<header>Header</header><main><button id="pdf">PDF</button><button id="print" disabled>Print</button><input value="draft"></main>');try{
  const pdf=d.querySelector('#pdf'),print=d.querySelector('#print'),one=w.AppLoading.action(pdf),two=w.AppLoading.action(print);
  assert.equal(d.querySelectorAll('[data-ui-action-loading]').length,2);assert.equal(pdf.querySelector('[data-ui-action-loading]').textContent,'');assert.equal(pdf.querySelector('[data-ui-action-loading]').getAttribute('role'),'status');
  assert.equal(pdf.classList.contains('is-action-loading'),true);assert.equal(pdf.querySelector('.app-action-label').textContent,'PDF');
  assert.equal(d.body.hasAttribute('aria-busy'),false);assert.equal(d.querySelector('header').inert,undefined);assert.equal(print.disabled,true);
  one();assert.equal(d.querySelectorAll('[data-ui-action-loading]').length,1);assert.equal(pdf.hasAttribute('aria-busy'),false);assert.equal(pdf.textContent,'PDF');assert.equal(pdf.querySelector('.app-action-label'),null);assert.equal(pdf.classList.contains('is-action-loading'),false);
  w.dispatchEvent(new w.Event('pagehide'));two();assert.equal(d.querySelector('[data-ui-action-loading]'),null);assert.equal(print.disabled,true);assert.equal(d.querySelector('input').value,'draft');
 }finally{dom.window.close();}
});

test('button loading preserves existing icons, native listeners and updated labels across retries',async()=>{
 const {dom,w,d}=await setup('<button class="btn-launch" id="save"><span id="icon" aria-hidden="true">+</span> Save configuration</button>');try{
  const save=d.querySelector('#save'),icon=d.querySelector('#icon');let clicks=0;icon.addEventListener('click',()=>clicks++);
  const first=w.AppLoading.action(save),second=w.AppLoading.action(save);first();
  assert.equal(save.querySelectorAll('[data-ui-action-loading]').length,1);assert.equal(save.querySelectorAll('.app-action-label').length,1);
  assert.equal(save.querySelector('#icon'),icon);second();assert.equal(save.firstElementChild,icon);icon.click();assert.equal(clicks,1);
  const finish=w.AppLoading.action(save);save.textContent='Saved';finish();assert.equal(save.textContent,'Saved');assert.equal(save.classList.contains('is-action-loading'),false);
 }finally{dom.window.close();}
});
