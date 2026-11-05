const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/client-profile-wizard.js','utf8');
const conditions=readFileSync('src/main/resources/static/js/client-onboarding.js','utf8');
const validation=readFileSync('src/main/resources/static/js/form-validation.js','utf8');
async function setup({error='',saved='',start='',name='Asha',registration=false,requiredProfile=false}={}){
 const dom=new JSDOM(`<form data-onboarding-form="self"><input type="hidden" name="_csrf" value="token"><input type="hidden" name="requestKey" value="request-key">
 <template data-profile-wizard-labels data-personal="Personal details" data-finances="Income and bank details" data-documents="Documents and privacy" data-review="Review and send" data-navigation="Profile steps" data-step="Step" data-edit="Edit" data-empty="Not provided" data-yes="Yes" data-no="No" data-no-documents="No documents" data-documents-error="Upload required documents" data-document-expiry-error="Expired document" data-acknowledgement-error="Accept consent" data-notice-changed-error="Notice changed"></template>
 <div data-profile-wizard data-profile-start="${start}"><div><fieldset>
 <section data-profile-section="personal"><h2>Bio data</h2><label class="kyc-field"><span>First name</span><input name="profile.firstName" required value="${name}"></label><div class="kyc-field"><label for="group">Group</label><select name="profile.groupId" id="group"><option value="">Individual / no group</option></select><button type="button"><span>Individual / no group</span></button></div><select data-profile-marital-status name="profile.maritalStatus"><option value="SINGLE">Single</option><option value="MARRIED">Married</option></select>${error==='field'?'<span class="field-error" id="profile.firstName.errors">Correct your name</span>':''}</section>
 <section data-profile-section="personal" data-profile-spouse-section><h2>Spouse details</h2><fieldset data-profile-spouse-fields><label class="kyc-field"><span>Spouse name</span><input name="profile.spouseName" value="Spouse value"></label></fieldset></section>
 <section data-profile-section="finances"><h2>Income sources</h2><fieldset class="kyc-income-choices"><legend>Business or employment</legend><label class="kyc-checkbox"><input type="checkbox" name="profile.incomeActivities" value="BUSINESS" checked><span>Business</span></label><label class="kyc-checkbox"><input type="checkbox" name="profile.incomeActivities" value="EMPLOYMENT" checked><span>Employment</span></label></fieldset><label class="kyc-field"><span>Other income sources</span><input name="profile.otherIncomeSources" value="Rent"></label></section>
 </fieldset><section data-profile-section="documents"><h2>Privacy notice</h2><label class="kyc-checkbox"><input type="checkbox" name="noticeAcknowledged" required checked><span>I agree to data use</span></label></section></div>
 <section data-profile-section="documents"><h3>Attachments and Documents</h3><p data-profile-document>Identity: identity.png</p><a href="/documents/new">Add document</a><label class="kyc-checkbox"><input type="checkbox" name="declarationAccepted" required checked><span>I confirm accuracy</span></label></section></div>
 ${error==='documents'?'<div data-workspace-form-error role="alert">Upload required documents</div>':''}
 <a href="/profile">Cancel</a><button type="submit" name="action" value="DRAFT" formnovalidate>Save draft</button><button type="button" data-profile-previous hidden>Previous</button><button type="button" data-profile-next hidden>Next</button><button type="submit" name="action" value="SUBMITTED" data-profile-send>Send for review</button></form>`,{url:registration?'http://localhost/client-portal/register':'http://localhost/app/onboarding',runScripts:'outside-only',pretendToBeVisual:true});
 const {window:w}=dom,d=w.document;
 if(requiredProfile){
  d.querySelector('[data-profile-section="personal"]').insertAdjacentHTML('beforeend','<label class="kyc-field"><span>Home address</span><input name="profile.address" data-profile-required="true"></label>');
  d.querySelector('[data-profile-section="finances"]').insertAdjacentHTML('beforeend','<label class="kyc-field"><span>Source of funds</span><input name="profile.sourceOfFunds" data-profile-required="true"></label>');
 }
 if(registration){
  const wizard=d.querySelector('[data-profile-wizard]');
  wizard.setAttribute('data-profile-registration','');
  wizard.querySelectorAll('[data-profile-section="documents"]').forEach(section=>section.remove());
  wizard.insertAdjacentHTML('afterbegin','<section data-profile-section="personal"><h2>Institution</h2><label class="kyc-field"><span>Institution</span><select name="institution" required><option value="I1">Synthetic institution</option></select></label></section>');
  wizard.insertAdjacentHTML('beforeend','<p data-profile-section="review">Verify your contact with an access code. You can then add photos and documents.</p>');
  wizard.querySelectorAll('[name^="profile."]').forEach(field=>field.name='registration.'+field.name);
  d.querySelector('[value="DRAFT"]').setAttribute('formaction','/client-portal/register/draft');
  const send=d.querySelector('[data-profile-send]');send.removeAttribute('name');send.textContent='Send access code';
 }
 if(saved)w.sessionStorage.setItem('client-profile-step:v1:/app/onboarding',saved);
 w.eval(conditions);w.eval(validation);w.eval(source);
 await new Promise(resolve=>d.addEventListener('DOMContentLoaded',resolve,{once:true}));await Promise.resolve();
 const next=()=>d.querySelector('[data-profile-next]').click();
 return {dom,w,d,form:d.querySelector('form'),next,active:()=>d.querySelector('[aria-current="step"]').textContent};
}
test('registration draft stays available on every step and bypasses completion and OTP submission',async()=>{
 const {dom,w,d,form,next}=await setup({registration:true,name:''});try{
  const draft=d.querySelector('[value="DRAFT"]');assert.equal(draft.formNoValidate,true);assert.equal(draft.getAttribute('formaction'),'/client-portal/register/draft');assert.equal(draft.hidden,false);
  const event=new w.SubmitEvent('submit',{submitter:draft,bubbles:true,cancelable:true});assert.equal(form.dispatchEvent(event),true);
  const data=new w.FormData(form,draft);assert.equal(data.get('action'),'DRAFT');assert.equal(data.get('registration.profile.firstName'),'');assert.equal(d.querySelector('[data-profile-send]').hidden,true);
  d.querySelector('[name="registration.profile.firstName"]').value='Asha';next();assert.equal(draft.hidden,false);next();assert.equal(draft.hidden,false);
 }finally{dom.window.close();}
});

