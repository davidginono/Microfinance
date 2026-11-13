const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const source = fs.readFileSync('src/main/resources/static/js/simple-group-loans.js','utf8');
function setup() {
  const dom = new JSDOM(`<form data-simple-group data-group-root="/finance/loan-recording/groups" data-leader-label="Leader" data-source-label="Income evidence reference" data-profile-missing="Update client profile:">
    <select name="loanFor"><option>GROUP</option><option>INDIVIDUAL</option></select><div data-individual-loan><input name="clientId"></div>
    <div data-simple-group-fields><select id="simple-group"><option value="">Choose</option><option value="g1">Group 1</option><option value="g2">Group 2</option></select>
    <input name="groupApplication.expectedVersion"><input type="date" name="groupApplication.signedOn"><input type="checkbox" name="groupApplication.checked"><input type="file" name="groupAgreement">
    <span data-profile-label="monthlyExpenses">Monthly expenses</span><div data-group-roster></div><p data-group-load-error hidden><button type="button" data-group-retry>Retry</button></p>
    <details data-group-debts><div data-group-debt-list></div><button type="button" data-add-group-debt>Add debt</button><template data-group-debt-template><div data-group-debt><input data-debt-field="reference"><select data-debt-field="clientIds" multiple></select><button type="button" data-remove-group-debt>Remove</button></div></template></details></div>
    <button type="button" data-recording-draft>Save draft</button></form>`,{runScripts:'outside-only'});
  const w=dom.window, form=w.document.querySelector('form');
  const context={setup:{version:7,leader:'Asha',holder:'Group',provider:'Bank',identifier:'123',people:[{id:'c1',number:'C1',name:'Asha'},{id:'c2',number:'C2',name:'Juma'}]},borrowers:{c1:{incomeEvidenceMissing:false,missingProfileFields:[]},c2:{incomeEvidenceMissing:true,missingProfileFields:['monthlyExpenses']}}};
  w.WorkspaceNavigation={onReady:fn=>fn(w.document)};w.fetch=async()=>({ok:true,json:async()=>context});w.eval(source);
  const select=w.document.querySelector('#simple-group');
  async function load(){select.value='g1';select.dispatchEvent(new w.Event('change',{bubbles:true}));await new Promise(resolve=>setTimeout(resolve,0));}
  return {dom,w,form,select,context,load};
}
test('group selection asks only for missing evidence and lists missing profile values',async()=>{
  const s=setup();await s.load();const rows=s.form.querySelectorAll('.simple-group-borrower');
  assert.equal(rows.length,2);assert.equal(rows[0].querySelectorAll('input:not([type="hidden"])').length,0);
  assert.equal(rows[1].querySelector('input[type="text"]').name,'groupApplication.signers[1].incomeSource');
  assert.match(rows[1].textContent,/Update client profile: Monthly expenses/);
  assert.equal(s.form.querySelectorAll('input[type="date"]').length,1);
  assert.equal(s.form.querySelectorAll('[name$=".evidence"]').length,0);
  assert.equal(s.form.elements['groupApplication.expectedVersion'].value,'7');
  assert.equal(s.form.querySelector('[data-group-roster]').hasAttribute('aria-busy'),false);s.dom.window.close();
});
test('switching loan type preserves missing evidence and disables the hidden group fields',async()=>{
  const s=setup();await s.load();const input=s.form.querySelector('input[type="text"]');input.value='Checked income document';
  s.form.elements.loanFor.value='INDIVIDUAL';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(input.disabled,true);
  s.form.elements.loanFor.value='GROUP';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(input.disabled,false);assert.equal(input.value,'Checked income document');s.dom.window.close();
});
test('failed group lookup can be retried without navigating away',async()=>{
  const s=setup();s.w.fetch=async()=>({ok:false});await s.load();assert.equal(s.form.querySelector('[data-group-load-error]').hidden,false);
  s.w.fetch=async()=>({ok:true,json:async()=>s.context});s.form.querySelector('[data-group-retry]').click();await new Promise(resolve=>setTimeout(resolve,0));
  assert.equal(s.form.querySelector('[data-group-load-error]').hidden,true);assert.equal(s.form.querySelectorAll('.simple-group-borrower').length,2);s.dom.window.close();
});
