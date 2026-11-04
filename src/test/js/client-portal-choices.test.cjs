const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {JSDOM} = require('jsdom');

const tick = () => new Promise(resolve => setImmediate(resolve));
const response = rows => ({ok: true, json: async () => rows});

async function setup(fetchChoices) {
    const dom = new JSDOM(`
        <meta name="app-context-path" content="">
        <body data-select-search="Search" data-select-no-matches="No matches" data-shell-no-options="No options">
            <select data-portal-institution><option value="">Choose</option></select>
            <select data-portal-branch><option value="">Choose</option></select>
        </body>`, {url: 'http://localhost/client-portal/register', runScripts: 'outside-only', pretendToBeVisual: true});
    const w = dom.window, d = w.document;
    w.HTMLElement.prototype.scrollIntoView = function () {};
    w.fetch = (url, {signal}) => fetchChoices(new URL(url, w.location).searchParams, signal);
    for (const script of ['select-enhancer', 'live-search', 'client-portal']) {
        w.eval(readFileSync(`src/main/resources/static/js/${script}.js`, 'utf8'));
    }
    await tick();
    const institution = d.querySelector('[data-portal-institution]');
    const branch = d.querySelector('[data-portal-branch]');
    const selectInstitution = value => {
        institution.value = value;
        institution.dispatchEvent(new w.Event('change', {bubbles: true}));
    };
    return {dom, w, d, branch, selectInstitution};
}

test('reopening a branch menu after an empty search restores the institution branches', async () => {
    const {dom, w, d, branch, selectInstitution} = await setup(async query => {
        if (!query.has('institution')) return response([{id: 'A', label: 'Institution A'}]);
        assert.equal(query.get('institution'), 'A');
        return response(query.get('search') ? [] : [{id: 'B1', label: 'Main branch'}]);
    });
    try {
        selectInstitution('A');
        await tick();
        branch.nextElementSibling.click();
        const menu = d.querySelector('.neo-select-menu.is-open');
        const search = menu.querySelector('input');
        search.value = 'missing branch';
        search.dispatchEvent(new w.KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));
        await tick();
        assert.equal(menu.querySelector('.neo-select-empty').textContent, 'No matches');
        search.dispatchEvent(new w.KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
        branch.nextElementSibling.click();
        await tick();
        assert.equal(search.value, '');
        menu.querySelector('.neo-select-option').click();
        assert.equal(branch.value, 'B1');
    } finally {
        dom.window.close();
    }
});

test('changing institutions cancels an in-flight branch lookup and ignores its late response', async () => {
    let firstRequest;
    const {dom, branch, selectInstitution} = await setup(async (query, signal) => {
        const institution = query.get('institution');
        if (!institution) return response([{id: 'A', label: 'Institution A'}, {id: 'B', label: 'Institution B'}]);
        if (institution === 'A') return new Promise(resolve => { firstRequest = {resolve, signal}; });
        return response([{id: 'B1', label: 'Branch B'}]);
    });
    try {
        selectInstitution('A');
        selectInstitution('B');
        assert.equal(firstRequest.signal.aborted, true);
        await tick();
        firstRequest.resolve(response([{id: 'A1', label: 'Branch A'}]));
        await tick();
        assert.deepEqual([...branch.options].map(option => option.value), ['', 'B1']);
        assert.equal(branch.value, '');
    } finally {
        dom.window.close();
    }
});
