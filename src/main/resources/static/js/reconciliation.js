document.addEventListener('DOMContentLoaded', () => {
    const button = document.querySelector('[data-add-allocation]');
    const container = document.querySelector('[data-allocations]');
    if (!button || !container) return;
    button.addEventListener('click', () => {
        const rows = container.querySelectorAll('[data-allocation-row]');
        if (rows.length >= 100) return;
        const row = rows[0].cloneNode(true);
        row.querySelectorAll('input,select').forEach(control => { control.value = ''; });
        container.appendChild(row);
        row.querySelector('select').focus();
        if (rows.length + 1 >= 100) button.disabled = true;
    });
});
