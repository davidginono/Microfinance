const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/dashboard-work.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
const card=(count=4)=>({id:'voucher-INCOMPLETE',labelKey:'dashboard.work.voucher.INCOMPLETE',tone:'neutral',count,amount:120000,oldestDays:3,href:'/finance/approval?category=INCOMPLETE',groups:[{labelKey:'voucher.title.RECEIPT',count,amount:120000,href:'/finance/approval?category=INCOMPLETE&type=RECEIPT'}]});
async function setup(fetcher,configure){
 const labels={expand:'Expand',collapse:'Collapse',filters:'Display filters',back:'Back',minimumDays:'Minimum age',minimumAmount:'Minimum amount',apply:'Apply',reset:'Reset',open:'Open workspace',oldest:'Oldest',days:'days',all:'All',empty:'No work',updated:'Updated',chart:'Pending work',title:'Pending work','voucher.INCOMPLETE':'Pending drafts'};
 const text=Object.entries(labels).map(([key,value])=>`<span data-key="dashboard.work.${key}">${value}</span>`).join('');
 const dom=new JSDOM(`<template id="dashboard-labels" data-icons="/icons/" data-loading="Loading" data-load-error="Unavailable"></template><template data-work-labels>${text}<span data-key="voucher.title.RECEIPT">Receipt vouchers</span></template><section data-dashboard-work data-work-url="/api/dashboard/work" data-context-path=""><span data-work-updated></span><div data-work-status></div><div data-work-cards></div><div data-work-chart></div></section>`,{url:'http://localhost/accountant/dashboard',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,calls=[];w.WorkspaceNavigation={onReady:f=>f()};w.DashboardCharts={render:(el,options)=>{el.textContent=options.title;}};
 w.fetch=async(...args)=>{calls.push(args);return fetcher?fetcher(...args):{ok:true,redirected:false,json:async()=>({infolets:[card()],capturedAt:'2026-10-22T09:42:00+03:00'})};};
 configure?.(w);w.eval(source);await settle();return {dom,w,d:w.document,calls};
}
test('draft count and chart use live payload; native workspace links remain direct',async()=>{
 const {dom,d,calls}=await setup();try{assert.equal(d.querySelectorAll('article').length,1);assert.equal(d.querySelector('.dashboard-infolet-count').textContent,'4');assert.equal(d.querySelector('.dashboard-infolet-open').getAttribute('href'),'http://localhost/finance/approval?category=INCOMPLETE');assert.equal(d.querySelector('.dashboard-infolet-bar').href,'http://localhost/finance/approval?category=INCOMPLETE&type=RECEIPT');assert.equal(d.querySelector('[data-work-chart]').textContent,'Pending work');assert.equal(calls[0][1].credentials,'same-origin');assert.equal(calls[0][1].cache,'no-store');}finally{dom.window.close();}
});
test('expansion and flip preserve the original card and return focus after server-filtered results',async()=>{
 const {dom,d,calls}=await setup();try{const original=d.querySelector('article'),buttons=original.querySelectorAll('.dashboard-infolet-tools button');buttons[0].click();assert.equal(original.classList.contains('is-expanded'),true);assert.equal(original.querySelector('.dashboard-infolet-breakdown').hidden,false);buttons[1].click();assert.equal(original.classList.contains('is-flipped'),true);assert.equal(original.querySelector('.dashboard-infolet-front').inert,true);const fields=original.querySelectorAll('input');assert.equal(d.activeElement,fields[0]);fields[0].value='3';fields[1].value='9999999999999999.99';original.querySelector('form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));await settle();assert.equal(d.querySelector('article'),original);assert.equal(original.classList.contains('is-expanded'),true);assert.equal(original.classList.contains('is-flipped'),false);assert.equal(d.activeElement,buttons[1]);const url=new URL(calls[1][0]);assert.equal(url.searchParams.get('days'),'3');assert.equal(url.searchParams.get('amount'),'9999999999999999.99');}finally{dom.window.close();}
});
test('failed refresh preserves verified values and displays an error instead of a false zero',async()=>{
 let fail=false;const {dom,w,d}=await setup(async()=>fail?{ok:false}:{ok:true,redirected:false,json:async()=>({infolets:[card(9)],capturedAt:'2026-10-22T09:42:00Z'})});try{fail=true;d.dispatchEvent(new w.Event('visibilitychange'));await settle();assert.equal(d.querySelector('.dashboard-infolet-count').textContent,'9');assert.equal(d.querySelector('[data-work-status]').textContent,'Unavailable');assert.equal(d.querySelector('[data-work-status]').getAttribute('role'),'alert');fail=false;d.dispatchEvent(new w.Event('visibilitychange'));await settle();assert.equal(d.querySelector('[data-work-status]').textContent,'');assert.equal(d.querySelector('[data-work-status]').getAttribute('role'),'status');}finally{dom.window.close();}
});
test('refresh removes cards when permission-scoped payload no longer includes them',async()=>{
 let allowed=true;const {dom,w,d}=await setup(async()=>({ok:true,redirected:false,json:async()=>({infolets:allowed?[card()]:[],capturedAt:'2026-10-22T09:42:00Z'})}));try{allowed=false;d.dispatchEvent(new w.Event('visibilitychange'));await settle();assert.equal(d.querySelectorAll('article').length,0);assert.equal(d.querySelector('[data-work-status]').textContent,'No work');}finally{dom.window.close();}
});
test('five-second empty refresh retains the message and does not restart initial loading',async()=>{
 let finish,poll,starts=0;const empty={infolets:[],capturedAt:'2026-11-17T12:15:00Z'};
 const {dom,w,d}=await setup(async()=>({ok:true,redirected:false,json:async()=>empty}),w=>{
  const set=w.setTimeout.bind(w);w.setTimeout=(callback,delay)=>delay===5000?(poll=callback,123):set(callback,delay);
  w.AppLoading={begin:()=>starts++,end:()=>{}};
 });
 try{
  const status=d.querySelector('[data-work-status]'),text=status.firstChild,mutations=[];
  const observer=new w.MutationObserver(records=>mutations.push(...records));observer.observe(status,{childList:true,characterData:true,subtree:true});
  w.fetch=()=>new Promise(resolve=>finish=resolve);poll();await settle();
  assert.equal(status.textContent,'No work');assert.equal(status.firstChild,text);assert.equal(starts,1);assert.equal(d.querySelector('[data-dashboard-work]').getAttribute('aria-busy'),'true');
  finish({ok:true,redirected:false,json:async()=>empty});await settle();
  assert.equal(status.firstChild,text);assert.equal(mutations.length,0);assert.equal(starts,1);assert.equal(d.querySelector('[data-dashboard-work]').hasAttribute('aria-busy'),false);observer.disconnect();
 }finally{dom.window.close();}
});
test('refresh retains its error until a successful response restores the empty state',async()=>{
 let poll,finish;const {dom,w,d}=await setup(async()=>({ok:true,redirected:false,json:async()=>({infolets:[],capturedAt:'2026-11-17T12:15:00Z'})}),w=>{
  const set=w.setTimeout.bind(w);w.setTimeout=(callback,delay)=>delay===5000?(poll=callback,123):set(callback,delay);
 });
 try{
  w.fetch=async()=>({ok:false});poll();await settle();const status=d.querySelector('[data-work-status]');assert.equal(status.textContent,'Unavailable');assert.equal(status.getAttribute('role'),'alert');
  w.fetch=()=>new Promise(resolve=>finish=resolve);poll();await settle();assert.equal(status.textContent,'Unavailable');assert.equal(status.getAttribute('role'),'alert');
  finish({ok:true,redirected:false,json:async()=>({infolets:[],capturedAt:'2026-11-17T12:15:00Z'})});await settle();assert.equal(status.textContent,'No work');assert.equal(status.getAttribute('role'),'status');
 }finally{dom.window.close();}
});
