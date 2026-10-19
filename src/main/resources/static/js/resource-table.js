/* Shared JSP resource tables. Server-owned queries, paging and commands stay authoritative. */
(() => {
    'use strict';
    const labels = () => document.getElementById('resource-table-labels')?.dataset || {};
    const lifetimes = new Map();
    const node = (tag, className, text) => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text) element.textContent = text;
        return element;
    };
    const icon = name => {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        const consoleArrow = ['previous','next','refresh','sort','caret'].includes(name);
        svg.setAttribute('viewBox', consoleArrow ? '0 0 16 16' : '0 0 24 24'); svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', consoleArrow ? '2' : '1.8');
        if (consoleArrow) svg.setAttribute('stroke-linejoin','round');
        // Arrow geometry: Cloudscape Design System (Amazon), Apache-2.0.
        // https://github.com/cloudscape-design/components/tree/main/src/icon/icons
        // See vendor/cloudscape-icons.LICENSE and vendor/cloudscape-icons.NOTICE.
        const paths = {
            previous: ['M11 2L5 8L11 14'], next: ['M5 2L11 8L5 14'], close: ['m6 6 12 12M6 18 18 6'],
            refresh: ['M15 0V5L10 4.95999', 'M15 8C15 11.87 11.87 15 8 15C4.13 15 1 11.87 1 8C1 4.13 4.13 1 8 1C10.79 1 13.2 2.63 14.33 5'],
            sort: ['M8 11L12 5H4L8 11Z'], caret: ['M8 11L12 5H4L8 11Z'],
            settings: ['M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8', 'M9.5 2h5l.5 2.5 2 1.2 2.5-.8L22 9l-2 1.8v2.4l2 1.8-2.5 4.1-2.5-.8-2 1.2-.5 2.5h-5L9 19.5l-2-1.2-2.5.8L2 15l2-1.8v-2.4L2 9l2.5-4.1 2.5.8 2-1.2Z']
        };
        paths[name].forEach(d => { const path = document.createElementNS(svg.namespaceURI, 'path'); path.setAttribute('d', d); svg.append(path); });
        if (name === 'caret') svg.setAttribute('fill','currentColor');
        return svg;
    };
    const iconButton = (name, label) => {
        const button = node('button', 'app-btn btn-neutral resource-table-icon');
        button.type = 'button'; button.title = label; button.setAttribute('aria-label', label); button.append(icon(name));
        return button;
    };
    function actionMenu(container, initial, key, region, l) {
        const signal = lifetimes.get(region).controller.signal;
        const group = node('div','resource-table-action-menu'), trigger = node('button','app-btn btn-neutral',l.actions);
        trigger.type='button'; trigger.setAttribute('aria-haspopup','menu'); trigger.setAttribute('aria-expanded','false');
        const menu = node('div','resource-table-menu'); menu.id=key+'-actions'; menu.hidden=true; menu.setAttribute('role','menu');
        trigger.setAttribute('aria-controls',menu.id); trigger.append(icon('caret')); menu.append(...initial); group.append(trigger,menu); container.append(group);
        const items = () => [...menu.querySelectorAll('button,a[href]')].filter(item=>!item.disabled && item.getAttribute('aria-disabled')!=='true');
        menu.querySelectorAll('button,a[href]').forEach(item=>item.setAttribute('role','menuitem'));
        const close = restore => {menu.hidden=true;trigger.setAttribute('aria-expanded','false');if(restore)trigger.focus();};
        const sync = () => {trigger.disabled=!items().length;if(trigger.disabled)close(false);};
        const open = last => {if(trigger.disabled)return;menu.hidden=false;trigger.setAttribute('aria-expanded','true');(last?items().at(-1):items()[0])?.focus();};
        trigger.addEventListener('click',()=>menu.hidden?open(false):close(false));
        trigger.addEventListener('keydown',event=>{if(['ArrowDown','ArrowUp'].includes(event.key)){event.preventDefault();open(event.key==='ArrowUp');}});
        menu.addEventListener('keydown',event=>{
            if(event.key==='Escape'){event.preventDefault();close(true);}
            else if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){
                event.preventDefault();const choices=items(),index=choices.indexOf(document.activeElement);
                const next=event.key==='Home'?0:event.key==='End'?choices.length-1:(index+(event.key==='ArrowDown'?1:-1)+choices.length)%choices.length;
                choices[next]?.focus();
            }
        });
        // Let the native click/submit complete before restoring focus.
        menu.addEventListener('click',event=>{if(event.target.closest('[role=menuitem]:not(:disabled)'))queueMicrotask(()=>{if(region.isConnected)close(group.contains(document.activeElement));});});
        document.addEventListener('click',event=>{if(!group.contains(event.target))close(false);},{signal});
        group.addEventListener('focusout',event=>{if(!group.contains(event.relatedTarget))close(false);});
        const observer = new MutationObserver(sync); observer.observe(menu,{subtree:true,childList:true,attributes:true,attributeFilter:['disabled','aria-disabled']});
        lifetimes.get(region).observers.push(observer);
        region.addEventListener('resource-table:reset',()=>{close(false);sync();}); sync();
        return {menu,trigger,sync,close};
    }
    function init(region) {
        if (region.dataset.resourceTableReady) return;
        const table = region.querySelector('table'), head = table?.tHead?.rows[0];
        if (!head) return;
        const lifetime = {controller:new AbortController(),observers:[]}; lifetimes.set(region,lifetime);
        const signal = lifetime.controller.signal;
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
            const heading = region.querySelector(':scope > h2, :scope > h3') || [...document.querySelectorAll('[data-table-heading-for]')].find(candidate => candidate.dataset.tableHeadingFor === region.dataset.resourceTable);
            const title = region.getAttribute('aria-label') || document.getElementById(region.dataset.tableTitleId)?.textContent;
            titlebar.append(heading || node('h2', '', table.caption?.textContent || title || l.records)); region.prepend(titlebar);
        }
        const duplicateHeading = region.querySelector(':scope > h2, :scope > h3');
        if (duplicateHeading && duplicateHeading.textContent.trim() === titlebar.querySelector('h2,h3')?.textContent.trim()) duplicateHeading.remove();
        scroller.tabIndex = 0; scroller.setAttribute('role', 'region');
        scroller.setAttribute('aria-label', titlebar.querySelector('h2,h3')?.textContent.trim() || l.records);
        let actions = titlebar.querySelector('.accounting-actions, .app-table-toolbar');
        if (!actions) { actions = node('div', 'app-table-toolbar'); titlebar.append(actions); }
        actions.classList.add('resource-table-actions');
        document.querySelectorAll('[data-table-toolbar-for]').forEach(group => {
            if (group.dataset.tableToolbarFor === region.dataset.resourceTable) {
                if (group.tagName === 'FORM') actions.append(group);
                else actions.append(...group.childNodes);
            }
        });
        titlebar.querySelectorAll(':scope > a.app-btn, :scope > button.app-btn').forEach(control => actions.append(control));
        let refresh = actions.querySelector('[data-aws-table-refresh], .app-icon-button');
        if (refresh) { refresh.replaceChildren(icon('refresh')); refresh.classList.add('resource-table-icon'); refresh.setAttribute('aria-label', l.refresh); refresh.title = l.refresh; }
        else if (region.dataset.awsNoRefresh !== 'true') {
            refresh = iconButton('refresh', l.refresh); actions.prepend(refresh);
        }
        if (refresh) refresh.dataset.resourceRefresh = 'true';
        const tools = node('div', 'resource-table-tools'), showTools = region.dataset.tableTools !== 'none';
        if (showTools) titlebar.after(tools);
        let filters = region.querySelector(':scope > form:not([method="post"]), :scope > .aws-filter-toolbar') || [...document.querySelectorAll('[data-table-filter-for]')].find(candidate => candidate.dataset.tableFilterFor === region.dataset.resourceTable);
        let search = filters?.querySelector('[data-aws-table-search]');
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
                let size = filters.querySelector('[name="size"]');
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
        const externalPaging = region.dataset.tablePaginationMode === 'external';
        let externalPage = {page:0,hasNext:false,loading:false};
        const renderPager = source => {
        navigation.replaceChildren();
        const page = (externalPaging ? externalPage.page : Number(source?.dataset.page || 0)) + 1;
        ['previous', 'next'].forEach(direction => {
            let control = source?.querySelector('[data-page-direction="' + direction + '"]');
            if (control) control = control.cloneNode(true);
            if (!control) { control = iconButton(direction, l[direction]); control.disabled = true; }
            if (externalPaging) {
                control.disabled = externalPage.loading || (direction === 'previous' ? page === 1 : !externalPage.hasNext);
                control.addEventListener('click', () => region.dispatchEvent(new CustomEvent('resource-table:page', {detail:{page:externalPage.page + (direction === 'previous' ? -1 : 1)}})));
            }
            control.className = 'resource-table-page'; control.setAttribute('aria-label', l[direction]); control.title = l[direction]; control.replaceChildren(icon(direction));
            if (region.dataset.tablePageSize && control.hasAttribute('href')) {
                const url = new URL(control.href, location.href);url.searchParams.set('size',region.dataset.tablePageSize);control.href=url.href;
            }
            if (direction === 'next') { const current = node('span', 'resource-table-page-number', String(page)); current.setAttribute('aria-current', 'page'); current.setAttribute('aria-label', l.page + ' ' + page); navigation.append(current); }
            navigation.append(control);
        });
        };
        renderPager(pager);
        if (externalPaging) lifetime.setPage = state => {
            if (externalPage.page === state.page && externalPage.hasNext === state.hasNext && externalPage.loading === state.loading) return;
            externalPage = state;renderPager();
        };
        if (pager) pager.dataset.tablePaginationUsed = 'true';
        utilities.append(navigation);
        const preferences = iconButton('settings', l.preferences); preferences.classList.add('resource-table-preferences-button'); utilities.append(preferences);

        // Existing row-selection controllers keep their business-specific command rules.
        const rowInputs = () => selectable ? [...table.querySelectorAll('tbody input[type="checkbox"]')].filter(input => input.closest('td')?.cellIndex === 0 && !input.closest('tr').hidden && !input.disabled) : [];
        let selectable = region.dataset.tableSelection === 'single';
        const actionHeaders = [...head.querySelectorAll('[data-table-actions-header]')];
        if (actionHeaders.length) {
            selectable = true;
            const actionIndexes = actionHeaders.map(header => header.cellIndex), columnCount = head.cells.length;
            actionHeaders.forEach(header => header.remove()); head.prepend(node('th'));
            const commands = node('div', 'resource-table-record-actions'); actions.prepend(commands);
            const records = new Map(), commandLabels = new Map();
            const secondary = actionMenu(commands,[],region.dataset.resourceTable+'-record',region,l);
            const context = node('div','resource-table-command-context'); context.hidden = true; region.append(context);
            let active, placements = [];
            const announcement = node('span', 'sr-only'); announcement.setAttribute('role', 'status'); region.append(announcement);
            const kind = control => control.tagName === 'A' && !control.hasAttribute('download') && !control.hasAttribute('data-download-action') && !/\.pdf(?:$|[?#])/.test(control.getAttribute('href') || '') ? 'navigation' : 'command';
            const resetCommands = () => {
                placements.forEach(({control,parent,next}) => parent.insertBefore(control,next?.parentNode === parent ? next : null)); placements=[];
                if (active) { active.content.append(...context.childNodes); active = null; }
                records.forEach(record => { record.input.checked = false; record.row.classList.remove('resource-table-selected'); });
                commands.querySelectorAll(':scope > button, :scope > a').forEach(control => control.remove()); secondary.menu.replaceChildren();
                (commandLabels.size ? [...commandLabels] : [[l.view,'navigation']]).forEach(([label,type]) => {
                    const button = node('button','app-btn btn-neutral',label); button.type='button'; button.disabled=true;
                    (type === 'navigation' ? commands : secondary.menu).append(button);
                });
                secondary.trigger.hidden = !secondary.menu.children.length; secondary.close(false); secondary.sync(); context.hidden=true; announcement.textContent = '';
            };
            const adaptRows = () => {
                [...table.tBodies].flatMap(body => [...body.rows]).forEach(row => {
                    if (row.cells.length === 1 && row.cells[0].colSpan === columnCount) row.cells[0].colSpan = columnCount - actionHeaders.length + 1;
                    if (records.has(row) || row.cells.length !== columnCount || row.cells[0].colSpan > 1 || row.querySelector('.resource-table-selection')) return;
                    const content = document.createDocumentFragment(), commandCells = actionIndexes.map(index => row.cells[index]);
                    commandCells.forEach(cell => {
                        cell.querySelectorAll('a.app-btn,button').forEach(control => commandLabels.set(control.textContent.trim(),kind(control)));
                        content.append(...cell.childNodes); cell.remove();
                    });
                    const selectCell = node('td','resource-table-selection'), input = node('input'); input.type='checkbox';
                    input.setAttribute('aria-label',l.select+' '+row.cells[0].textContent.trim()); selectCell.append(input); row.prepend(selectCell);
                    const record = {row,input,content}; records.set(row,record);
                    input.addEventListener('change', () => {
                        const selected=input.checked; resetCommands();
                        if (selected) {
                            commands.querySelectorAll(':scope > button, :scope > a').forEach(control => control.remove());
                            secondary.menu.replaceChildren();
                            active=record; input.checked=true; row.classList.add('resource-table-selected'); context.append(content);
                            const controls=[...context.querySelectorAll('a.app-btn,button')];
                            controls.forEach(control => {
                                const moved=control.closest('form') || control;
                                if (placements.some(item=>item.control===moved)) return;
                                placements.push({control:moved,parent:moved.parentNode,next:moved.nextSibling});
                                control.classList.add('app-btn');
                                if (kind(control)==='navigation') commands.append(moved);
                                else {control.setAttribute('role','menuitem');secondary.menu.append(moved);}
                            });
                            secondary.trigger.hidden=!secondary.menu.children.length; secondary.sync();
                            context.hidden=!context.textContent.trim() || context.textContent.trim()==='-'; announcement.textContent=input.getAttribute('aria-label');
                        }
                    });
                    row.addEventListener('click',event=>{if(!event.target.closest('input,button,a,select,textarea,label,summary'))input.click();});
                });
                for (const [row] of records) if (!table.contains(row)) {if(active?.row===row)resetCommands();records.delete(row);}
            };
            adaptRows(); resetCommands(); region.addEventListener('resource-table:reset',resetCommands);
            const adapterObserver = new MutationObserver(() => {if(region.isConnected){adaptRows();if(!active)resetCommands();}});
            adapterObserver.observe(table,{childList:true,subtree:true}); lifetime.observers.push(adapterObserver);
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
            region.dispatchEvent(new CustomEvent('resource-table:reset',{bubbles:true})); syncSelection();
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
        navigation.addEventListener('click', clearSelection); window.addEventListener('pageshow', clearSelection,{signal});

        const headers = [...head.cells];
        const syncSortHeaders = () => headers.forEach(header => {
            header.scope = 'col';
            let link = header.querySelector('a[data-table-sort]');
            // Explicit server sort values; never infer ordering from translated labels.
            if (header.dataset.tableSortAsc || header.dataset.tableSortDesc) {
                const sort = filters?.querySelector('[name=sort]');
                if (sort && filters instanceof HTMLFormElement) {
                    const asc = header.dataset.tableSortAsc, desc = header.dataset.tableSortDesc;
                    const direction = filters.querySelector('[name=direction]');
                    const active = direction ? sort.value === header.dataset.tableSortKey : [asc,desc].includes(sort.value);
                    const ascending = active && (direction ? direction.value === 'asc' : sort.value === asc);
                    header.setAttribute('aria-sort', active ? ascending ? 'ascending' : 'descending' : 'none');
                    const url = new URL(filters.action || location.href, location.href);
                    new FormData(filters).forEach((value,key) => { if (typeof value === 'string') url.searchParams.set(key,value); });
                    url.searchParams.set('sort', direction ? header.dataset.tableSortKey : ascending ? desc || asc : asc || desc);
                    if (direction) url.searchParams.set('direction', ascending ? 'desc' : 'asc');
                    url.searchParams.set('page','0');
                    if (!link) {link = node('a');link.dataset.tableSort = '';link.append(...header.childNodes);header.append(link);}
                    link.href = url.href;
                }
            }
            if (!link) return;
            if (link.classList.contains('resource-table-sort')) return;
            link.classList.add('resource-table-sort');
            const label = node('span'); label.append(...link.childNodes); link.append(label,icon('sort'));
            link.addEventListener('click', clearSelection);
        });
        syncSortHeaders();
        let queryUrl = new URL(location.href), request, version = 0;
        const errorRow = node('tr','resource-table-load-error'), errorCell = node('td','',l.loadError);
        errorCell.colSpan = headers.length; errorCell.setAttribute('role','alert'); errorRow.append(errorCell);
        const updateTable = async (target, changeHistory = true) => {
            const url = new URL(target || queryUrl, location.href);
            if (url.origin !== location.origin) return;
            const current = ++version; request?.abort(); const currentRequest = new AbortController(); request=currentRequest;
            const timeout = setTimeout(()=>currentRequest.abort(),20000);
            clearSelection(); errorRow.remove();window.AppLoading?.begin(region);
            try {
                const response = await fetch(url.href,{credentials:'same-origin',headers:{Accept:'text/html'},signal:currentRequest.signal});
                if (!response.ok || response.redirected) throw new Error('table');
                const page = new DOMParser().parseFromString(await response.text(),'text/html');
                if (current !== version || !region.isConnected) return;
                const fresh = [...page.querySelectorAll('[data-resource-table]')].find(item=>item.dataset.resourceTable===region.dataset.resourceTable);
                const freshTable = fresh?.querySelector('table');
                let freshHeaders = [...(freshTable?.tHead?.rows[0]?.cells || [])];
                if (actionHeaders.length) freshHeaders = [null,...freshHeaders.filter(item=>!item.hasAttribute('data-table-actions-header'))];
                if (!freshTable?.tBodies[0] || freshHeaders.length !== headers.length) throw new Error('table');
                window.AppLoading?.end(region);
                [...table.tBodies].forEach((body,index)=>body.replaceChildren(...[...(freshTable.tBodies[index]?.childNodes || [])].map(child=>document.importNode(child,true))));
                noMatches = undefined;
                if (fresh.dataset.tablePageSize) region.dataset.tablePageSize = fresh.dataset.tablePageSize;
                region.querySelectorAll('.admin-register-count').forEach((summary,index)=>{
                    const updated=fresh.querySelectorAll('.admin-register-count')[index];
                    if(updated)summary.textContent=updated.textContent;
                });
                const freshFilters = fresh.querySelector('form:not([method="post"])') || [...page.querySelectorAll('[data-table-filter-for]')].find(item=>item.dataset.tableFilterFor===region.dataset.resourceTable);
                ['sort','direction','size'].forEach(name=>{
                    const input = filters?.querySelector('[name="'+name+'"]'), value = freshFilters?.querySelector('[name="'+name+'"]');
                    if (input && value) {input.value=value.value;window.refreshEnhancedSelects?.(filters);}
                });
                if (region.dataset.tableSyncQuery === 'true') {
                    // Keep existing controls, card links and their listeners mounted.
                    [...(filters?.elements || [])].forEach(input=>{
                        if (!input.name || ['submit','button'].includes(input.type)) return;
                        const source=freshFilters?.elements.namedItem(input.name);
                        if (source && 'value' in source) input.value=source.value;
                    });
                    window.refreshEnhancedSelects?.(filters);
                    const heading=titlebar.querySelector('h2,h3'), sourceHeading=fresh.querySelector('.app-table-titlebar h2,.app-table-titlebar h3');
                    if (heading && sourceHeading) {heading.textContent=sourceHeading.textContent;scroller.setAttribute('aria-label',heading.textContent.trim());}
                    const states=[...page.querySelectorAll('[data-table-query-state-for]')].filter(item=>item.dataset.tableQueryStateFor===region.dataset.resourceTable);
                    document.querySelectorAll('[data-table-query-state-for]').forEach(state=>{
                        if (state.dataset.tableQueryStateFor!==region.dataset.resourceTable) return;
                        state.querySelectorAll('[data-table-query-value]').forEach(value=>{
                            const source=states.flatMap(item=>[...item.querySelectorAll('[data-table-query-value]')]).find(item=>item.dataset.tableQueryValue===value.dataset.tableQueryValue);
                            if(source)value.textContent=source.textContent;
                        });
                    });
                }
                headers.forEach((header,index)=>{
                    const source = freshHeaders[index], link = header.querySelector('[data-table-sort]'), freshLink = source?.querySelector('[data-table-sort]');
                    if (link && freshLink) {link.href=new URL(freshLink.getAttribute('href'),url).href;header.setAttribute('aria-sort',source.getAttribute('aria-sort') || 'none');}
                });
                syncSortHeaders();
                const freshPager = fresh.querySelector('[data-table-pagination]') || [...page.querySelectorAll('[data-table-pagination][data-table-for]')].find(item=>item.dataset.tableFor.split(' ').includes(region.dataset.resourceTable));
                renderPager(freshPager);
                queryUrl=url;if(changeHistory)history.replaceState(history.state,'',url.href);
                region.dispatchEvent(new CustomEvent('resource-table:updated',{bubbles:true}));
                queueMicrotask(()=>{apply();applySearch();syncSelection();});
            } catch (error) {
                if(current===version && region.isConnected) {
                    window.AppLoading?.end(region);
                    table.tBodies[0].prepend(errorRow);
                }
            } finally {clearTimeout(timeout);if(current===version)window.AppLoading?.end(region);}
        };
        signal.addEventListener('abort',()=>{version++;request?.abort();window.AppLoading?.end(region);});
        document.addEventListener('click',event=>{
            if(event.defaultPrevented || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
            const link=event.target.closest('a[data-table-query-for]');
            if(!link || link.dataset.tableQueryFor!==region.dataset.resourceTable || externalPaging)return;
            const target=new URL(link.href,location.href);
            if(target.origin!==location.origin || target.pathname!==queryUrl.pathname)return;
            event.preventDefault();
            if(link.dataset.tableQueryParam){
                const parameter=link.dataset.tableQueryParam;
                const url=new URL(queryUrl);
                if(filters?.tagName==='FORM')new FormData(filters).forEach((value,name)=>{if(typeof value==='string')url.searchParams.set(name,value);});
                url.searchParams.set(parameter,target.searchParams.get(parameter)||'');
                url.searchParams.set('page','0');void updateTable(url);
            }else void updateTable(target);
        },{signal});
        region.addEventListener('resource-table:refresh',event=>{if(!event.defaultPrevented && !externalPaging)void updateTable(queryUrl);},{signal});
        region.addEventListener('click',event=>{
            if(event.defaultPrevented || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
            const control=event.target.closest('[data-resource-refresh],a[data-table-sort],.resource-table-pagination a[href]');
            if(!control || control.disabled)return;
            event.preventDefault();event.stopImmediatePropagation();
            if(control.hasAttribute('data-resource-refresh'))region.dispatchEvent(new Event('resource-table:refresh',{bubbles:true,cancelable:true}));
            else void updateTable(control.href);
        },{capture:true,signal});
        if(filters?.tagName==='FORM')filters.addEventListener('submit',event=>{
            if(event.defaultPrevented || filters.method==='post')return;
            event.preventDefault();event.stopImmediatePropagation();
            const url=new URL(filters.action || queryUrl,location.href);
            new FormData(filters).forEach((value,name)=>{if(typeof value==='string')url.searchParams.set(name,value);});
            url.searchParams.set('page','0');void updateTable(url);
        },{capture:true,signal});
        // Original forms and listeners remain attached when commands move into the menu.
        const menuCommands = [...actions.querySelectorAll(':scope > [data-table-command]')];
        if (menuCommands.length) actionMenu(actions,menuCommands,region.dataset.resourceTable,region,l);
        // This fallback searches only the bounded rows on this page; server filters remain authoritative.
        const searchVisibility = new WeakMap();
        let noMatches;
        const applySearch = () => {
            if (!search) return;
            if(table.querySelector('.app-loading-row')){if(noMatches)noMatches.hidden=true;return;}
            const query = search.value.trim().toLocaleLowerCase();
            const rows = [...table.tBodies].flatMap(body => [...body.rows]).filter(row => row !== noMatches && !row.classList.contains('app-loading-row') && row.cells.length === headers.length && ![...row.cells].some(cell => cell.colSpan > 1));
            let visible = 0;
            rows.forEach(row => {
                if (!searchVisibility.has(row)) searchVisibility.set(row, row.hidden);
                const values = [...row.querySelectorAll('input:not([type="hidden"]):not([type="checkbox"]),select,textarea')].map(control => control.tagName === 'SELECT' ? [...control.selectedOptions].map(option => option.textContent).join(' ') : control.value);
                row.hidden = searchVisibility.get(row) || ![row.textContent, ...values].join(' ').toLocaleLowerCase().includes(query);
                if (!row.hidden) visible++;
            });
            const loading=!!table.querySelector('.app-loading-row');
            if (query && rows.length && !visible && !noMatches && !loading) {
                noMatches = node('tr', 'resource-table-no-matches');
                const cell = node('td', '', l.noMatches); cell.colSpan = headers.length; cell.setAttribute('role', 'status'); noMatches.append(cell); table.tBodies[0].append(noMatches);
            }
            if (noMatches) noMatches.hidden = loading || !query || !rows.length || visible > 0;
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
            return new Set(headers.flatMap((header, index) => hasGroupedCells || header.hasAttribute('data-table-identifier') || index <= (selectable ? 1 : 0) || rows.some(row => originalCells(row)[index]?.querySelector('input,select,textarea')) ? [index] : []));
        };
        const key = 'resource-table:v1:' + (region.dataset.resourceTable || scroller.dataset.viewPositionKey || location.pathname + ':' + [...document.querySelectorAll('[data-resource-table]')].indexOf(region));
        const defaultOrder = headers.map((_, index) => index);
        let settings = {hidden: [], compact: true, wrap: false, striped: false, sticky: 0, order: defaultOrder};
        try {
            const stored = showTools ? JSON.parse(localStorage.getItem(key)) : null;
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
        if (window.ResizeObserver) {const resizeObserver=new ResizeObserver(stickyOffsets);resizeObserver.observe(scroller);lifetime.observers.push(resizeObserver);}
        window.addEventListener('resize', stickyOffsets,{signal});
        const observer = new MutationObserver(() => { if(region.isConnected){apply(); applySearch(); syncSelection();} });
        [...table.tBodies].forEach(body => observer.observe(body, {childList:true, subtree:true}));
        lifetime.observers.push(observer);
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
            const sizes = region.dataset.tablePageSizeOptions ? region.dataset.tablePageSizeOptions.split(' ').map(Number) : [10,30,50];
            const pageSize = region.dataset.tablePageSize ? radioGroup(l.pageSize, sizes.map(size => [size, size + ' ' + l.records.toLocaleLowerCase()]), Number(region.dataset.tablePageSize), 'pageSize') : [];
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
            const footer = node('div', 'app-modal-actions resource-table-dialog-actions'); const cancel = node('button', 'app-btn btn-neutral', l.cancel); cancel.type = 'button'; const confirm = node('button', 'app-btn btn-neutral', l.confirm); confirm.type = 'submit'; footer.append(cancel, confirm); form.append(footer); dialog.append(form);
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
                if (size && size !== region.dataset.tablePageSize) { const url = new URL(queryUrl); url.searchParams.set('size',size); url.searchParams.set('page','0'); void updateTable(url); }
            });
            dialog.showModal(); close.focus();
        });
        if (showTools) window.ResourceSplitView?.init(region, {headers, originalCells, selectable, labels:l});
    }
    window.ResourceTable = {setPage: (region, state) => {
        if (region?.dataset.tablePaginationMode !== 'external') return;
        init(region);
        lifetimes.get(region)?.setPage({page:Math.max(0,Math.trunc(state.page)),hasNext:!!state.hasNext,loading:!!state.loading});
    }, refresh: control => {
        const region=control.closest('[data-resource-table]');if(!region)return false;
        region.dispatchEvent(new Event('resource-table:refresh',{bubbles:true,cancelable:true}));return true;
    }, init: root => {
        for (const [region,lifetime] of lifetimes) if (!region.isConnected) {
            lifetime.controller.abort(); lifetime.observers.forEach(observer=>observer.disconnect()); lifetimes.delete(region);
        }
        (root || document).querySelectorAll('[data-resource-table]').forEach(init);
        document.querySelectorAll('[data-table-pagination-used]').forEach(pager => pager.remove());
        document.querySelectorAll('[data-table-pagination-shell]').forEach(shell => {
            if (!shell.querySelector('[data-table-pagination]')) shell.remove();
        });
        document.querySelectorAll('[data-resource-header-source], .erp-panel-header').forEach(source => {
            if (!source.textContent.trim() && !source.querySelector('input,select,button,a,img')) source.hidden=true;
        });
        document.querySelectorAll('.erp-panel').forEach(panel => {
            const body=panel.querySelector(':scope > .erp-panel-body');
            if (!body) return;
            const visible=[...panel.children].filter(child=>!child.hidden && child!==body);
            if (!visible.length && body.children.length && [...body.children].every(child=>child.hasAttribute('data-resource-table'))) {
                panel.classList.add('resource-table-surface'); body.classList.add('resource-table-surface-body');
            }
        });
    }};
    window.addEventListener('load', () => window.ResourceTable.init());
})();
