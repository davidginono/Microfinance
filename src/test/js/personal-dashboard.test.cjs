const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/resources/static/js/personal-dashboard.js','utf8');
const settle=()=>new Promise(resolve=>setTimeout(resolve,0));
const widget=(id,hidden=false)=>`<section data-personal-activity="${id}" data-activity-url="/api/dashboard/activities/${id}" ${hidden?'hidden':''}><a href="/staff/offices/loan-officer/queue?source=${id}" data-activity-count="loan-officer">—</a><p data-activity-status></p></section>`;
async function setup(html,fetch){
 const dom=new JSDOM('<template id="dashboard-labels" data-loading="Loading" data-load-error="Unable to refresh"></template>'+html,{url:'http://localhost/staff/dashboard',runScripts:'outside-only',pretendToBeVisual:true});
 const timers=new Map(),nativeTimeout=dom.window.setTimeout.bind(dom.window),nativeClear=dom.window.clearTimeout.bind(dom.window);let next=-1;
 dom.window.setTimeout=(fn,delay)=>{if(delay!==5000)return nativeTimeout(fn,delay);const id=next--;timers.set(id,fn);return id;};
 dom.window.clearTimeout=id=>{if(id<0)timers.delete(id);else nativeClear(id);};
 dom.window.fetch=fetch;dom.window.eval(readFileSync('src/main/resources/static/js/ui-loading.js','utf8'));dom.window.eval(source);await settle();
 return {dom,w:dom.window,d:dom.window.document,tick(){const entry=timers.entries().next().value;if(entry){timers.delete(entry[0]);entry[1]();}},timers};
}
const response=(source,shown=2,more=false)=>({ok:true,redirected:false,json:async()=>({source,entries:[{office:'loan-officer',shown,more}]})});

test('loads at most two widgets concurrently and preserves queue links',async t=>{
 const pending=[],calls=[];
 const {dom,d}=await setup(widget('loans')+widget('clients')+widget('accounts'),(url,options)=>{calls.push([url,options]);return new Promise(resolve=>pending.push(resolve));});t.after(()=>dom.window.close());
 assert.equal(calls.length,2);pending[0](response('loans',25,true));await settle();await settle();assert.equal(calls.length,3);
 assert.equal(d.querySelector('[data-activity-count]').textContent,'25+');assert.match(d.querySelector('a').href,/loan-officer\/queue\?source=loans$/);
 assert.equal(calls[0][1].credentials,'same-origin');assert.equal(calls[0][1].cache,'no-store');
 pending[1](response('clients'));pending[2](response('accounts'));await settle();
});
test('hidden widgets do not fetch until restored and resizing never fetches',async t=>{
 let calls=0;const {dom,w,d}=await setup(widget('loans',true),async()=>{calls++;return response('loans');});t.after(()=>dom.window.close());
 assert.equal(calls,0);const root=d.querySelector('section');root.hidden=false;root.dispatchEvent(new w.Event('dashboard:visibility'));await settle();assert.equal(calls,1);
 w.dispatchEvent(new w.Event('resize'));await settle();assert.equal(calls,1);
});
test('refresh failures retain verified counts and allow a local retry',async t=>{
 let fail=false;const {dom,d,tick}=await setup(widget('loans'),async()=>{if(fail)throw new Error('offline');return response('loans',4);});t.after(()=>dom.window.close());
 fail=true;tick();await settle();assert.equal(d.querySelector('a').textContent,'4');assert.equal(d.querySelector('[role=alert]').textContent,'Unable to refresh');assert.equal(d.querySelector('button'),null);
 fail=false;tick();await settle();assert.equal(d.querySelector('[data-activity-status]').textContent,'');
});
test('authorization failure clears previously rendered counts',async t=>{
 let revoked=false;const {dom,d,tick,timers}=await setup(widget('loans'),async()=>revoked?{ok:false,status:403}:response('loans',4));t.after(()=>dom.window.close());
 revoked=true;tick();await settle();assert.equal(d.querySelector('a').textContent,'—');assert.equal(d.querySelector('[role=alert]').textContent,'Unable to refresh');assert.equal(timers.size,0);
});
test('removed widgets abort their request and ignore stale responses',async t=>{
 let resolve,signal;const {dom,w,d}=await setup(widget('loans'),(url,options)=>{signal=options.signal;return new Promise(r=>resolve=r);});t.after(()=>dom.window.close());
 const root=d.querySelector('section');root.hidden=true;root.dispatchEvent(new w.Event('dashboard:visibility'));assert.equal(signal.aborted,true);resolve(response('loans',9));await settle();assert.equal(d.querySelector('a').textContent,'—');
});
test('workspace exit cancels requests and restored pages fetch again',async t=>{
 const signals=[];const {dom,w,d}=await setup(widget('loans'),(url,options)=>{signals.push(options.signal);return new Promise((resolve,reject)=>options.signal.addEventListener('abort',()=>reject(new Error('abort'))));});t.after(()=>dom.window.close());
 w.dispatchEvent(new w.Event('pagehide'));assert.equal(signals[0].aborted,true);await settle();const event=new w.Event('pageshow');Object.defineProperty(event,'persisted',{value:true});w.dispatchEvent(event);await settle();assert.equal(signals.length,2);w.dispatchEvent(new w.Event('pagehide'));
});
test('unknown or malformed response counts are never rendered',async t=>{
 const {dom,d}=await setup(widget('loans'),async()=>response('clients',-1));t.after(()=>dom.window.close());assert.equal(d.querySelector('a').textContent,'—');assert.equal(d.querySelector('[role=alert]').textContent,'Unable to refresh');
});
test('five-second polling updates only numbers without hiding them or overlapping requests',async t=>{
 let calls=0,resolve;const {dom,w,d,tick,timers}=await setup(widget('loans'),async()=>{
  calls++;if(calls===1)return response('loans',2);return new Promise(r=>resolve=r);
 });t.after(()=>dom.window.close());
 assert.equal(timers.size,1);tick();assert.equal(calls,2);assert.equal(d.querySelector('a').textContent,'2');assert.equal(d.querySelector('.app-loading-spinner'),null);
 assert.equal(timers.size,0);tick();assert.equal(calls,2);resolve(response('loans',3));await settle();
 assert.equal(d.querySelector('a').textContent,'3');assert.equal(timers.size,1);
 const root=d.querySelector('section');root.hidden=true;root.dispatchEvent(new w.Event('dashboard:visibility'));assert.equal(timers.size,0);
});
