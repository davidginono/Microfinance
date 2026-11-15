(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => document.addEventListener('DOMContentLoaded', callback, {once: true}));
    ready(() => {
        const fields = document.querySelector('[data-office-return-fields]');
        const returnPath = document.querySelector('[data-office-return]');
        const main = document.getElementById('main-content');
        const breadcrumb = main?.querySelector('.erp-page-path:not([data-office-return])');
        const returnLink = returnPath?.querySelector('a');
        if (breadcrumb && returnLink) {
            const separator = document.createElement('span');
            separator.className = 'erp-page-path__separator';
            separator.setAttribute('aria-hidden', 'true');
            separator.append(window.ConsoleResourceDetails?.icon('angle-right') || document.createTextNode('›'));
            breadcrumb.prepend(returnLink, separator);
            if (fields) returnPath.before(fields);
            returnPath.remove();
        }
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
