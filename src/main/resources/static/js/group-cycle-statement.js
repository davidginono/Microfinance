/* Cycle balances and all statement sections belong to one read snapshot. */
(() => {
    'use strict';
    const navigate = url => {
        if (window.WorkspaceNavigation?.navigate) window.WorkspaceNavigation.navigate(url,{force:true});
        else window.location.assign(url);
    };
    document.addEventListener('click', event => {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        if (!event.target.closest('[data-group-cycle-statement]')) return;
        const link = event.target.closest('.resource-table-pagination a[href], [data-table-pagination] a[href]');
        const refresh = event.target.closest('[data-resource-refresh]');
        if (!link && !refresh) return;
        const url = new URL(link ? link.href : location.href, location.href);
        if (url.origin !== location.origin || url.pathname !== location.pathname) return;
        event.preventDefault();event.stopImmediatePropagation();navigate(url.href);
    }, true);
    document.addEventListener('resource-table:refresh', event => {
        if (event.defaultPrevented || !event.target.closest('[data-group-cycle-statement]')) return;
        event.preventDefault();event.stopImmediatePropagation();navigate(location.href);
    }, true);
})();
