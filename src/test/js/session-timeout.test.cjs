const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

function setup(warningEnabled = true, {requestStartedAt, navigationStartedAt, initializedAt = 0} = {}) {
    const dom = new JSDOM(`<form method="post" class="accounting-create-form"><input name="purpose" value="original"><a href="/finance/loan-records">Cancel</a></form>
        <div id="accounting-discard-modal" hidden><div role="dialog"><button data-accounting-keep>Close</button>
        <div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div>
        <div id="sessionInactivityPrompt" class="hidden" data-timeout-ms="120000" data-warning-ms="60000" data-warning-enabled="${warningEnabled}"
        data-unfinished-work-message="You have unfinished work. Verify to continue."
        data-keepalive-url="/session/keepalive" data-csrf-header="X-CSRF-TOKEN" data-csrf-token="masked-token" data-refresh-error-message="Please retry.">
        <p id="sessionInactivityMessage"></p>
        <span id="sessionInactivityCountdown"></span><div><div id="sessionInactivityProgress"></div></div>
        <input id="sessionInactivityPassword" type="password"><p id="sessionInactivityError" class="hidden"></p>
        <form id="sessionInactivityLogoutForm" action="/logout" method="post"><button type="submit">Logout Now</button></form>
        <button id="sessionInactivityStayButton">Stay Logged In</button></div>`, {url:'http://localhost/finance/loan-records/new', runScripts:'outside-only'});
    const w = dom.window, d = w.document;
    let now = initializedAt, nextId = 1, logouts = 0;
    if (requestStartedAt !== undefined) d.getElementById('sessionInactivityPrompt').dataset.clientRequestStartedAt = requestStartedAt;
    if (navigationStartedAt !== undefined) {
        Object.defineProperty(w.performance, 'timeOrigin', {value:navigationStartedAt});
        w.performance.getEntriesByType = () => [{requestStart:0}];
    }
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
    const end = shell.indexOf("    onReady(", start);
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

test('slow page initialization does not restart the full timeout after the HTML request', () => {
    const s = setup(true, {requestStartedAt:0, initializedAt:30000});
    try {
        s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a full-page load uses navigation request timing before scripts initialize', () => {
    const s = setup(true, {navigationStartedAt:1000, initializedAt:31000});
    try {
        s.advance(61000); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(121000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a renewal submitted before expiry can succeed after the old countdown reaches zero', async () => {
    const s = setup();
    try {
        let resolve;
        s.w.fetch = () => new Promise(done => { resolve = done; });
        s.advance(119000); s.password.value = 'current'; s.stay.click();
        s.advance(121000); assert.equal(s.logouts(), 0);
        resolve({ok:true, status:200, json:async () => ({refreshed:true, timeoutMs:120000})}); await settled();
        assert.equal(s.prompt.classList.contains('hidden'), true);
        assert.equal(s.stay.disabled, false); assert.equal(s.logouts(), 0);
        s.advance(239000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('renewal uses the server timeout and request start rather than response arrival', async () => {
    const s = setup();
    try {
        let resolve;
        s.w.fetch = () => new Promise(done => { resolve = done; });
        s.advance(60000); s.password.value = 'current'; s.stay.click();
        s.advance(65000);
        resolve({ok:true, status:200, json:async () => ({refreshed:true, timeoutMs:180000})}); await settled();
        s.advance(179999); assert.equal(s.prompt.classList.contains('hidden'), true);
        s.advance(180000); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(240000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('warning off logs out untouched pages automatically without showing the modal', () => {
    const s = setup(false);
    try {
        s.advance(60000);
        assert.equal(s.prompt.classList.contains('hidden'), true);
        assert.equal(s.logouts(), 0);
        s.advance(120000);
        assert.equal(s.prompt.classList.contains('hidden'), true);
        assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('warning off still protects unsaved changes and keeps the original expiry deadline', () => {
    const s = setup(false);
    try {
        s.form.querySelector('input').value = 'unfinished';
        s.advance(60000);
        assert.equal(s.prompt.classList.contains('hidden'), false);
        assert.equal(s.d.getElementById('sessionInactivityMessage').textContent, 'You have unfinished work. Verify to continue.');
        s.advance(120000);
        assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('an edit during the final minute opens the work warning without extending expiry', () => {
    const s = setup(false);
    try {
        s.advance(60000);
        assert.equal(s.prompt.classList.contains('hidden'), true);
        s.advance(90000);
        const input = s.form.querySelector('input'); input.value = 'unfinished';
        input.dispatchEvent(new s.w.Event('input', {bubbles:true}));
        assert.equal(s.prompt.classList.contains('hidden'), false);
        assert.equal(s.d.getElementById('sessionInactivityCountdown').textContent, '30');
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('reverted edits and GET filters do not trigger the work warning', () => {
    const s = setup(false);
    try {
        const input = s.form.querySelector('input'); input.value = 'edited'; input.value = 'original';
        const filter = s.d.createElement('form'); filter.innerHTML = '<input name="search" value="">';
        s.d.body.append(filter); filter.querySelector('input').value = 'filter text';
        s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), true);
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a saved form normalized after initialization uses its shared clean baseline', () => {
    const s = setup(false);
    try {
        s.form.querySelector('input').value = 'initialized saved value';
        s.form.dispatchEvent(new s.w.Event('accounting:form-refreshed', {bubbles:true}));
        s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), true);
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('selected upload files trigger the work warning even when the file control is disabled', () => {
    const s = setup(false);
    try {
        const upload = s.d.createElement('input'); upload.type = 'file'; upload.name = 'document'; upload.disabled = true;
        Object.defineProperty(upload, 'files', {value:[new s.w.File(['document'], 'evidence.pdf')]});
        s.form.append(upload);
        s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(120000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('changed checkbox and multi-select values trigger the work warning', () => {
    for (const markup of ['<input name="enabled" type="checkbox">', '<select name="documents" multiple><option>A</option><option>B</option></select>']) {
        const s = setup(false);
        try {
            s.form.insertAdjacentHTML('beforeend', markup);
            const field = s.form.lastElementChild;
            if (field.tagName === 'SELECT') field.options[1].selected = true;
            else field.checked = true;
            s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), false);
        } finally { s.dom.window.close(); }
    }
});

test('an in-flight upload remains protected while its form is detached and clears on completion', () => {
    for (const completed of [false, true]) {
        const s = setup(false);
        try {
            const upload = s.d.createElement('input'); upload.type = 'file'; upload.name = 'document';
            Object.defineProperty(upload, 'files', {value:[new s.w.File(['document'], 'evidence.pdf')]});
            s.form.append(upload);
            s.d.dispatchEvent(new s.w.CustomEvent('workspace:form-pending', {detail:{form:s.form}}));
            s.form.remove();
            if (completed) s.d.dispatchEvent(new s.w.CustomEvent('workspace:form-settled', {detail:{form:s.form}}));
            s.advance(60000); assert.equal(s.prompt.classList.contains('hidden'), completed);
        } finally { s.dom.window.close(); }
    }
});

test('late wake after expiry logs out without granting extra time for unfinished work', () => {
    const s = setup(false);
    try {
        s.form.querySelector('input').value = 'unfinished';
        s.advance(125000); assert.equal(s.logouts(), 1);
        assert.equal(s.prompt.classList.contains('hidden'), true);
    } finally { s.dom.window.close(); }
});

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

test('a stalled renewal has a bounded wait and a late response cannot undo logout', async () => {
    const s = setup();
    try {
        s.form.querySelector('input').value = 'unsaved';
        let resolve;
        s.w.fetch = () => new Promise(done => { resolve = done; });
        s.advance(119000); s.password.value = 'correct'; s.stay.click();
        s.advance(120000); assert.equal(s.logouts(), 0);
        s.advance(134000); await settled(); assert.equal(s.logouts(), 1);
        const unload = new s.w.Event('beforeunload', {cancelable:true}); s.w.dispatchEvent(unload);
        assert.equal(unload.defaultPrevented, false);
        resolve({ok:true, status:200, json:async () => ({refreshed:true})}); await settled();
        assert.equal(s.stay.disabled, true); assert.equal(s.prompt.classList.contains('hidden'), false);
        s.advance(240000); assert.equal(s.logouts(), 1);
    } finally { s.dom.window.close(); }
});

test('a renewal timeout before session expiry keeps the warning usable for retry', async () => {
    const s = setup();
    try {
        let signal;
        s.w.fetch = (_, options) => { signal = options.signal; return new Promise(() => {}); };
        s.advance(60000); s.password.value = 'correct'; s.stay.click();
        s.advance(75000); await settled();
        assert.equal(signal.aborted, true); assert.equal(s.logouts(), 0);
        assert.equal(s.stay.disabled, false); assert.equal(s.password.disabled, false);
        assert.equal(s.d.getElementById('sessionInactivityError').textContent, 'Please retry.');
        s.w.fetch = async () => ({ok:true, status:200, json:async () => ({refreshed:true, timeoutMs:120000})});
        s.stay.click(); await settled();
        assert.equal(s.prompt.classList.contains('hidden'), true);
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
