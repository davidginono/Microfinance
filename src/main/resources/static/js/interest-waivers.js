(() => {
    function ready() {
        const form = document.querySelector('[data-waiver-selection]');
        const output = form?.querySelector('[data-waiver-total]');
        if (!output || form.dataset.ready) return;
        form.dataset.ready = 'true';
        const language = document.documentElement.lang || undefined;
        const wholeNumber = new Intl.NumberFormat(language);
        const decimal = new Intl.NumberFormat(language).formatToParts(0.1).find(part => part.type === 'decimal').value;
        const money = cents => wholeNumber.format(cents / 100n) + decimal + String(cents % 100n).padStart(2, '0');
        const toCents = value => {
            const [whole, fraction = ''] = value.split('.');
            return BigInt(whole || '0') * 100n + BigInt(fraction.padEnd(2, '0'));
        };
        const show = (cell, value) => {
            if (cell.textContent !== value) cell.textContent = value;
        };
        const rows = [...form.querySelectorAll('[data-waiver-row]:has([data-waiver-check])')].map(row => ({
            check: row.querySelector('[data-waiver-check]'),
            amount: row.querySelector('[data-waiver-amount]'),
            revised: row.querySelector('[data-waiver-revised]'),
            accrued: toCents(row.dataset.accrued),
            remaining: toCents(row.dataset.installmentRemaining)
        }));
        const accruedOutput = form.querySelector('[data-waiver-accrued]');
        const futureOutput = form.querySelector('[data-waiver-future]');
        const waiveAll = form.querySelector('[data-waiver-all]');
        const confirmation = form.elements.confirmed;
        const render = () => {
            let total = 0n;
            let accruedTotal = 0n;
            let selected = false;
            let valid = true;
            for (const {check, amount, revised, accrued, remaining} of rows) {
                amount.disabled = !check.checked;
                amount.required = check.checked;
                if (!check.checked) {
                    show(revised, money(remaining));
                    continue;
                }
                selected = true;
                if (!amount.validity.valid || !/^(?:\d+|\d*\.\d{1,2})$/.test(amount.value)) {
                    show(revised, '—');
                    valid = false;
                    continue;
                }
                const waived = toCents(amount.value);
                total += waived;
                accruedTotal += waived < accrued ? waived : accrued;
                show(revised, money(remaining - waived));
            }
            output.value = valid ? money(total) : '—';
            accruedOutput.value = valid ? money(accruedTotal) : '—';
            futureOutput.value = valid ? money(total - accruedTotal) : '—';
            rows[0]?.check.setCustomValidity(selected ? '' : form.dataset.waiverSelectionRequired);
        };
        waiveAll.hidden = false;
        waiveAll.addEventListener('click', () => {
            for (const {check, amount} of rows) {
                check.checked = true;
                amount.value = amount.max;
            }
            confirmation.checked = false;
            render();
        });
        form.addEventListener('input', event => {
            if (!event.target.matches('[data-waiver-check], [data-waiver-amount]')) return;
            confirmation.checked = false;
            render();
        });
        render();
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else document.addEventListener('DOMContentLoaded', ready, {once:true});
})();
