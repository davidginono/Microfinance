const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');
const source = readFileSync('src/main/resources/static/js/select-enhancer.js','utf8');
const remote = readFileSync('src/main/resources/static/js/loan-recording.js','utf8');
const tick = () => new Promise(resolve=>setTimeout(resolve,0));
async function setup(html, fetch) {
    const dom = new JSDOM(`<body class="aws-console" data-select-search="Search options" data-select-no-matches="No matching options." data-select-load-more="Load more" data-shell-select="Select an option" data-shell-no-options="No options available" data-shell-loading="Loading results...">${html}</body>`,{url:'http://localhost/finance/loan-recording/new',runScripts:'outside-only',pretendToBeVisual:true});
    const w=dom.window;let now=0,serial=0;const timers=new Map();
    w.setTimeout=(fn,delay=0)=>{const id=++serial;timers.set(id,{fn,at:now+delay});return id;};w.clearTimeout=id=>timers.delete(id);
    w.requestAnimationFrame=fn=>{fn();return 0;};w.HTMLElement.prototype.scrollIntoView=function(){};
    w.eval(readFileSync('src/main/resources/static/js/live-search.js','utf8'));w.eval(source);
    if(fetch){w.fetch=fetch;w.eval(remote);}
    if(w.document.readyState!=='complete')await new Promise(resolve=>w.addEventListener('load',resolve,{once:true}));
    await tick();
    const advance=ms=>{now+=ms;for(const [id,timer] of [...timers])if(timer.at<=now){timers.delete(id);timer.fn();}};
    return {dom,w,d:w.document,advance};
}
const options = ['Client One','Client Two','Client Three'].map((name,n)=>`<option value="C${n+1}" ${n===0?'selected':''}>${name}</option>`).join('');
const local = `<form method="post"><label for="client">Client</label><select id="client" name="clientId" data-searchable="true"><option value="">Select an option</option>${options}</select><input name="purpose" value="Working capital"></form>`;
const loan = `<form method="post"><div data-loan-choice data-category="clients"><label for="client">Client</label><select id="client" name="clientId" data-choice-select><option value="">Select an option</option><option value="C1" selected>Client One</option></select><span data-choice-error data-error="Lookup unavailable" data-search-label="Search clients"></span></div><input name="purpose" value="Working capital"></form>`;
const response = choices => ({ok:true,redirected:false,json:async()=>choices});
const labels = d => [...d.querySelectorAll('.neo-select-option')].map(item=>item.firstChild.textContent);

test('searching to a single option reserves a full row below the fixed search, irrespective of old popup height',async()=>{
    const {dom,w,d}=await setup(local);const menu=d.querySelector('.neo-select-menu');
    Object.defineProperty(menu,'scrollHeight',{value:54});d.querySelector('.neo-select-button').getBoundingClientRect=()=>({top:100,bottom:132,left:30,width:400});
    d.querySelector('.neo-select-button').click();const search=d.querySelector('.neo-select-search input');search.value='Two';search.dispatchEvent(new w.Event('input'));
    assert.ok(parseFloat(menu.style.height)>=86);assert.equal(labels(d)[0],'Client Two');assert.equal(d.querySelector('.neo-select-option-label').textContent,'Client Two');dom.window.close();
});
test('remote option requests show an inline spinner while retaining authorized matches and native selection',async()=>{
    const {dom,d}=await setup(local);const select=d.querySelector('select'),picker=dom.window.NeoSelect.search(select);
    picker.setLoading(true);assert.equal(d.querySelector('.neo-select-options').getAttribute('aria-busy'),'true');assert.equal(d.querySelectorAll('.neo-select-loading .app-loading-spinner').length,1);
    assert.equal(select.value,'C1');assert.equal(labels(d).length,3);picker.setLoading(false);assert.equal(d.querySelector('.neo-select-loading'),null);assert.equal(select.value,'C1');dom.window.close();
});

test('searchable options filter immediately without altering native values or submitting forms',async()=>{
    const {dom,w,d}=await setup(local);let submits=0;d.querySelector('form').addEventListener('submit',event=>{event.preventDefault();submits++;});
    d.querySelector('.neo-select-button').click();const search=d.querySelector('.neo-select-search input');assert.equal(d.activeElement,search);
    assert.equal(search.closest('form'),null);assert.equal(d.querySelector('.neo-select-options').getAttribute('role'),'listbox');
    search.value='tWo';search.dispatchEvent(new w.Event('input',{bubbles:true}));assert.deepEqual(labels(d),['Client Two']);
    assert.equal(d.querySelector('select').value,'C1');assert.equal(d.querySelector('[name=purpose]').value,'Working capital');
    const enter=new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});search.dispatchEvent(enter);assert.equal(enter.defaultPrevented,true);assert.equal(submits,0);
    search.value='missing';search.dispatchEvent(new w.Event('input'));assert.equal(d.querySelector('.neo-select-empty').textContent,'No matching options.');
    search.value='';search.dispatchEvent(new w.Event('input'));assert.equal(labels(d).length,3);dom.window.close();
});

