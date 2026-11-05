const {test}=require('node:test'),assert=require('node:assert/strict'),{readFileSync}=require('node:fs'),{JSDOM}=require('jsdom');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
const row='<div data-creation-document-row><label><span>Category</span><select name="documents[0].category"><option value="IDENTITY">Identity</option><option value="ADDRESS">Address</option><option value="PROFILE_PHOTO">Photo</option></select></label><label><span>File</span><input type="file" name="documents[0].file"></label><label><span>Expiry</span><input type="date" name="documents[0].expiresOn"></label><button type="button" data-creation-remove hidden>Remove</button></div>';
async function setup(){
 const dom=new JSDOM('<body><main><form action="/finance/clients/onboarding/new" method="post" enctype="multipart/form-data" data-onboarding-form="create"><input name="_csrf" value="token"><input name="requestKey" value="key-1"><input name="profile.city" value="Arusha"><input name="profile.firstName" required><template data-creation-labels data-draft-saved="Draft saved" data-draft-files="Draft saved; files attach on Create" data-draft-error="Draft not saved" data-draft-uncertain="Save not confirmed" data-file-error="Invalid file" data-limit-error="Upload limit"></template><div data-creation-documents>'+row+'</div><template data-creation-document-template>'+row+'</template><button type="button" data-creation-add hidden>Add</button><div data-form-actions><button type="submit" name="action" value="DRAFT" formnovalidate>Save draft</button><button type="submit" name="action" value="CREATE">Create</button></div></form></main></body>',{url:'http://localhost/finance/clients/onboarding/new',runScripts:'outside-only'});
 const w=dom.window,d=w.document,form=d.querySelector('form'),requests=[],toasts=[],settled=[];
 w.WorkspaceNavigation={onReady:fn=>fn()};w.showToast=(...args)=>toasts.push(args);w.fetch=(url,options)=>new Promise((resolve,reject)=>requests.push({url,options,resolve,reject}));
 d.addEventListener('workspace:form-settled',event=>settled.push(event.detail));w.eval(readFileSync('src/main/resources/static/js/client-onboarding.js','utf8'));
 const submit=value=>form.dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:form.querySelector('[value='+value+']')}));
 const respond=async(data,status=200)=>{requests.at(-1).resolve({ok:status===200,headers:{get:()=> 'application/json'},json:async()=>data});await tick();await tick();};
 return {dom,w,d,form,requests,toasts,settled,submit,respond};
}
test('Save draft confirms success without a page request or replacement and preserves selected files',async()=>{
 const f=await setup();try{
  const input=f.form.querySelector('[type=file]'),file=new f.w.File(['image'],'id.png',{type:'image/png'});Object.defineProperty(input,'files',{value:[file],configurable:true});
  const error=f.d.createElement('div');error.dataset.workspaceFormError='';error.textContent='Old uncertainty';f.form.append(error);
  let genericSubmissions=0;f.w.addEventListener('submit',()=>genericSubmissions++);
  assert.equal(f.submit('DRAFT'),false);f.submit('DRAFT');assert.equal(f.requests.length,1);assert.equal(f.toasts.length,0);assert.equal(genericSubmissions,0);
  assert.equal(f.requests[0].options.headers.Accept,'application/json');assert.equal(f.requests[0].options.body.get('_csrf'),'token');assert.equal(f.requests[0].options.body.get('action'),'DRAFT');assert.equal(f.requests[0].options.body.has('documents[0].file'),false);
  await f.respond({saved:true,requestKey:'key-1'});
  assert.equal(f.toasts[0][0],'success');assert.equal(f.toasts[0][1],'Draft saved; files attach on Create');assert.equal(f.toasts[0][2].placement,'workspace');assert.equal(input.files[0],file);assert.equal(f.form.elements['profile.city'].value,'Arusha');assert.equal(f.form.querySelector('[data-workspace-form-error]'),null);
  assert.equal(f.settled.at(-1).saved,false);assert.equal(f.form.hasAttribute('aria-busy'),false);assert.equal(f.form.inert,false);assert.equal(f.form.querySelector('[value=DRAFT]').disabled,false);
 }finally{f.dom.window.close();}
});
test('an empty optional document section permits saving and creation remains the native command',async()=>{
 const f=await setup();try{
  assert.equal(f.form.querySelector('[type=file]').required,false);f.submit('DRAFT');await f.respond({saved:true,requestKey:'key-1'});assert.equal(f.settled.at(-1).saved,true);assert.equal(f.toasts[0][0],'success');assert.equal(f.toasts[0][1],'Draft saved');
  assert.equal(f.submit('CREATE'),true);assert.equal(f.requests.length,1);
 }finally{f.dom.window.close();}
});
test('rejected or unconfirmed drafts preserve values and only acknowledged saves show success',async()=>{
 const f=await setup();try{
  f.submit('DRAFT');await f.respond({saved:false,message:'Invalid phone'},400);assert.equal(f.form.querySelector('[data-creation-draft-error]').dataset.messageType,'error');assert.equal(f.toasts.length,0);
  f.submit('DRAFT');f.requests.at(-1).reject(new TypeError('Disconnected'));await tick();await tick();assert.equal(f.form.querySelectorAll('[data-creation-draft-error]').length,1);assert.equal(f.form.querySelector('[data-creation-draft-error]').dataset.messageType,'warning');
  assert.equal(f.requests.length,2);assert.equal(f.form.elements['profile.city'].value,'Arusha');assert.equal(f.form.querySelector('[value=DRAFT]').disabled,false);assert.equal(f.toasts.length,0);
  f.submit('DRAFT');await f.respond({saved:true,requestKey:'other-key'});assert.equal(f.toasts.length,0);
 }finally{f.dom.window.close();}
});
test('optional upload rows keep category and file pairs, enforce bounds and renumber after removal',async()=>{
 const f=await setup();try{
  const first=f.form.querySelector('[data-creation-document-row]'),fileInput=first.querySelector('[type=file]');Object.defineProperty(fileInput,'files',{value:[new f.w.File(['a'],'id.png')],configurable:true});
  const add=f.form.querySelector('[data-creation-add]');for(let i=0;i<12;i++)add.click();assert.equal(f.form.querySelectorAll('[data-creation-document-row]').length,10);assert.equal(add.disabled,true);
  const second=f.form.querySelectorAll('[data-creation-document-row]')[1];second.querySelector('select').value='PROFILE_PHOTO';second.querySelector('select').dispatchEvent(new f.w.Event('change'));assert.equal(second.querySelector('[type=date]').disabled,true);
  first.querySelector('[data-creation-remove]').click();assert.equal(second.querySelector('select').name,'documents[0].category');assert.equal(second.querySelector('select').value,'PROFILE_PHOTO');assert.equal(add.disabled,false);
  const input=second.querySelector('[type=file]');Object.defineProperty(input,'files',{value:[new f.w.File(['bad'],'bad.pdf')],configurable:true});input.dispatchEvent(new f.w.Event('change'));assert.equal(input.validationMessage,'Invalid file');
 }finally{f.dom.window.close();}
});
