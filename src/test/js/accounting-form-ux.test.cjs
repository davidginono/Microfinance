const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

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
