const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const source = fs.readFileSync('src/main/resources/static/js/institution-details.js', 'utf8');
const sections = ['overview', 'loans', 'members', 'financials', 'sms', 'audit'];
const settle = () => new Promise(resolve => setImmediate(resolve));
const response = (section, extra = {}) => ({ok:true, redirected:false, headers:{get:()=>'text/html'},
    text:async()=>`<div id="institution-section" data-institution-section="${section}" role="tabpanel" aria-labelledby="institution-tab-${section}"><section>${section}</section></div>`, ...extra});
function setup(fetch) {
    const dom = new JSDOM(`<div data-institution-details data-loading="Loading" data-error="Section unavailable" data-retry="Try again">
        <section data-detail-summary>Institution 1001</section><form data-institution-scope><input name="section" value="overview"><input name="reason" value="draft"></form>
        <a data-institution-reset href="?section=overview">Reset</a><nav data-institution-tabs>${sections.map(section=>`<a id="institution-tab-${section}" role="tab" data-institution-tab="${section}" href="?section=${section}&stationId=BR-1">${section}</a>`).join('')}</nav>
        <div id="institution-section" data-institution-section="overview" role="tabpanel"><section>Original overview</section></div></div>`,
        {url:'http://localhost/admin/saccos/1001?section=overview&stationId=BR-1', runScripts:'outside-only'});
    const w=dom.window, d=w.document, initialized=[];
    w.WorkspaceNavigation={onReady:callback=>callback()}; w.fetch=fetch;
    w.AbortSignal=AbortSignal; w.AbortController=AbortController;
    w.ResourceTable={init:root=>initialized.push(root)};
    w.eval(source);
    return {dom,w,d,initialized,tab:section=>d.querySelector(`[data-institution-tab=${section}]`)};
}
test('tabs replace only their section, retain live branch form values and update navigation context', async()=>{
    const calls=[];
    const {dom,w,d,tab,initialized}=setup(async(url,options)=>{calls.push({url,options});return response('loans');});
    try {
        const summary=d.querySelector('[data-detail-summary]'), form=d.querySelector('form');
        form.elements.reason.value='Unsaved reason';
        tab('loans').click(); await settle();
        assert.equal(d.querySelector('[data-detail-summary]'),summary); assert.equal(d.querySelector('form'),form);
        assert.equal(form.elements.reason.value,'Unsaved reason'); assert.equal(form.elements.section.value,'loans');
        assert.equal(d.querySelector('[data-institution-section]').dataset.institutionSection,'loans');
        assert.equal(tab('loans').getAttribute('aria-selected'),'true'); assert.equal(tab('overview').tabIndex,-1);
        assert.equal(new URL(w.location.href).searchParams.get('stationId'),'BR-1');
        assert.equal(new URL(d.querySelector('[data-institution-reset]').href).searchParams.get('section'),'loans');
        assert.equal(calls.length,1); assert.equal(calls[0].options.credentials,'same-origin'); assert.equal(initialized.length,1);
    } finally {dom.window.close();}
});
test('slow and canceled tab responses cannot replace a newer selection', async()=>{
    const waiting=[];
    const {dom,d,tab}=setup((url,options)=>new Promise(resolve=>waiting.push({resolve,options})));
    try {
        tab('loans').click(); assert.equal(d.querySelector('#institution-section').getAttribute('aria-busy'),'true');
        tab('members').click(); assert.equal(waiting[0].options.signal.aborted,true);
        waiting[1].resolve(response('members')); await settle();
        waiting[0].resolve(response('loans')); await settle();
        assert.equal(d.querySelector('[data-institution-section]').dataset.institutionSection,'members');
        assert.equal(d.querySelector('[role=alert]'),null);
    } finally {dom.window.close();}
});
test('failed tabs keep current content and offer local retry without replaying a mutation', async()=>{
    let attempts=0;
    const {dom,d,tab}=setup(async()=>++attempts===1?response('loans',{ok:false}):response('loans'));
    try {
        tab('loans').click(); await settle();
        assert.match(d.querySelector('#institution-section').textContent,/Original overview/);
        assert.equal(tab('overview').getAttribute('aria-selected'),'true');
        assert.equal(d.querySelector('#institution-section').hasAttribute('aria-busy'),false);
        assert.equal(d.activeElement,d.querySelector('[role=alert]'));
        d.querySelector('[role=alert] button').click(); await settle();
        assert.equal(attempts,2); assert.equal(d.querySelector('[data-institution-section]').dataset.institutionSection,'loans');
        assert.equal(d.querySelector('[role=alert]'),null);
    } finally {dom.window.close();}
});
test('wrong-section and authentication responses are rejected without dropping the current form', async()=>{
    for (const result of [response('overview'),response('loans',{redirected:true})]) {
        const {dom,d,tab}=setup(async()=>result);
        try {const form=d.querySelector('form');tab('loans').click();await settle();assert.equal(d.querySelector('form'),form);assert.ok(d.querySelector('[role=alert]'));}
        finally {dom.window.close();}
    }
});
test('keyboard tabs use roving focus and choosing the current tab cancels a pending request', async()=>{
    let calls=0;
    const {dom,w,d,tab}=setup(()=>{calls++;return new Promise(()=>{});});
    try {
        tab('overview').dispatchEvent(new w.KeyboardEvent('keydown',{key:'End',bubbles:true,cancelable:true}));
        assert.equal(d.activeElement,tab('audit'));assert.equal(calls,1);
        tab('overview').click();assert.equal(calls,1);assert.equal(d.querySelector('#institution-section').hasAttribute('aria-busy'),false);
        assert.equal(d.querySelector('.institution-section-status'),null);
        d.dispatchEvent(new w.Event('workspace:before-navigate'));
    } finally {dom.window.close();}
});
