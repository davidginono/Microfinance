const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/staff-office.js','utf8');
function setup(html){
    const dom=new JSDOM(html,{url:'http://localhost/finance/loan-records/1/review',runScripts:'outside-only'});
    dom.window.WorkspaceNavigation={onReady:callback=>callback()};dom.window.eval(source);return dom;
}
test('office return adds only navigation fields and preserves the native financial form',()=>{
    const dom=setup(`<main id="main-content"><template data-office-return-fields><input name="office" value="credit-officer" type="hidden"><input name="officeSource" value="loan-reviews" type="hidden"></template>
    <form method="post" action="/finance/loan-records/1/review"><input name="_csrf" value="token"><input name="reason" value="Checked evidence"><button name="decision" value="APPROVE">Approve</button></form></main>`);
    const form=dom.window.document.querySelector('form');
    assert.equal(form.getAttribute('action'),'/finance/loan-records/1/review');
    assert.equal(form.elements._csrf.value,'token');assert.equal(form.elements.reason.value,'Checked evidence');
    assert.equal(form.elements.office.value,'credit-officer');assert.equal(form.elements.decision.value,'APPROVE');
    dom.window.eval(source);assert.equal(form.querySelectorAll('[name=office]').length,1);dom.window.close();
});
test('changing queue source resets sort without losing the search',()=>{
    const dom=setup(`<section data-resource-table="staff-office-queue"><form><select name="source"><option data-chronological="true">loans</option><option data-chronological="false">clients</option></select>
    <input name="search" value="Asha"><select name="sort"><option value="number">Reference</option><option selected value="newest">Newest</option></select></form></section>`);
    const form=dom.window.document.querySelector('form');form.elements.source.selectedIndex=1;
    form.elements.source.dispatchEvent(new dom.window.Event('change'));
    assert.equal(form.elements.sort.value,'number');assert.equal(form.elements.search.value,'Asha');
    assert.equal(form.elements.sort.options[1].disabled,true);dom.window.close();
});
