const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const jsp=readFileSync('src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp','utf8');
const editor=jsp.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
const shared=readFileSync('src/main/resources/static/js/resource-table.js','utf8');
const split=readFileSync('src/main/resources/static/js/resource-split-view.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
async function setup(){
 const row=(feature,action,key,allowed)=>`<tr data-claim-row><td><input type="checkbox" data-claim-select aria-label="Select ${key}"></td><td>${feature}</td><td>${action}</td><td><code>${key}</code></td><td><input type="checkbox" name="claims" data-access-claim="${key}" value="${key}" ${allowed?'checked':''}><span data-claim-state data-allowed="Allowed" data-denied="Not allowed">${allowed?'Allowed':'Not allowed'}</span></td></tr>`;
 const labels=`<template id="resource-table-labels" data-search-page="Search claims" data-no-matches="No matching claims" data-select="Select" data-select-first="Select first row" data-clear-selection="Clear selection" data-details="Details" data-select-record="Select a claim" data-show-details="Show details" data-close="Close" data-panel-position="Panel position" data-bottom="Bottom" data-side="Side" data-resize="Resize details" data-preferences="Preferences" data-pagination="Pagination" data-page="Page" data-previous="Previous" data-next="Next"></template>`;
 const markup=`${labels}<script type="application/json" id="role-default-claims-json">{"MANAGER":["VOUCHER_APPROVAL_APPROVE"]}</script><script type="application/json" id="Member-default-claims-json">[]</script><form data-access-edit-form data-member-access="false" method="post"><input type="hidden" name="_csrf" value="kept"><input type="checkbox" data-staff-role-checkbox="edit-user" value="MANAGER" checked><section data-access-matrix data-resource-table="admin-user-claims" data-table-selection="single" data-resource-split data-aws-no-refresh="true"><div class="app-table-titlebar"><h2>Access matrix</h2><div class="accounting-actions"><button type="button" data-restore-default-claims>Restore defaults</button></div></div><div class="aws-filter-toolbar"><input type="search" data-aws-table-search></div><div class="erp-table-scroll"><table data-table-sort-mode="none"><thead><tr><th>Select</th><th>Feature</th><th>Action</th><th>Claim</th><th>Allowed</th></tr></thead><tbody>${row('Voucher approvals','Approve','VOUCHER_APPROVAL_APPROVE',false)}${row('Repayments','View','LOAN_REPAYMENTS_VIEW',true)}${row('Voucher approvals','Reject','VOUCHER_APPROVAL_REJECT',true)}</tbody></table></div></section></form>`;
 const dom=new JSDOM(markup,{url:'http://localhost/admin/users/1/edit',runScripts:'outside-only',pretendToBeVisual:true});const w=dom.window;
 w.eval(editor);w.eval(split);w.eval(shared);if(w.document.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
 return {dom,w,d:w.document};
}
test('claim search responds to identifiers and labels immediately without changing hidden permission values',async()=>{
 const {dom,w,d}=await setup();try{
  const form=d.querySelector('form'),search=d.querySelector('[data-aws-table-search]');let requests=0,submits=0;w.fetch=()=>requests++;form.addEventListener('submit',()=>submits++);
  const before=[...new w.FormData(form).getAll('claims')];search.value='voucher_approval_approve';search.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(d.querySelectorAll('[data-claim-row]:not([hidden])').length,1);assert.match(d.querySelector('[data-claim-row]:not([hidden])').textContent,/Approve/);
  search.value='repayments';search.dispatchEvent(new w.Event('input',{bubbles:true}));assert.equal(d.querySelectorAll('[data-claim-row]:not([hidden])').length,1);
  search.value='missing';search.dispatchEvent(new w.Event('input',{bubbles:true}));assert.equal(d.querySelector('.resource-table-no-matches').hidden,false);
  assert.deepEqual([...new w.FormData(form).getAll('claims')],before);assert.equal(new w.FormData(form).get('_csrf'),'kept');assert.equal(requests,0);assert.equal(submits,0);
 }finally{dom.window.close();}
});
test('claim selection and split details never grant access; restore defaults updates all rows and the read-only preview',async()=>{
 const {dom,w,d}=await setup();try{
  const rows=d.querySelectorAll('[data-claim-row]'),form=d.querySelector('form'),first=rows[0],grant=first.querySelector('[data-access-claim]');first.cells[1].click();await settle();
  assert.equal(first.querySelector('[data-claim-select]').checked,true);assert.equal(grant.checked,false);assert.match(d.querySelector('.resource-split-content').textContent,/VOUCHER_APPROVAL_APPROVE/);assert.match(d.querySelector('.resource-split-content').textContent,/Not allowed/);assert.equal(d.querySelector('.resource-split-content input'),null);
  grant.click();await settle();assert.equal(grant.checked,true);assert.equal(first.querySelector('[data-claim-select]').checked,true);assert.match(d.querySelector('.resource-split-content').textContent,/Allowed/);
  const search=d.querySelector('[data-aws-table-search]');search.value='VOUCHER_APPROVAL_APPROVE';search.dispatchEvent(new w.Event('input',{bubbles:true}));await settle();assert.equal(first.querySelector('[data-claim-select]').checked,false);
  d.querySelector('[data-restore-default-claims]').click();await settle();assert.deepEqual([...new w.FormData(form).getAll('claims')],['VOUCHER_APPROVAL_APPROVE']);
  first.querySelector('[data-claim-select]').click();await settle();assert.match(d.querySelector('.resource-split-content').textContent,/Allowed/);
  const divider=d.querySelector('[role=separator]'),before=Number(divider.getAttribute('aria-valuenow'));divider.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true}));assert.ok(Number(divider.getAttribute('aria-valuenow'))>before);
 }finally{dom.window.close();}
});
