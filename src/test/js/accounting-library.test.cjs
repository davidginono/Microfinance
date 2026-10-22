const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/accounting-library.js','utf8');

test('activity configuration command follows selection, protects system records and resets on table queries',()=>{
 const dom=new JSDOM(`<section id="library-register"><button id="library-view" disabled>Transactions</button><button id="library-edit" disabled data-url="/finance/library/activities/">Edit configuration</button><span id="library-selection-status" data-selected="Selected" data-empty="None"></span><table><tbody><tr data-library-row data-id="ordinary" data-system="false" data-url="/transactions"><td><input type="checkbox" name="library-selection"></td><td data-library-code>OPS</td><td data-library-name>Operations</td></tr><tr data-library-row data-id="system" data-system="true" data-url="/transactions/system"><td><input type="checkbox" name="library-selection"></td><td data-library-code>SYSTEM.LOAN_REPAYMENT</td><td data-library-name>Repayment</td></tr></tbody></table></section>`,{url:'http://localhost/finance/library',runScripts:'dangerously'});
 try {
  const w=dom.window,d=w.document,visits=[];w.WorkspaceNavigation={navigate:url=>visits.push(url)};w.eval(source);
  const edit=d.querySelector('#library-edit'),inputs=d.querySelectorAll('input');assert.equal(edit.disabled,true);
  inputs[0].checked=true;inputs[0].dispatchEvent(new w.Event('change',{bubbles:true}));edit.click();assert.deepEqual(visits,['/finance/library/activities/ordinary/edit']);
  inputs[1].checked=true;inputs[1].dispatchEvent(new w.Event('change',{bubbles:true}));assert.equal(edit.disabled,true);edit.click();assert.equal(visits.length,1);
  d.querySelector('#library-register').dispatchEvent(new w.Event('resource-table:reset'));assert.equal(edit.disabled,true);assert.equal([...inputs].some(i=>i.checked),false);
 } finally {dom.window.close();}
});
