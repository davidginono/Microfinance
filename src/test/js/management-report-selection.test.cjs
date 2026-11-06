const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

function fixture() {
  const dom = new JSDOM(`<form><select data-report-group><option value="">All</option><option value="group-1">Assets</option></select>
    <div data-report-accounts data-remove-label="Remove account"><label data-loan-choice data-choice-url="/reports/financial/operations/cashbook/choices/accounts">
    <select data-report-account><option value="">Choose</option>${Array.from({length:21},(_,i)=>`<option value="account-${i}">Account ${i}</option>`).join('')}</select></label>
    <ul data-report-selected></ul><span data-report-selection-limit hidden></span></div></form>`, {url:'http://localhost/',runScripts:'outside-only'});
  dom.window.eval(fs.readFileSync('src/main/resources/static/js/management-reports.js','utf8'));
  const picker = dom.window.document.querySelector('[data-report-account]');
  const choose = id => {picker.value=id;picker.dispatchEvent(new dom.window.Event('change'));};
  return {dom,choose,picker,document:dom.window.document};
}

test('account choices submit exact IDs and remove duplicates without submitting the form', () => {
  const {dom,choose,document,picker} = fixture();
  choose('account-1');choose('account-2');choose('account-1');
  assert.deepEqual([...new dom.window.FormData(document.querySelector('form')).getAll('accountIds')],['account-1','account-2']);
  assert.equal(picker.value,'');
  const remove=document.querySelector('[data-remove-account]');assert.equal(remove.type,'button');remove.click();
  assert.deepEqual([...new dom.window.FormData(document.querySelector('form')).getAll('accountIds')],['account-2']);
  dom.window.close();
});

test('changing the group clears stale account choices and refreshes the bounded lookup', () => {
  const {dom,choose,document,picker} = fixture();choose('account-1');let reloads=0;
  picker.addEventListener('choice:reload',()=>reloads++);
  const group=document.querySelector('[data-report-group]');group.value='group-1';group.dispatchEvent(new dom.window.Event('change'));
  assert.equal(document.querySelectorAll('[name=accountIds]').length,0);assert.equal(reloads,1);
  assert.equal(new URL(picker.closest('[data-loan-choice]').dataset.choiceUrl,dom.window.location.href).searchParams.get('groupId'),'group-1');
  group.value='';group.dispatchEvent(new dom.window.Event('change'));
  assert.equal(new URL(picker.closest('[data-loan-choice]').dataset.choiceUrl,dom.window.location.href).searchParams.has('groupId'),false);
  dom.window.close();
});

test('account selection stays bounded and recovers after removing an item', () => {
  const {dom,choose,document} = fixture();for(let i=0;i<21;i++)choose(`account-${i}`);
  assert.equal(document.querySelectorAll('[name=accountIds]').length,20);assert.equal(document.querySelector('[data-report-selection-limit]').hidden,false);
  document.querySelector('[data-remove-account]').click();choose('account-20');
  assert.equal(document.querySelectorAll('[name=accountIds]').length,20);assert.equal(document.querySelector('[data-report-selection-limit]').hidden,true);
  dom.window.close();
});
