const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { JSDOM } = require('jsdom');

const script = readFileSync('src/main/resources/static/js/interest-waivers.js', 'utf8');

test('individual and full waivers preview revised instalments before submitting', () => {
    const row = (accrued, remaining, interest) => `
        <tr data-waiver-row data-accrued="${accrued}" data-installment-remaining="${remaining}">
            <td><input type="checkbox" data-waiver-check></td>
            <td><input type="number" min="0.01" max="${interest}" step="0.01" value="${interest}" data-waiver-amount></td>
            <td data-waiver-revised></td>
        </tr>`;
    const dom = new JSDOM(`<html lang="en"><form data-waiver-selection data-waiver-selection-required="Select one">
        <button type="button" data-waiver-all hidden>Waive all remaining interest</button>
        <table>${row('577.55', '112500.00', '12500.00')}${row('0.00', '112500.00', '12500.00')}</table>
        <output data-waiver-total></output>
        <output data-waiver-accrued></output>
        <output data-waiver-future></output>
        <input type="checkbox" name="confirmed" required>
    </form></html>`, { runScripts: 'outside-only' });
    try {
        const { window } = dom;
        const { document } = window;
        window.WorkspaceNavigation = { onReady: ready => ready() };
        window.eval(script);
        const [first, second] = document.querySelectorAll('[data-waiver-check]');
        const [firstAmount] = document.querySelectorAll('[data-waiver-amount]');
        const [firstRevised, secondRevised] = document.querySelectorAll('[data-waiver-revised]');
        const value = name => document.querySelector(`[data-waiver-${name}]`).value;

        first.click();
        firstAmount.value = '600.00';
        firstAmount.dispatchEvent(new window.Event('input', { bubbles: true }));
        assert.equal(firstRevised.textContent, '111,900.00');
        assert.equal(secondRevised.textContent, '112,500.00');
        assert.equal(value('accrued'), '577.55');
        assert.equal(value('future'), '22.45');

        const confirmation = document.querySelector('[name=confirmed]');
        confirmation.checked = true;
        document.querySelector('[data-waiver-all]').click();
        assert.equal(firstAmount.value, '12500.00');
        assert.equal(second.checked, true);
        assert.equal(firstRevised.textContent, '100,000.00');
        assert.equal(secondRevised.textContent, '100,000.00');
        assert.equal(value('total'), '25,000.00');
        assert.equal(value('future'), '24,422.45');
        assert.equal(confirmation.checked, false);

        firstAmount.value = '12500.01';
        firstAmount.dispatchEvent(new window.Event('input', { bubbles: true }));
        assert.equal(value('total'), '—');
        assert.equal(firstAmount.validity.rangeOverflow, true);
    } finally {
        dom.window.close();
    }
});
