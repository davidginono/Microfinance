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
    <div data-recording-evidence data-validate-url="/validate" data-root="/evidence" data-error="Could not load product" data-upload-limit="Choose up to 10 files">
      <nav data-recording-steps hidden><ol>${['loan','guarantors','collateral','attachments'].map((id,index) => `<li><button type="button" data-recording-step-button aria-controls="recording-panel-${id}"><span class="recording-step-number">${index+1}</span><span ${id === 'guarantors' ? `data-guarantor-label data-individual-label="Guarantors" data-group-label="${groupLabel}"` : ''}>${id}</span></button></li>`).join('')}</ol></nav>
      <section id="recording-panel-loan"><h2 tabindex="-1" data-recording-step-heading>Loan</h2><input name="purpose" required value="Stock"><div data-evidence-fields></div><p data-recording-step-error hidden></p></section>
      <fieldset id="recording-panel-guarantors"><h2 tabindex="-1" data-recording-step-heading data-guarantor-label data-individual-label="Guarantors" data-group-label="${groupLabel}">Guarantors</h2>
        <div data-repeat-list="guarantors"><div data-repeat-row><label>Name<input data-field="fullName" required></label>
          <div class="recording-row-photos"><label><input type="file" data-row-photos><span class="attachment-dropzone-help">Photo</span></label><div data-row-photo-list></div></div>
          <div class="recording-item-actions"><button type="button" data-remove-row>Remove</button></div></div></div><button type="button" data-add-row="guarantors">Add</button><p data-recording-step-error hidden></p>
      </fieldset>
      <section id="recording-panel-collateral"><h2 tabindex="-1" data-recording-step-heading>Collateral</h2><input name="collateral"><p data-recording-step-error hidden></p></section>
      <section id="recording-panel-attachments"><h2 tabindex="-1" data-recording-step-heading>Attachments</h2><fieldset data-group-guarantor-evidence hidden><div data-group-guarantor-evidence-list></div>
      <template data-group-guarantor-evidence-template><div data-group-guarantor-evidence-row><h3 data-group-guarantor-name></h3><input data-field="clientNumber"><input data-field="signedOn"></div></template>
      </fieldset><input name="attachments" type="file"><div data-evidence-documents></div><p data-recording-step-error hidden></p></section>
      <template data-row-template="guarantors"><div data-repeat-row><label>Name<input data-field="fullName" required></label><div class="recording-item-actions"><button type="button" data-remove-row>Remove</button></div></div></template>
      <template data-evidence-document-template><div><span data-evidence-filename></span><label><input data-document-field="documentNames"></label><label><select data-document-field="documentTypes"><option>OTHER</option><option>GUARANTOR_PHOTO</option></select></label><label><select data-document-field="requiredAttachmentIds"><option value=""></option></select></label><button type="button" data-remove-upload>Remove</button></div></template>
      <div data-evidence-error hidden></div><div data-evidence-requirements></div>
    </div><button type="button" data-recording-previous>Previous</button><button type="button" data-recording-next>Next</button><button type="submit" data-recording-submit>Save</button>
  </form></html>`, {runScripts:'outside-only',url:'http://localhost/finance/loan-records/new'});
  const w=dom.window, d=w.document, form=d.querySelector('form'), requests=[];
  Object.defineProperty(form.elements.attachments,'files',{value:[],writable:true});
  w.DataTransfer=class {files=[];items={add:file=>this.files.push(file)};};
  w.WorkspaceNavigation={onReady:fn=>fn()};
  w.fetch=url=>url==="/validate"?Promise.resolve({status:204}):new Promise(resolve=>requests.push(resolve));
  w.eval(fs.readFileSync('src/main/resources/static/js/loan-recording-evidence.js','utf8'));
  const change=(name,value)=>{form.elements[name].value=value;form.elements[name].dispatchEvent(new w.Event('change',{bubbles:true}));};
  const response=async(index,count,ok=true)=>{requests[index]({ok,redirected:false,json:async()=>({product:{guarantors:count},requirements:[],fields:[]})});await new Promise(resolve=>setImmediate(resolve));};
  const next=()=>d.querySelector('[data-recording-next]').click();
  const current=()=>d.querySelector('[aria-current="step"]').getAttribute('aria-controls');
  return {dom,w,d,form,requests,change,response,next,current};
}


for(const language of ['en','sw'])test(language+': extra guarantors remain optional with any product minimum',async()=>{
  const {dom,w,d,form,response,next,current}=setup(language);
  try {
    await response(0,2);
    assert.equal(d.querySelector('#recording-panel-guarantors').disabled,false);
    assert.equal(d.querySelector('#recording-panel-guarantors h2').textContent,language==='sw'?'Wadhamini wa ziada':'Extra guarantors');
    assert.equal(d.querySelector('[data-group-guarantor-evidence]').hidden,true);
    next();await new Promise(resolve=>setImmediate(resolve));
    assert.equal(current(),'recording-panel-guarantors');
    next();await new Promise(resolve=>setImmediate(resolve));
    assert.equal(current(),'recording-panel-collateral');
    assert.equal([...new w.FormData(form).keys()].some(key=>key.startsWith('applicationDetails.guarantors')),false);
  }finally{dom.window.close();}
});

test('group evidence follows named rows and leaves individual submissions when loan type changes',async()=>{
  const {dom,w,d,form,change,response}=setup();
  const setName=(index,value)=>{
    const input=d.querySelectorAll('[data-repeat-list="guarantors"] [data-field="fullName"]')[index];
    input.value=value;input.dispatchEvent(new w.Event('input',{bubbles:true}));
  };
  try{
    await response(0,0);
    setName(0,'Asha');
    const box=d.querySelector('[data-group-guarantor-evidence]');
    assert.equal(box.hidden,false);
    box.querySelector('[data-field=clientNumber]').value='C99';
    d.querySelector('[data-add-row=guarantors]').click();setName(1,'Neema');
    box.querySelectorAll('[data-field=clientNumber]')[1].value='C100';
    d.querySelector('[data-remove-row]').click();
    assert.equal(box.querySelector('[data-group-guarantor-name]').textContent,'Neema');
    assert.equal(new w.FormData(form).get('applicationDetails.guarantors[0].clientNumber'),'C100');
    change('loanFor','INDIVIDUAL');
    const individual=new w.FormData(form);
    assert.equal(box.hidden,true);
    assert.equal(individual.get('applicationDetails.guarantors[0].fullName'),'Neema');
    assert.equal(individual.has('applicationDetails.guarantors[0].clientNumber'),false);
  }finally{dom.window.close();}
});
