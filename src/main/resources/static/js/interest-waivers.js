(() => {
    function ready() {
        const form = document.querySelector('[data-waiver-selection]');
        const output = form?.querySelector('[data-waiver-total]');
        if (!output || form.dataset.ready) return;
        form.dataset.ready = 'true';
        const rows = [...form.querySelectorAll('[data-waiver-row]')].map(row => ({
            check: row.querySelector('[data-waiver-check]'),
            amount: row.querySelector('[data-waiver-amount]')
        })).filter(row => row.check);
        const render = () => {
            let cents = 0n;
            let selected = false;
            for (const {check, amount} of rows) {
                amount.disabled = !check.checked;
                amount.required = check.checked;
                if (!check.checked) continue;
                selected = true;
                if (!amount.validity.valid || !/^(?:\d+|\d*\.\d{1,2})$/.test(amount.value)) continue;
                const [whole, fraction = ''] = amount.value.split('.');
                cents += BigInt(whole || '0') * 100n + BigInt(fraction.padEnd(2, '0'));
            }
            output.value = (cents / 100n) + '.' + String(cents % 100n).padStart(2, '0');
            rows[0]?.check.setCustomValidity(selected ? '' : form.dataset.waiverSelectionRequired);
        };
        form.addEventListener('input', render);
        render();
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else document.addEventListener('DOMContentLoaded', ready, {once:true});
})();
