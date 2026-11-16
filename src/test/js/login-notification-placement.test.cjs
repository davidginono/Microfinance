const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
function markup(file){
 const source=readFileSync('src/main/webapp/WEB-INF/jsp/'+file,'utf8');
 return source.slice(source.indexOf('<body'),source.indexOf('<script>',source.indexOf('<body')))
  .replace(/<%@[^]*?%>/g,'').replace(/<\/?(?:c|spring):[^>]*>/g,'');
}
test('staff and system-admin notifications share an external rail above the login card',()=>{
 const dom=new JSDOM(markup('login.jsp'));
 try{
  const d=dom.window.document,rail=d.querySelector('#authToastContainer'),card=d.querySelector('.auth-frame');
  assert.equal(rail.closest('.auth-frame'),null);
  assert.equal(rail.parentElement,card.parentElement);
  assert.equal(rail.nextElementSibling,card);
  assert.equal(d.querySelectorAll('#authToastContainer').length,1);
  assert.equal(rail.getAttribute('aria-live'),'polite');
  for(const type of ['staff-password','system-admin-password'])assert.ok(card.querySelector(`input[name="loginType"][value="${type}"]`));
  assert.ok(card.querySelector('#staffLoginIdentifier'));
  assert.equal(card.querySelector('#clientLoginIdentifier'),null);
 }finally{dom.window.close();}
});
test('MFA errors and success feedback sit above the verification card without moving its form',()=>{
 const dom=new JSDOM(markup('auth/staff-mfa.jsp'));
 try{
  const d=dom.window.document,card=d.querySelector('.mfa-card');
  for(const [id,role] of [['staffMfaError','alert'],['staffMfaSuccess','status']]){
   const notice=d.getElementById(id);
   assert.equal(notice.parentElement,card.parentElement);
   assert.equal(notice.closest('.mfa-card'),null);
   assert.equal(notice.getAttribute('role'),role);
   assert.ok(notice.compareDocumentPosition(card)&dom.window.Node.DOCUMENT_POSITION_FOLLOWING);
  }
  assert.ok(card.querySelector('#staffMfaVerifyForm'));
 }finally{dom.window.close();}
});
