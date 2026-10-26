const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const source = fs.readFileSync('src/main/resources/static/js/form-validation.js', 'utf8');
const dateSource = fs.readFileSync('src/main/resources/static/js/date-controls.js', 'utf8');
const selectSource = fs.readFileSync('src/main/resources/static/js/select-enhancer.js', 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));
function fixture(fields, options = {}) {
    const dom = new JSDOM(`<body class="aws-console" data-select-search="Search" data-date-invalid="Enter a valid date as DD/MM/YYYY.">
        <span id="form-validation-labels" hidden data-field="This field" data-required="{0} is required."
        data-invalid="Enter a valid value for {0}." data-minimum="{0} must be at least {1}."
        data-maximum="{0} must be no more than {2}." data-short="{0} must have at least {1} characters."
        data-long="{0} must have no more than {2} characters."></span>
        <form method="post">${fields}<button type="submit">Save</button><button type="submit" formnovalidate>Draft</button></form></body>`,
        {runScripts:'outside-only', url:'http://localhost/validation'});
    const w = dom.window, d = w.document;
    w.HTMLElement.prototype.scrollIntoView = function () {};
    w.requestAnimationFrame = cb => w.setTimeout(cb, 0);
    w.WorkspaceNavigation = {onReady:cb => cb()};
    if (options.dates) w.eval(dateSource);
    if (options.selects) w.eval(selectSource);
    w.eval(source);
    const fire = (node, type) => node.dispatchEvent(new w.Event(type, {bubbles:true}));
    return {dom,w,d,form:d.querySelector('form'), fire};
}
test('pristine fields remain quiet; blur shows a contextual error before the hint and correction preserves hint associations', async () => {
    const {dom,d,fire} = fixture('<label>Name<input name="name" required aria-describedby="hint"><small id="hint">Use a unique name.</small></label>');
    try {
        const input = d.querySelector('input');
        assert.equal(d.querySelector('.erp-field-error'), null);
        fire(input, 'focusout');
        const error = d.querySelector('.erp-field-error');
        assert.equal(error.textContent, 'Name is required.');
        assert.equal(input.nextElementSibling, error);
        assert.equal(error.nextElementSibling.id, 'hint');
        assert.equal(input.getAttribute('aria-invalid'), 'true');
        assert.equal(input.getAttribute('aria-describedby'), `hint ${error.id}`);
        input.value = 'My business'; fire(input, 'input');
        assert.equal(d.querySelector('.erp-field-error'), null);
        assert.equal(input.getAttribute('aria-invalid'), null);
        assert.equal(input.getAttribute('aria-describedby'), 'hint');
    } finally {await tick(); dom.window.close();}
});
test('native submission remains blocked, opens nested error sections and focuses the first invalid field', async () => {
    const {dom,d,form} = fixture('<details><summary>Settings</summary><details><summary>Names</summary><label>Name<input name="name" required></label></details></details><label>Email<input name="email" type="email" required></label>');
    try {
        let submits = 0; form.addEventListener('submit', event => {event.preventDefault(); submits++;});
        form.requestSubmit(form.querySelector('button'));
        await tick();
        assert.equal(submits, 0);
        assert.equal(d.querySelectorAll('.erp-field-error').length, 2);
        assert.equal(d.activeElement, form.elements.name);
        assert.ok([...d.querySelectorAll('details')].every(el => el.open));
        assert.equal(form.querySelector('button').disabled, false);
        form.elements.name.value = 'Valid'; form.elements.email.value = 'user@example.org';
        form.requestSubmit(form.querySelector('button')); assert.equal(submits, 1);
    } finally {await tick(); dom.window.close();}
});
test('Save draft bypasses native completeness validation and preserves the submitter', async () => {
    const {dom,form} = fixture('<input required name="name">');
    try {
        // jsdom does not implement the formnovalidate submitter override. Verify the
        // real browser path separately; a novalidate form exercises this script's boundary here.
        form.noValidate = true;
        const draft = form.querySelector('[formnovalidate]'); let submitter;
        form.addEventListener('submit', event => {event.preventDefault(); submitter = event.submitter;});
        form.requestSubmit(draft); assert.equal(submitter, draft);
        assert.equal(form.querySelector('.erp-field-error'), null);
    } finally {await tick(); dom.window.close();}
});
test('moving focus to Submit leaves layout stable until native submission validates the form', async () => {
    const {dom,w,d,form} = fixture('<label>Name<input name="name" required></label>');
    try {
        form.elements.name.dispatchEvent(new w.FocusEvent('focusout',{bubbles:true,relatedTarget:form.querySelector('button')}));
        assert.equal(d.querySelector('.erp-field-error'),null);
        form.checkValidity();await tick();
        assert.equal(d.querySelector('.erp-field-error').textContent,'Name is required.');
    } finally {await tick();dom.window.close();}
});
test('server errors retain their text and hint association until the value changes', async () => {
    const {dom,d,form,fire} = fixture('<label>Code<input name="code" id="code" value="TAKEN" aria-describedby="hint"><small id="hint">Account code.</small><span id="code.errors" class="coa-error">Code is already in use.</span></label>');
    try {
        await tick(); const input = form.elements.code, error = d.getElementById('code.errors');
        assert.equal(d.activeElement, input);
        assert.equal(input.nextElementSibling, error);
        fire(input, 'focusout'); assert.equal(error.hidden, false);
        fire(input, 'input'); assert.equal(error.hidden, false);
        input.value = 'NEW'; fire(input, 'input');
        assert.equal(error.hidden, true);
        assert.equal(error.textContent, 'Code is already in use.');
        assert.equal(input.getAttribute('aria-describedby'), 'hint');
    } finally {await tick(); dom.window.close();}
});
test('required radios share one message and clear together after a choice', async () => {
    const {dom,d,form,fire} = fixture('<fieldset><legend>Mode</legend><label>First<input type="radio" name="mode" value="a" required></label><label>Second<input type="radio" name="mode" value="b" required></label></fieldset>');
    try {
        form.checkValidity(); assert.equal(d.querySelectorAll('.erp-field-error').length, 1);
        const radios = d.querySelectorAll('input');
        assert.ok([...radios].every(el => el.getAttribute('aria-invalid') === 'true'));
        radios[1].checked = true; fire(radios[1], 'change');
        assert.equal(d.querySelector('.erp-field-error'), null);
        assert.ok([...radios].every(el => !el.hasAttribute('aria-invalid')));
    } finally {await tick(); dom.window.close();}
});
test('amount bounds and custom validity messages use the existing constraints', async () => {
    const {dom,d,fire} = fixture('<label>Amount<input name="amount" type="number" min="0.01" max="100" step="0.01" value="0"></label>');
    try {
        const input = d.querySelector('input'); fire(input, 'focusout');
        assert.equal(d.querySelector('.erp-field-error').textContent, 'Amount must be at least 0.01.');
        input.value = '101'; fire(input, 'input');
        assert.equal(d.querySelector('.erp-field-error').textContent, 'Amount must be no more than 100.');
        input.value = '10'; input.setCustomValidity('Choose an open accounting period.'); fire(input, 'input');
        assert.equal(d.querySelector('.erp-field-error').textContent, 'Choose an open accounting period.');
        input.setCustomValidity(''); fire(input, 'input'); assert.equal(d.querySelector('.erp-field-error'), null);
    } finally {await tick(); dom.window.close();}
});
test('enhanced selects show inline errors and focus the visible trigger, then clear on selection', async () => {
    const {dom,d,form,fire} = fixture('<label for="branch">Branch</label><select id="branch" name="branch" required aria-describedby="hint"><option value="">Choose</option><option value="1">Main</option></select><small id="hint">Operating branch.</small>', {selects:true});
    try {
        const select = form.elements.branch, trigger = d.querySelector('.neo-select-button');
        form.checkValidity(); await tick();
        assert.equal(d.activeElement, trigger);
        assert.equal(trigger.getAttribute('aria-invalid'), 'true');
        assert.ok(trigger.getAttribute('aria-describedby').includes(d.querySelector('.erp-field-error').id));
        select.value = '1'; fire(select, 'change');
        assert.equal(d.querySelector('.erp-field-error'), null);
        assert.equal(trigger.getAttribute('aria-invalid'), null);
        assert.equal(select.getAttribute('aria-describedby'), 'hint');
    } finally {await tick(); dom.window.close();}
});
test('date adapter keeps ISO submission, shares visible errors and recovers from invalid typed dates', async () => {
    const {dom,d,form,fire} = fixture('<label for="date">Date</label><input id="date" name="date" type="date" required>', {dates:true});
    try {
        const input = d.querySelector('[data-date-display]'), native = form.elements.date;
        input.value = '31/02/2026'; fire(input, 'input'); fire(input, 'focusout');
        assert.equal(d.querySelector('.erp-field-error').textContent, 'Enter a valid date as DD/MM/YYYY.');
        assert.equal(input.getAttribute('aria-invalid'), 'true');
        form.checkValidity(); await tick(); assert.equal(d.activeElement, input);
        input.value = '26/10/2026'; fire(input, 'input');
        assert.equal(native.value, '2026-10-26'); assert.equal(d.querySelector('.erp-field-error'), null);
        assert.equal(input.getAttribute('aria-invalid'), null);
    } finally {await tick(); dom.window.close();}
});
test('dynamic indexed server errors bind to their own field and reset removes only managed associations', async () => {
    const {dom,d,form} = fixture('<input name="existing" aria-describedby="hint"><small id="hint">Hint</small>');
    try {
        form.insertAdjacentHTML('afterbegin', '<label>Amount<input name="rows[1].amount" id="rows1.amount" value="10" aria-describedby="row-hint"><small id="row-hint">TZS</small><span id="rows[1].amount.errors" class="coa-error">Amount exceeds the balance.</span></label>');
        await tick(); const input = form.elements.namedItem('rows[1].amount');
        assert.equal(input.getAttribute('aria-invalid'), 'true');
        assert.ok(input.getAttribute('aria-describedby').includes('rows[1].amount.errors'));
        form.reset(); await tick();
        assert.equal(input.getAttribute('aria-invalid'), null);
        assert.equal(input.getAttribute('aria-describedby'), 'row-hint');
        assert.equal(d.getElementById('rows[1].amount.errors').hidden, true);
    } finally {await tick(); dom.window.close();}
});
test('localized text follows the page language and ignores disabled and readonly fields', async () => {
    const {dom,d,fire} = fixture('<label>Jina<input name="name" required></label><input required disabled><input required readonly>');
    try {
        d.getElementById('form-validation-labels').dataset.required = '{0} inahitajika.';
        const [input,disabled,readonly] = d.querySelectorAll('input');
        fire(input, 'focusout'); fire(disabled, 'focusout'); fire(readonly, 'focusout');
        assert.equal(d.querySelectorAll('.erp-field-error').length, 1);
        assert.equal(d.querySelector('.erp-field-error').textContent, 'Jina inahitajika.');
    } finally {await tick(); dom.window.close();}
});
test('server consent and radio errors clear when checked state or the chosen option changes', async () => {
    const {dom,d,fire} = fixture('<label>Consent<input id="consent" name="consent" type="checkbox" required><span id="consent.errors" class="coa-error">Consent is required.</span></label><fieldset><legend>Mode</legend><label>First<input id="mode" name="mode" type="radio" value="a" checked></label><label>Second<input name="mode" type="radio" value="b"></label><span id="mode.errors" class="coa-error">Choose another mode.</span></fieldset>');
    try {
        const checkbox=d.getElementById('consent'), radio=d.querySelector('input[value=b]');
        checkbox.checked=true;fire(checkbox,'change');radio.checked=true;fire(radio,'change');
        assert.equal(d.querySelectorAll('.erp-field-error:not([hidden])').length,0);
        assert.ok([...d.querySelectorAll('input')].every(input=>!input.hasAttribute('aria-invalid')));
    } finally {await tick();dom.window.close();}
});
test('a dynamic server error replaces a stale browser error and its association', async () => {
    const {dom,d,form,fire} = fixture('<label>Name<input id="name" name="name" required></label>');
    try {
        const input=form.elements.name;fire(input,'focusout');
        const oldId=d.querySelector('.erp-field-error').id;input.value='Taken';
        input.insertAdjacentHTML('afterend','<span class="coa-error" id="name.errors">Name is already in use.</span>');
        await tick();
        assert.equal(d.querySelectorAll('.erp-field-error:not([hidden])').length,1);
        assert.equal(d.querySelector('.erp-field-error').textContent,'Name is already in use.');
        assert.equal(input.getAttribute('aria-describedby'),'name.errors');assert.equal(d.getElementById(oldId),null);
    } finally {await tick();dom.window.close();}
});
