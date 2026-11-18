const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
test('COA expansion shows one accessible spinner without a loading caption and retains retry errors',async()=>{
 const dom=new JSDOM(`<body data-shell-loading="Loading"><section id="coa-register" data-children-url="/finance/accounts/children" data-loading="Loading accounts" data-error="Could not load accounts" data-retry="Retry" data-expand="Expand" data-collapse="Collapse"><form class="coa-filters"></form><button id="coa-view"></button><span id="coa-selection-status" data-empty=""></span><table><tbody><tr data-coa-row data-id="parent" data-heading="true" data-depth="0"><td><input name="coa-selection" type="checkbox"><span class="coa-account-code">111000</span><button class="coa-tree-toggle" aria-expanded="false">Expand</button></td></tr></tbody></table></section></body>`,{url:'http://localhost/finance/accounts',runScripts:'outside-only'});
 const w=dom.window,d=w.document;let finish;
 try{
  w.fetch=()=>new Promise(resolve=>finish=resolve);
  w.eval(fs.readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));
  w.eval(fs.readFileSync('src/main/resources/static/js/accounting-accounts.js','utf8'));
  d.querySelector('.coa-tree-toggle').click();
  const cell=d.querySelector('.coa-tree-message');assert.equal(cell.textContent,'');assert.equal(cell.querySelectorAll('.app-loading-spinner').length,1);assert.equal(cell.querySelector('[role=status]').getAttribute('aria-label'),'Loading accounts');
  finish({ok:false});await new Promise(resolve=>setTimeout(resolve,0));
  assert.equal(d.querySelectorAll('.app-loading-spinner').length,0);assert.match(d.querySelector('.coa-tree-message').textContent,/Could not load accounts.*Retry/);assert.equal(d.querySelector('[data-coa-row]').hasAttribute('aria-busy'),false);
 }finally{dom.window.close();}
});
