const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/shell.js','utf8');

for(const personal of [false,true])test('admin breadcrumb uses the available '+(personal?'personal':'platform')+' dashboard',()=>{
    const dom=new JSDOM(`<aside class="shell-sidebar-scroll">${personal?'<a href="/staff/dashboard">Dashboard</a>':''}</aside><main class="shell-main"><div class="erp-page-header" data-aws-page-header><p class="erp-breadcrumb">Admin Tools / Settings</p><h1>Settings</h1></div></main>`,{runScripts:'outside-only',url:'http://localhost/admin/settings-controls',pretendToBeVisual:true});
    try{
        dom.window.ResizeObserver=class{observe(){}};dom.window.matchMedia=()=>({matches:false,addEventListener(){}});dom.window.eval(source);
        assert.equal(dom.window.document.querySelector('.erp-page-path a').getAttribute('href'),personal?'/staff/dashboard':'/admin/dashboard');
    }finally{dom.window.close();}
});

test('authored breadcrumbs stay above promoted detail titles across templates, notices and wrappers',()=>{
    const dom=new JSDOM(`<main class="shell-main"><div class="shell-content-frame">
      <nav id="path" class="erp-page-path"><a href="/finance/accounts">Chart of accounts</a><span aria-current="page">Account version details</span></nav>
      <p id="notice" role="status">Saved</p><template data-resource-details-page></template>
      <div class="accounting-workspace"><div id="header" class="erp-page-header" data-aws-page-header><h1 class="erp-page-title">Account version details</h1></div>
      <section><nav id="nested" class="erp-page-path">Nested section</nav><dl><dt>Account code</dt><dd>111001</dd></dl></section></div>
    </div></main>`,{runScripts:'outside-only',url:'http://localhost/finance/accounts/versions/example',pretendToBeVisual:true});
    try{
        const w=dom.window,d=w.document;w.ResizeObserver=class{observe(){}};w.matchMedia=()=>({matches:false,addEventListener(){}});
        const path=d.querySelector('#path'),header=d.querySelector('#header'),frame=d.querySelector('.shell-content-frame');
        w.eval(source);
        assert.deepEqual([...d.querySelector('.shell-main').children],[path,header,frame]);
        assert.equal(d.querySelector('#notice').parentElement,frame);
        assert.equal(d.querySelector('#nested').parentElement.tagName,'SECTION');
        assert.equal(d.querySelectorAll('#path').length,1);
    }finally{dom.window.close();}
});
