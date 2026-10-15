/* Keep policy detail selection in the shared, read-only split pane. */
(() => {
    'use strict';
    document.querySelectorAll('.policy-record-table').forEach(region => {
        const table = region.querySelector('table');
        table.addEventListener('change', event => {
            if (!event.target.matches('tbody input[type="checkbox"]')) return;
            table.querySelectorAll('tbody input[type="checkbox"]').forEach(input => {
                if (input !== event.target) input.checked = false;
                input.closest('tr').classList.toggle('resource-table-selected', input.checked);
            });
        });
        table.addEventListener('click', event => {
            if (event.target.closest('input,button,a,select,textarea,label')) return;
            event.target.closest('tbody tr')?.querySelector('input[type="checkbox"]')?.click();
        });
    });
})();
