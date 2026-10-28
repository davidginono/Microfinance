const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/loan-portfolio-report.js','utf8');
test('portfolio tabs retain the active section through a redirect and reload; explicit links take priority',()=>{
 const markup='<div data-portfolio-tabs><a href="#summary">Summary</a><a href="#loans">Loans</a></div><section id="summary" data-portfolio-panel></section><section id="loans" data-portfolio-panel></section>';
 const dom=new JSDOM(markup,{url:'http://localhost/reports/financial/portfolio',runScripts:'outside-only'});const w=dom.window,d=w.document;
 try{
  w.eval(source);d.querySelector('a[href="#loans"]').click();assert.equal(d.querySelector('#loans').hidden,false);
  w.history.replaceState({},'', '/reports/financial/portfolio');d.body.innerHTML=markup;w.eval(source);assert.equal(d.querySelector('#loans').hidden,false);
  w.history.replaceState({},'', '/reports/financial/portfolio#summary');d.body.innerHTML=markup;w.eval(source);assert.equal(d.querySelector('#summary').hidden,false);
 }finally{dom.window.close();}
});
