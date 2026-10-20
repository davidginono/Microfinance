const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

const source = fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js', 'utf8');
function fixture(forms = '<form method="post" class="accounting-create-form"><input name="name" value="Saved"><button type="submit">Save</button></form>') {
  const dom = new JSDOM(`${forms}<a href="#leave">Leave</a>
    <div id="accounting-discard-modal" hidden><div role="dialog"><button data-accounting-keep>Close</button>
    <div class="app-modal-actions"><button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button></div></div></div>`,
    {runScripts:'outside-only', url:'http://localhost/admin/settings-controls/customised-loan'});
  const w = dom.window, d = w.document;
  w.eval(source);
  const navigate = () => d.dispatchEvent(new w.CustomEvent('workspace:before-navigate', {cancelable:true, detail:{resume(){}}}));
  const submit = form => {
    const event = new w.SubmitEvent('submit', {bubbles:true, cancelable:true, submitter:form.querySelector('button[type=submit]')});
    form.dispatchEvent(event); return event;
  };
  return {dom,w,d,navigate,submit};
}

test('leaving a submitted configuration restored from browser history does not discard already submitted values', () => {
  const {dom,w,d,navigate,submit} = fixture();
  try {
    const form = d.querySelector('form'); form.elements.name.value = 'Updated configuration';
    assert.equal(submit(form).defaultPrevented, false);
    w.dispatchEvent(new w.PageTransitionEvent('pagehide', {persisted:true}));
    w.dispatchEvent(new w.PageTransitionEvent('pageshow', {persisted:true}));
    assert.equal(navigate(), true);
    assert.equal(d.querySelector('#accounting-discard-modal').hidden, true);
    form.elements.name.value = 'Another unsaved change';
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('a prevented save keeps changes protected when the page is restored', () => {
  const {dom,w,d,navigate,submit} = fixture();
  try {
    const form = d.querySelector('form'); form.elements.name.value = 'Invalid configuration';
    form.addEventListener('submit', event => event.preventDefault());
    assert.equal(submit(form).defaultPrevented, true);
    w.dispatchEvent(new w.PageTransitionEvent('pageshow', {persisted:true}));
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('late form initialization is clean, but edits made after initialization remain protected', async () => {
  const {dom,w,d,navigate} = fixture();
  try {
    d.querySelector('input').value = 'Saved and formatted';
    d.dispatchEvent(new w.Event('DOMContentLoaded'));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(navigate(), true);
    d.querySelector('input').value = 'Unsaved';
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('initialization never clears an edit made while the page is still loading', async () => {
  const {dom,w,d,navigate} = fixture();
  try {
    const input = d.querySelector('input'); input.value = 'Unsaved during loading';
    input.dispatchEvent(new w.Event('input', {bubbles:true}));
    d.dispatchEvent(new w.Event('DOMContentLoaded'));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('initial pageshow normalization is included in the clean configuration baseline', async () => {
  const {dom,w,d,navigate} = fixture();
  try {
    w.addEventListener('pageshow', () => d.querySelector('input').value = 'Initialized saved configuration', {once:true});
    w.dispatchEvent(new w.PageTransitionEvent('pageshow'));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(navigate(), true);
  } finally {dom.window.close();}
});

test('Back preserves edits made after the submitted snapshot and keeps restricted buttons disabled', () => {
  const {dom,w,d,navigate,submit} = fixture('<form class="accounting-create-form" method="post"><input name="name" value="Saved"><button type="submit">Save</button><button type="submit" disabled>Restricted</button></form>');
  try {
    const form = d.querySelector('form'); form.elements.name.value = 'Submitted';
    submit(form); form.elements.name.value = 'Typed while saving';
    w.dispatchEvent(new w.PageTransitionEvent('pagehide', {persisted:true}));
    w.dispatchEvent(new w.PageTransitionEvent('pageshow', {persisted:true}));
    assert.equal(navigate(), false);
    assert.equal(form.querySelectorAll('button')[0].disabled, false);
    assert.equal(form.querySelectorAll('button')[1].disabled, true);
  } finally {dom.window.close();}
});

test('saving one form asks before discarding another form and Continue editing preserves both', () => {
  const {dom,d,navigate,submit} = fixture(`<form class="accounting-create-form"><input name="first" value="Saved"></form>
    <form class="accounting-create-form"><input name="second" value="Saved"><button type="submit">Save</button></form>`);
  try {
    d.querySelector('[name=first]').value = 'Unsaved first form';
    d.querySelector('[name=second]').value = 'Unsaved second form';
    assert.equal(submit(d.querySelectorAll('form')[1]).defaultPrevented, true);
    d.querySelector('.app-modal-actions [data-accounting-keep]').click();
    assert.equal(d.querySelector('[name=first]').value, 'Unsaved first form');
    assert.equal(d.querySelector('[name=second]').value, 'Unsaved second form');
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('a second guarded form can save its own edits without a discard prompt', () => {
  const {dom,d,navigate,submit} = fixture(`<form class="accounting-create-form" method="get"><input name="filter" value="Selected loan"></form>
    <form class="accounting-create-form" method="post"><input name="name" value="Saved"><button type="submit">Save</button></form>`);
  try {
    const form = d.querySelectorAll('form')[1]; form.elements.name.value = 'Updated';
    assert.equal(submit(form).defaultPrevented, false);
    assert.equal(d.querySelector('#accounting-discard-modal').hidden, true);
    assert.equal(navigate(), true);
  } finally {dom.window.close();}
});

test('leaving a second guarded form with unsaved changes still asks for confirmation', () => {
  const {dom,d,navigate} = fixture(`<form class="accounting-create-form"><input name="first" value="Saved"></form>
    <form class="accounting-create-form"><input name="second" value="Saved"></form>`);
  try {
    d.querySelector('[name=second]').value = 'Unsaved';
    assert.equal(navigate(), false);
  } finally {dom.window.close();}
});

test('a rejected loan application still requires confirmation before discarding restored values', () => {
  const dom = new JSDOM(`<form class="accounting-create-form">
    <div class="loan-error-summary" tabindex="-1">Select a client.</div>
    <input name="principal" value="1000"><a href="/finance/loan-records">Cancel</a>
  </form><div id="accounting-discard-modal" hidden><div role="dialog">
    <button data-accounting-keep>Close</button><div class="app-modal-actions">
      <button data-accounting-keep>Continue editing</button><button data-accounting-discard>Discard</button>
    </div></div></div>`, {runScripts: 'outside-only', url: 'http://localhost/finance/loan-records'});
  const w = dom.window, d = w.document;
  w.eval(fs.readFileSync('src/main/resources/static/js/accounting-form-ux.js', 'utf8'));
  assert.equal(d.activeElement, d.querySelector('.loan-error-summary'));
  d.querySelector('a').focus();
  assert.equal(d.querySelector('a').dispatchEvent(new w.MouseEvent('click', {bubbles: true, cancelable: true})), false);
  assert.equal(d.getElementById('accounting-discard-modal').hidden, false);
  assert.equal(d.documentElement.style.overflow, 'hidden');
  assert.equal(d.body.style.overflow, 'hidden');
  d.querySelector('.app-modal-actions [data-accounting-keep]').click();
  assert.equal(d.getElementById('accounting-discard-modal').hidden, true);
  assert.equal(d.querySelector('input').value, '1000');
  assert.equal(d.activeElement, d.querySelector('a'));
  assert.equal(d.documentElement.style.overflow, '');
  assert.equal(d.body.style.overflow, '');
  dom.window.close();
});
