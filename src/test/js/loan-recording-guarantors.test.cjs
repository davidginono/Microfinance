const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

function setup(language = 'en') {
  const groupLabel = language === 'sw' ? 'Wadhamini wa ziada' : 'Extra guarantors';
  const dom = new JSDOM(`<html lang="${language}"><form>
    <select name="loanFor"><option>GROUP</option><option>INDIVIDUAL</option></select>
    <select name="loanMode"><option>CONFIGURED</option><option>CUSTOM</option></select>
    <select name="productId"><option value="one">One</option><option value="two">Two</option></select><select name="clientId"></select>
    <div data-recording-evidence data-root="/evidence" data-error="Could not load product" data-upload-limit="Choose up to 10 files">
      <nav data-recording-steps hidden><ol>${['loan','guarantors','collateral','attachments'].map((id,index) => `<li><button type="button" data-recording-step-button aria-controls="recording-panel-${id}"><span class="recording-step-number">${index+1}</span><span ${id === 'guarantors' ? `data-guarantor-label data-individual-label="Guarantors" data-group-label="${groupLabel}"` : ''}>${id}</span></button></li>`).join('')}</ol></nav>
      <section id="recording-panel-loan"><h2 tabindex="-1" data-recording-step-heading>Loan</h2><input name="purpose" required value="Stock"><div data-evidence-fields></div></section>
      <fieldset id="recording-panel-guarantors"><h2 tabindex="-1" data-recording-step-heading data-guarantor-label data-individual-label="Guarantors" data-group-label="${groupLabel}">Guarantors</h2>
        <div data-repeat-list="guarantors"><div data-repeat-row><label>Client<input data-field="clientNumber" value="C99" required></label>
          <div class="recording-row-photos"><label><input type="file" data-row-photos><span class="attachment-dropzone-help">Photo</span></label><div data-row-photo-list></div></div>
          <div class="recording-item-actions"><button type="button" data-remove-row>Remove</button></div></div></div><button type="button" data-add-row="guarantors">Add</button>
      </fieldset>
      <section id="recording-panel-collateral"><h2 tabindex="-1" data-recording-step-heading>Collateral</h2><input name="collateral"></section>
      <section id="recording-panel-attachments"><h2 tabindex="-1" data-recording-step-heading>Attachments</h2><input name="attachments" type="file"><div data-evidence-documents></div></section>
      <template data-row-template="guarantors"><div data-repeat-row><label>Client<input data-field="clientNumber"></label><div class="recording-item-actions"><button type="button" data-remove-row>Remove</button></div></div></template>
      <template data-evidence-document-template><div><span data-evidence-filename></span><label><input data-document-field="documentNames"></label><label><select data-document-field="documentTypes"><option>OTHER</option><option>GUARANTOR_PHOTO</option></select></label><label><select data-document-field="requiredAttachmentIds"><option value=""></option></select></label><button type="button" data-remove-upload>Remove</button></div></template>
      <div data-evidence-error hidden></div><div data-evidence-requirements></div>
    </div><button type="button" data-recording-previous>Previous</button><button type="button" data-recording-next>Next</button><button type="submit" data-recording-submit>Save</button>
  </form></html>`, {runScripts:'outside-only',url:'http://localhost/finance/loan-records/new'});
  const w=dom.window, d=w.document, form=d.querySelector('form'), requests=[];
  Object.defineProperty(form.elements.attachments,'files',{value:[],writable:true});
  w.DataTransfer=class {files=[];items={add:file=>this.files.push(file)};};
  w.WorkspaceNavigation={onReady:fn=>fn()};
  w.fetch=()=>new Promise(resolve=>requests.push(resolve));
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording-evidence.js','utf8'));
  const change=(name,value)=>{form.elements[name].value=value;form.elements[name].dispatchEvent(new w.Event('change',{bubbles:true}));};
  const response=async(index,count,ok=true)=>{requests[index]({ok,redirected:false,json:async()=>({product:{guarantors:count},requirements:[],fields:[]})});await new Promise(resolve=>setImmediate(resolve));};
  const next=()=>d.querySelector('[data-recording-next]').click();
  const current=()=>d.querySelector('[aria-current="step"]').getAttribute('aria-controls');
  return {dom,w,d,form,requests,change,response,next,current};
}

