const assert = require('node:assert/strict');
const fs = require('node:fs');
const { test } = require('node:test');
const { JSDOM } = require('jsdom');

test('preview search debounces, ignores stale results, and preserves the original report actions', async () => {
  const dom = new JSDOM(`<main data-report-preview>
    <a id="export" href="/report/pdf?from=2026-11-01">PDF</a>
    <input type="search" data-report-preview-search data-error="Search failed">
    <section data-report-preview-results data-ui-loading-region data-report-cutoff="frozen">Original rows</section>
  </main>`, { runScripts: 'outside-only', url: 'https://local.test/report?run=true&page=2' });
  const window = dom.window;
  const timers = new Map();
  const requests = [];
  let timer = 0;
  window.setTimeout = callback => { timers.set(++timer, callback); return timer; };
  window.clearTimeout = id => timers.delete(id);
  window.WorkspaceNavigation = { onReady: callback => callback() };
  window.AppLoading = {
    begin: region => region.setAttribute('aria-busy', 'true'),
    end: region => region.removeAttribute('aria-busy'),
    fail: region => region.setAttribute('data-error', 'true')
  };
  window.fetch = (url, request) => new Promise(resolve => requests.push({ url, request, resolve }));
  for (const name of ['live-search.js', 'report-preview-search.js']) {
    window.eval(fs.readFileSync('src/main/resources/static/js/' + name, 'utf8'));
  }
  const input = window.document.querySelector('input');
  const type = term => { input.value = term; input.dispatchEvent(new window.Event('input', { bubbles:true })); };
  const flush = () => { const callbacks = [...timers.values()]; timers.clear(); callbacks.forEach(callback => callback()); };
  const result = () => window.document.querySelector('[data-report-preview-results]');
  const respond = (request, text) => request.resolve({
    ok:true, redirected:false, text:async () => `<section data-report-preview-results>${text}</section>`
  });
  const settle = () => new Promise(resolve => setImmediate(resolve));

  type('B'); type('Ba'); type('Bank');
  assert.equal(requests.length, 0);
  assert.equal(timers.size, 1);
  flush();
  assert.equal(requests[0].url.searchParams.get('previewSearch'), 'Bank');
  assert.equal(requests[0].url.searchParams.get('cutoff'), 'frozen');
  assert.equal(requests[0].url.searchParams.has('page'), false);
  assert.equal(result().getAttribute('aria-busy'), 'true');
  assert.equal(result().textContent, 'Original rows');

  type('JV-42'); flush();
  assert.equal(requests[0].request.signal.aborted, true);
  respond(requests[0], 'Stale rows'); await settle();
  assert.equal(result().textContent, 'Original rows');
  respond(requests[1], 'Matching rows'); await settle();
  assert.equal(result().textContent, 'Matching rows');
  assert.equal(window.document.querySelector('[data-report-preview-search]'), input);
  assert.equal(window.document.querySelector('#export').getAttribute('href'), '/report/pdf?from=2026-11-01');
  dom.window.close();
});
