(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => document.addEventListener('DOMContentLoaded', callback, {once: true}));
    ready(() => {
        const fields = document.querySelector('[data-office-return-fields]');
        if (fields) document.querySelectorAll('#main-content form').forEach(form => {
            if (form.method.toLowerCase() !== 'post' || form.dataset.officeReturnReady) return;
            const action = new URL(form.action, location.href);
            if (action.origin !== location.origin) return;
            form.dataset.officeReturnReady = 'true';
            form.append(fields.content.cloneNode(true));
        });
        document.querySelectorAll('[data-resource-table="staff-office-queue"] form').forEach(form => {
            if (form.dataset.officeFilterReady) return;
            form.dataset.officeFilterReady = 'true';
            const source = form.elements.namedItem('source'), sort = form.elements.namedItem('sort');
            if (!source || !sort) return;
            const sync = () => {
                const chronological = source.options[source.selectedIndex]?.dataset.chronological === 'true';
                for (const option of sort.options) option.disabled = !['number','number_desc'].includes(option.value) && !chronological;
                if (sort.options[sort.selectedIndex]?.disabled) sort.value = 'number';
            };
            source.addEventListener('change', () => {sort.value = 'number'; sync();});
            sync();
        });
    });
})();
