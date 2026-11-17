const {test}=require('node:test');const assert=require('node:assert/strict');const {readFileSync}=require('node:fs');const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/dashboard-charts.js','utf8');
function setup(){const dom=new JSDOM('<template id="dashboard-labels" data-view-data="View data" data-series="Show" data-no-data="Unavailable" data-no-activity="No activity" data-no-series="Choose a series"></template><div id="chart"></div>',{runScripts:'outside-only',pretendToBeVisual:true});dom.window.eval(source);return {dom,w:dom.window,d:dom.window.document,root:dom.window.document.querySelector('#chart')};}
test('charts start at zero, retain missing points as unavailable and expose a readable data table',()=>{const {dom,w,d,root}=setup();w.DashboardCharts.render(root,{type:'line',title:'Size',labels:['A','B','C'],series:[{label:'Size',values:[5,null,10]}],unit:'MB',integer:false});assert.ok(d.querySelector('svg text').textContent==='0');assert.equal(d.querySelectorAll('circle').length,2);assert.equal(d.querySelectorAll('tbody td')[1].textContent,'—');assert.match(d.querySelector('path').getAttribute('d'),/M.*M/);assert.equal(d.querySelector('summary').textContent,'View data');dom.window.close();});
test('zero and unavailable data do not fabricate bars or convert missing values to zero',()=>{const {dom,w,d,root}=setup();w.DashboardCharts.render(root,{type:'bar',labels:['A'],series:[{label:'Clients',values:[0]}],integer:true});assert.equal(d.querySelector('.dashboard-chart-empty').textContent,'No activity');assert.equal(d.querySelector('rect'),null);w.DashboardCharts.render(root,{type:'bar',labels:['A'],series:[{label:'Clients',values:[null]}],integer:true});assert.equal(d.querySelector('.dashboard-chart-empty').textContent,'Unavailable');assert.equal(d.querySelector('tbody td').textContent,'—');w.DashboardCharts.render(root,{type:'bar',labels:['A','B'],series:[{label:'Clients',values:[0,null]}],integer:true});assert.equal(d.querySelector('.dashboard-chart-empty').textContent,'Unavailable');dom.window.close();});
test('legend controls change the visible series while the data alternative retains exact values',()=>{const {dom,w,d,root}=setup();w.DashboardCharts.render(root,{type:'bar',title:'Activity',labels:['Today'],series:[{label:'Logins',values:[3]},{label:'Views',values:[10]}],integer:true});const control=d.querySelector('.dashboard-chart-legend button');assert.equal(d.querySelectorAll('svg [data-series]').length,2);control.click();assert.equal(control.getAttribute('aria-pressed'),'false');assert.equal(d.querySelectorAll('svg [data-series]').length,1);assert.equal(d.querySelector('tbody td').textContent,'3');assert.equal(d.querySelector('svg').getAttribute('aria-label'),'Activity');dom.window.close();});

test('chart points use one tab stop and arrow keys announce exact values without trapping Tab',()=>{const {dom,w,d,root}=setup();w.DashboardCharts.render(root,{type:'bar',title:'Logins',labels:['A','B','C'],series:[{label:'Logins',values:[1,2,3]}],integer:true});const points=d.querySelectorAll('[data-point-key]');assert.equal(d.querySelectorAll('[data-point-key][tabindex="0"]').length,1);points[0].focus();points[0].dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));assert.equal(d.activeElement,points[1]);assert.match(d.querySelector('.dashboard-chart-tooltip').textContent,/B: Logins 2/);points[1].dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));assert.equal(d.activeElement,d.querySelector('.dashboard-chart-legend button'));dom.window.close();});

test('hiding all chart series prompts a display choice while retaining actual data',()=>{const {dom,w,d,root}=setup();w.DashboardCharts.render(root,{type:'bar',labels:['Today'],series:[{label:'Logins',values:[3]}],integer:true});d.querySelector('.dashboard-chart-legend button').click();assert.equal(d.querySelector('.dashboard-chart-empty').textContent,'Choose a series');assert.equal(d.querySelector('tbody td').textContent,'3');dom.window.close();});

test('unchanged polling keeps chart, legend, data rows and focused controls mounted',()=>{
 const {dom,w,d,root}=setup();try{
  const data={type:'bar',labels:['Today'],series:[{label:'Logins',values:[3]}],integer:true};w.DashboardCharts.render(root,data);
  const chart=root.querySelector('svg'),legend=root.querySelector('button'),row=root.querySelector('tbody tr'),details=root.querySelector('details');details.open=true;legend.focus();
  const observer=new w.MutationObserver(()=>{});observer.observe(root,{childList:true,subtree:true});w.DashboardCharts.render(root,JSON.parse(JSON.stringify(data)));
  assert.equal(root.querySelector('svg'),chart);assert.equal(root.querySelector('button'),legend);assert.equal(root.querySelector('tbody tr'),row);assert.equal(d.activeElement,legend);assert.equal(details.open,true);assert.equal(observer.takeRecords().length,0);observer.disconnect();
 }finally{w.close();}
});
test('changed values replace the graphic atomically while preserving legend focus and the open data view',()=>{
 const {dom,w,d,root}=setup();try{
  const data={type:'bar',labels:['Today'],series:[{label:'Logins',values:[3]}],integer:true};w.DashboardCharts.render(root,data);
  const graphic=root.firstElementChild,legend=root.querySelector('button'),details=root.querySelector('details');details.open=true;legend.focus();
  const observer=new w.MutationObserver(()=>{});observer.observe(graphic,{childList:true});w.DashboardCharts.render(root,{...data,series:[{label:'Logins',values:[4]}]});
  const mutations=observer.takeRecords();assert.equal(mutations.length,1);assert.equal(mutations[0].addedNodes[0].tagName,'svg');assert.equal(mutations[0].removedNodes[0].tagName,'svg');
  assert.equal(root.querySelector('button'),legend);assert.equal(d.activeElement,legend);assert.equal(details.open,true);assert.equal(root.querySelector('tbody td').textContent,'4');observer.disconnect();
 }finally{w.close();}
});
test('content-height notifications do not redraw a chart; an actual width change does',()=>{
 const {dom,w,root}=setup();try{
  const observers=[];w.ResizeObserver=class{constructor(callback){observers.push(callback);}observe(){}disconnect(){}};
  let width=400;root.getBoundingClientRect=()=>({width});w.DashboardCharts.render(root,{type:'bar',labels:['Today'],series:[{label:'Logins',values:[3]}]});w.DashboardCharts.init();
  const chart=root.querySelector('svg');observers[0]();assert.equal(root.querySelector('svg'),chart);
  width=600;observers[0]();assert.notEqual(root.querySelector('svg'),chart);assert.match(root.querySelector('svg').getAttribute('viewBox'),/0 0 600 /);
 }finally{w.close();}
});
test('cleared chart regions can render again even when the returning values are unchanged',()=>{
 const {w,root}=setup();try{const data={type:'bar',labels:['Today'],series:[{label:'Logins',values:[3]}]};w.DashboardCharts.render(root,data);root.replaceChildren();w.DashboardCharts.render(root,data);assert.equal(root.querySelectorAll('rect').length,1);}finally{w.close();}
});
