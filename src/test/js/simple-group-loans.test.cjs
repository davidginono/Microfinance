const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const source = fs.readFileSync('src/main/resources/static/js/simple-group-loans.js','utf8');
function setup({editing=false}={}) {
  const dom = new JSDOM(`<form data-simple-group data-group-root="/finance/loan-recording/groups">
    <select name="loanFor"><option>GROUP</option><option>INDIVIDUAL</option></select><div data-individual-loan><input name="clientId"></div>
    <div data-simple-group-fields><select id="simple-group"><option value="">Choose</option><option value="g1">Group 1</option><option value="g2">Group 2</option></select>
    <input name="groupApplication.expectedVersion"><input type="date" name="groupApplication.signedOn">
    <p data-group-load-error hidden><button type="button" data-group-retry>Retry</button></p>
    <details data-group-debts><div data-group-debt-list></div><button type="button" data-add-group-debt>Add debt</button><template data-group-debt-template><div data-group-debt><input data-debt-field="reference"><select data-debt-field="clientIds" multiple></select><button type="button" data-remove-group-debt>Remove</button></div></template></details></div>
    <section id="recording-panel-collateral"><div data-group-confirmation><p>Group responsibility</p><input type="checkbox" name="groupApplication.checked" required></div></section>
    <section id="recording-panel-attachments"><div data-group-agreement-upload><input type="hidden" name="groupApplication.removeAgreement" value="false"><label><input type="file" name="groupAgreement" data-error="Choose a PDF up to 10 MB"></label><p data-group-agreement-error hidden></p><div data-group-agreement-selected></div><template data-group-agreement-template><div><span data-group-agreement-name></span><span data-group-agreement-size></span><button type="button" data-remove-group-agreement data-remove-label="Remove"><svg><path d="M6 6l12 12M18 6L6 18"/></svg></button></div></template></div><input type="file" name="attachments" multiple></section>
    <button type="button" data-recording-draft>Save draft</button></form>`,{runScripts:'outside-only'});
  const w=dom.window, form=w.document.querySelector('form');
  if(editing){
    const fixedGroup=w.document.createElement('input');
    Object.assign(fixedGroup,{type:'hidden',id:'simple-group',name:'groupApplication.groupId',value:'g1'});
    form.querySelector('#simple-group').replaceWith(fixedGroup);
    form.querySelector('[data-group-load-error]').remove();
    form.elements['groupApplication.expectedVersion'].value='7';
    form.querySelector('[data-group-agreement-selected]').insertAdjacentHTML('afterend','<div data-group-agreement-saved><button type="button" data-remove-group-agreement>Remove saved agreement</button></div>');
  }
  const context={setup:{version:7,leader:'Asha',holder:'Group',provider:'Bank',identifier:'123',people:[{id:'c1',number:'C1',name:'Asha'},{id:'c2',number:'C2',name:'Juma'}]},borrowers:{c1:{incomeEvidenceMissing:false,missingProfileFields:[]},c2:{incomeEvidenceMissing:true,missingProfileFields:['monthlyExpenses']}}};
  w.WorkspaceNavigation={onReady:fn=>fn(w.document)};w.fetch=async()=>({ok:true,json:async()=>context});w.eval(source);
  const agreement=form.elements.groupAgreement;let selectedFiles=[];
  Object.defineProperty(agreement,'files',{get:()=>selectedFiles,set:files=>{selectedFiles=Array.from(files);}});
  Object.defineProperty(agreement,'value',{get:()=>selectedFiles.length?'C:\\fakepath\\'+selectedFiles[0].name:'',set:value=>{if(value==='')selectedFiles=[];}});
  function choose(file){agreement.files=[file];agreement.dispatchEvent(new w.Event('change',{bubbles:true}));}
  const select=w.document.querySelector('#simple-group');
  async function load(){select.value='g1';select.dispatchEvent(new w.Event('change',{bubbles:true}));await new Promise(resolve=>setTimeout(resolve,0));}
  return {dom,w,form,select,context,load,choose};
}
test('editing a recorded group initializes debt and saved agreement controls with the group fixed',()=>{
  const s=setup({editing:true});
  s.form.querySelector('[data-add-group-debt]').click();
  assert.equal(s.form.querySelectorAll('[data-group-debt-list] [data-group-debt]').length,1);
  assert.equal(s.form.elements['groupApplication.groupId'].value,'g1');
  assert.equal(s.form.elements['groupApplication.expectedVersion'].value,'7');
  s.form.querySelector('[data-group-agreement-saved] button').click();
  assert.equal(s.form.elements['groupApplication.removeAgreement'].value,'true');
  assert.equal(s.form.querySelector('[data-group-agreement-saved]').hidden,true);
  s.dom.window.close();
});
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

test('the agreement stays in Attachments across steps and the X clears the native file input',()=>{
  const s=setup(),file=new s.w.File(['%PDF- synthetic'],'signed-group.pdf',{type:'application/pdf'});s.choose(file);
  assert.equal(s.form.elements.groupAgreement.closest('section').id,'recording-panel-attachments');
  assert.match(s.form.querySelector('[data-group-agreement-selected]').textContent,/signed-group.pdf/);
  const remove=s.form.querySelector('[data-group-agreement-selected] [data-remove-group-agreement]');assert.equal(remove.getAttribute('aria-label'),'Remove signed-group.pdf');assert.ok(remove.querySelector('svg'));
  s.form.elements.loanFor.value='INDIVIDUAL';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(s.form.elements.groupAgreement.disabled,true);
  s.form.elements.loanFor.value='GROUP';s.form.elements.loanFor.dispatchEvent(new s.w.Event('change',{bubbles:true}));assert.equal(s.form.elements.groupAgreement.files[0],file);
  remove.click();assert.equal(s.form.elements.groupAgreement.files.length,0);assert.equal(s.form.querySelector('[data-group-agreement-selected]').children.length,0);assert.equal(s.form.elements['groupApplication.removeAgreement'].value,'true');
  assert.equal(s.form.elements.attachments.files.length,0);s.dom.window.close();
});
test('invalid agreement selections can be removed or replaced without blocking a corrected file',()=>{
  const s=setup();s.choose(new s.w.File(['bad'],'image.png',{type:'image/png'}));assert.equal(s.form.elements.groupAgreement.validity.customError,true);assert.equal(s.form.querySelector('[data-group-agreement-error]').hidden,false);
  s.form.querySelector('[data-group-agreement-selected] [data-remove-group-agreement]').click();assert.equal(s.form.elements.groupAgreement.validity.valid,true);
  s.choose(new s.w.File(['%PDF good'],'good.pdf',{type:'application/pdf'}));assert.equal(s.form.elements.groupAgreement.validity.valid,true);assert.equal(s.form.querySelector('[data-group-agreement-error]').hidden,true);s.dom.window.close();
});
test('changing groups clears the agreement token and marks any saved agreement for removal',async()=>{
  const s=setup();s.choose(new s.w.File(['%PDF'],'signed.pdf',{type:'application/pdf'}));await s.load();assert.equal(s.form.elements.groupAgreement.files.length,0);assert.equal(s.form.querySelector('[data-group-agreement-selected]').children.length,0);assert.equal(s.form.elements['groupApplication.removeAgreement'].value,'true');s.dom.window.close();
});