for (const language of ['en','sw']) test(`${language}: zero-extra group loans skip and renumber the step in both directions`,async()=>{
  const {dom,w,d,form,response,next,current}=setup(language);
  try {
    await response(0,0);
    const panel=d.querySelector('#recording-panel-guarantors');
    assert.equal(panel.disabled,true);
    assert.equal(d.querySelector('[aria-controls="recording-panel-guarantors"]').closest('li').hidden,true);
    assert.equal(d.querySelector('#recording-panel-guarantors h2').textContent,language==='sw'?'Wadhamini wa ziada':'Extra guarantors');
    assert.deepEqual([...d.querySelectorAll('li:not([hidden]) .recording-step-number')].map(x=>x.textContent),['1','2','3']);
    form.elements.purpose.value='';next();assert.equal(current(),'recording-panel-loan');
    form.elements.purpose.value='Stock';form.elements.purpose.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));
    assert.equal(current(),'recording-panel-collateral');assert.equal(d.activeElement,d.querySelector('#recording-panel-collateral h2'));
    d.querySelector('[data-recording-previous]').click();assert.equal(current(),'recording-panel-loan');
    assert.equal(new w.FormData(form).has('applicationDetails.guarantors[0].clientNumber'),false);
    next();next();assert.equal(current(),'recording-panel-attachments');assert.equal(d.querySelector('[data-recording-submit]').hidden,false);
  } finally {dom.window.close();}
});

test('required extras remain enabled; individual loans keep their guarantor step',async()=>{
  const {dom,w,d,form,change,response,next,current}=setup();
  try {
    await response(0,2);next();assert.equal(current(),'recording-panel-guarantors');
    assert.equal(new w.FormData(form).get('applicationDetails.guarantors[0].clientNumber'),'C99');
    change('loanFor','INDIVIDUAL');assert.equal(d.querySelector('#recording-panel-guarantors h2').textContent,'Guarantors');
    change('productId','two');await response(1,0);
    assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
    assert.equal(d.querySelector('[aria-controls="recording-panel-guarantors"]').closest('li').hidden,false);
  } finally {dom.window.close();}
});

test('unknown, failed and stale product responses never remove required extras',async()=>{
  const {dom,d,change,response}=setup();
  try {
    change('productId','two');await response(1,1);await response(0,0);
    assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
    change('productId','one');await response(2,null,false);
    assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
    assert.equal(d.querySelector('[data-evidence-error]').hidden,false);
    change('productId','two');await response(3,null);
    assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
    change('loanMode','CUSTOM');assert.equal(d.querySelector('#recording-panel-guarantors').disabled,true);
    change('loanFor','INDIVIDUAL');assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
  } finally {dom.window.close();}
});

test('skipped extra fields and photos are omitted, with remaining upload indices kept together',async()=>{
  const {dom,w,d,form,change,response}=setup();
  try {
    await response(0,1);
    const picker=d.querySelector('[data-row-photos]');
    Object.defineProperty(picker,'files',{value:[new w.File(['photo'],'extra.png',{type:'image/png'})],writable:true});
    picker.dispatchEvent(new w.Event('change',{bubbles:true}));
    form.elements.attachments.files=[new w.File(['pdf'],'agreement.pdf')];form.elements.attachments.dispatchEvent(new w.Event('change',{bubbles:true}));
    change('productId','two');await response(1,0);
    assert.deepEqual(Array.from(form.elements.attachments.files,x=>x.name),['agreement.pdf']);
    const data=new w.FormData(form);assert.equal(data.has('applicationDetails.guarantors[0].clientNumber'),false);
    assert.equal(data.get('applicationDetails.documentNames[0]'),'agreement.pdf');
    change('productId','one');await response(2,1);form.dispatchEvent(new w.Event('submit',{cancelable:true}));
    assert.deepEqual(Array.from(form.elements.attachments.files,x=>x.name),['extra.png','agreement.pdf']);
    assert.equal(form.querySelector('[data-field="clientNumber"]').value,'C99');
  } finally {dom.window.close();}
});

test('photos in a skipped step do not consume the active attachment limit',async()=>{
  const {dom,w,d,form,change,response}=setup();
  try {
    await response(0,1);
    const picker=d.querySelector('[data-row-photos]');
    Object.defineProperty(picker,'files',{value:[new w.File(['photo'],'extra.png',{type:'image/png'})],writable:true});
    picker.dispatchEvent(new w.Event('change',{bubbles:true}));
    change('productId','two');await response(1,0);
    form.elements.attachments.files=Array.from({length:10},(_,i)=>new w.File(['pdf'],`evidence-${i}.pdf`));
    form.elements.attachments.dispatchEvent(new w.Event('change',{bubbles:true}));
    assert.equal(form.elements.attachments.files.length,10);assert.equal(form.elements.attachments.validity.customError,false);
    change('productId','one');await response(2,1);
    assert.equal(form.elements.attachments.validity.customError,true);
  } finally {dom.window.close();}
});
