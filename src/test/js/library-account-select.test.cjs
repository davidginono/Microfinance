const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),{JSDOM}=require('jsdom');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
test('library accounts open with results, page on the server and submit account codes',async()=>{
 const dom=new JSDOM(`<body data-select-search="Search" data-select-load-more="More" data-shell-select="Choose" data-shell-loading="Loading"><div data-library-account><input type="hidden" name="template.rules[0].debitCode" value=""><span data-library-account-label hidden></span><select data-library-account-select data-searchable="true" data-url="/finance/library/lookup/accounts" data-error="Retry"><option value="">Choose</option></select></div></body>`,{url:'http://localhost/finance/library/transactions',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document,calls=[];
 try{
  w.HTMLElement.prototype.scrollIntoView=function(){};
  w.fetch=async url=>{calls.push(new URL(url));const page=Number(new URL(url).searchParams.get('page'));return {ok:true,redirected:false,json:async()=>page?[{code:'111099',name:'Last bank'}]:Array.from({length:26},(_,n)=>({code:'1110'+String(n).padStart(2,'0'),name:'Bank '+n}))};};
  for(const name of ['live-search.js','select-enhancer.js'])w.eval(fs.readFileSync('src/main/resources/static/js/'+name,'utf8'));
  if(d.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
  for(const name of ['voucher-selects.js','accounting-library.js'])w.eval(fs.readFileSync('src/main/resources/static/js/'+name,'utf8'));
  const select=d.querySelector('select');select.closest('.neo-select').querySelector('button').click();await tick();await tick();
  assert.equal(calls[0].searchParams.get('page'),'0');
  const more=[...d.querySelectorAll('button')].find(b=>b.textContent==='More');assert.ok(more);more.click();await tick();await tick();
  assert.equal(calls[1].searchParams.get('page'),'1');
  const option=[...d.querySelectorAll('.neo-select-option')].find(o=>o.textContent.includes('Last bank'));assert.ok(option);option.click();
  assert.equal(d.querySelector('input[type=hidden]').value,'111099');assert.match(d.querySelector('[data-library-account-label]').textContent,/111099.*Last bank/);
 }finally{dom.window.close();}
});
