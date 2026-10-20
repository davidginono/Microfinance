const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {JSDOM}=require('jsdom');

for(const activity of ['activity-a','UNASSIGNED'])test('report generation and refresh preserve the selected activity without carrying an old cutoff or page',()=>{
  const dom=new JSDOM(`<form action="/reports/financial/sl-gl-reconciliation" data-reconciliation-query>
    <input name="run" value="true"><input name="cutoff" value="old-cutoff">
    <input name="from" value="2026-10-01"><input name="through" value="2026-10-19">
    <select name="activityId"><option value="${activity}" selected>Configured activity</option></select>
  </form><section data-resource-table="sl-gl-reconciliation"></section>`,{url:'http://localhost/reports/financial/sl-gl-reconciliation'});
  try{
    let destination;const location={href:`http://localhost/reports/financial/sl-gl-reconciliation?activityId=${activity}&page=3&cutoff=old-cutoff`,assign:value=>destination=new URL(value)};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/sl-gl-reconciliation.js','utf8'),{document:dom.window.document,location,URL,FormData:dom.window.FormData,window:{WorkspaceNavigation:{navigate:value=>destination=new URL(value)}}});
    dom.window.document.querySelector('form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    assert.equal(destination.searchParams.get('activityId'),activity);assert.equal(destination.searchParams.get('cutoff'),null);assert.equal(destination.searchParams.get('page'),null);
    dom.window.document.querySelector('section').dispatchEvent(new dom.window.CustomEvent('resource-table:refresh',{bubbles:true,cancelable:true}));
    assert.equal(destination.searchParams.get('activityId'),activity);assert.equal(destination.searchParams.get('cutoff'),null);assert.equal(destination.searchParams.get('page'),'0');
  }finally{dom.window.close();}
});