test('profile completion requirements are visible and block Next on the step that owns the field',async()=>{
 const {dom,d,next,active}=await setup({requiredProfile:true});try{
  const address=d.querySelector('[name="profile.address"]');
  const funds=d.querySelector('[name="profile.sourceOfFunds"]');
  assert.equal(address.required,true);assert.match(address.closest('label').textContent,/Home address \*/);
  next();assert.match(active(),/Personal details/);assert.equal(address.getAttribute('aria-invalid'),'true');
  address.value='Synthetic address';next();assert.match(active(),/Income and bank/);
  next();assert.match(active(),/Income and bank/);assert.equal(funds.getAttribute('aria-invalid'),'true');
  funds.value='Synthetic sales';next();assert.match(active(),/Documents and privacy/);
 }finally{dom.window.close();}
});
test('provisional registration reviews the institution and entered details before requesting an access code',async()=>{
 const {dom,w,d,form,next,active}=await setup({registration:true});try{
  const firstName=d.querySelector('[name="registration.profile.firstName"]');
  assert.match(active(),/Personal details/);
  assert.equal(d.querySelectorAll('nav button').length,3);
  assert.equal(d.querySelector('[data-profile-send]').hidden,true);
  const enter=new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});firstName.dispatchEvent(enter);
  assert.equal(enter.defaultPrevented,true);assert.match(active(),/Income and bank/);
  next();assert.match(active(),/Review and send/);
  assert.equal(d.querySelector('#profile-step-documents'),null);
  assert.match(d.querySelector('#profile-step-review').textContent,/You can then add photos and documents/);
  assert.match(d.querySelector('#profile-step-review').textContent,/Synthetic institution.*Asha/s);
  let submitted;
  form.addEventListener('submit',event=>{event.preventDefault();submitted=new w.FormData(form,event.submitter);});
  d.querySelector('[data-profile-send]').click();
  assert.equal(submitted.get('institution'),'I1');
  assert.equal(submitted.get('registration.profile.firstName'),'Asha');
  assert.equal(submitted.get('_csrf'),'token');
 }finally{dom.window.close();}
});
test('verified provisional clients start on Documents and can still edit earlier steps',async()=>{
 const {dom,d,active}=await setup({saved:'review',start:'documents'});try{
  assert.match(active(),/Documents and privacy/);
  assert.ok(d.querySelector('#profile-step-documents a[href="/documents/new"]'));
  d.querySelector('[data-profile-previous]').click();
  assert.match(active(),/Income and bank details/);
 }finally{dom.window.close();}
});
test('steps preserve live controls, both income choices and native draft/submit commands',async()=>{
 const {dom,w,d,form,next,active}=await setup();try{
  const input=d.querySelector('[name="profile.firstName"]');input.value='Changed client';
  assert.match(active(),/Personal details/);assert.equal(d.querySelector('[value="SUBMITTED"]').hidden,true);
  assert.equal(d.querySelector('[data-profile-previous]').hidden,true);assert.equal(d.querySelectorAll('nav button')[2].disabled,true);
  next();assert.match(active(),/Income and bank/);next();assert.match(active(),/Documents and privacy/);next();assert.match(active(),/Review and send/);
  assert.equal(d.querySelector('[value="SUBMITTED"]').hidden,false);assert.equal(d.querySelector('[data-profile-next]').hidden,true);
  assert.equal(d.querySelectorAll('[name="profile.firstName"]').length,1);assert.equal(d.querySelector('[name="profile.firstName"]'),input);
  const data=new w.FormData(form);assert.equal(data.get('_csrf'),'token');assert.equal(data.get('requestKey'),'request-key');assert.equal(data.get('profile.firstName'),'Changed client');assert.deepEqual(data.getAll('profile.incomeActivities'),['BUSINESS','EMPLOYMENT']);
  assert.equal(d.querySelector('[value="DRAFT"]').formNoValidate,true);
  assert.match(d.querySelector('#profile-step-review').textContent,/Changed client/);assert.match(d.querySelector('#profile-step-review').textContent,/Business, Employment/);assert.match(d.querySelector('#profile-step-review').textContent,/identity.png/);assert.doesNotMatch(d.querySelector('#profile-step-review').textContent,/Spouse value/);
  assert.ok([...d.querySelectorAll('#profile-step-review dt')].some(dt=>dt.textContent==='Group' && dt.nextElementSibling.textContent==='Individual / no group'));
  const edit=d.querySelector('[aria-label="Edit: Bio data"]');edit.click();assert.match(active(),/Personal details/);assert.equal(d.activeElement.id,'profile-step-personal-heading');
  input.value='<img src=x onerror=alert(1)>';d.querySelectorAll('nav button')[3].click();assert.equal(d.querySelector('#profile-step-review img'),null);assert.match(d.querySelector('#profile-step-review').textContent,/<img src=x/);
  assert.equal(w.sessionStorage.length,1);assert.equal(w.sessionStorage.getItem(w.sessionStorage.key(0)),'review');
 }finally{dom.window.close();}
});
test('Next validates only the current step and reveals a failed acknowledgement',async()=>{
 const {dom,d,next,active}=await setup({name:''});try{
  d.querySelector('[name="declarationAccepted"]').checked=false;
  next();assert.match(active(),/Personal details/);assert.equal(d.querySelector('[name="profile.firstName"]').getAttribute('aria-invalid'),'true');
  d.querySelector('[name="profile.firstName"]').value='Asha';next();assert.match(active(),/Income and bank/);next();assert.match(active(),/Documents and privacy/);
  next();assert.match(active(),/Documents and privacy/);assert.equal(d.querySelector('[name="declarationAccepted"]').getAttribute('aria-invalid'),'true');
  d.querySelector('[name="declarationAccepted"]').checked=true;next();assert.match(active(),/Review and send/);
 }finally{dom.window.close();}
});
test('final native validation opens the first invalid step and keeps all form fields enabled',async()=>{
 const {dom,d,form,active}=await setup({saved:'review'});try{
  d.querySelector('[name="profile.firstName"]').value='';d.querySelector('[name="declarationAccepted"]').checked=false;
  assert.equal(form.checkValidity(),false);assert.match(active(),/Personal details/);
  assert.equal(d.querySelector('[name="noticeAcknowledged"]').disabled,false);assert.equal(d.querySelector('[name="declarationAccepted"]').disabled,false);
 }finally{dom.window.close();}
});
test('server errors override a restored step and document failures return to Documents',async()=>{
 for(const [error,expected] of [['field','Personal details'],['documents','Documents and privacy']]){
  const {dom,active}=await setup({error,saved:'review'});try{assert.match(active(),new RegExp(expected));}finally{dom.window.close();}
 }
 const {dom,w,d,form,next,active}=await setup();try{
  next();next();next();const error=d.createElement('div');error.dataset.workspaceFormError='';error.textContent='Upload required documents';form.append(error);
  d.dispatchEvent(new w.CustomEvent('workspace:form-settled',{detail:{form}}));assert.match(active(),/Documents and privacy/);
 }finally{dom.window.close();}
});
test('review reflects conditional sections and edited values without creating another mutation',async()=>{
 const {dom,w,d,form,next}=await setup();try{
  const marital=d.querySelector('[data-profile-marital-status]');marital.value='MARRIED';marital.dispatchEvent(new w.Event('change',{bubbles:true}));
  next();next();next();assert.match(d.querySelector('#profile-step-review').textContent,/Spouse value/);
  let submits=0;form.addEventListener('submit',e=>{e.preventDefault();submits++;});
  d.querySelector('[aria-label="Edit: Income sources"]').click();d.querySelector('[name="profile.otherIncomeSources"]').value='Pension';next();next();
  assert.match(d.querySelector('#profile-step-review').textContent,/Pension/);assert.equal(submits,0);
 }finally{dom.window.close();}
});
test('Enter advances a text input while the final command still requires review',async()=>{
 const {dom,w,d,form,active}=await setup();try{
  const input=d.querySelector('[name="profile.firstName"]');const event=new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});input.dispatchEvent(event);
  assert.equal(event.defaultPrevented,true);assert.match(active(),/Income and bank/);
  const send=d.querySelector('[value="SUBMITTED"]'),submit=new w.SubmitEvent('submit',{submitter:send,bubbles:true,cancelable:true});
  form.dispatchEvent(submit);assert.equal(submit.defaultPrevented,true);assert.match(active(),/Review and send/);
 }finally{dom.window.close();}
});
test('the shared shell keeps wizard navigation labels while enhancing ordinary page links',async()=>{
 const {dom,w,d}=await setup();try{
  const pager=d.createElement('a');pager.className='app-btn';pager.href='/registry?page=1';pager.textContent='Next';d.body.append(pager);
  w.WorkspaceNavigation={onReady:callback=>callback()};w.matchMedia=()=>({matches:false,addEventListener(){}});
  w.eval(readFileSync('src/main/resources/static/js/shell.js','utf8'));
  assert.equal(d.querySelector('[data-profile-next]').textContent,'Next');assert.equal(d.querySelector('[data-profile-previous]').textContent,'Previous');
  assert.equal(d.querySelector('[data-profile-next]').classList.contains('aws-pagination-chevron'),false);
  assert.equal(pager.classList.contains('aws-pagination-chevron'),true);assert.equal(pager.getAttribute('aria-label'),'Next page');
 }finally{dom.window.close();}
});
test('Save draft bypasses submission-only consent checks in the shared shell',async()=>{
 const {dom,w,d,form}=await setup({saved:'review'});try{
  d.querySelector('[name="declarationAccepted"]').checked=false;
  w.WorkspaceNavigation={onReady:callback=>callback()};w.matchMedia=()=>({matches:false,addEventListener(){}});
  w.eval(readFileSync('src/main/resources/static/js/shell.js','utf8'));
  w.addEventListener('submit',event=>event.preventDefault());
  let validations=0;const check=form.checkValidity.bind(form);form.checkValidity=()=>{validations++;return check();};
  // jsdom does not implement formnovalidate; dispatch the event browsers emit for this submitter.
  form.dispatchEvent(new w.SubmitEvent('submit',{submitter:d.querySelector('[value="DRAFT"]'),bubbles:true,cancelable:true}));
  assert.equal(validations,0);assert.equal(d.querySelector('.erp-field-error'),null);
  assert.equal(d.querySelector('[name="declarationAccepted"]').checked,false);
  form.dispatchEvent(new w.SubmitEvent('submit',{submitter:d.querySelector('[value="SUBMITTED"]'),bubbles:true,cancelable:true}));
  assert.equal(validations,0);assert.equal(d.querySelector('[name="declarationAccepted"]').getAttribute('aria-invalid'),'true');
 }finally{dom.window.close();}
});
test('missing evidence blocks Review before opening confirmations',async()=>{
 const {dom,w,d,form,next,active}=await setup({saved:'documents'});try{
  d.querySelector('#profile-step-documents').insertAdjacentHTML('beforeend',
   '<div data-profile-evidence><h4 tabindex="-1">Required documents</h4><ul><li data-evidence-status="missing">Identity evidence</li><li data-evidence-status="expired">Address evidence</li></ul><p data-evidence-error hidden>Complete the list</p></div>');
  let checks=0;form.addEventListener('profile:before-review',()=>checks++);
  next();assert.match(active(),/Documents and privacy/);assert.equal(checks,0);
  assert.equal(d.querySelector('[data-evidence-error]').hidden,false);
  assert.equal(d.activeElement.textContent,'Required documents');
  d.querySelectorAll('[data-evidence-status]').forEach(item=>item.dataset.evidenceStatus='added');
  next();assert.match(active(),/Review and send/);assert.equal(checks,1);
  const response=new w.DOMParser().parseFromString(
   '<form><div data-profile-evidence><h4 tabindex="-1">Required documents</h4><ul><li data-evidence-status="expired">Address evidence</li></ul><p data-evidence-error hidden>Replace expired files</p></div><p data-workspace-form-error>Expired document</p></form>',
   'text/html');
  assert.equal(form.dispatchEvent(new w.CustomEvent('profile:validation-response',{cancelable:true,detail:response.querySelector('form')})),false);
  assert.match(active(),/Documents and privacy/);
  assert.equal(d.querySelector('[data-evidence-error]').textContent,'Replace expired files');
  assert.equal(d.querySelector('[data-evidence-error]').hidden,false);
 }finally{dom.window.close();}
});
test('Add document remains a navigation link when final-confirmation errors are already visible',async()=>{
 const {dom,w,d,form,active}=await setup({saved:'documents'});try{
  const declaration=d.querySelector('[name="declarationAccepted"]');declaration.checked=false;
  d.querySelector('[data-profile-next]').click();assert.match(active(),/Documents and privacy/);
  assert.equal(declaration.getAttribute('aria-invalid'),'true');
  let submits=0,validations=0,destination;
  form.addEventListener('submit',()=>submits++);form.checkValidity=()=>{validations++;return false;};
  const link=d.querySelector('a[href="/documents/new"]');
  link.addEventListener('click',event=>{event.preventDefault();destination=link.href;});link.click();
  assert.equal(destination,'http://localhost/documents/new');assert.equal(submits,0);assert.equal(validations,0);
 }finally{dom.window.close();}
});