test('keyboard navigation advances from the focused option and selection emits original form events',async()=>{
    const {dom,w,d}=await setup(local);const select=d.querySelector('select');let changes=0;select.addEventListener('change',()=>changes++);
    d.querySelector('.neo-select-button').click();const search=d.querySelector('.neo-select-search input');
    search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement.dataset.optionIndex,'1');
    d.activeElement.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));assert.equal(d.activeElement.dataset.optionIndex,'2');
    d.activeElement.click();assert.equal(select.value,'C2');assert.equal(changes,1);assert.equal(d.querySelector('.neo-select-menu').hidden,true);assert.equal(d.activeElement,d.querySelector('.neo-select-button'));
    d.querySelector('.neo-select-button').click();search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.equal(d.querySelector('.neo-select-menu').hidden,true);dom.window.close();
});

test('large static lists acquire search automatically, while small enums and native opt-outs retain their contracts',async()=>{
    const {dom,d}=await setup(`<select id="large">${Array.from({length:8},(_,n)=>`<option>${n}</option>`).join('')}</select><select id="small"><option>Active</option><option>Inactive</option></select><select id="native" data-native-select="true"><option>Native</option></select>`);
    assert.equal(d.querySelectorAll('.neo-select-search').length,1);assert.equal(d.querySelectorAll('.neo-select-button').length,2);assert.equal(d.querySelector('#native').dataset.neoEnhanced,undefined);
    dom.window.close();
});

test('remote selects show immediate cached matches and issue one request after rapid typing stops',async()=>{
    const calls=[];const {dom,w,d,advance}=await setup(loan,async url=>{calls.push(url);return response([{id:'C1',label:'Client One'},{id:'C2',label:'Client Two'},{id:'C3',label:'Client Three'}]);});
    d.querySelector('.neo-select-button').click();const search=d.querySelector('.neo-select-search input');
    for(const query of ['t','tw','two']){search.value=query;search.dispatchEvent(new w.Event('input'));advance(100);}
    assert.deepEqual(labels(d),['Client Two']);assert.equal(calls.length,1);advance(249);assert.equal(calls.length,1);advance(1);await tick();assert.equal(calls.length,2);
    assert.equal(new URL(calls[1],'http://localhost').searchParams.get('search'),'two');assert.equal(d.querySelector('select').value,'C1');assert.equal(d.querySelector('[name=purpose]').value,'Working capital');
    dom.window.close();
});

test('out-of-order server responses cannot overwrite a newer query; closing cancels pending searches',async()=>{
    const pending=[];let initial=true;const {dom,w,d,advance}=await setup(loan,url=>{if(initial){initial=false;return Promise.resolve(response([{id:'C1',label:'Client One'}]));}return new Promise(resolve=>pending.push({url,resolve}));});
    d.querySelector('.neo-select-button').click();const search=d.querySelector('.neo-select-search input');
    search.value='old';search.dispatchEvent(new w.Event('input'));advance(350);
    search.value='new';search.dispatchEvent(new w.Event('input'));advance(350);
    pending[1].resolve(response([{id:'C3',label:'Newest result'}]));await tick();pending[0].resolve(response([{id:'C2',label:'Stale result'}]));await tick();assert.deepEqual(labels(d),['Newest result']);
    search.value='cancelled';search.dispatchEvent(new w.Event('input'));d.querySelector('.neo-select-button').click();advance(350);assert.equal(pending.length,2);dom.window.close();
});

test('Load more replaces arrow paging and keeps server pages and retained options bounded',async()=>{
    const calls=[];const {dom,d}=await setup(loan,async url=>{const page=Number(new URL(url,'http://localhost').searchParams.get('page'));calls.push(page);return response(Array.from({length:26},(_,n)=>({id:'C'+(page*25+n+1),label:'Client '+(page*25+n+1)})));});
    const more=d.querySelector('.neo-select-more');assert.equal(more.hidden,false);assert.equal(d.querySelector('.loan-choice-controls'),null);
    for(let page=1;page<=5;page++){more.click();await tick();}
    assert.deepEqual(calls,[0,1,2,3,4,5]);assert.ok(d.querySelector('select').options.length<=102);assert.equal(d.querySelector('select').value,'C1');dom.window.close();
});

test('body-mounted menus fit the viewport and are removed with dynamic controls',async()=>{
    const {dom,w,d}=await setup(local);const button=d.querySelector('.neo-select-button');button.getBoundingClientRect=()=>({top:730,bottom:762,left:10,width:320});
    button.click();const menu=d.querySelector('.neo-select-menu');assert.ok(Number.parseFloat(menu.style.top)<730);assert.equal(menu.style.width,'320px');
    d.querySelector('form').remove();await tick();assert.equal(d.querySelector('.neo-select-menu'),null);dom.window.close();
});
