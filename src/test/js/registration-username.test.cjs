const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const page = fs.readFileSync('src/main/webapp/WEB-INF/jsp/register-member.jsp', 'utf8');
const checker = page.slice(page.indexOf('const createAvailabilityChecker ='), page.indexOf('const checkUsernameAvailability ='));

function fixture() {
    const field = page.match(/<form:input path="username"[\s\S]*?\/>/)[0]
        .replace('form:input', 'input').replace('path="username"', 'name="username"').replace('cssClass=', 'class=');
    const dom = new JSDOM(`<form>${field}<p id="status" data-checking-message="Checking" data-available-message="Available"
        data-unavailable-message="Taken" data-failed-message="Could not check"></p></form>`, {runScripts:'outside-only', url:'http://localhost/register/member'});
    const w = dom.window, input = w.document.querySelector('input'), status = w.document.querySelector('#status');
    let now = 0, sequence = 0;
    const timers = new Map(), requests = [];
    w.setTimeout = (callback, delay) => {const id = ++sequence; timers.set(id, {callback, due:now + delay}); return id;};
    w.clearTimeout = id => timers.delete(id);
    w.fetch = (url, options) => new Promise((resolve, reject) => requests.push({url, options, resolve, reject}));
    w.eval(checker + '\nwindow.bindAvailability = createAvailabilityChecker;');
    const check = w.bindAvailability(input, status, value => '/register/member/username-availability?username=' + encodeURIComponent(value), () => input.validity.valid);
    const type = value => {input.value = value; input.dispatchEvent(new w.Event('input', {bubbles:true}));};
    const advance = milliseconds => {
        now += milliseconds;
        for (const [id, timer] of [...timers]) if (timer.due <= now) {timers.delete(id); timer.callback();}
    };
    const reply = (index, available, ok = true) => requests[index].resolve({ok, json:async () => ({available})});
    return {dom, w, input, status, requests, check, type, advance, reply};
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('username availability waits for a typing pause and sends only the latest valid value', async () => {
    const f = fixture();
    try {
        for (const value of ['a','as','ash','asha.mushi']) f.type(value);
        f.advance(349); assert.equal(f.requests.length, 0);
        f.advance(1); assert.equal(f.requests.length, 1);
        assert.equal(f.requests[0].url, '/register/member/username-availability?username=asha.mushi');
        assert.equal(f.requests[0].options.cache, 'no-store');
        f.reply(0, true); await flush(); assert.equal(f.status.textContent, 'Available');
    } finally {f.dom.window.close();}
});

test('short, invalid and empty usernames do not query the server', () => {
    const f = fixture();
    try {
        for (const value of ['', 'ab', '1234', 'asha@example.com', 'asha mushi', 'a'.repeat(33)]) {
            f.type(value); f.advance(350); assert.equal(f.requests.length, 0); assert.equal(f.status.textContent, '');
        }
    } finally {f.dom.window.close();}
});

test('stale responses cannot overwrite the status of a newer username', async () => {
    const f = fixture();
    try {
        f.type('asha'); f.advance(350);
        f.type('neema'); assert.equal(f.requests[0].options.signal.aborted, true); f.advance(350);
        f.reply(1, false); await flush(); assert.equal(f.status.textContent, 'Taken');
        f.reply(0, true); await flush(); assert.equal(f.status.textContent, 'Taken');
        f.type(''); f.advance(350); assert.equal(f.status.textContent, ''); assert.equal(f.requests.length, 2);
    } finally {f.dom.window.close();}
});

test('a failed check is shown as unavailable data rather than an available or taken username', async () => {
    const f = fixture();
    try {
        f.type('asha'); f.advance(350); f.reply(0, true, false); await flush();
        assert.equal(f.status.textContent, 'Could not check');
        f.type('neema'); f.advance(350); f.requests[1].reject(new Error('Network unavailable')); await flush();
        assert.equal(f.status.textContent, 'Could not check');
    } finally {f.dom.window.close();}
});

test('leaving registration cancels scheduled and in-flight availability checks', async () => {
    const f = fixture();
    try {
        f.type('asha'); f.w.dispatchEvent(new f.w.Event('pagehide')); f.advance(350);
        assert.equal(f.requests.length, 0);
        f.type('neema'); f.advance(350); f.w.dispatchEvent(new f.w.Event('pagehide'));
        assert.equal(f.requests[0].options.signal.aborted, true);
        f.reply(0, true); await flush(); assert.notEqual(f.status.textContent, 'Available');
    } finally {f.dom.window.close();}
});

test('a recovered username is checked without clearing its value', async () => {
    const f = fixture();
    try {
        f.input.value = 'Asha.Mushi'; f.check(); f.advance(350); f.reply(0, true); await flush();
        assert.equal(f.input.value, 'Asha.Mushi'); assert.equal(f.status.textContent, 'Available');
    } finally {f.dom.window.close();}
});
