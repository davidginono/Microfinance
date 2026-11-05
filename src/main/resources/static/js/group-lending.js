(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once:true}));
    ready(() => document.querySelectorAll('[data-group-register]:not([data-group-ready])').forEach(region => {
        region.dataset.groupReady = 'true';
        let selected;
        const commands = [...region.querySelectorAll('[data-group-command]')];
        const status = region.querySelector('[data-group-selection-status]');
        const clear = () => {
            selected = null;
            region.querySelectorAll('[data-group-row]').forEach(row => {
                row.classList.remove('is-selected');
                row.querySelector('input[type="checkbox"]').checked = false;
            });
            commands.forEach(button => button.disabled = true);
            if (status) status.textContent = '';
        };
        region.addEventListener('click', event => {
            const row = event.target.closest('[data-group-row]');
            if (!row || event.target.closest('a,button,select,textarea,input:not([type="checkbox"])')) return;
            if (region.hasAttribute('data-group-open-on-row') && !event.target.closest('input,label') && event.target.closest('td')?.cellIndex !== 0) {
                if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || window.getSelection()?.toString()) return;
                event.preventDefault();
                window.WorkspaceNavigation.navigate(row.dataset.target);
                return;
            }
            const next = selected === row.dataset.groupRow ? null : row;
            clear();
            if (!next) return;
            selected = next.dataset.groupRow;
            next.classList.add('is-selected');
            next.querySelector('input[type="checkbox"]').checked = true;
            commands.forEach(button => button.disabled = button.dataset.groupCommand === 'end' && next.dataset.active !== 'true');
            if (status) status.textContent = next.querySelector('[data-group-identity]').textContent;
        });
        commands.forEach(button => button.addEventListener('click', () => {
            if (button.disabled || !selected) return;
            const row = [...region.querySelectorAll('[data-group-row]')].find(row => row.dataset.groupRow === selected);
            if (row) window.WorkspaceNavigation.navigate(row.dataset.target);
        }));
        ['resource-table:reset','resource-table:updated'].forEach(type => region.addEventListener(type, clear));
        region.querySelectorAll('form[method="get"]').forEach(form => ['input','change','submit'].forEach(type => form.addEventListener(type, clear)));
        window.addEventListener('pageshow', clear);
        clear();
    }));
})();
