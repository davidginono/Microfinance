const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const {implForWrapper}=require('jsdom/lib/generated/idl/utils.js');
const source=readFileSync('src/main/resources/static/js/client-profile-documents.js','utf8');

async function setup({nativeFallback=false}={}) {
 const dom=new JSDOM(`<body><form id="client-onboarding-form" data-onboarding-form="create" method="post" enctype="multipart/form-data">
   <input name="profile.firstName" value="Asha"><input name="_csrf" value="token"><input name="requestKey" value="create-key">
   <template data-profile-wizard-labels data-done="Done" data-cancel="Cancel" data-close="Close" data-upload-error="Upload error" data-choose-files="Choose files" data-file-label="Images" data-drop-images="Drop images here" data-selected-images="Selected images" data-no-documents="No documents" data-file-size="Size" data-file-modified="Modified" data-remove-selected="Remove file {0}: {1}"></template>
   <div data-profile-document-list></div><button type="button" data-profile-add-documents hidden>Add document</button>
   <div data-profile-creation-files><label><span>Category</span><select name="documents[0].category"><option value="IDENTITY">Identity</option><option value="ADDRESS">Address</option><option value="PROFILE_PHOTO">Photo</option></select></label>
   <label><span>Expiry</span><input type="date" name="documents[0].expiresOn"></label>
   <input type="file" name="documents[0].file" data-upload-max-bytes="1048576" data-upload-remaining-files="10" data-upload-remaining-bytes="10485760" data-upload-file-error="Invalid image" data-upload-limit-error="Upload limit"></div>
   <button type="submit" name="action" value="CREATE">Create client</button></form></body>`,{url:'http://localhost/finance/clients/onboarding/new',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,profile=d.querySelector('form'),requests=[],created=[],revoked=[];
 w.fetch=(...args)=>{requests.push(args);throw Error('Selection must stay local');};
 w.URL.createObjectURL=()=>{const url='blob:test-'+created.length;created.push(url);return url;};
 w.URL.revokeObjectURL=url=>revoked.push(url);
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
 w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 if(!nativeFallback) w.DataTransfer=class {
  constructor(){const input=d.createElement('input');input.type='file';this.files=input.files;this.items={add:file=>implForWrapper(this.files).push(implForWrapper(file))};}
 };
 w.eval(source);await new Promise(resolve=>d.addEventListener('DOMContentLoaded',resolve,{once:true}));
 const add=()=>d.querySelector('[data-profile-add-documents]').click();
 const file=name=>new w.File(['image'],name,{type:'image/png',lastModified:Date.UTC(2026,10,3,10)});
 const choose=(...files)=>{const picker=d.querySelector('dialog input[type=file]');Object.defineProperty(picker,'files',{value:files,configurable:true});picker.dispatchEvent(new w.Event('change',{bubbles:true}));};
 const drop=(...files)=>{const event=new w.Event('drop',{bubbles:true,cancelable:true});Object.defineProperty(event,'dataTransfer',{value:{files}});d.querySelector('dialog .kyc-upload-dropzone').dispatchEvent(event);};
 const done=()=>d.querySelector('dialog form').dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true}));
 const data=()=>new w.FormData(profile,profile.querySelector('[value=CREATE]'));
 return {dom,w,d,profile,requests,created,revoked,add,file,choose,drop,done,data};
}

test('staff uses the shared chooser and file tokens, then submits selected files with Create',async()=>{
 const f=await setup();try{
  f.add();assert.equal(f.d.querySelector('dialog').parentElement,f.d.body);assert.equal(f.d.activeElement.dataset.profileChoose,'');
  f.choose(f.file('id.png'));f.drop(f.file('photo.png'));
  const [photo,identity]=f.d.querySelectorAll('dialog .kyc-upload-row');
  assert.match(photo.textContent,/photo\.png.*5 B.*03\/11\/2026/s);assert.ok(photo.querySelector('.kyc-upload-thumbnail'));
  photo.querySelector('select').value='PROFILE_PHOTO';photo.querySelector('select').dispatchEvent(new f.w.Event('change'));
  assert.equal(photo.querySelector('input[type=date]').disabled,true);
  identity.querySelector('select').value='ADDRESS';identity.querySelector('input[type=date]').value='2027-01-01';
  assert.equal(f.data().has('documents[0].file'),false);
  f.done();assert.equal(f.d.querySelector('dialog'),null);
  assert.deepEqual([...f.profile.querySelectorAll('[data-profile-file-name]')].map(node=>node.textContent),['photo.png','id.png']);
  const data=f.data();assert.equal(data.get('documents[0].file').name,'id.png');assert.equal(data.get('documents[0].category'),'ADDRESS');assert.equal(data.get('documents[0].expiresOn'),'2027-01-01');
  assert.equal(data.get('documents[1].file').name,'photo.png');assert.equal(data.get('documents[1].category'),'PROFILE_PHOTO');assert.equal(data.has('documents[1].expiresOn'),false);
  assert.equal(data.get('action'),'CREATE');assert.equal(data.get('_csrf'),'token');assert.equal(data.get('profile.firstName'),'Asha');assert.equal(f.requests.length,0);
  f.w.dispatchEvent(new f.w.PageTransitionEvent('pagehide'));assert.equal(f.revoked.length,f.created.length);
 }finally{f.dom.window.close();}
});

test('Cancel preserves the prior selection and individual removal reindexes the multipart files',async()=>{
 const f=await setup();try{
  f.add();f.choose(f.file('first.png'),f.file('second.png'));f.done();
  f.add();assert.equal(f.d.querySelectorAll('dialog .kyc-upload-row').length,2);
  f.d.querySelector('dialog .kyc-upload-remove').click();f.d.querySelector('dialog .app-modal-close').click();
  assert.equal(f.data().get('documents[1].file').name,'second.png');assert.equal(f.d.activeElement,f.d.querySelector('[data-profile-add-documents]'));
  f.profile.querySelector('button[aria-label$="first.png"]').click();assert.equal(f.data().get('documents[0].file').name,'second.png');assert.equal(f.data().has('documents[1].file'),false);
  f.add();f.d.querySelector('dialog .kyc-upload-remove').click();f.done();assert.equal(f.data().has('documents[0].file'),false);assert.equal(f.requests.length,0);
 }finally{f.dom.window.close();}
});

test('invalid files stay out of the form and browsers without the shared picker retain native attachment',async()=>{
 const f=await setup();try{
  f.add();f.choose(f.file('bad.pdf'));assert.match(f.d.querySelector('dialog [data-upload-error]').textContent,/Invalid image/);
  f.drop(...Array.from({length:11},(_,index)=>f.file(index+'.png')));assert.match(f.d.querySelector('dialog [data-upload-error]').textContent,/Upload limit/);
  assert.equal(f.d.querySelectorAll('dialog .kyc-upload-row').length,0);assert.equal(f.data().has('documents[0].file'),false);
 }finally{f.dom.window.close();}
 const fallback=await setup({nativeFallback:true});try{
  assert.ok(fallback.profile.elements['documents[0].file']);assert.equal(fallback.profile.elements['documents[0].file'].required,false);
  assert.equal(fallback.d.querySelector('[data-profile-add-documents]').hidden,true);
 }finally{fallback.dom.window.close();}
});
