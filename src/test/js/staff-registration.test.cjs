const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const flush = () => new Promise(resolve => setImmediate(resolve));

function fixture() {
    const dom = new JSDOM(`<button data-user-modal-open="create-user"></button><div data-user-modal="create-user">
      <form data-staff-registration data-contact-check-url="/admin/users/contact-availability"
        data-checking="Checking" data-available="Available" data-used="Already used" data-failed="Could not check"
        data-name-invalid="Enter three names" data-email-invalid="Invalid email" data-phone-invalid="Invalid phone">
        <input type="hidden" name="_csrf" value="token" data-staff-csrf>
        <input name="fullName" required><input name="email" type="email" required>
        <span id="staff-email-status" role="status"></span><input name="phone" required pattern="255[0-9]{9}">
        <span id="staff-phone-status" role="status"></span></form></div>`, {runScripts:'outside-only', url:'http://localhost/admin/users'});
    const w = dom.window, form = w.document.querySelector('form'), requests = [], timers = new Map();
    let time = 0, sequence = 0;
    w.setTimeout = (callback, delay) => {const id = ++sequence; timers.set(id, {callback, due:time + delay}); return id;};
    w.clearTimeout = id => timers.delete(id);
    w.fetch = (url, options) => new Promise((resolve, reject) => requests.push({url, options, resolve, reject}));
    w.eval(fs.readFileSync('src/main/resources/static/js/live-search.js', 'utf8'));
    w.eval(fs.readFileSync('src/main/resources/static/js/staff-registration.js', 'utf8'));
    const type = (field, value, options = {}) => {
        form.elements[field].value = value;
        form.elements[field].dispatchEvent(new w.InputEvent('input', {bubbles:true, ...options}));
    };
    const advance = milliseconds => {
        time += milliseconds;
        for (const [id, timer] of [...timers]) if (timer.due <= time) {timers.delete(id); timer.callback();}
    };
    const reply = (index, available, extra = {}) => requests[index].resolve({ok:true, redirected:false, json:async () => ({available}), ...extra});
    const status = field => w.document.querySelector(`#staff-${field}-status`).textContent;
    return {dom, w, form, requests, type, advance, reply, status};
}

test('contact checks wait for a typing pause and POST only the latest value with CSRF', async () => {
    const f = fixture();
    try {
        for (const value of ['a', 'asha', 'asha@', 'asha@example.invalid']) f.type('email', value);
        f.advance(349); assert.equal(f.requests.length, 0);
        f.advance(1); assert.equal(f.requests.length, 1);
        const request = f.requests[0];
        assert.equal(request.url, '/admin/users/contact-availability');
        assert.equal(request.options.method, 'POST'); assert.equal(request.options.cache, 'no-store');
        assert.equal(request.options.credentials, 'same-origin');
        assert.equal(request.options.body.get('value'), 'asha@example.invalid');
        assert.equal(request.options.body.get('field'), 'email'); assert.equal(request.options.body.get('_csrf'), 'token');
        f.reply(0, true); await flush(); assert.equal(f.status('email'), 'Available');
    } finally {f.dom.window.close();}
});

test('invalid and empty contacts do not query the server', () => {
    const f = fixture();
    try {
        for (const [field, values] of [['email', ['', 'bad', 'a@b', 'a'.repeat(161) + '@b.com']], ['phone', ['', '2557', '0712345678', '2557123456789']]]) {
            for (const value of values) {f.type(field, value); f.advance(350); assert.equal(f.requests.length, 0);}
        }
        assert.equal(f.status('email'), 'Invalid email'); assert.equal(f.status('phone'), 'Invalid phone');
    } finally {f.dom.window.close();}
});

test('editing aborts the old request and stale results cannot change the new status', async () => {
    const f = fixture();
    try {
        f.type('email', 'asha@example.invalid'); f.advance(350);
        f.type('email', 'neema@example.invalid'); assert.equal(f.requests[0].options.signal.aborted, true);
        assert.equal(f.status('email'), ''); f.advance(350);
        f.reply(1, false); await flush(); assert.equal(f.status('email'), 'Already used');
        f.reply(0, true); await flush(); assert.equal(f.status('email'), 'Already used');
        f.type('email', ''); f.advance(350); assert.equal(f.status('email'), '');
    } finally {f.dom.window.close();}
});

test('phone and email checks are independent and duplicate feedback leaves the server to decide client reuse', async () => {
    const f = fixture();
    try {
        f.type('fullName', 'Asha Jane Mushi');
        f.type('email', 'asha@example.invalid'); f.type('phone', '255712345678'); f.advance(350);
        assert.equal(f.requests.length, 2); assert.equal(f.requests[1].options.body.get('field'), 'phone');
        f.reply(0, false); f.reply(1, true); await flush();
        assert.equal(f.status('email'), 'Already used'); assert.equal(f.status('phone'), 'Available');
        assert.equal(f.form.checkValidity(), true);
    } finally {f.dom.window.close();}
});

test('failed, redirected and malformed checks are shown as unavailable information', async () => {
    const f = fixture();
    try {
        for (const extra of [{ok:false}, {redirected:true}, {json:async () => ({})}]) {
            f.type('phone', '255712345678'); f.advance(350); f.reply(f.requests.length - 1, true, extra); await flush();
            assert.equal(f.status('phone'), 'Could not check');
        }
        f.form.elements.phone.dispatchEvent(new f.w.KeyboardEvent('keydown', {key:'Enter', bubbles:true, cancelable:true}));
        assert.equal(f.requests.length, 4);
        f.reply(3, true); await flush(); assert.equal(f.status('phone'), 'Available');
    } finally {f.dom.window.close();}
});

test('names require exactly three real name parts and retain Unicode and normal punctuation', () => {
    const f = fixture();
    try {
        for (const value of ['', 'Asha Mushi', 'Asha Jane Mary Mushi', 'Asha 123 Mushi', 'Asha <Jane> Mushi']) {
            f.type('fullName', value); assert.equal(f.form.elements.fullName.validity.customError, true);
        }
        for (const value of ['Asha Jane Mushi', 'Asha-Jane Mary O’Neil', 'Élodie Jane D’Angelo', '李 王 明', '  Asha   Jane Mushi ']) {
            f.type('fullName', value); assert.equal(f.form.elements.fullName.validity.customError, false);
        }
        assert.equal(f.requests.length, 0);
    } finally {f.dom.window.close();}
});

test('IME input, modal close, reset and page exit cancel pending or in-flight checks', async () => {
    const f = fixture();
    try {
        const email = f.form.elements.email;
        email.dispatchEvent(new f.w.CompositionEvent('compositionstart'));
        f.type('email', 'asha@example.invalid', {isComposing:true}); f.advance(350); assert.equal(f.requests.length, 0);
        email.dispatchEvent(new f.w.CompositionEvent('compositionend')); f.advance(350); assert.equal(f.requests.length, 1);
        f.w.document.dispatchEvent(new f.w.CustomEvent('resource:before-close', {detail:{modal:f.form.parentElement}}));
        assert.equal(f.requests[0].options.signal.aborted, true);
        f.type('phone', '255712345678'); f.form.reset(); f.advance(350); assert.equal(f.requests.length, 1);
        f.type('email', 'neema@example.invalid'); f.advance(350);
        f.w.dispatchEvent(new f.w.Event('pagehide')); assert.equal(f.requests[1].options.signal.aborted, true);
        f.reply(1, true); await flush(); assert.equal(f.status('email'), '');
    } finally {f.dom.window.close();}
});
