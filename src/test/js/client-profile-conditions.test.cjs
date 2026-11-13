const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/client-onboarding.js','utf8');
async function setup(prefix='profile.',readOnly=false){
 const dom=new JSDOM(`<form><input name="_csrf" value="token"><fieldset ${readOnly?'disabled':''}><select name="${prefix}hasBusiness" data-profile-business-choice><option value="">Choose</option><option value="true">Yes</option><option value="false" selected>No</option></select><fieldset data-profile-business-fields><input name="${prefix}businessName" value="Unsaved shop"><input name="${prefix}dailySales" type="number" min="0" value="-1"></fieldset><select name="${prefix}maritalStatus" data-profile-marital-status><option value="SINGLE" selected>Single</option><option value="MARRIED">Married</option><option value="SEPARATED">Separated</option><option value="DIVORCED">Divorced</option><option value="WIDOWED">Widowed</option><option value="NOT_DISCLOSED">Prefer not to say</option></select><section data-profile-spouse-section><fieldset data-profile-spouse-fields><input name="${prefix}spouseName" value="Unsaved spouse"></fieldset></section><input name="${prefix}otherIncomeSources" value="Employment"></fieldset></form>`,{url:'http://localhost/profile',runScripts:'outside-only',pretendToBeVisual:true});
 dom.window.eval(source);await new Promise(resolve=>dom.window.document.addEventListener('DOMContentLoaded',resolve,{once:true}));
 const w=dom.window,d=w.document,form=d.querySelector('form'),business=d.querySelector('[data-profile-business-choice]'),marital=d.querySelector('[data-profile-marital-status]');
 const choose=(control,value)=>{control.value=value;control.dispatchEvent(new w.Event('change',{bubbles:true}));};
 return {dom,w,d,form,business,marital,choose};
}
for(const prefix of ['profile.','registration.profile.'])test(`conditional sections preserve edits and skip irrelevant validation for ${prefix}`,async()=>{
 const {dom,w,d,form,business,marital,choose}=await setup(prefix);
 try{
  assert.equal(d.querySelector('[data-profile-business-fields]').hidden,true);assert.equal(d.querySelector('[data-profile-spouse-section]').hidden,true);
  const data=new w.FormData(form);assert.equal(data.get('_csrf'),'token');assert.equal(data.get(prefix+'hasBusiness'),'false');assert.equal(data.has(prefix+'businessName'),false);assert.equal(data.has(prefix+'spouseName'),false);assert.equal(data.get(prefix+'otherIncomeSources'),'Employment');assert.equal(form.checkValidity(),true);
  choose(business,'true');assert.equal(d.querySelector('[data-profile-business-fields]').hidden,false);assert.equal(form.checkValidity(),false);
  choose(business,'false');choose(business,'true');assert.equal(d.querySelector(`[name="${prefix}businessName"]`).value,'Unsaved shop');
  for(const state of ['MARRIED','SEPARATED','NOT_DISCLOSED']){choose(marital,state);assert.equal(d.querySelector('[data-profile-spouse-section]').hidden,false);assert.equal(new w.FormData(form).get(prefix+'spouseName'),'Unsaved spouse');}
  for(const state of ['SINGLE','DIVORCED','WIDOWED']){choose(marital,state);assert.equal(d.querySelector('[data-profile-spouse-section]').hidden,true);assert.equal(new w.FormData(form).has(prefix+'spouseName'),false);}
  choose(marital,'MARRIED');assert.equal(d.querySelector(`[name="${prefix}spouseName"]`).value,'Unsaved spouse');
 }finally{dom.window.close();}
});
test('reset restores conditional visibility without clearing typed fields on ordinary toggles',async()=>{
 const {dom,d,form,business,marital,choose}=await setup();try{choose(business,'true');choose(marital,'MARRIED');form.reset();await Promise.resolve();assert.equal(d.querySelector('[data-profile-business-fields]').hidden,true);assert.equal(d.querySelector('[data-profile-spouse-section]').hidden,true);}finally{dom.window.close();}
});
test('conditional initialization never enables controls in a submitted read-only profile',async()=>{
 const {dom,d,business,marital,choose}=await setup('profile.',true);try{choose(business,'true');choose(marital,'MARRIED');assert.equal(d.querySelector('[name="profile.businessName"]').matches(':disabled'),true);assert.equal(d.querySelector('[name="profile.spouseName"]').matches(':disabled'),true);}finally{dom.window.close();}
});
