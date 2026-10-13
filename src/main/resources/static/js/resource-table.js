/* Shared JSP resource tables. Server-owned queries, paging and commands stay authoritative. */
(() => {
    'use strict';
    const labels = () => document.getElementById('resource-table-labels')?.dataset || {};
    const node = (tag, className, text) => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text) element.textContent = text;
        return element;
    };
    const icon = name => {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '1.8');
        const paths = {
            previous: ['m15 18-6-6 6-6'], next: ['m9 18 6-6-6-6'], close: ['m6 6 12 12M6 18 18 6'],
            refresh: ['M20 7v5h-5M4 17v-5h5', 'M6.1 7a7 7 0 0 1 11.5-2L20 8M4 16l2.4 3A7 7 0 0 0 18 17'],
            settings: ['M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8', 'M9.5 2h5l.5 2.5 2 1.2 2.5-.8L22 9l-2 1.8v2.4l2 1.8-2.5 4.1-2.5-.8-2 1.2-.5 2.5h-5L9 19.5l-2-1.2-2.5.8L2 15l2-1.8v-2.4L2 9l2.5-4.1 2.5.8 2-1.2Z']
        };
        paths[name].forEach(d => { const path = document.createElementNS(svg.namespaceURI, 'path'); path.setAttribute('d', d); svg.append(path); });
        return svg;
    };
    const iconButton = (name, label) => {
        const button = node('button', 'app-btn btn-neutral resource-table-icon');
        button.type = 'button'; button.title = label; button.setAttribute('aria-label', label); button.append(icon(name));
        return button;
    };
    function init(region) {
        if (region.dataset.resourceTableReady) return;
        const table = region.querySelector('table'), head = table?.tHead?.rows[0];
        if (!head) return;
        region.dataset.resourceTableReady = 'true';
        const l = labels();
        let scroller = table.closest('.erp-table-scroll');
        if (!scroller || scroller === region) {
            scroller = node('div', 'erp-table-scroll'); table.before(scroller); scroller.append(table);
            region.classList.remove('erp-table-scroll');
        }
        let titlebar = region.querySelector(':scope > .app-table-titlebar');
        if (!titlebar) {
            titlebar = node('div', 'app-table-titlebar');
            const heading = region.querySelector(':scope > h2, :scope > h3');
            titlebar.append(heading || node('h2', '', table.caption?.textContent || l.records)); region.prepend(titlebar);
        }
        const duplicateHeading = region.querySelector(':scope > h2, :scope > h3');
        if (duplicateHeading && duplicateHeading.textContent.trim() === titlebar.querySelector('h2,h3')?.textContent.trim()) duplicateHeading.remove();
        scroller.tabIndex = 0; scroller.setAttribute('role', 'region');
        scroller.setAttribute('aria-label', titlebar.querySelector('h2,h3')?.textContent.trim() || l.records);
        let actions = titlebar.querySelector('.accounting-actions, .app-table-toolbar, .business-actions, .reconciliation-actions');
        if (!actions) { actions = node('div', 'app-table-toolbar'); titlebar.append(actions); }
        actions.classList.add('resource-table-actions');
        titlebar.querySelectorAll(':scope > a.app-btn, :scope > button.app-btn').forEach(control => actions.append(control));
        const refresh = actions.querySelector('[data-aws-table-refresh], .app-icon-button');
        if (refresh) { refresh.replaceChildren(icon('refresh')); refresh.classList.add('resource-table-icon'); refresh.setAttribute('aria-label', l.refresh); refresh.title = l.refresh; }
        else if (region.dataset.awsNoRefresh !== 'true') {
            const button = iconButton('refresh', l.refresh); actions.prepend(button); button.addEventListener('click', () => location.reload());
        }
        const tools = node('div', 'resource-table-tools'); titlebar.after(tools);
        let filters = region.querySelector(':scope > form:not([method="post"]), :scope > .aws-filter-toolbar');
        let search;
        if (!filters || !filters.querySelector('input:not([type="hidden"]),select,textarea')) {
            filters ||= node('div');
            filters.classList.add('resource-table-search');
            const label = node('label'); label.append(node('span', 'sr-only', l.searchPage));
            search = node('input', 'aws-control'); search.type = 'search'; search.placeholder = l.searchPage;
            label.append(search); filters.append(label);
            if (filters.tagName === 'FORM') filters.addEventListener('submit', event => event.preventDefault());
            search.addEventListener('keydown', event => { if (event.key === 'Enter') event.preventDefault(); });
        }
        if (filters) {
            filters.classList.add('resource-table-filters');
            const filterCommands = [...filters.querySelectorAll(':scope > button, :scope > a.app-btn')];
            if (filterCommands.length) { const group = node('div', 'resource-table-filter-actions'); group.append(...filterCommands); filters.append(group); }
            tools.append(filters);
        }
        const utilities = node('div', 'resource-table-utilities'); tools.append(utilities);
        const pager = region.querySelector('[data-table-pagination]') || [...document.querySelectorAll('[data-table-pagination][data-table-for]')].find(candidate => candidate.dataset.tableFor.split(' ').includes(region.dataset.resourceTable));
        const navigation = node('nav', 'resource-table-pagination'); navigation.setAttribute('aria-label', l.pagination);
        const page = Number(pager?.dataset.page || 0) + 1;
        ['previous', 'next'].forEach(direction => {
            let control = pager?.querySelector('[data-page-direction="' + direction + '"]');
            if (control) control = control.cloneNode(true);
            if (!control) { control = iconButton(direction, l[direction]); control.disabled = true; }
            control.className = 'resource-table-page'; control.setAttribute('aria-label', l[direction]); control.title = l[direction]; control.replaceChildren(icon(direction));
            if (direction === 'next') { const current = node('span', 'resource-table-page-number', String(page)); current.setAttribute('aria-current', 'page'); current.setAttribute('aria-label', l.page + ' ' + page); navigation.append(current); }
            navigation.append(control);
        });
        if (pager) pager.dataset.tablePaginationUsed = 'true';
        utilities.append(navigation);
        const preferences = iconButton('settings', l.preferences); preferences.classList.add('resource-table-preferences-button'); utilities.append(preferences);

        // Existing row-selection controllers keep their business-specific command rules.
        const rowInputs = () => selectable ? [...table.querySelectorAll('tbody input[type="checkbox"]')].filter(input => input.closest('td')?.cellIndex === 0 && !input.closest('tr').hidden && !input.disabled) : [];
        let selectable = region.dataset.tableSelection === 'single';
        const actionHeader = head.querySelector('[data-table-actions-header]');
        if (actionHeader) {
            selectable = true;
            const actionIndex = actionHeader.cellIndex, columnCount = head.cells.length;
            actionHeader.remove(); head.prepend(node('th'));
            const commands = node('div', 'resource-table-record-actions'); actions.prepend(commands);
            const records = [], commandLabels = new Set();
            let active;
            const announcement = node('span', 'sr-only'); announcement.setAttribute('role', 'status'); region.append(announcement);
            const resetCommands = () => {
                if (active) { active.content.append(...commands.childNodes); active = null; }
                records.forEach(record => { record.input.checked = false; record.row.classList.remove('resource-table-selected'); });
                commands.replaceChildren();
                (commandLabels.size ? [...commandLabels] : [l.view]).forEach(label => { const button = node('button', 'app-btn btn-neutral', label); button.type = 'button'; button.disabled = true; commands.append(button); });
                announcement.textContent = '';
            };
            [...table.tBodies].flatMap(body => [...body.rows]).forEach(row => {
                if (row.cells.length !== columnCount || row.cells[0].colSpan > 1) return;
                const content = document.createDocumentFragment(), cell = row.cells[actionIndex];
                cell.querySelectorAll('a.app-btn, button').forEach(control => commandLabels.add(control.textContent.trim()));
                content.append(...cell.childNodes); cell.remove();
                const selectCell = node('td', 'resource-table-selection'), input = node('input'); input.type = 'checkbox';
                input.disabled = !content.querySelector('a[href],button:not(:disabled)');
                input.setAttribute('aria-label', l.select + ' ' + row.cells[0].textContent.trim()); selectCell.append(input); row.prepend(selectCell);
                const record = {row, input, content}; records.push(record);
                input.addEventListener('change', () => {
                    const selected = input.checked; resetCommands();
                    if (selected) { active = record; input.checked = true; row.classList.add('resource-table-selected'); commands.replaceChildren(content); announcement.textContent = input.getAttribute('aria-label'); }
                });
                row.addEventListener('click', event => { if (!event.target.closest('input,button,a,select,textarea,label,summary')) input.click(); });
            });
            resetCommands(); region.addEventListener('resource-table:reset', resetCommands);
        }
        let selectHeader;
        const syncSelection = () => {
            if (!selectHeader) return;
            const inputs = rowInputs(), checked = inputs.some(input => input.checked);
            selectHeader.disabled = !inputs.length; selectHeader.checked = checked; selectHeader.indeterminate = checked && inputs.length > 1;
            selectHeader.setAttribute('aria-label', checked ? l.clearSelection : l.selectFirst);
        };
        const clearSelection = () => {
            rowInputs().filter(input => input.checked).forEach(input => input.click());
            region.dispatchEvent(new CustomEvent('resource-table:reset')); syncSelection();
        };
        if (selectable) {
            selectHeader = node('input'); selectHeader.type = 'checkbox'; selectHeader.className = 'resource-table-select';
            head.cells[0].replaceChildren(selectHeader); head.cells[0].classList.add('resource-table-selection');
            head.cells[0].scope = 'col';
            selectHeader.addEventListener('click', () => {
                const inputs = rowInputs(), checked = inputs.find(input => input.checked);
                (checked || inputs[0])?.click(); syncSelection();
            });
            table.addEventListener('click', () => queueMicrotask(syncSelection));
            table.addEventListener('change', syncSelection);
            syncSelection();
        }
        filters?.addEventListener('input', clearSelection); filters?.addEventListener('change', clearSelection);
        navigation.addEventListener('click', clearSelection); window.addEventListener('pageshow', clearSelection);

        const headers = [...head.cells];
        // This fallback searches only the bounded rows on this page; server filters remain authoritative.
        const searchVisibility = new WeakMap();
        let noMatches;
        const applySearch = () => {
            if (!search) return;
            const query = search.value.trim().toLocaleLowerCase();
            const rows = [...table.tBodies].flatMap(body => [...body.rows]).filter(row => row !== noMatches && row.cells.length === headers.length && ![...row.cells].some(cell => cell.colSpan > 1));
            let visible = 0;
            rows.forEach(row => {
                if (!searchVisibility.has(row)) searchVisibility.set(row, row.hidden);
                const values = [...row.querySelectorAll('input:not([type="hidden"]):not([type="checkbox"]),select,textarea')].map(control => control.tagName === 'SELECT' ? [...control.selectedOptions].map(option => option.textContent).join(' ') : control.value);
                row.hidden = searchVisibility.get(row) || ![row.textContent, ...values].join(' ').toLocaleLowerCase().includes(query);
                if (!row.hidden) visible++;
            });
            if (query && rows.length && !visible && !noMatches) {
                noMatches = node('tr', 'resource-table-no-matches');
                const cell = node('td', '', l.noMatches); cell.colSpan = headers.length; cell.setAttribute('role', 'status'); noMatches.append(cell); table.tBodies[0].append(noMatches);
            }
            if (noMatches) noMatches.hidden = !query || !rows.length || visible > 0;
            syncSelection();
        };
        search?.addEventListener('input', () => { clearSelection(); applySearch(); });
        const lockedColumns = () => new Set(headers.flatMap((header, index) => index <= (selectable ? 1 : 0) || [...table.tBodies].some(body => [...body.rows].some(row => row.cells[index]?.querySelector('input,select,textarea'))) ? [index] : []));
        const key = 'resource-table:v1:' + (region.dataset.resourceTable || scroller.dataset.viewPositionKey || location.pathname + ':' + [...document.querySelectorAll('[data-resource-table]')].indexOf(region));
        let settings = {hidden: [], compact: true, wrap: false};
        try { const stored = JSON.parse(localStorage.getItem(key)); if (stored && Array.isArray(stored.hidden)) settings = {hidden: stored.hidden.filter(n => Number.isInteger(n) && n > (selectable ? 1 : 0) && n < headers.length), compact: stored.compact !== false, wrap: stored.wrap === true}; } catch { /* Storage may be disabled. */ }
        const apply = () => {
            const locked = lockedColumns();
            settings.hidden = settings.hidden.filter(index => !locked.has(index));
            region.classList.toggle('resource-table-compact', settings.compact); region.classList.toggle('resource-table-wrap-lines', settings.wrap);
            [...table.rows].forEach(row => {
                if (row.cells.length !== headers.length || [...row.cells].some(cell => cell.colSpan > 1)) return;
                [...row.cells].forEach((cell, index) => { cell.hidden = settings.hidden.includes(index); });
            });
        };
        apply();
        const observer = new MutationObserver(() => { apply(); applySearch(); syncSelection(); });
        [...table.tBodies].forEach(body => observer.observe(body, {childList:true, subtree:true}));
        preferences.addEventListener('click', () => {
            const dialog = node('dialog', 'resource-table-preferences app-modal-panel');
            const title = node('h2', '', l.preferences); title.id = 'resource-table-preferences-title'; dialog.setAttribute('aria-labelledby', title.id);
            const top = node('div', 'resource-table-dialog-header'); const close = iconButton('close', l.close); top.append(title, close); dialog.append(top);
            const form = node('form'); form.method = 'dialog';
            const body = node('div', 'resource-table-dialog-body'); form.append(body);
            const check = (parent, text, checked, disabled = false) => { const label = node('label', 'resource-table-option'); const input = node('input'); input.type = 'checkbox'; input.checked = checked; input.disabled = disabled; label.append(input, document.createTextNode(text)); parent.append(label); return input; };
            const compact = check(body, l.compact, settings.compact), wrap = check(body, l.wrap, settings.wrap);
            const columns = node('fieldset'); columns.append(node('legend', '', l.columns)); body.append(columns);
            const locked = lockedColumns();
            const choices = headers.map((header, index) => selectable && index === 0 ? null : check(columns, header.textContent.trim(), locked.has(index) || !settings.hidden.includes(index), locked.has(index)));
            const footer = node('div', 'app-modal-actions resource-table-dialog-actions'); const cancel = node('button', 'app-btn btn-neutral', l.cancel); cancel.type = 'button'; const confirm = node('button', 'app-btn btn-primary', l.confirm); confirm.type = 'submit'; footer.append(cancel, confirm); form.append(footer); dialog.append(form);
            document.body.append(dialog);
            const oldBodyOverflow = document.body.style.overflow, oldRootOverflow = document.documentElement.style.overflow;
            document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
            dialog.addEventListener('close', () => { document.body.style.overflow = oldBodyOverflow; document.documentElement.style.overflow = oldRootOverflow; dialog.remove(); preferences.focus(); });
            close.addEventListener('click', () => dialog.close()); cancel.addEventListener('click', () => dialog.close());
            form.addEventListener('submit', event => { event.preventDefault(); settings = {compact:compact.checked, wrap:wrap.checked, hidden:choices.flatMap((choice,index) => choice && !choice.checked ? [index] : [])}; clearSelection(); apply(); try { localStorage.setItem(key, JSON.stringify(settings)); } catch { /* Preferences still apply for this view. */ } dialog.close(); });
            dialog.showModal(); close.focus();
        });
    }
    window.ResourceTable = {init: root => {
        (root || document).querySelectorAll('[data-resource-table]').forEach(init);
        document.querySelectorAll('[data-table-pagination-used]').forEach(pager => pager.remove());
    }};
    window.addEventListener('load', () => window.ResourceTable.init());
})();
