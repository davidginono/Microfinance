(() => {
    const register = document.getElementById('coa-register');
    if (!register) return;
    const inputs = Array.from(register.querySelectorAll('input[name="coa-selection"]'));
    const view = document.getElementById('coa-view'), create = document.getElementById('coa-create');
    const status = document.getElementById('coa-status');
    const statusForm = document.getElementById('coa-status-form'), summary = document.getElementById('coa-selection-status');
    const clear = document.getElementById('coa-clear-selection');
    const selected = () => inputs.find(input => input.checked && input.getClientRects().length)?.closest('[data-coa-row]');
    const update = () => {
        const row = selected();
        inputs.forEach(input => input.closest('tr').classList.toggle('coa-row-selected', input.checked));
        view.disabled = !row || row.dataset.heading !== 'true';
        view.textContent = row?.dataset.family === 'true' ? view.dataset.accountsLabel : view.dataset.groupsLabel;
        if (create) {
            const mode = row?.dataset.group === 'true' ? 'group' : row?.dataset.posting === 'true' ? 'account' : null;
            create.disabled = !mode;
            create.textContent = mode ? create.dataset[mode + 'Label'] : create.dataset.emptyLabel;
            create.dataset.url = mode ? create.dataset[mode + 'Url'] : '';
        }
        if (status) {
            status.disabled = !row || row.dataset.lifecycle !== 'true';
            const action = row?.dataset.active === 'false' ? 'reactivate' : 'deactivate';
            status.textContent = status.dataset[action];
            if (row) statusForm.action = statusForm.dataset.url + row.dataset.id + '/' + action;
            else statusForm.removeAttribute('action');
        }
        summary.textContent = row ? summary.dataset.selected + ' ' + row.cells[1].textContent.trim() + ' · ' + row.querySelector('[data-coa-name]').firstChild.textContent.trim() : summary.dataset.empty;
        clear.hidden = !row;
    };
    inputs.forEach(input => input.addEventListener('change', () => {
        if (input.checked) inputs.forEach(other => { if (other !== input) other.checked = false; });
        update();
    }));
    register.querySelectorAll('[data-coa-row]').forEach(row => row.addEventListener('click', event => {
        if (event.target.closest('a,button,input,label')) return;
        const input = row.querySelector('input[name="coa-selection"]');
        const checked = !input.checked; inputs.forEach(other => other.checked = false);
        input.checked = checked; input.focus(); update();
    }));
    const reset = () => { inputs.forEach(input => input.checked = false); update(); };
    clear.addEventListener('click', () => { const input = inputs.find(input => input.checked); reset(); input?.focus(); });
    view.addEventListener('click', () => {
        const row = selected(); if (!row || view.disabled) return;
        const url = new URL(view.dataset.url, location.href); url.searchParams.set('parentId', row.dataset.id); location.assign(url.href);
    });
    create?.addEventListener('click', () => {
        const row = selected(); if (!row || create.disabled) return;
        const url = new URL(create.dataset.url, location.href); url.searchParams.set('parentId', row.dataset.id); location.assign(url.href);
    });
    statusForm?.addEventListener('submit', event => { if (!selected() || status.disabled) event.preventDefault(); else status.disabled = true; });
    window.addEventListener('pageshow', reset);
    reset();
})();
