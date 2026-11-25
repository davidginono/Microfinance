(() => {
    function ready() {
        const form = document.querySelector('[data-waiver-selection]');
        if (!form || form.dataset.ready) return;
        form.dataset.ready = 'true';
        const rows = [...form.querySelectorAll('[data-waiver-row]')].map(row => ({
            check: row.querySelector('[data-waiver-check]'),
            amount: row.querySelector('[data-waiver-amount]')
        })).filter(row => row.check);
        const output = form.querySelector('[data-waiver-total]');
        const render = () => {
            let cents = 0n;
            for (const {check, amount} of rows) {
                amount.disabled = !check.checked;
                amount.required = check.checked;
                if (!check.checked || !/^\d+(?:\.\d{1,2})?$/.test(amount.value)) continue;
                const [whole, fraction = ''] = amount.value.split('.');
                cents += BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
            }
            output.value = (cents / 100n) + '.' + String(cents % 100n).padStart(2, '0');
        };
        form.addEventListener('input', render);
        render();
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else document.addEventListener('DOMContentLoaded', ready, {once:true});
})();
