(() => {
    const register = document.getElementById('coa-register');
    if (!register) return;
    const inputs = () => Array.from(register.querySelectorAll('input[name="coa-selection"]'));
    const body = register.querySelector('tbody');
    const view = document.getElementById('coa-view'), create = document.getElementById('coa-create');
    const status = document.getElementById('coa-status');
    const statusForm = document.getElementById('coa-status-form'), summary = document.getElementById('coa-selection-status');
    const selected = () => inputs().find(input => input.checked && !input.closest('tr').hidden)?.closest('[data-coa-row]');
    const update = () => {
        const row = selected();
        inputs().forEach(input => input.closest('tr').classList.toggle('coa-row-selected', input.checked));
        view.disabled = !row || row.dataset.heading !== 'true';
        view.textContent = row?.dataset.expanded === 'true' ? register.dataset.collapse : row?.dataset.family === 'true' ? view.dataset.accountsLabel : view.dataset.groupsLabel;
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
        summary.textContent = row ? summary.dataset.selected + ' ' + row.querySelector('.coa-account-code').textContent.trim() + ' · ' + row.querySelector('[data-coa-name]').firstChild.textContent.trim() : summary.dataset.empty;
    };
    register.addEventListener('change', event => {
        const input = event.target;
        if (!input.matches('input[name="coa-selection"]')) return;
        if (input.checked) inputs().forEach(other => { if (other !== input) other.checked = false; });
        update();
    });
    body.addEventListener('click', event => {
        if (event.target.closest('a,button,input,label')) return;
        const row = event.target.closest('[data-coa-row]');
        if (!row) return;
        const input = row.querySelector('input[name="coa-selection"]');
        const checked = !input.checked; inputs().forEach(other => other.checked = false);
        input.checked = checked; input.focus(); update();
    });
    const reset = () => { inputs().forEach(input => input.checked = false); update(); };
    const refreshBranches = () => {
        const rows = new Map();
        Array.from(body.rows).forEach(row => {
            const parent = rows.get(row.dataset.treeParent);
            row.hidden = !!row.dataset.treeParent && (!parent || parent.hidden || parent.dataset.expanded !== 'true');
            if (row.dataset.id) rows.set(row.dataset.id, row);
            if (row.hidden && row.querySelector('input[name="coa-selection"]')) row.querySelector('input[name="coa-selection"]').checked = false;
        });
        update();
    };
    const branchMessage = (parent, text, retry) => {
        const row = document.createElement('tr');
        row.dataset.treeParent = parent.dataset.id; row.dataset.depth = Number(parent.dataset.depth || 0) + 1;
        const cell = row.insertCell(); cell.colSpan = 8; cell.className = 'coa-tree-message';
        const message = document.createElement('span'); message.textContent = text; message.setAttribute('role', retry ? 'alert' : 'status'); cell.append(message);
        if (retry) {
            const button = document.createElement('button'); button.type = 'button'; button.className = 'app-btn btn-neutral';
            button.dataset.coaRetry = ''; button.textContent = register.dataset.retry; cell.append(button);
        }
        return row;
    };
    const loadChildren = async (parent, page = 0, placeholder) => {
        if (parent.dataset.loading === 'true') return;
        parent.dataset.loading = 'true'; parent.setAttribute('aria-busy', 'true');
        let anchor = placeholder;
        if (!anchor) {
            anchor = branchMessage(parent, register.dataset.loading, false); parent.after(anchor);
        } else anchor.querySelector('button').disabled = true;
        const pendingCell=anchor.cells[0];
        const branchSpinner=window.AppLoading?.status(register.dataset.loading,true);
        if(branchSpinner){branchSpinner.classList.add('coa-branch-loading');pendingCell.prepend(branchSpinner);}
        refreshBranches();
        const url = new URL(register.dataset.childrenUrl, location.href);
        url.searchParams.set('parentId', parent.dataset.id); url.searchParams.set('page', page);
        new FormData(register.querySelector('.coa-filters')).forEach((value, key) => { if (key !== 'parentId') url.searchParams.set(key, value); });
        const abort = new AbortController(), timeout = setTimeout(() => abort.abort(), 20000);
        try {
            const response = await fetch(url, {headers: {'X-Requested-With': 'XMLHttpRequest'}, signal: abort.signal});
            if(!parent.isConnected)return;
            if (!response.ok || response.redirected || response.headers.get('X-COA-Children') !== 'true') throw new Error('children');
            const fragment = document.createElement('tbody'); fragment.innerHTML = await response.text();
            const ancestorIds = new Set([parent.dataset.id]);
            let ancestor = parent;
            while (ancestor.dataset.treeParent) {
                ancestorIds.add(ancestor.dataset.treeParent);
                ancestor = Array.from(body.rows).find(row => row.dataset.id === ancestor.dataset.treeParent);
                if (!ancestor) break;
            }
            if (Array.from(fragment.rows).some(row => ancestorIds.has(row.dataset.id))) throw new Error('cycle');
            const children = Array.from(fragment.rows);
            children.forEach(row => {
                const existing = row.dataset.id && Array.from(body.rows).find(item => item.dataset.id === row.dataset.id);
                if (existing) {
                    let descendant = existing.nextElementSibling;
                    while (descendant && Number(descendant.dataset.depth || 0) > Number(existing.dataset.depth || 0)) {
                        const next = descendant.nextElementSibling; descendant.remove(); descendant = next;
                    }
                    existing.remove();
                }
                row.dataset.treeParent = parent.dataset.id; row.dataset.depth = Number(parent.dataset.depth || 0) + 1;
                row.querySelector('.coa-tree-code')?.style.setProperty('--coa-depth', row.dataset.depth);
                body.insertBefore(row, anchor);
            });
            if (!children.length && page === 0) anchor.replaceWith(branchMessage(parent, register.dataset.emptyChildren, false));
            else anchor.remove();
            parent.dataset.loaded = 'true';
        } catch {
            const error = branchMessage(parent, register.dataset.error, true); error.dataset.coaPage = page;
            anchor.replaceWith(error);
        } finally {
            branchSpinner?.remove();
            clearTimeout(timeout); delete parent.dataset.loading; parent.removeAttribute('aria-busy'); refreshBranches();
        }
    };
    body.addEventListener('click', event => {
        const toggle = event.target.closest('.coa-tree-toggle');
        if (toggle) {
            const row = toggle.closest('[data-coa-row]');
            const expanded = toggle.getAttribute('aria-expanded') !== 'true';
            row.dataset.expanded = String(expanded); toggle.setAttribute('aria-expanded', String(expanded));
            const label = expanded ? register.dataset.collapse : register.dataset.expand;
            toggle.setAttribute('aria-label', label + ' ' + row.querySelector('.coa-account-code').textContent); toggle.title = label;
            refreshBranches();
            if (expanded && row.dataset.loaded !== 'true' && !Array.from(body.rows).some(child => child.dataset.treeParent === row.dataset.id)) loadChildren(row);
        }
        const more = event.target.closest('[data-coa-load-more],[data-coa-retry]');
        if (more) {
            const placeholder = more.closest('tr'), parent = Array.from(body.rows).find(row => row.dataset.id === placeholder.dataset.treeParent);
            if (parent) loadChildren(parent, Number(placeholder.dataset.coaMore || placeholder.dataset.coaPage || 0), placeholder);
        }
    });
    register.querySelector('.coa-filters').addEventListener('change', reset);
    register.querySelector('.coa-filters').addEventListener('input', reset);
    view.addEventListener('click', () => {
        const row = selected(); if (!row || view.disabled) return;
        row.querySelector('.coa-tree-toggle')?.click();
    });
    create?.addEventListener('click', () => {
        const row = selected(); if (!row || create.disabled) return;
        const url = new URL(create.dataset.url, location.href); url.searchParams.set('parentId', row.dataset.id); location.assign(url.href);
    });
    statusForm?.addEventListener('submit', event => { if (!selected() || status.disabled) event.preventDefault(); else status.disabled = true; });
    window.addEventListener('pageshow', reset);
    register.addEventListener('resource-table:reset',reset);
    register.addEventListener('resource-table:updated',reset);
    reset();
})();
