const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

const script = name => fs.readFileSync(`src/main/resources/static/js/${name}.js`, 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));

test('Next keeps a rejected guarantor row in view and a correction allows recording', async () => {
    const steps = ['loan', 'guarantors', 'collateral', 'attachments'];
    const dom = new JSDOM(`<form><input type="hidden" name="_csrf" value="test-token">
        <div data-recording-evidence data-root="/evidence" data-validate-url="/validate-step">
        <nav data-recording-steps>${steps.map(step => `<button type="button" data-recording-step-button aria-controls="recording-panel-${step}"></button>`).join('')}</nav>
        ${steps.map(step => `<section data-validation-step id="recording-panel-${step}"><h2 data-recording-step-heading tabindex="-1">${step}</h2>
        ${step === 'loan' ? '<select name="loanFor"><option>GROUP</option></select><select name="loanMode"><option>CONFIGURED</option></select><select name="productId"><option value=""></option></select>' : ''}
        ${step === 'guarantors' ? `<div data-repeat-list="guarantors"><div data-repeat-row><label>Client number<input data-field="clientNumber" value="C123"></label><label>Guarantee limit<input data-field="guaranteeBudget" value="100"></label></div></div>` : ''}
        <p data-recording-step-error tabindex="-1" hidden></p></section>`).join('')}
        <p data-evidence-error hidden></p><div data-evidence-fields></div><p data-evidence-requirements></p><input type="file" name="attachments">
        <button type="button" data-recording-previous>Previous</button><button type="button" data-recording-next>Next</button>
        <button type="submit" data-recording-submit>Save Loan Record</button><button type="submit" formnovalidate>Save draft</button></div></form>`,
        {runScripts:'outside-only', url:'http://localhost/loan-records/new'});
    const {window} = dom;
    const {document} = window;
    try {
        const form = document.querySelector('form');
        const budget = form.querySelector('[data-field="guaranteeBudget"]');
        const field = 'applicationDetails.guarantors[0].guaranteeBudget';
        const next = form.querySelector('[data-recording-next]');
        const calls = [];
        window.DataTransfer = class {constructor() {this.files = form.elements.attachments.files;}};
        window.WorkspaceNavigation = {onReady: initialise => initialise()};
        window.fetch = async (url, {body}) => {
            assert.equal(url, '/validate-step');
            assert.equal(body.get('_csrf'), 'test-token');
            const step = body.get('step');
            calls.push(step);
            return step === 'guarantors' && body.get(field) === '100'
                ? {status:422, json:async () => ({errors:[{field,message:'The available guarantee limit is too low.'}]})}
                : {status:204};
        };
        window.eval(script('form-validation'));
        window.eval(script('loan-recording-evidence'));
        const current = () => document.querySelector('[aria-current="step"]').getAttribute('aria-controls');
        next.click();
        await settle();
        assert.equal(current(), 'recording-panel-guarantors');
        next.click();
        await settle();
        assert.equal(current(), 'recording-panel-guarantors');
        assert.equal(document.activeElement, budget);
        assert.equal(budget.nextElementSibling.textContent, 'The available guarantee limit is too low.');
        assert.equal(budget.value, '100');

        budget.value = '10000';
        budget.dispatchEvent(new window.Event('input', {bubbles:true}));
        next.click();
        await settle();
        assert.equal(current(), 'recording-panel-collateral');
        next.click();
        await settle();
        assert.equal(current(), 'recording-panel-attachments');
        let saves = 0;
        form.addEventListener('submit', event => {if (!event.defaultPrevented) saves++; event.preventDefault();});
        form.querySelector('[data-recording-submit]').click();
        await settle();
        assert.equal(saves, 1);
        assert.deepEqual(calls, ['loan','guarantors','guarantors','collateral','attachments']);
    } finally {await settle(); dom.window.close();}
});
