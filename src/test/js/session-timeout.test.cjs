const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

function setup() {
    const dom = new JSDOM(`<form class="accounting-create-form"><input name="purpose" value="original"><a href="/finance/loan-records">Cancel</a></form>
        <div id="accounting-discard-modal" hidden><div role="dialog"><button data-accounting-keep>Close</button>
        <div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div>
        <div id="sessionInactivityPrompt" class="hidden" data-timeout-ms="120000" data-warning-ms="60000"
        data-keepalive-url="/session/keepalive" data-csrf-header="X-CSRF-TOKEN" data-csrf-token="masked-token" data-refresh-error-message="Please retry.">
        <span id="sessionInactivityCountdown"></span><div><div id="sessionInactivityProgress"></div></div>
        <input id="sessionInactivityPassword" type="password"><p id="sessionInactivityError" class="hidden"></p>
        <form id="sessionInactivityLogoutForm" action="/logout" method="post"><button type="submit">Logout Now</button></form>
        <button id="sessionInactivityStayButton">Stay Logged In</button></div>`, {url:'http://localhost/finance/loan-records/new', runScripts:'outside-only'});
    const w = dom.window, d = w.document;
    let now = 0, nextId = 1, logouts = 0;
    const timeouts = new Map(), intervals = new Map();
    w.Date.now = () => now;
    w.setTimeout = (fn, delay) => { const id = nextId++; timeouts.set(id, {fn, at:now + delay}); return id; };
    w.clearTimeout = id => timeouts.delete(id);
    w.setInterval = fn => { const id = nextId++; intervals.set(id, fn); return id; };
    w.clearInterval = id => intervals.delete(id);
    w.requestAnimationFrame = fn => fn();
    d.getElementById('sessionInactivityLogoutForm').submit = () => { logouts++; };
    w.eval(fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js', 'utf8'));
    const shell = fs.readFileSync('src/main/resources/static/js/shell.js', 'utf8');
    const start = shell.indexOf('    const initSessionInactivityPrompt = function () {');
    const end = shell.indexOf("    window.addEventListener('load',", start);
    w.eval(shell.slice(start, end) + '\ninitSessionInactivityPrompt();');
    const advance = value => {
        now = value;
        for (const [id, timer] of [...timeouts]) if (timer.at <= now) { timeouts.delete(id); timer.fn(); }
        for (const fn of [...intervals.values()]) fn();
    };
    return {dom, w, d, advance, logouts:() => logouts,
        prompt:d.getElementById('sessionInactivityPrompt'), password:d.getElementById('sessionInactivityPassword'),
        stay:d.getElementById('sessionInactivityStayButton'), form:d.querySelector('.accounting-create-form'),
        logout:d.getElementById('sessionInactivityLogoutForm')};
}
const settled = () => new Promise(resolve => setImmediate(resolve));

test('verification sends the password and CSRF token, restores focus and retains unsaved values', async () => {
    const s = setup();
    try {
        const input = s.form.querySelector('input'); input.value = 'unsaved'; input.focus();
        let request;
        s.w.fetch = async (url, options) => { request = {url, options}; return {ok:true, status:200, json:async () => ({refreshed:true})}; };
        s.advance(60000); s.advance(60000);
        assert.equal(s.d.activeElement, s.password);
        s.password.value = ' password+&= ';
        s.stay.click(); await settled();
        assert.equal(request.url, '/session/keepalive');
        assert.equal(new URLSearchParams(request.options.body).get('password'), ' password+&= ');
        assert.equal(request.options.headers['X-CSRF-TOKEN'], 'masked-token');
        assert.equal(s.prompt.classList.contains('hidden'), true);
        assert.equal(s.password.value, ''); assert.equal(input.value, 'unsaved');
        assert.equal(s.d.activeElement, input); assert.equal(s.logouts(), 0);
        s.advance(119999); assert.equal(s.prompt.classList.contains('hidden'), true);
    } finally { s.dom.window.close(); }
});

test('failed verification leaves the warning usable for a successful retry', async () => {
    const s = setup();
    try {
        s.advance(60000); s.password.value = 'incorrect';
        s.w.fetch = async () => ({ok:false, status:403, json:async () => ({refreshed:false, message:'Wrong password'})});
        s.stay.click(); await settled();
        assert.equal(s.d.getElementById('sessionInactivityError').textContent, 'Wrong password');
        assert.equal(s.stay.disabled, false); assert.equal(s.password.disabled, false);
        assert.equal(s.d.activeElement, s.password);
        s.w.fetch = async () => ({ok:true, status:200, json:async () => ({refreshed:true})});
        s.password.value = 'correct'; s.stay.click(); await settled();
        assert.equal(s.prompt.classList.contains('hidden'), true);
    } finally { s.dom.window.close(); }
});

test('Logout Now bypasses dirty-form confirmation and beforeunload, including an already open discard dialog', () => {
    const s = setup();
    try {
        s.form.querySelector('input').value = 'unsaved';
        s.form.querySelector('a').click();
        assert.equal(s.d.getElementById('accounting-discard-modal').hidden, false);
        s.advance(60000);
        s.logout.querySelector('button').click();
        assert.equal(s.logouts(), 1);
        assert.equal(s.d.getElementById('accounting-discard-modal').hidden, true);
        const unload = new s.w.Event('beforeunload', {cancelable:true});
        s.w.dispatchEvent(unload); assert.equal(unload.defaultPrevented, false);
        s.logout.querySelector('button').click(); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('automatic expiry cannot be blocked by dirty forms or undone by a late renewal response', async () => {
    const s = setup();
    try {
        s.form.querySelector('input').value = 'unsaved';
        let resolve;
        s.w.fetch = () => new Promise(done => { resolve = done; });
        s.advance(60000); s.password.value = 'correct'; s.stay.click();
        s.advance(120000); assert.equal(s.logouts(), 1);
        const unload = new s.w.Event('beforeunload', {cancelable:true}); s.w.dispatchEvent(unload);
        assert.equal(unload.defaultPrevented, false);
        resolve({ok:true, status:200, json:async () => ({refreshed:true})}); await settled();
        assert.equal(s.stay.disabled, true); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(240000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a delayed warning keeps the original expiry deadline', () => {
    const s = setup();
    try {
        s.advance(75000);
        assert.equal(s.d.getElementById('sessionInactivityCountdown').textContent, '45');
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a server-expired session exits without opening a discard confirmation', async () => {
    const s = setup();
    try {
        s.form.querySelector('input').value = 'unsaved'; s.advance(60000);
        s.w.fetch = async () => ({ok:false, status:401, json:async () => ({refreshed:false})});
        s.password.value = 'current'; s.stay.click(); await settled();
        assert.equal(s.logouts(), 1);
        assert.equal(s.d.getElementById('accounting-discard-modal').hidden, true);
    } finally { s.dom.window.close(); }
});

test('network and stale-page failures show recovery text rather than claiming a wrong password', async () => {
    const s = setup();
    try {
        s.advance(60000); s.password.value = 'current';
        s.w.fetch = async () => { throw new TypeError('Failed to fetch'); };
        s.stay.click(); await settled();
        assert.equal(s.d.getElementById('sessionInactivityError').textContent, 'Please retry.');
        s.w.fetch = async () => ({ok:false, status:403, json:async () => { throw new SyntaxError(); }});
        s.stay.click(); await settled();
        assert.equal(s.d.getElementById('sessionInactivityError').textContent, 'Please retry.');
        assert.equal(s.logouts(), 0); assert.equal(s.stay.disabled, false);
    } finally { s.dom.window.close(); }
});

test('the session warning contains keyboard focus above other dialogs', () => {
    const s = setup();
    try {
        s.advance(60000); s.advance(60000);
        s.form.querySelector('input').focus(); assert.equal(s.d.activeElement, s.password);
        s.password.dispatchEvent(new s.w.KeyboardEvent('keydown', {key:'Tab', shiftKey:true, bubbles:true, cancelable:true}));
        assert.equal(s.d.activeElement, s.stay);
        s.stay.dispatchEvent(new s.w.KeyboardEvent('keydown', {key:'Tab', bubbles:true, cancelable:true}));
        assert.equal(s.d.activeElement, s.password);
    } finally { s.dom.window.close(); }
});
