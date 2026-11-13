const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const source = fs.readFileSync('src/main/resources/static/js/simple-group-loans.js','utf8');
function setup() {
  const dom = new JSDOM(`<form data-simple-group data-group-root="/finance/loan-recording/groups">
    <select name="loanFor"><option>GROUP</option><option>INDIVIDUAL</option></select><div data-individual-loan><input name="clientId"></div>
    <div data-simple-group-fields><select id="simple-group"><option value="">Choose</option><option value="g1">Group 1</option><option value="g2">Group 2</option></select>
    <input name="groupApplication.expectedVersion"><input type="date" name="groupApplication.signedOn"><input type="file" name="groupAgreement">
    <p data-group-load-error hidden><button type="button" data-group-retry>Retry</button></p>
    <details data-group-debts><div data-group-debt-list></div><button type="button" data-add-group-debt>Add debt</button><template data-group-debt-template><div data-group-debt><input data-debt-field="reference"><select data-debt-field="clientIds" multiple></select><button type="button" data-remove-group-debt>Remove</button></div></template></details></div>
    <section id="recording-panel-collateral"><div data-group-confirmation><p>Group responsibility</p><input type="checkbox" name="groupApplication.checked" required></div></section>
    <button type="button" data-recording-draft>Save draft</button></form>`,{runScripts:'outside-only'});
  const w=dom.window, form=w.document.querySelector('form');
  const context={setup:{version:7,leader:'Asha',holder:'Group',provider:'Bank',identifier:'123',people:[{id:'c1',number:'C1',name:'Asha'},{id:'c2',number:'C2',name:'Juma'}]},borrowers:{c1:{incomeEvidenceMissing:false,missingProfileFields:[]},c2:{incomeEvidenceMissing:true,missingProfileFields:['monthlyExpenses']}}};
  w.WorkspaceNavigation={onReady:fn=>fn(w.document)};w.fetch=async()=>({ok:true,json:async()=>context});w.eval(source);
  const select=w.document.querySelector('#simple-group');
  async function load(){select.value='g1';select.dispatchEvent(new w.Event('change',{bubbles:true}));await new Promise(resolve=>setTimeout(resolve,0));}
  return {dom,w,form,select,context,load};
}
test('group selection keeps names, financial details and income references out of recording',async()=>{
  const s=setup();await s.load();
  assert.equal(s.form.querySelectorAll('.simple-group-borrower,[name*=".signers["]').length,0);
  assert.equal(s.form.querySelectorAll('input[type="date"]').length,1);
  assert.doesNotMatch(s.form.textContent,/Update client profile|Monthly expenses|Income evidence reference|Leader:/);
  assert.equal(s.form.elements['groupApplication.expectedVersion'].value,'7');
  assert.equal(s.form.querySelector('[data-simple-group-fields]').hasAttribute('aria-busy'),false);
  s.form.querySelector('[data-add-group-debt]').click();
  assert.equal(s.form.querySelector('[data-group-debt-list] select').options.length,2);s.dom.window.close();
});

test('switching loan type keeps one shared signing date and disables the group fields',async()=>{
  const s=setup();await s.load();const input=s.form.elements['groupApplication.signedOn'];input.value='2026-11-13';const confirmation=s.form.elements['groupApplication.checked'];confirmation.checked=true;
  s.form.elements.loanFor.value='INDIVIDUAL';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(input.disabled,true);assert.equal(confirmation.disabled,true);assert.equal(confirmation.closest('[data-group-confirmation]').hidden,true);
  s.form.elements.loanFor.value='GROUP';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(input.disabled,false);assert.equal(input.value,'2026-11-13');assert.equal(confirmation.disabled,false);assert.equal(confirmation.checked,true);assert.equal(confirmation.closest('[data-group-confirmation]').hidden,false);s.dom.window.close();
});

test('failed group lookup can be retried without navigating away',async()=>{
  const s=setup();s.w.fetch=async()=>({ok:false});await s.load();assert.equal(s.form.querySelector('[data-group-load-error]').hidden,false);
  s.w.fetch=async()=>({ok:true,json:async()=>s.context});s.form.querySelector('[data-group-retry]').click();await new Promise(resolve=>setTimeout(resolve,0));
  assert.equal(s.form.querySelector('[data-group-load-error]').hidden,true);assert.equal(s.form.elements['groupApplication.expectedVersion'].value,'7');s.dom.window.close();
});

test('changing the selected group clears confirmation at the end of Collateral',async()=>{
  const s=setup();const checkbox=s.form.elements['groupApplication.checked'];checkbox.checked=true;
  assert.equal(checkbox.closest('section').id,'recording-panel-collateral');await s.load();
  assert.equal(checkbox.checked,false);assert.equal(checkbox.required,true);s.dom.window.close();
});
