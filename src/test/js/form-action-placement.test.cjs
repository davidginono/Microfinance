const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

test('legacy table enhancement keeps orange form actions with their submitting form', () => {
  const dom = new JSDOM(`<body class="aws-console"><main>
    <a id="create" class="app-btn btn-launch" href="/new">Create</a>
    <form id="create-form"><input name="description"><div id="footer">
      <button id="post" class="app-btn btn-launch">Post journal</button>
    </div></form>
    <button id="external" class="app-btn btn-launch" form="create-form">Save</button>
    <div class="erp-table-wrap"><table class="erp-table"><thead><tr><th>Name</th></tr></thead><tbody><tr><td>Example</td></tr></tbody></table></div>
  </main></body>`, {runScripts: 'outside-only', url: 'http://localhost/admin/example', pretendToBeVisual: true});
  try {
    const w = dom.window, d = w.document;
    w.matchMedia = () => ({matches: false, addEventListener() {}});
    w.eval(fs.readFileSync('src/main/resources/static/js/shell.js', 'utf8'));
    w.dispatchEvent(new w.Event('load'));
    assert.equal(d.getElementById('post').parentElement.id, 'footer');
    assert.equal(d.getElementById('post').form.id, 'create-form');
    assert.equal(d.getElementById('external').parentElement.tagName, 'MAIN');
    assert.equal(d.getElementById('external').form.id, 'create-form');
    assert.ok(d.getElementById('create').closest('.app-table-toolbar'));
  } finally { dom.window.close(); }
});
