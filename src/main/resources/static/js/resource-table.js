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
        const consoleArrow = ['previous','next','refresh'].includes(name);
        svg.setAttribute('viewBox', consoleArrow ? '0 0 16 16' : '0 0 24 24'); svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', consoleArrow ? '2' : '1.8');
        if (consoleArrow) svg.setAttribute('stroke-linejoin','round');
        // Arrow geometry: Cloudscape Design System (Amazon), Apache-2.0.
        // https://github.com/cloudscape-design/components/tree/main/src/icon/icons
        // See vendor/cloudscape-icons.LICENSE and vendor/cloudscape-icons.NOTICE.
        const paths = {
            previous: ['M11 2L5 8L11 14'], next: ['M5 2L11 8L5 14'], close: ['m6 6 12 12M6 18 18 6'],
            refresh: ['M15 0V5L10 4.95999', 'M15 8C15 11.87 11.87 15 8 15C4.13 15 1 11.87 1 8C1 4.13 4.13 1 8 1C10.79 1 13.2 2.63 14.33 5'],
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
            if (region.dataset.tablePageSize && filters.tagName === 'FORM') {
                let size = filters.querySelector('input[name="size"]');
                if (!size) { size=node('input');size.type='hidden';size.name='size';filters.append(size); }
                size.value=region.dataset.tablePageSize;
            }
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
            if (region.dataset.tablePageSize && control.hasAttribute('href')) {
                const url = new URL(control.href, location.href);url.searchParams.set('size',region.dataset.tablePageSize);control.href=url.href;
            }
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
            inputs.forEach(input=>input.closest('td').classList.add('resource-table-selection'));
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
        const cells = new WeakMap();
        const originalCells = row => {
            if (!cells.has(row)) cells.set(row, [...row.cells]);
            return cells.get(row);
        };
        const lockedColumns = () => {
            const rows=[...table.rows].filter(row=>row!==head);
            const hasGroupedCells=rows.some(row=>row.cells.length>1 && [...row.cells].some(cell=>cell.colSpan>1 || cell.rowSpan>1));
            return new Set(headers.flatMap((header, index) => hasGroupedCells || index <= (selectable ? 1 : 0) || rows.some(row => originalCells(row)[index]?.querySelector('input,select,textarea')) ? [index] : []));
        };
        const key = 'resource-table:v1:' + (region.dataset.resourceTable || scroller.dataset.viewPositionKey || location.pathname + ':' + [...document.querySelectorAll('[data-resource-table]')].indexOf(region));
        const defaultOrder = headers.map((_, index) => index);
        let settings = {hidden: [], compact: false, wrap: false, striped: false, sticky: 0, order: defaultOrder};
        try {
            const stored = JSON.parse(localStorage.getItem(key));
            if (stored && Array.isArray(stored.hidden)) settings = {...settings, ...stored,
                hidden: stored.hidden.filter(n => Number.isInteger(n) && n >= 0 && n < headers.length),
                sticky: [0,1,2].includes(stored.sticky) ? stored.sticky : 0,
                order: Array.isArray(stored.order) && stored.order.length === headers.length && new Set(stored.order).size === headers.length && stored.order.every(n => defaultOrder.includes(n)) ? stored.order : defaultOrder};
        } catch { /* Storage may be disabled. */ }
        const stickyOffsets = () => {
            let left=0, count=0;
            const offsets=[...head.cells].map(cell=>{
                const sticky=!cell.hidden && count < settings.sticky + (settings.sticky && selectable ? 1 : 0);
                const offset=left;
                if(!cell.hidden){left+=cell.getBoundingClientRect().width;count++;}
                return {sticky,left:offset};
            });
            [...table.rows].forEach(row => {
                [...row.cells].forEach((cell,index) => {
                    const {sticky,left}=row.cells.length===headers.length && cell.colSpan===1 ? offsets[index] : {sticky:false,left:0};
                    cell.classList.toggle('resource-table-sticky', sticky);
                    cell.style.setProperty('--sticky-left', left + 'px');
                });
            });
        };
        const apply = () => {
            const locked = lockedColumns();
            settings.hidden = settings.hidden.filter(index => !locked.has(index));
            // Keep identifiers and editable columns in their original positions for page controllers.
            const movable = settings.order.filter(index => !locked.has(index));
            settings.order = defaultOrder.map(index => locked.has(index) ? index : movable.shift());
            region.classList.toggle('resource-table-compact', settings.compact); region.classList.toggle('resource-table-wrap-lines', settings.wrap);
            region.classList.toggle('resource-table-striped', settings.striped);
            [...table.rows].forEach(row => {
                if (row.cells.length !== headers.length || [...row.cells].some(cell => cell.colSpan > 1)) return;
                const originals = originalCells(row), ordered = settings.order.map(index => originals[index]);
                originals.forEach((cell, index) => { cell.hidden = settings.hidden.includes(index); });
                if (ordered.some((cell, index) => row.cells[index] !== cell)) row.append(...ordered);
            });
            stickyOffsets();
        };
        apply();
        if (window.ResizeObserver) new ResizeObserver(stickyOffsets).observe(scroller);
        window.addEventListener('resize', stickyOffsets);
        const observer = new MutationObserver(() => { apply(); applySearch(); syncSelection(); });
        [...table.tBodies].forEach(body => observer.observe(body, {childList:true, subtree:true}));
        preferences.addEventListener('click', () => {
            const dialog = node('dialog', 'resource-table-preferences app-modal-panel');
            const title = node('h2', '', l.preferences); title.id = 'resource-table-preferences-title'; dialog.setAttribute('aria-labelledby', title.id);
            const top = node('div', 'resource-table-dialog-header'); const close = iconButton('close', l.close); top.append(title, close); dialog.append(top);
            const form = node('form'); form.method = 'dialog';
            const body = node('div', 'resource-table-dialog-body'); form.append(body);
            const check = (parent, text, checked, disabled = false) => { const label = node('label', 'resource-table-option'); const input = node('input'); input.type = 'checkbox'; input.checked = checked; input.disabled = disabled; label.append(input, document.createTextNode(text)); parent.append(label); return input; };
            const display = node('div', 'resource-table-display-options'); body.append(display);
            const radioGroup = (title, options, current, name) => {
                const group = node('fieldset'); group.append(node('legend', '', title)); display.append(group);
                return options.map(([value, text]) => {
                    const label = node('label', 'resource-table-option'), input = node('input');
                    input.type = 'radio'; input.name = name; input.value = value; input.checked = value === current;
                    label.append(input, document.createTextNode(text)); group.append(label); return input;
                });
            };
            const pageSize = region.dataset.tablePageSize ? radioGroup(l.pageSize, [10,30,50].map(size => [size, size + ' ' + l.records.toLocaleLowerCase()]), Number(region.dataset.tablePageSize), 'pageSize') : [];
            const wrap = check(display, l.wrap, settings.wrap), striped = check(display, l.striped, settings.striped), compact = check(display, l.compact, settings.compact);
            const sticky = radioGroup(l.sticky, [[0,l.none],[1,l.firstColumn],[2,l.firstTwoColumns]], settings.sticky, 'sticky');
            const columns = node('fieldset', 'resource-table-column-options'); columns.append(node('legend', '', l.columns)); body.append(columns);
            const locked = lockedColumns();
            const choices = [], order = [...settings.order];
            let dragged;
            const reorder = (source, target) => {
                if (locked.has(source) || locked.has(target) || source === target) return;
                const movable=order.filter(index=>!locked.has(index));
                const from=movable.indexOf(source), to=movable.indexOf(target);
                if(from<0 || to<0)return;
                movable.splice(to,0,...movable.splice(from,1));
                order.forEach((index,position)=>{if(!locked.has(index))order[position]=movable.shift();});
            };
            const renderColumns = () => {
                columns.querySelectorAll('.resource-table-column-option').forEach(item => item.remove());
                order.forEach(index => {
                    if (selectable && index === 0) return;
                    const item = node('div', 'resource-table-column-option'), handle = node('button', 'resource-table-drag', '⠿');
                    handle.type = 'button'; handle.disabled = locked.has(index); handle.draggable = !handle.disabled;
                    handle.setAttribute('aria-label', l.reorder + ' ' + headers[index].textContent.trim());
                    handle.title = l.reorder;
                    const move = target => {
                        if (locked.has(index) || locked.has(target) || index === target) return;
                        reorder(index,target); renderColumns();
                        columns.querySelector('[data-column="' + index + '"] button')?.focus();
                    };
                    handle.addEventListener('keydown', event => {
                        if (!['ArrowUp','ArrowDown'].includes(event.key)) return;
                        event.preventDefault(); const movable = order.filter(n => !locked.has(n));
                        const target = movable[movable.indexOf(index) + (event.key === 'ArrowUp' ? -1 : 1)];
                        if (target !== undefined) move(target);
                    });
                    handle.addEventListener('dragstart', event => { dragged = index; event.dataTransfer?.setData('text/plain', String(index)); });
                    item.addEventListener('dragover', event => { if (!locked.has(index)) event.preventDefault(); });
                    item.addEventListener('drop', event => { event.preventDefault(); if (dragged !== undefined) { reorder(dragged,index); renderColumns(); } dragged=undefined; });
                    item.dataset.column = index; item.append(handle); columns.append(item);
                    const checked = choices[index]?.checked ?? (locked.has(index) || !settings.hidden.includes(index));
                    choices[index] = check(item, headers[index].textContent.trim(), checked, locked.has(index));
                    choices[index].setAttribute('role','switch');
                });
            };
            renderColumns();
            const footer = node('div', 'app-modal-actions resource-table-dialog-actions'); const cancel = node('button', 'app-btn btn-neutral', l.cancel); cancel.type = 'button'; const confirm = node('button', 'app-btn btn-primary', l.confirm); confirm.type = 'submit'; footer.append(cancel, confirm); form.append(footer); dialog.append(form);
            document.body.append(dialog);
            const oldBodyOverflow = document.body.style.overflow, oldRootOverflow = document.documentElement.style.overflow;
            document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
            dialog.addEventListener('close', () => { document.body.style.overflow = oldBodyOverflow; document.documentElement.style.overflow = oldRootOverflow; dialog.remove(); preferences.focus(); });
            close.addEventListener('click', () => dialog.close()); cancel.addEventListener('click', () => dialog.close());
            form.addEventListener('submit', event => {
                event.preventDefault(); settings = {compact:compact.checked, wrap:wrap.checked, striped:striped.checked, sticky:Number(sticky.find(input => input.checked).value), order, hidden:choices.flatMap((choice,index) => choice && !choice.checked ? [index] : [])};
                clearSelection(); apply(); try { localStorage.setItem(key, JSON.stringify(settings)); } catch { /* Preferences still apply for this view. */ }
                dialog.close();
                const size = pageSize.find(input => input.checked)?.value;
                if (size && size !== region.dataset.tablePageSize) { const url = new URL(location.href); url.searchParams.set('size',size); url.searchParams.set('page','0'); location.assign(url); }
            });
            dialog.showModal(); close.focus();
        });
        window.ResourceSplitView?.init(region, {headers, originalCells, selectable, labels:l});
    }
    window.ResourceTable = {init: root => {
        (root || document).querySelectorAll('[data-resource-table]').forEach(init);
        document.querySelectorAll('[data-table-pagination-used]').forEach(pager => pager.remove());
    }};
    window.addEventListener('load', () => window.ResourceTable.init());
})();
