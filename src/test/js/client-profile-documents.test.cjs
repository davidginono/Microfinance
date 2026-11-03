const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/client-profile-documents.js','utf8');
const settle=()=>new Promise(resolve=>setImmediate(resolve));
async function setup({existing=false,failAt=0,remainingFiles=10,remainingBytes=10485760,maxBytes=1048576}={}){
 const dom=new JSDOM(`<body><main><form id="client-onboarding-form" data-onboarding-form="self"><input name="profile.address" value="Saved address"><input name="expectedSequence" value="3"><input name="requestKey" value="profile-key"><template data-profile-wizard-labels data-upload="Upload files" data-done="Done" data-remove="Remove" data-uploaded="Uploaded" data-upload-error="Upload failed; edits retained" data-choose-files="Choose files" data-close="Close" data-file-label="Images" data-drop-images="Drop images here" data-selected-images="Selected images" data-file-size="Size" data-file-modified="Modified" data-remove-selected="Remove file {0}: {1}" data-uploading-file="Uploading" data-uploading-files="Uploading {0} files"></template><section data-profile-confirmations><h2>Privacy notice</h2><label><input type="checkbox" name="noticeAcknowledged" required>I agree</label></section><div data-profile-document-list>${existing?'<p data-profile-document>Existing ID</p>':'No documents added'}</div><a data-profile-add-documents href="/app/onboarding/documents/new">Add document</a><div data-profile-declaration><label><input type="checkbox" name="declarationAccepted" required>I confirm accuracy</label></div></form></main></body>`,{url:'http://localhost/app/onboarding',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,requests=[],posts=[],created=[],revoked=[];let sequence=0;
 w.URL.createObjectURL=file=>{const url='blob:test-'+created.length;created.push(url);return url;};w.URL.revokeObjectURL=url=>revoked.push(url);
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 const upload=()=>`<form data-onboarding-form="document"><input type="hidden" name="_csrf" value="csrf-token"><input type="hidden" name="revisionId" value="revision-3"><input type="hidden" name="requestKey" value="upload-${++sequence}"><label><span>Category</span><select name="category"><option value="IDENTITY">Identity</option><option value="ADDRESS">Address</option><option value="ACTIVITY">Business</option><option value="PROFILE_PHOTO">Client photo</option></select></label><label><span>Expiry</span><input type="date" name="expiresOn"></label><input type="file" name="file" data-upload-max-bytes="${maxBytes}" data-upload-remaining-files="${remainingFiles}" data-upload-remaining-bytes="${remainingBytes}" data-upload-file-error="JPG or PNG, up to 1 MB" data-upload-limit-error="Upload limit reached"><div class="accounting-form-actions"><a href="/app/onboarding">Cancel</a></div></form>`;
 w.fetch=async(url,options={})=>{requests.push({url,options});let html;if(options.method==='POST'){posts.push(options.body);html=failAt===posts.length?upload()+'<div data-workspace-response-error>Invalid file</div>':`<div data-profile-document-list>${posts.map((data,i)=>'<p data-profile-document>'+data.get('file').name+'</p>').join('')}</div>`;}else html=upload();return {ok:true,headers:{get:()=> 'text/html'},text:async()=>html};};
 w.eval(source);await new Promise(resolve=>d.addEventListener('DOMContentLoaded',resolve,{once:true}));
 const profile=d.querySelector('form');profile.classList.add('accounting-create-form');
 const guard=d.createElement('div');guard.id='accounting-discard-modal';guard.hidden=true;guard.innerHTML='<div role="dialog"><button type="button" data-accounting-keep>Close</button><div class="app-modal-actions"><button type="button" data-accounting-keep>Continue editing</button><button type="button" data-accounting-discard>Discard</button></div></div>';d.body.append(guard);
 w.eval(readFileSync('src/main/resources/static/js/accounting-form-ux.js','utf8'));await settle();profile.elements['profile.address'].value='Unsaved home address';profile.elements['profile.address'].dispatchEvent(new w.Event('input',{bubbles:true}));
 const open=async()=>{d.querySelector('[data-profile-add-documents]').click();await settle();};
 const choose=async(...names)=>{const input=d.querySelector('dialog input[type=file]');Object.defineProperty(input,'files',{value:names.map(name=>new w.File(['synthetic'],name,{type:'image/png',lastModified:Date.UTC(2026,10,3,10)})),configurable:true});input.dispatchEvent(new w.Event('change',{bubbles:true}));await settle();};
 const agree=()=>{profile.elements.noticeAcknowledged.checked=true;profile.elements.declarationAccepted.checked=true;};
 const submit=async()=>{d.querySelector('dialog form').dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true}));await settle();await settle();};
 return {dom,w,d,profile,requests,posts,open,choose,agree,submit,created,revoked};
}
test('Add document opens in place despite unsaved profile changes and hides confirmations until files are selected',async()=>{
 const {dom,w,d,profile,requests,open,choose,agree}=await setup();try{
  let leaveAttempts=0;d.addEventListener('click',event=>{if(event.target.closest('a'))leaveAttempts++;});
  assert.equal(d.querySelector('[data-profile-confirmations]').hidden,true);
  await open();assert.equal(leaveAttempts,0);assert.equal(d.querySelector('#accounting-discard-modal').hidden,true);assert.equal(d.querySelector('dialog').parentElement,d.body);assert.equal(d.querySelector('.kyc-upload-confirmations').hidden,true);assert.equal(requests.length,1);
  assert.equal(d.querySelector('dialog input[type=file]').labels[0].textContent,'Images');assert.equal(d.querySelector('dialog input[type=file]').hidden,true);
  await choose('identity.png','address.png');await choose('business.png');assert.equal(d.querySelectorAll('.kyc-upload-row').length,3);assert.equal(d.querySelector('.kyc-upload-confirmations').hidden,false);
  assert.equal(profile.elements['profile.address'].value,'Unsaved home address');assert.equal(new w.FormData(profile).get('expectedSequence'),'3');
  agree();d.querySelector('.app-modal-close').click();assert.equal(d.querySelector('dialog'),null);assert.equal(profile.elements['profile.address'].value,'Unsaved home address');assert.equal(d.body.style.overflow,'');assert.equal(profile.elements.noticeAcknowledged.checked,false);assert.equal(profile.elements.declarationAccepted.checked,false);
  assert.equal(d.dispatchEvent(new w.CustomEvent('workspace:before-navigate',{cancelable:true,detail:{resume(){}}})),false);assert.equal(d.querySelector('#accounting-discard-modal').hidden,false);
 }finally{dom.window.close();}
});
test('one confirmation covers the completed selection and each file uses its own request key and category',async()=>{
 const {dom,w,d,profile,posts,open,choose,agree,submit}=await setup();try{
  await open();await choose('id.png','address.png');d.querySelectorAll('.kyc-upload-row select')[1].value='ADDRESS';
  await submit();assert.equal(posts.length,0);agree();await submit();
  assert.equal(posts.length,2);assert.deepEqual(posts.map(data=>data.get('category')),['IDENTITY','ADDRESS']);assert.notEqual(posts[0].get('requestKey'),posts[1].get('requestKey'));
  assert.ok(posts.every(data=>data.get('_csrf')==='csrf-token'&&data.get('revisionId')==='revision-3'));assert.equal(d.querySelector('dialog'),null);
  assert.equal(profile.elements.noticeAcknowledged.checked,true);assert.equal(profile.elements.declarationAccepted.checked,true);assert.equal(new w.FormData(profile).get('requestKey'),'profile-key');
  assert.equal(profile.elements['profile.address'].value,'Unsaved home address');assert.equal(d.querySelectorAll('[data-profile-document]').length,2);assert.equal(profile.elements.noticeAcknowledged.required,false);
 }finally{dom.window.close();}
});
test('changing the selection resets confirmations and photo rows do not submit expiry dates',async()=>{
 const {dom,w,d,profile,posts,open,choose,agree,submit}=await setup();try{
  await open();await choose('id.png');agree();await choose('photo.png');assert.equal(profile.elements.noticeAcknowledged.checked,false);
  const row=d.querySelectorAll('.kyc-upload-row')[1],category=row.querySelector('select');category.value='PROFILE_PHOTO';category.dispatchEvent(new w.Event('change',{bubbles:true}));assert.equal(row.querySelector('input[type=date]').disabled,true);
  d.querySelector('.kyc-upload-row button').click();assert.equal(d.querySelectorAll('.kyc-upload-row').length,1);agree();await submit();assert.equal(posts[0].get('category'),'PROFILE_PHOTO');assert.equal(posts[0].get('expiresOn'),'');
 }finally{dom.window.close();}
});
test('partial failures retain the remaining files, original profile and successful uploads without replaying them',async()=>{
 const {dom,d,profile,posts,open,choose,agree,submit}=await setup({failAt:2});try{
  await open();await choose('id.png','address.png');agree();await submit();assert.equal(posts.length,2);assert.ok(d.querySelector('dialog[open]'));assert.equal(d.querySelector('.kyc-upload-row[data-upload-state="error"] [role="alert"]').textContent,'Invalid file');assert.equal(profile.elements['profile.address'].value,'Unsaved home address');
  await submit();assert.equal(posts.length,3);assert.equal(posts[2].get('file').name,'address.png');assert.equal(posts[2].get('requestKey'),posts[1].get('requestKey'));assert.equal(d.querySelector('dialog'),null);
 }finally{dom.window.close();}
});
test('selected image tokens show basename, size, modified date and previews with accessible individual removal',async()=>{
 const {dom,d,open,choose,created,revoked}=await setup();try{
  await open();await choose('C:\\fakepath\\identity.png','address.png');
  const rows=d.querySelectorAll('ul.kyc-upload-queue > li');assert.equal(rows.length,2);assert.equal(rows[0].querySelector('.kyc-upload-name').textContent,'identity.png');
  assert.match(rows[0].querySelector('.kyc-upload-metadata').textContent,/Size9 B/);assert.match(rows[0].querySelector('.kyc-upload-metadata').textContent,/Modified03\/11\/2026/);assert.equal(rows[0].querySelector('img').src,created[0]);
  const remove=rows[0].querySelector('button');assert.equal(remove.getAttribute('aria-label'),'Remove file 1: identity.png');remove.click();assert.deepEqual(revoked,[created[0]]);assert.equal(d.activeElement,d.querySelector('.kyc-upload-row button'));
  d.querySelector('.app-modal-close').click();assert.deepEqual(new Set(revoked),new Set(created));
 }finally{dom.window.close();}
});
test('Choose files only opens the picker and dropping more images appends tokens',async()=>{
 const {dom,w,d,open,choose}=await setup();try{
  await open();let pickerClicks=0,submits=0;d.querySelector('dialog input[type=file]').addEventListener('click',()=>pickerClicks++);d.querySelector('dialog form').addEventListener('submit',()=>submits++);
  [...d.querySelectorAll('dialog button')].find(button=>button.textContent==='Choose files').click();assert.equal(pickerClicks,1);assert.equal(submits,0);
  await choose('first.png');const drop=new w.Event('drop',{bubbles:true,cancelable:true});Object.defineProperty(drop,'dataTransfer',{value:{files:[new w.File(['data'],'second.jpg',{lastModified:Date.UTC(2026,10,3)})]}});d.querySelector('.kyc-upload-dropzone').dispatchEvent(drop);await settle();
  assert.equal(drop.defaultPrevented,true);assert.deepEqual([...d.querySelectorAll('.kyc-upload-name')].map(node=>node.textContent),['first.png','second.jpg']);
 }finally{dom.window.close();}
});
test('the active file token shows uploading state and one batch announcement',async()=>{
 const {dom,w,d,open,choose,agree,submit}=await setup();try{
  const fetch=w.fetch;let release;w.fetch=(url,options={})=>options.method==='POST'?new Promise(resolve=>{release=()=>resolve(fetch(url,options));}):fetch(url,options);
  await open();await choose('one.png','two.png');agree();await submit();
  assert.equal(d.querySelector('dialog [role="status"]').textContent,'Uploading 2 files');assert.equal(d.querySelectorAll('.kyc-upload-row[data-upload-state="loading"]').length,1);assert.ok(d.querySelector('.kyc-upload-status .app-loading-spinner'));
  release();await settle();await settle();assert.equal(d.querySelectorAll('.kyc-upload-row[data-upload-state="success"]').length,1);release();await settle();await settle();assert.equal(d.querySelector('dialog'),null);
 }finally{dom.window.close();}
});
test('existing uploaded documents can be confirmed without uploading or navigating again',async()=>{
 const {dom,w,d,profile,posts,agree,submit}=await setup({existing:true});try{
  const event=new w.CustomEvent('profile:before-review',{cancelable:true});profile.dispatchEvent(event);assert.equal(event.defaultPrevented,true);await settle();assert.equal(d.querySelector('.kyc-upload-confirmations').hidden,false);agree();await submit();assert.equal(posts.length,0);assert.equal(d.querySelector('dialog'),null);
  assert.equal(profile.dispatchEvent(new w.CustomEvent('profile:before-review',{cancelable:true})),true);
 }finally{dom.window.close();}
});
test('an early final command opens document confirmation without posting or discarding the profile',async()=>{
 const {dom,w,d,profile,posts}=await setup({existing:true});try{
  const send=d.createElement('button');send.type='submit';send.value='SUBMITTED';profile.append(send);
  const event=new w.SubmitEvent('submit',{submitter:send,bubbles:true,cancelable:true});profile.dispatchEvent(event);await settle();
  assert.equal(event.defaultPrevented,true);assert.ok(d.querySelector('dialog[open]'));assert.equal(d.querySelector('#accounting-discard-modal').hidden,true);assert.equal(posts.length,0);
 }finally{dom.window.close();}
});
test('file selection enforces image type, per-file size and remaining historical quota before upload',async()=>{
 for(const options of [{remainingFiles:0},{remainingFiles:1},{remainingBytes:3},{maxBytes:3}]){
  const {dom,d,posts,open,choose}=await setup(options);try{
   await open();await choose('one.png','two.png');assert.equal(d.querySelectorAll('.kyc-upload-row').length,0);assert.ok(d.querySelector('dialog [data-upload-error]'));assert.equal(posts.length,0);
  }finally{dom.window.close();}
 }
 const {dom,d,open,choose}=await setup();try{await open();await choose('document.pdf');assert.equal(d.querySelectorAll('.kyc-upload-row').length,0);assert.equal(d.querySelector('dialog input[type=file]').accept,'.png,.jpg,.jpeg');}finally{dom.window.close();}
});
