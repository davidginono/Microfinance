(function () {
    const onReady = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener("load", callback, {once:true}));
    const shellTopNav = document.getElementById('shellTopNav');
    const toastContainer = document.getElementById('appToastContainer');
    const pageTitleRail = document.getElementById('shellPageTitleRail');
    const pageTitleRailText = document.getElementById('shellPageTitleRailText');
    const pageBreadcrumbRailText = document.getElementById('shellPageBreadcrumbRailText');
    const toastQueue = [];
    const visibleToasts = [];
    const maxVisibleToasts = 3;
    const pageToastState = {
        container: toastContainer,
        queue: toastQueue,
        visible: visibleToasts
    };
    const modalToastStates = new WeakMap();
    let titleRailTicking = false;
    let exportDownloadActive = false;
    let exportPrintActive = false;
    const exportDownloadTimeoutMs = 60 * 1000;
    const exportPrintFrameCleanupDelayMs = 2 * 60 * 1000;
    const legacyScrollRestoreStorageKey = 'Saccos:restore-scroll';
    const scrollRestoreStorageKeyPrefix = 'Saccos:restore-scroll:';
    const modalRestoreStorageKey = 'Saccos:open-modal';
    const restoreStateMaxAgeMs = 24 * 60 * 60 * 1000;
    let scrollStateSaveTimer = null;
    const normalizeBreadcrumbSegment = function (value) {
        return (value || '').replace(/\s+/g, ' ').trim().toLowerCase();
    };
    const currentReviewWorkspaceBase = function () {
        const path = window.location.pathname || '';
        if (path.indexOf('/chairperson') === 0) {
            return '/chairperson';
        }
        if (path.indexOf('/credit-committee') === 0) {
            return '/credit-committee';
        }
        return '/board';
    };
    const currentStaffWorkspaceHome = function () {
        const path = window.location.pathname || '';
        const match = path.match(/^\/(manager|chairperson|credit-committee|board|loan-officer|accountant|disbursement)(?:\/|$)/);
        return match ? '/' + match[1] + '/dashboard' : '/staff/analytics';
    };
    const memberWorkspaceRoutes = {
        'application detail': '/app/loan-applications',
        'application progress': '/app/loan-applications',
        'archives': '/app/archives',
        'guarantee archive': '/app/archives?section=guarantors',
        'guarantee requests': '/app/guarantee-requests',
        'guarantees': '/app/guaranteed-loans',
        'guarantor selection': '/app/loan-applications',
        'loan products': '/app/loan-products',
        'notifications': '/app/notifications',
        'reports & analytics': '/app/reports',
        'replies': '/app/support/replies',
        'sent archive': '/app/support/archive',
        'settings': '/app/settings',
        'support': '/app/support'
    };
    const breadcrumbRouteGroups = {
        'admin tools': {
            'audit log': '/admin/events',
            'dashboard': '/admin/dashboard',
            'event log': '/admin/events',
            'incidents': '/admin/incidents',
            'loan product controls': '/admin/loan-products',
            'loan products': '/admin/settings-controls?section=loan',
            'outbox monitor': '/admin/outbox',
            'platform dashboard': '/admin/dashboard',
            'platform settings': '/admin/platform-settings',
            'reports': '/admin/reports',
            'institution registration': '/admin/saccos/registry',
            'institution workspace': '/admin/scope/select',
            'institutions': '/admin/saccos',
            'sacco registration': '/admin/saccos/registry',
            'sacco workspace': '/admin/scope/select',
            'saccos': '/admin/saccos',
            'settings & controls': '/admin/settings-controls',
            'sms usage': '/admin/sms-usage',
            'support': '/admin/support',
            'users & roles': '/admin/users'
        },
        'member workspace': memberWorkspaceRoutes,
        'client workspace': memberWorkspaceRoutes,
        'staff workspace': {
            'settings': '/staff/settings',
            'staff reports and analytics': '/staff/analytics'
        },
        'manager panel': {
            'archive': '/manager/archive',
            'loan reports': '/manager/reports',
            'notifications': '/manager/notifications',
            'queue': '/manager/loan-applications?status=READY_FOR_MANAGER',
            'settings': '/manager/settings'
        },
        'accountant panel': {
            'archive': '/accountant/archive',
            'notifications': '/accountant/notifications',
            'queue': '/accountant/loan-applications?filter=AWAITING_ACCOUNTANT',
            'reports': '/accountant/reports'
        },
        'disbursement panel': {
            'archive': '/disbursement/archive',
            'notifications': '/disbursement/notifications',
            'queue': '/disbursement/loan-applications?filter=READY_FOR_DISBURSEMENT',
            'reports': '/disbursement/reports'
        },
        'loan officer panel': {
            'archive': '/loan-officer/archive',
            'assigned': '/loan-officer/assigned',
            'loan reports': '/loan-officer/reports',
            'notifications': '/loan-officer/notifications',
            'queue': '/loan-officer/queue'
        },
        'board panel': {
            'archive': '/board/archive',
            'assigned': '/board/assigned',
            'loan reports': '/board/reports',
            'notifications': '/board/notifications',
            'queue': '/board/queue',
            'reports': '/board/reports'
        },
        'chairperson panel': {
            'archive': '/chairperson/archive',
            'assigned': '/chairperson/assigned',
            'loan reports': '/chairperson/reports',
            'notifications': '/chairperson/notifications',
            'queue': '/chairperson/queue',
            'reports': '/chairperson/reports'
        },
        'credit committee panel': {
            'archive': '/credit-committee/archive',
            'assigned': '/credit-committee/assigned',
            'loan reports': '/credit-committee/reports',
            'notifications': '/credit-committee/notifications',
            'queue': '/credit-committee/queue',
            'reports': '/credit-committee/reports'
        },
        'documents': {
            'attachment preview': window.location.pathname + window.location.search
        },
        'workspace': {
            'profile': '/profile'
        }
    };
    const breadcrumbRootHref = function (root) {
        if (root === 'admin tools') {
            return '/admin/dashboard';
        }
        if (root === 'member workspace' || root === 'client workspace') {
            return '/app/dashboard';
        }
        if (root === 'staff workspace') {
            return currentStaffWorkspaceHome();
        }
        if (root === 'manager panel') {
            return '/manager/dashboard';
        }
        if (root === 'accountant panel') {
            return '/accountant/dashboard';
        }
        if (root === 'disbursement panel') {
            return '/disbursement/dashboard';
        }
        if (root === 'loan officer panel') {
            return '/loan-officer/dashboard';
        }
        if (root === 'board panel' || root === 'chairperson panel' || root === 'credit committee panel') {
            return currentReviewWorkspaceBase() + '/dashboard';
        }
        if (root === 'documents') {
            return '/app/loan-applications';
        }
        if (root === 'workspace') {
            return currentStaffWorkspaceHome();
        }
        return null;
    };
    const resolveBreadcrumbHref = function (segment, index, segments) {
        if (index === segments.length - 1) {
            return null;
        }
        const root = normalizeBreadcrumbSegment(segments[0]);
        if (index === 0) {
            return breadcrumbRootHref(root);
        }
        const routes = breadcrumbRouteGroups[root] || {};
        return routes[normalizeBreadcrumbSegment(segment)] || null;
    };
    if ('scrollRestoration' in window.history) {
        window.history.scrollRestoration = 'manual';
    }
    const syncActiveSidebarLink = function () {
        const navigation = document.querySelector('.shell-sidebar-scroll');
        if (!navigation) {
            return;
        }
        const currentPath = window.location.pathname || '/';
        const currentSearch = window.location.search || '';
        const links = Array.from(navigation.querySelectorAll('a[href]'));
        let bestMatch = null;
        let bestScore = -1;
        links.forEach(function (link) {
            let target;
            try {
                target = new URL(link.getAttribute('href'), window.location.origin);
            } catch (ignored) {
                return;
            }
            const exactPath = target.pathname === currentPath;
            const parentPath = currentPath.indexOf(target.pathname + '/') === 0;
            if (!exactPath && !parentPath) {
                return;
            }
            let score = target.pathname.length;
            if (exactPath) {
                score += 10000;
            }
            if (target.search && target.search === currentSearch) {
                score += 20000;
            } else if (target.search) {
                score -= 1000;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = link;
            }
        });
        if (bestMatch) {
            bestMatch.classList.add('shell-nav-active');
            bestMatch.setAttribute('aria-current', 'page');
        }
    };
    syncActiveSidebarLink();
    if (toastContainer && toastContainer.parentElement !== document.body) {
        document.body.appendChild(toastContainer);
    }
    const syncShellNavHeight = function () {
        if (!shellTopNav) {
            return;
        }
        const measuredHeight = Math.max(Math.ceil(shellTopNav.getBoundingClientRect().height), 38);
        document.documentElement.style.setProperty('--shell-nav-height', measuredHeight + 'px');
    };
    const createConsoleIcon = function (pathData) {
        const namespace = 'http://www.w3.org/2000/svg';
        const icon = document.createElementNS(namespace, 'svg');
        icon.setAttribute('viewBox', '0 0 24 24');
        icon.setAttribute('fill', 'none');
        icon.setAttribute('stroke', 'currentColor');
        icon.setAttribute('stroke-width', '2');
        icon.setAttribute('stroke-linecap', 'round');
        icon.setAttribute('stroke-linejoin', 'round');
        icon.setAttribute('aria-hidden', 'true');
        pathData.forEach(function (definition) {
            const path = document.createElementNS(namespace, 'path');
            path.setAttribute('d', definition);
            icon.appendChild(path);
        });
        return icon;
    };
    const initConsoleNavigationSearch = function () {
        const search = document.getElementById('consoleNavigationSearch');
        const sidebar = document.getElementById('appSidebar');
        if (!search || !sidebar) return;
        const results = document.createElement('div');
        results.className = 'app-navigation-search-results'; results.id = 'navigation-search-results'; results.hidden = true;
        search.parentElement.append(results); search.setAttribute('aria-controls', results.id);
        const hide = () => {results.hidden = true; search.setAttribute('aria-expanded', 'false');};
        const matches = () => {
            const query = search.value.trim().toLocaleLowerCase();
            return query ? Array.from(sidebar.querySelectorAll('a[href]')).filter(link =>
                !link.closest('[hidden]') && (link.textContent || '').trim().toLocaleLowerCase().includes(query)).slice(0,25) : [];
        };
        const render = () => {
            results.replaceChildren();
            if (!search.value.trim()) {hide(); return;}
            matches().forEach(link => {
                const option = document.createElement('a'); option.href = link.href; option.textContent = link.textContent.trim(); results.append(option);
            });
            if (!results.childElementCount) {const empty = document.createElement('p'); empty.setAttribute('role', 'status'); empty.textContent = document.body.dataset.shellNoOptions; results.append(empty);}
            results.hidden = false; search.setAttribute('aria-expanded', 'true');
        };
        const binding = window.AppSearch.bind(search, render, hide);
        search.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.isComposing) {const destination = matches()[0]; if (destination) window.WorkspaceNavigation.navigate(destination.href);}
            if (event.key === 'ArrowDown') {event.preventDefault(); binding.run(true); results.querySelector('a')?.focus();}
        });
        search.parentElement.addEventListener('keydown', event => {
            if (event.key === 'Escape') {event.preventDefault(); binding.cancel(); hide(); search.focus();}
            const links = Array.from(results.querySelectorAll('a')); const index = links.indexOf(document.activeElement);
            if (event.target !== search && index >= 0 && ['ArrowDown','ArrowUp'].includes(event.key)) {
                event.preventDefault(); links[(index + (event.key === 'ArrowDown' ? 1 : links.length - 1)) % links.length]?.focus();
            }
        });
        document.addEventListener('click', event => {if (!search.parentElement.contains(event.target)) {binding.cancel(); hide();}});
        hide();
    };
    const initUppercaseInputs = function () {
        document.querySelectorAll('[data-uppercase-input]').forEach(function (input) {
            input.addEventListener('input', function () {
                const selectionStart = input.selectionStart;
                const selectionEnd = input.selectionEnd;
                input.value = input.value.toLocaleUpperCase();
                if (selectionStart !== null && selectionEnd !== null) {
                    input.setSelectionRange(selectionStart, selectionEnd);
                }
            });
        });
    };
    const filePickerLabelText = function (input, emptyText) {
        const files = Array.from(input.files || []);
        if (files.length === 0) {
            return emptyText || 'No file chosen';
        }
        if (files.length === 1) {
            return files[0].name || 'Selected file';
        }
        return String(files.length) + ' files selected';
    };
    const refreshAwsFilePicker = function (input) {
        if (!input) {
            return;
        }
        const picker = input.closest('.aws-file-picker, .aws-file-dropzone, .profile-photo-dropzone');
        const label = picker?.querySelector('[data-file-picker-name]');
        if (!label) {
            return;
        }
        const emptyText = input.getAttribute('data-file-picker-empty') || label.getAttribute('data-file-picker-empty') || label.textContent.trim();
        label.setAttribute('data-file-picker-empty', emptyText || 'No file chosen');
        const nextText = filePickerLabelText(input, emptyText);
        label.textContent = nextText;
        label.title = nextText;
    };
    const initAwsFilePickers = function (root) {
        (root || document).querySelectorAll('[data-file-picker-input]').forEach(function (input) {
            if (input.dataset.filePickerReady === 'true') {
                refreshAwsFilePicker(input);
                return;
            }
            input.dataset.filePickerReady = 'true';
            input.addEventListener('change', function () {
                refreshAwsFilePicker(input);
            });
            refreshAwsFilePicker(input);
        });
    };
    window.SaccosFilePickers = {
        init: initAwsFilePickers,
        refresh: refreshAwsFilePicker
    };
    const enhanceConsolePagination = function () {
        document.querySelectorAll('a.app-btn, button.app-btn, span.app-btn').forEach(function (control) {
            if (control.closest('[data-resource-table], [data-table-pagination]')) return;
            const label = (control.textContent || '').trim().toLocaleLowerCase();
            const isPrevious = label === 'previous';
            const isNext = label === 'next';
            if (!isPrevious && !isNext) {
                return;
            }
            control.classList.add('app-icon-button', 'aws-pagination-chevron');
            control.setAttribute('aria-label', isPrevious ? 'Previous page' : 'Next page');
            control.textContent = '';
            control.appendChild(createConsoleIcon([
                isPrevious ? 'm15 18-6-6 6-6' : 'm9 18 6-6-6-6'
            ]));
        });
    };
    const resolveConsoleTableTitle = function (region, index) {
        const table = region.querySelector('table');
        const explicitLabel = region.getAttribute('aria-label')
            || table?.getAttribute('aria-label')
            || table?.querySelector('caption')?.textContent;
        if (explicitLabel && explicitLabel.trim()) {
            return explicitLabel.trim();
        }
        const panel = region.closest('.erp-panel, section');
        const nearbyHeading = panel?.querySelector('.erp-panel-title, .erp-widget-heading, h2, h3');
        if (nearbyHeading && nearbyHeading.textContent.trim()) {
            return nearbyHeading.textContent.trim();
        }
        const pageTitle = document.querySelector('.erp-page-title')?.textContent.trim();
        const resultsLabel = document.body.dataset.shellResults || 'Results';
        return pageTitle ? pageTitle + ' — ' + resultsLabel : resultsLabel + ' ' + String(index + 1);
    };
    const isConsoleDownloadAction = function (control) {
        if (!control) {
            return false;
        }
        const label = (control.textContent || '').trim().toLocaleLowerCase();
        const href = control.getAttribute?.('href') || '';
        return control.getAttribute?.('data-download-action') === 'true'
            || control.hasAttribute?.('download')
            || /\.(pdf|xlsx?|csv|zip)(?:$|[?#])/i.test(href)
            || /^(export|csv|xlsx?|pdf|excel|download)/.test(label);
    };
    const isConsolePrintAction = function (control) {
        return !!control && control.getAttribute?.('data-print-action') === 'true';
    };
    const ensureConsoleTableScroller = function (region, index) {
        const existingScroller = region.querySelector(':scope > .erp-table-scroll');
        if (existingScroller || !region.classList.contains('erp-table-scroll')) {
            return existingScroller;
        }
        const table = region.querySelector(':scope > table');
        if (!table) {
            return null;
        }
        const scroller = document.createElement('div');
        scroller.className = 'erp-table-scroll';
        ['erp-table-scroll-sm', 'erp-table-scroll-lg'].forEach(function (modifier) {
            if (region.classList.contains(modifier)) {
                region.classList.remove(modifier);
                scroller.classList.add(modifier);
            }
        });
        scroller.setAttribute('data-view-position-key', region.getAttribute('data-view-position-key') || 'table-region-' + String(index + 1));
        region.removeAttribute('data-view-position-key');
        region.classList.remove('erp-table-scroll');
        table.insertAdjacentElement('beforebegin', scroller);
        scroller.appendChild(table);
        return scroller;
    };
    const enhanceConsoleTables = function () {
        const regions = Array.from(document.querySelectorAll('.erp-table-wrap')).filter(function (region) {
            return region.querySelector('table') && !region.hasAttribute('data-resource-table');
        });
        regions.forEach(function (region, index) {
            ensureConsoleTableScroller(region, index);
            region.setAttribute('data-aws-table-region', '');
            region.setAttribute('aria-busy', 'false');
            let titlebar = region.querySelector(':scope > .app-table-titlebar');
            const suppressTitlebar = region.getAttribute('data-aws-no-titlebar') === 'true'
                || Boolean(document.querySelector('[data-staff-review-page="true"]'));
            if (!titlebar && !suppressTitlebar) {
                titlebar = document.createElement('div');
                titlebar.className = 'app-table-titlebar';
                const heading = document.createElement('div');
                heading.className = 'app-table-heading';
                const title = document.createElement('h2');
                title.textContent = resolveConsoleTableTitle(region, index);
                heading.appendChild(title);
                const toolbar = document.createElement('div');
                toolbar.className = 'app-table-toolbar';
                if (region.getAttribute('data-aws-no-refresh') !== 'true') {
                    const refresh = document.createElement('button');
                    refresh.type = 'button';
                    refresh.className = 'app-icon-button btn-neutral';
                    refresh.setAttribute('aria-label', document.body.dataset.shellRefresh || 'Refresh table');
                    refresh.appendChild(createConsoleIcon([
                        'M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5',
                        'M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5'
                    ]));
                    refresh.addEventListener('click', function () {
                        if (!window.ResourceTable?.refresh(refresh)) window.WorkspaceNavigation.navigate(window.location.href,{force:true});
                    });
                    toolbar.appendChild(refresh);
                }
                titlebar.append(heading, toolbar);
                region.insertBefore(titlebar, region.firstChild);
            }

            const parentSurface = region.parentElement;
            const filterCandidates = Array.from(document.querySelectorAll('form.aws-filter-toolbar')).filter(function (form) {
                if (form.closest('.erp-table-wrap') || form.dataset.awsTableAssigned === 'true' || form.dataset.awsFilterPin === 'true') {
                    return false;
                }
                if (parentSurface && parentSurface.contains(form)) {
                    return true;
                }
                return Boolean(form.compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING);
            });
            const filterForm = filterCandidates[filterCandidates.length - 1];
            if (filterForm && titlebar) {
                const filterTabs = filterForm.parentElement?.querySelector(':scope > .erp-filter-row');
                filterForm.dataset.awsTableAssigned = 'true';
                titlebar.insertAdjacentElement('afterend', filterForm);
                if (filterTabs && !filterTabs.closest('.erp-table-wrap')) {
                    titlebar.insertAdjacentElement('afterend', filterTabs);
                }
            }
        });

        document.querySelectorAll('.aws-pagination-chevron').forEach(function (control) {
            if (control.closest('[data-resource-table], [data-table-pagination]')) return;
            const precedingRegions = regions.filter(function (region) {
                return Boolean(region.compareDocumentPosition(control) & Node.DOCUMENT_POSITION_FOLLOWING);
            });
            const region = precedingRegions[precedingRegions.length - 1];
            const toolbar = region?.querySelector(':scope > .app-table-titlebar .app-table-toolbar');
            if (toolbar) {
                toolbar.appendChild(control);
            }
        });

        const tableActions = Array.from(document.querySelectorAll('a.app-btn, button.app-btn')).filter(function (control) {
            if (control.form || control.closest('form')
                || control.closest('table')
                || control.closest('[data-resource-table], [data-table-toolbar-for], [data-table-pagination]')
                || control.closest('.app-table-titlebar')
                || control.closest('.erp-page-header')
                || control.closest('.aws-filter-toolbar')
                || control.closest('.app-modal-overlay')
                || control.closest('[data-aws-action-pin="true"]')) {
                return false;
            }
            return control.classList.contains('btn-launch')
                || isConsoleDownloadAction(control);
        });
        tableActions.forEach(function (control) {
            const followingRegion = regions.find(function (region) {
                return Boolean(control.compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING);
            }) || regions[0];
            const toolbar = followingRegion?.querySelector(':scope > .app-table-titlebar .app-table-toolbar');
            if (toolbar) {
                toolbar.appendChild(control);
            }
        });
    };
    const initAwsClientTables = function () {
        document.querySelectorAll('[data-aws-table-refresh]').forEach(function (control) {
            if (control.dataset.awsRefreshReady === 'true') {
                return;
            }
            control.dataset.awsRefreshReady = 'true';
            control.addEventListener('click', function () {
                if (!window.ResourceTable?.refresh(control)) window.WorkspaceNavigation.navigate(window.location.href,{force:true});
            });
        });

        document.querySelectorAll('[data-aws-client-table]').forEach(function (region) {
            if (region.hasAttribute('data-resource-table')) return;
            const input = region.querySelector('[data-aws-table-search]');
            const table = region.querySelector('.erp-table');
            const body = table?.tBodies?.[0];
            if (!input || !body || input.dataset.awsSearchReady === 'true') {
                return;
            }
            input.dataset.awsSearchReady = 'true';
            const rows = Array.from(body.querySelectorAll('[data-aws-table-row]'));
            const count = region.querySelector('.app-table-count');
            const emptyText = input.getAttribute('data-empty-label') || 'No matching results.';

            const applySearch = function () {
                const query = input.value.trim().toLocaleLowerCase();
                let visibleCount = 0;
                body.querySelector('.aws-Member-table-empty')?.remove();
                rows.forEach(function (row) {
                    const visible = !query || (row.textContent || '').toLocaleLowerCase().includes(query);
                    row.hidden = !visible;
                    if (visible) {
                        visibleCount += 1;
                    }
                });
                if (query && visibleCount === 0) {
                    const emptyRow = document.createElement('tr');
                    emptyRow.className = 'aws-Member-table-empty';
                    const cell = document.createElement('td');
                    cell.className = 'erp-table-empty';
                    cell.colSpan = Math.max(table.tHead?.rows?.[0]?.cells?.length || 1, 1);
                    cell.textContent = emptyText;
                    emptyRow.appendChild(cell);
                    body.appendChild(emptyRow);
                }
                if (count) {
                    count.textContent = '(' + String(visibleCount) + ')';
                }
            };

            input.addEventListener('input', applySearch);
            input.addEventListener('keydown', function (event) {
                if (event.key === 'Escape' && input.value) {
                    input.value = '';
                    applySearch();
                }
            });
        });
    };
    const syncConsoleFiltersFromUrl = function () {
        const query = new URLSearchParams(window.location.search);
        document.querySelectorAll('form[method="get"], form:not([method])').forEach(function (form) {
            const actionPath = new URL(form.getAttribute('action') || window.location.href, window.location.href).pathname;
            if (actionPath !== window.location.pathname) {
                return;
            }
            form.querySelectorAll('[name]').forEach(function (field) {
                const name = field.getAttribute('name');
                if (!name || !query.has(name)) {
                    return;
                }
                const values = query.getAll(name);
                if (field.type === 'checkbox' || field.type === 'radio') {
                    field.checked = values.includes(field.value);
                } else if (field.tagName === 'SELECT' && field.multiple) {
                    Array.from(field.options).forEach(function (option) {
                        option.selected = values.includes(option.value);
                    });
                } else {
                    field.value = values[values.length - 1] || '';
                }
            });
        });
    };
    const clearConsoleTableLoading = function (options) {
        const force = options && options.force === true;
        document.querySelectorAll('.erp-table-wrap.is-loading').forEach(function (region) {
            if (!force && region.getAttribute('data-aws-async-table-loading') === 'true') {
                return;
            }
            region.classList.remove('is-loading');
            region.setAttribute('aria-busy', 'false');
            region.removeAttribute('data-aws-async-table-loading');
            window.AppLoading?.end(region);
            region.querySelectorAll('.aws-table-loader').forEach(function (loader) {
                loader.remove();
            });
        });
    };
    const observeLoadingInteraction = function (container, loadingHost, loadingClass, loaderSelector) {
        const lockedChildren = new Set();
        const syncInteractionLock = function () {
            const loading = loadingHost.classList.contains(loadingClass);
            lockedChildren.forEach(function (child) {
                if (!loading || child.parentElement !== container) {
                    child.inert = false;
                    lockedChildren.delete(child);
                }
            });
            if (loading) {
                Array.from(container.children).forEach(function (child) {
                    // Keep the loading status accessible and preserve existing inert state.
                    if (!child.matches(loaderSelector) && !child.inert) {
                        child.inert = true;
                        lockedChildren.add(child);
                    }
                });
            }
        };
        const observer = new MutationObserver(syncInteractionLock);
        observer.observe(container, { childList: true });
        observer.observe(loadingHost, {
            attributes: true,
            attributeFilter: ['class'],
            childList: loadingHost === container
        });
        syncInteractionLock();
    };
    const initConsoleTableLoading = function () {
        clearConsoleTableLoading();
        document.querySelectorAll('.erp-table-wrap').forEach(function (region) {
            // Async hydration also uses these regions, independently of form navigation.
            if(!window.AppLoading)observeLoadingInteraction(region, region, 'is-loading', '.aws-table-loader');
        });
        window.addEventListener('pagehide', function () {
            clearConsoleTableLoading({ force: true });
        });
        window.addEventListener('pageshow', function () {
            clearConsoleTableLoading({ force: true });
            syncConsoleFiltersFromUrl();
            window.requestAnimationFrame(function () {
                clearConsoleTableLoading({ force: true });
            });
        });
        window.addEventListener('popstate', function () {
            clearConsoleTableLoading({ force: true });
            syncConsoleFiltersFromUrl();
        });
    };
    const enhancePageBreadcrumbs = function () {
        document.querySelectorAll('.erp-page-header[data-aws-page-header]').forEach(function (header) {
            const breadcrumb = header.querySelector('.erp-breadcrumb');
            if (!breadcrumb || header.previousElementSibling?.classList.contains('erp-page-path')) {
                return;
            }
            const segments = (breadcrumb.textContent || '')
                .split(/\s*(?:\/|>|›)\s*/u)
                .map(function (segment) { return segment.trim(); })
                .filter(Boolean);
            if (segments.length === 0) {
                breadcrumb.remove();
                return;
            }
            const path = document.createElement('nav');
            path.className = 'erp-page-path';
            path.setAttribute('aria-label', 'Breadcrumb');
            const explicitRootHref = breadcrumb.getAttribute('data-breadcrumb-root-href');
            segments.forEach(function (segment, index) {
                if (index > 0) {
                    const separator = document.createElement('span');
                    separator.className = 'erp-page-path__separator';
                    separator.setAttribute('aria-hidden', 'true');
                    separator.textContent = '>';
                    path.appendChild(separator);
                }
                const href = index === 0 && explicitRootHref
                    ? explicitRootHref
                    : resolveBreadcrumbHref(segment, index, segments);
                const item = document.createElement(href ? 'a' : 'span');
                item.className = href ? 'erp-page-path__item erp-page-path__link' : 'erp-page-path__item';
                item.textContent = segment;
                if (href) {
                    item.href = href;
                }
                if (index === segments.length - 1) {
                    item.setAttribute('aria-current', 'page');
                }
                path.appendChild(item);
            });
            header.parentElement?.insertBefore(path, header);
            breadcrumb.remove();
        });
    };
    enhancePageBreadcrumbs();
    const promotePageHeadersToShell = function () {
        document.querySelectorAll('.erp-page-header[data-aws-page-header]').forEach(function (header) {
            const contentFrame = header.closest('.shell-content-frame');
            const shellMain = contentFrame?.parentElement;
            if (!shellMain?.classList.contains('shell-main')) {
                return;
            }
            // Templates, notices and resource wrappers may separate an authored path from its header.
            const path = [...contentFrame.querySelectorAll('.erp-page-path')].filter(function (candidate) {
                return candidate.closest('section,form,dialog,[role="dialog"]') === header.closest('section,form,dialog,[role="dialog"]')
                    && (candidate.compareDocumentPosition(header) & Node.DOCUMENT_POSITION_FOLLOWING);
            }).at(-1);
            if (path) {
                shellMain.insertBefore(path, contentFrame);
            }
            shellMain.insertBefore(header, contentFrame);
        });
    };
    promotePageHeadersToShell();
    const resolveStickyTitleSource = function () {
        return document.querySelector('[data-sticky-title-source]') || document.querySelector('.erp-page-title');
    };
    const updatePageTitleRail = function () {
        if (!pageTitleRail || !pageTitleRailText) {
            return;
        }
        const source = resolveStickyTitleSource();
        const breadcrumb = document.querySelector('.erp-page-header .erp-breadcrumb');
        const contentFrame = document.querySelector('.shell-content-frame');
        if (!source || !contentFrame) {
            pageTitleRail.classList.remove('is-active');
            pageTitleRailText.textContent = '';
            if (pageBreadcrumbRailText) {
                pageBreadcrumbRailText.textContent = '';
            }
            return;
        }
        const titleText = (source.getAttribute('data-sticky-title') || source.textContent || '').trim();
        const frameRect = contentFrame.getBoundingClientRect();
        if (!titleText) {
            pageTitleRail.classList.remove('is-active');
            pageTitleRailText.textContent = '';
            return;
        }
        document.documentElement.style.setProperty('--shell-page-title-left', Math.round(frameRect.left) + 'px');
        pageTitleRailText.textContent = titleText;
        if (pageBreadcrumbRailText) {
            const breadcrumbText = (breadcrumb?.textContent || '').trim();
            pageBreadcrumbRailText.textContent = breadcrumbText && breadcrumbText !== titleText
                ? breadcrumbText.replace(/\s*\/\s*/g, '  ›  ')
                : '';
        }
        pageTitleRail.classList.add('is-active');
    };
    const schedulePageTitleRailUpdate = function () {
        if (titleRailTicking) {
            return;
        }
        titleRailTicking = true;
        window.requestAnimationFrame(function () {
            titleRailTicking = false;
            updatePageTitleRail();
        });
    };

    const resolveToastContextElement = function (options) {
        if (!options || typeof options !== 'object') {
            return null;
        }
        const candidate = options.contextElement || options.sourceElement || options.target || options.modal;
        return candidate instanceof HTMLElement ? candidate : null;
    };

    const isToastModalOpen = function (modal) {
        if (!(modal instanceof HTMLElement)) {
            return false;
        }
        if (modal.classList.contains('app-modal-overlay')) {
            return isModalOpen(modal);
        }
        return modal.classList.contains('session-timeout-overlay')
            && !modal.classList.contains('hidden')
            && modal.getAttribute('aria-hidden') === 'false';
    };

    const findOpenToastModal = function () {
        const openModals = Array.from(document.querySelectorAll('.app-modal-overlay, .session-timeout-overlay'))
            .filter(isToastModalOpen);
        return openModals.length ? openModals[openModals.length - 1] : null;
    };

    const resolveToastModal = function (options) {
        if (options && (options.forcePage === true || options.placement === 'page')) {
            return null;
        }
        let modal = options && options.modal instanceof HTMLElement ? options.modal : null;
        const contextElement = resolveToastContextElement(options);
        if (!modal && contextElement) {
            modal = contextElement.closest('.app-modal-overlay, .session-timeout-overlay');
        }
        if ((!modal || !isToastModalOpen(modal)) && document.activeElement instanceof HTMLElement) {
            modal = document.activeElement.closest('.app-modal-overlay, .session-timeout-overlay');
        }
        if ((!modal || !isToastModalOpen(modal)) && options && options.preferModal === true) {
            modal = findOpenToastModal();
        }
        return modal && isToastModalOpen(modal) ? modal : null;
    };

    const updateModalToastOffset = function (panel, container) {
        const header = panel.querySelector('.app-modal-header, .session-timeout-header, .guarantor-financial-modal-header');
        const height = header instanceof HTMLElement ? Math.max(0, Math.ceil(header.getBoundingClientRect().height)) : 0;
        container.style.setProperty('--modal-toast-top', height + 'px');
    };

    const ensureModalToastContainer = function (modal) {
        const panel = modal.querySelector('.app-modal-panel, .session-timeout-panel, [role="dialog"], [role="alertdialog"]') || modal;
        let container = Array.from(panel.children).find(function (child) {
            return child instanceof HTMLElement && child.hasAttribute('data-modal-toast-container');
        });
        if (!container) {
            container = document.createElement('div');
            container.className = 'app-modal-toast-container';
            container.setAttribute('data-modal-toast-container', 'true');
            container.setAttribute('aria-live', 'polite');
            container.setAttribute('aria-atomic', 'false');
            panel.appendChild(container);
        }
        updateModalToastOffset(panel, container);
        return container;
    };

    const resolveToastState = function (options) {
        const modal = resolveToastModal(options);
        if (!modal) {
            return pageToastState;
        }
        const container = ensureModalToastContainer(modal);
        const existingState = modalToastStates.get(modal);
        if (existingState) {
            existingState.container = container;
            return existingState;
        }
        const state = {
            container: container,
            queue: [],
            visible: []
        };
        modalToastStates.set(modal, state);
        return state;
    };

    const clearToastState = function (state) {
        if (!state) {
            return;
        }
        state.queue.length = 0;
        state.visible.forEach(function (entry) {
            entry.toast.remove();
        });
        state.visible.length = 0;
    };

    const clearModalToasts = function (modal) {
        const state = modalToastStates.get(modal);
        clearToastState(state);
        modalToastStates.delete(modal);
        modal.querySelectorAll('[data-modal-toast-container]').forEach(function (container) {
            container.remove();
        });
    };

    const refreshToastLayers = function (state) {
        state.visible.forEach(function (entry, index) {
            const depth = Math.max(state.visible.length - 1 - index, 0);
            entry.toast.style.setProperty('--toast-depth', String(depth));
            entry.toast.style.zIndex = String(100 + index);
        });
    };

    const promoteQueuedToast = function (state) {
        while (state.visible.length < maxVisibleToasts && state.queue.length > 0) {
            const entry = state.queue.shift();
            state.visible.push(entry);
            state.container.appendChild(entry.toast);
            refreshToastLayers(state);
        }
    };

    window.showToast = function (type, message, options) {
        if (!message || !String(message).trim()) {
            return null;
        }
        const state = resolveToastState(options || {});
        if (!state.container) {
            return null;
        }
        const variant = type === 'error' ? 'error' : (type === 'success' ? 'success' : 'info');
        const toast = document.createElement('div');
        toast.className = 'app-toast-enter pointer-events-auto relative overflow-hidden rounded-xl border px-4 py-3 shadow-lg backdrop-blur-sm ' +
            (variant === 'success' ? 'app-toast-success' : variant === 'error' ? 'app-toast-error' : 'app-toast-info');
        toast.setAttribute('data-app-toast', 'true');
        toast.setAttribute('role', variant === 'error' ? 'alert' : 'status');
        toast.innerHTML =
            '<div class="flex items-start gap-3">' +
                '<div class="mt-0.5 shrink-0">' +
                    (variant === 'success'
                        ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/></svg>'
                        : variant === 'error'
                            ? '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm.75-11.5a.75.75 0 00-1.5 0v4.25a.75.75 0 001.5 0V6.5zm0 7a.75.75 0 00-1.5 0v.25a.75.75 0 001.5 0v-.25z" clip-rule="evenodd"/></svg>'
                            : '<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M18 10A8 8 0 112 10a8 8 0 0116 0zm-7.25-3.75a.75.75 0 10-1.5 0v.25a.75.75 0 001.5 0V6.25zm0 2.5a.75.75 0 00-1.5 0v5a.75.75 0 001.5 0v-5z" clip-rule="evenodd"/></svg>') +
                '</div>' +
                '<div class="min-w-0 flex-1 pr-6">' +
                    '<p class="text-sm font-semibold leading-5" data-toast-text></p>' +
                '</div>' +
                '<button type="button" class="app-toast-close" aria-label="Dismiss notification">' +
                    '<svg class="app-toast-close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>' +
                '</button>' +
            '</div>';
        toast.querySelector('[data-toast-text]').textContent = String(message);

        const entry = { toast: toast, dismiss: null };
        const dismiss = function () {
            if (!toast.isConnected || toast.classList.contains('app-toast-exit')) {
                const queuedIndex = state.queue.indexOf(entry);
                if (queuedIndex >= 0) {
                    state.queue.splice(queuedIndex, 1);
                }
                return;
            }
            toast.classList.remove('app-toast-enter');
            toast.classList.add('app-toast-exit');
            window.setTimeout(function () {
                toast.remove();
                const visibleIndex = state.visible.indexOf(entry);
                if (visibleIndex >= 0) {
                    state.visible.splice(visibleIndex, 1);
                }
                refreshToastLayers(state);
                promoteQueuedToast(state);
            }, 190);
        };
        entry.dismiss = dismiss;

        const closeButton = toast.querySelector('button');
        closeButton?.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            dismiss();
        });
        if (state.visible.length < maxVisibleToasts) {
            state.visible.push(entry);
            state.container.appendChild(toast);
            refreshToastLayers(state);
        } else {
            state.queue.push(entry);
        }
        return { dismiss: dismiss, element: toast };
    };

    const submissionLoading = new Map();
    const clearSubmissionLoading = function () {
        for (const [form,state] of submissionLoading) {
            state.finish?.();
            state.controls.forEach(control => {control.disabled = false;});
            submissionLoading.delete(form);
        }
    };
    const showSubmissionLoading = function (form,submitter) {
        if (submissionLoading.has(form)) return;
        const controls=[...form.querySelectorAll('button[type="submit"],button:not([type]),input[type="submit"]')].filter(control=>!control.disabled);
        const finish=window.AppLoading?.action(submitter || controls[0]);
        controls.forEach(control=>control.disabled=true);
        submissionLoading.set(form,{controls,finish});
    };

    const resolveDownloadFilename = function (response, target) {
        const disposition = response.headers.get('Content-Disposition') || '';
        const encodedFilename = disposition.match(/filename\*=UTF-8''([^;]+)/i);
        const plainFilename = disposition.match(/filename="?([^";]+)"?/i);
        let filename = encodedFilename ? encodedFilename[1] : (plainFilename ? plainFilename[1] : '');
        if (encodedFilename) {
            try {
                filename = decodeURIComponent(filename);
            } catch (ignored) {
            }
        }
        if (!filename) {
            filename = target.pathname.split('/').filter(Boolean).pop() || 'download';
        }
        filename = filename.trim().replace(/^["']|["']$/g, '');
        return filename.replace(/[\\/:*?"<>|]/g, '_');
    };

    const triggerBrowserDownload = function (blob, filename) {
        const objectUrl = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = objectUrl;
        anchor.download = filename;
        anchor.hidden = true;
        anchor.setAttribute('data-no-page-preloader', 'true');
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(function () {
            window.URL.revokeObjectURL(objectUrl);
        }, 1000);
    };

    const triggerBrowserPrint = function (blob, target, signal) {
        return new Promise(function (resolve, reject) {
            const objectUrl = window.URL.createObjectURL(blob);
            const frame = document.createElement('iframe');
            let cleaned = false;
            const cleanup = function () {
                if (cleaned) return;
                cleaned = true;
                signal.removeEventListener('abort', abort);
                frame.remove();
                window.URL.revokeObjectURL(objectUrl);
            };
            const abort = function () {
                cleanup();
                reject(new DOMException('Print preparation timed out', 'AbortError'));
            };
            signal.addEventListener('abort', abort, { once: true });
            frame.setAttribute('aria-hidden', 'true');
            frame.setAttribute('data-export-print-frame', 'true');
            frame.title = 'Print export';
            frame.style.cssText = 'position:fixed;right:0;bottom:0;width:1px;height:1px;border:0;opacity:0';
            frame.addEventListener('load', async function () {
                try {
                    if (!frame.contentWindow) throw new Error('Print frame is not available');
                    if (blob.type.includes('html')) await frame.contentDocument.fonts.ready;
                    if (signal.aborted) return;
                    frame.contentWindow.addEventListener('afterprint', cleanup, { once: true });
                    frame.contentWindow.focus();
                    frame.contentWindow.print();
                    signal.removeEventListener('abort', abort);
                    window.setTimeout(cleanup, exportPrintFrameCleanupDelayMs);
                    resolve();
                } catch (error) {
                    cleanup();
                    reject(error);
                }
            }, { once: true });
            frame.addEventListener('error', function () {
                cleanup();
                reject(new Error('Print document could not be loaded'));
            }, { once: true });
            if (blob.type.includes('html')) {
                blob.text().then(function (html) {
                    if (signal.aborted) return;
                    const printable = new DOMParser().parseFromString(html, 'text/html');
                    printable.querySelectorAll('script, base').forEach(function (node) { node.remove(); });
                    const base = printable.createElement('base');
                    base.href = target.toString();
                    printable.head.prepend(base);
                    frame.srcdoc = '<!DOCTYPE html>' + printable.documentElement.outerHTML;
                    document.body.appendChild(frame);
                }).catch(function (error) { cleanup(); reject(error); });
            } else {
                frame.src = objectUrl;
                document.body.appendChild(frame);
            }
        });
    };

    const downloadWithLocalLoading = async function (url, control = document.activeElement) {
        const target = new URL(url, window.location.href);
        if (target.origin !== window.location.origin) return;
        if (exportDownloadActive) {
            return;
        }
        exportDownloadActive = true;
        clearConsoleTableLoading();
        const finish=window.AppLoading?.action(control?.closest?.('.shell-main,[role="dialog"]') ? control : document.querySelector('.erp-page-title,h1'));
        const controller = new AbortController();
        let timedOut=false;
        const cancel=()=>controller.abort();
        window.addEventListener('pagehide',cancel,{once:true});
        const timeout = window.setTimeout(function () {
            timedOut=true;controller.abort();
        }, exportDownloadTimeoutMs);
        try {
            const response = await window.fetch(target.toString(), {
                credentials: 'same-origin',
                signal: controller.signal
            });
            const redirectedPath = response.redirected ? new URL(response.url, window.location.href).pathname : '';
            if (!response.ok || /^\/(?:login|auth)(?:\/|$)/.test(redirectedPath)) {
                throw new Error('Export request failed');
            }
            const blob = await response.blob();
            triggerBrowserDownload(blob, resolveDownloadFilename(response, target));
        } catch (error) {
            if(controller.signal.aborted && !timedOut)return;
            if (typeof window.showToast === 'function') {
                window.showToast('error', timedOut
                    ? 'The export took too long. Please try again.'
                    : 'The export could not be completed. Please try again.');
            }
        } finally {
            window.clearTimeout(timeout);
            window.removeEventListener('pagehide',cancel);
            exportDownloadActive = false;
            finish?.();
        }
    };

    const printWithLocalLoading = async function (url, control = document.activeElement) {
        const target = new URL(url, window.location.href);
        if (target.origin !== window.location.origin) return;
        if (exportPrintActive) {
            return;
        }
        exportPrintActive = true;
        clearConsoleTableLoading();
        const finish=window.AppLoading?.action(control?.closest?.('.shell-main,[role="dialog"]') ? control : document.querySelector('.erp-page-title,h1'));
        const controller = new AbortController();
        let timedOut=false;
        const cancel=()=>controller.abort();
        window.addEventListener('pagehide',cancel,{once:true});
        const timeout = window.setTimeout(function () {
            timedOut=true;controller.abort();
        }, exportDownloadTimeoutMs);
        try {
            const response = await window.fetch(target.toString(), {
                credentials: 'same-origin',
                signal: controller.signal
            });
            const redirectedPath = response.redirected ? new URL(response.url, window.location.href).pathname : '';
            if (!response.ok || /^\/(?:login|auth)(?:\/|$)/.test(redirectedPath)) {
                throw new Error('Print request failed');
            }
            const blob = await response.blob();
            await triggerBrowserPrint(blob, target, controller.signal);
        } catch (error) {
            if(controller.signal.aborted && !timedOut)return;
            if (typeof window.showToast === 'function') {
                window.showToast('error', timedOut
                    ? 'The print file took too long. Please try again.'
                    : 'The print preview could not be prepared. Please try again.');
            }
        } finally {
            window.clearTimeout(timeout);
            window.removeEventListener('pagehide',cancel);
            exportPrintActive = false;
            finish?.();
        }
    };

    window.printExport = printWithLocalLoading;
    window.downloadExport = downloadWithLocalLoading;

    const resolvePrintControlUrl = function (control) {
        const url = control?.getAttribute?.('data-print-url') || control?.getAttribute?.('href') || '';
        if (!url || url.startsWith('#') || url.startsWith('javascript:')) {
            return null;
        }
        const target = new URL(url, window.location.href);
        if (target.origin !== window.location.origin) {
            return null;
        }
        return target.toString();
    };

    const resolveExportFormUrl = function (form, submitter) {
        if (!form || (form.method && form.method.toLowerCase() !== 'get')) {
            return null;
        }
        const target = new URL(form.action || window.location.href, window.location.href);
        if (target.origin !== window.location.origin) {
            return null;
        }
        if (!isConsoleDownloadAction(submitter)
            && !/\.(?:csv|pdf|xlsx?|zip)$/i.test(target.pathname)) {
            return null;
        }
        target.search = '';
        new FormData(form).forEach(function (value, name) {
            if (typeof value === 'string') {
                target.searchParams.append(name, value);
            }
        });
        if (submitter && submitter.name && !target.searchParams.has(submitter.name)) {
            target.searchParams.append(submitter.name, submitter.value || '');
        }
        return target.toString();
    };

    const currentScrollRestorePath = function () {
        return window.location.pathname + window.location.search;
    };
    const currentScrollRestoreStorageKey = function () {
        return scrollRestoreStorageKeyPrefix + encodeURIComponent(window.location.pathname || '/');
    };
    const scrollRestorePathname = function (path) {
        try {
            return new URL(path || '/', window.location.origin).pathname;
        } catch (ignored) {
            return '';
        }
    };

    const scrollablePositionKey = function (element, index) {
        if (!element) {
            return '';
        }
        const explicitKey = element.getAttribute('data-view-position-key');
        if (explicitKey) {
            return 'view:' + explicitKey;
        }
        if (element.id) {
            return 'id:' + element.id;
        }
        const modal = element.closest('.app-modal-overlay');
        const modalIdentity = identifyModal(modal);
        if (modalIdentity) {
            return 'modal:' + modalIdentity.type + ':' + modalIdentity.value + ':' + index;
        }
        const role = element.classList.contains('erp-table-scroll')
            ? 'erp-table-scroll'
            : element.classList.contains('app-modal-scroll')
                ? 'app-modal-scroll'
                : element.classList.contains('shell-sidebar-scroll')
                    ? 'shell-sidebar-scroll'
                    : 'scrollable';
        return role + ':' + index;
    };

    const scrollablePositionSelector = '[data-view-position-key], [data-aws-persist-scroll], [data-fcms-persist-scroll], .erp-table-scroll, .app-modal-scroll, .shell-sidebar-scroll, .shell-main, .aws-filter-toolbar';

    const scrollableElements = function () {
        return Array.from(document.querySelectorAll(scrollablePositionSelector))
            .filter(function (element) {
                return element instanceof HTMLElement;
            });
    };

    const isScrollablePositionElement = function (element) {
        return element instanceof HTMLElement && element.matches(scrollablePositionSelector);
    };

    const canScrollVertically = function (element) {
        if (!(element instanceof HTMLElement)) {
            return false;
        }
        const style = window.getComputedStyle(element);
        return /(auto|scroll|overlay)/.test(style.overflowY || '')
            && element.scrollHeight - element.clientHeight > 1;
    };

    const canDocumentScrollVertically = function () {
        const scroller = document.scrollingElement || document.documentElement;
        return scroller && scroller.scrollHeight - scroller.clientHeight > 1;
    };

    const canScrollInDirection = function (element, deltaY) {
        if (element === document.scrollingElement || element === document.documentElement || element === document.body) {
            const currentTop = window.scrollY || window.pageYOffset || 0;
            const maxTop = Math.max((document.scrollingElement || document.documentElement).scrollHeight - window.innerHeight, 0);
            return deltaY < 0 ? currentTop > 0 : currentTop < maxTop - 1;
        }
        const maxTop = Math.max(element.scrollHeight - element.clientHeight, 0);
        return deltaY < 0 ? element.scrollTop > 0 : element.scrollTop < maxTop - 1;
    };

    const findVerticalScrollParent = function (element) {
        let parent = element ? element.parentElement : null;
        while (parent) {
            if (canScrollVertically(parent)) {
                return parent;
            }
            parent = parent.parentElement;
        }
        return canDocumentScrollVertically() ? (document.scrollingElement || document.documentElement) : null;
    };

    const normalizedWheelDeltaY = function (event) {
        if (!event) {
            return 0;
        }
        if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) {
            return event.deltaY * 16;
        }
        if (event.deltaMode === WheelEvent.DOM_DELTA_PAGE) {
            return event.deltaY * window.innerHeight;
        }
        return event.deltaY;
    };

    const handOffTableWheelScroll = function (event) {
        if (!event || event.defaultPrevented || event.ctrlKey) {
            return;
        }
        const target = event.target instanceof Element ? event.target.closest('.erp-table-scroll') : null;
        if (!(target instanceof HTMLElement)) {
            return;
        }
        const deltaY = normalizedWheelDeltaY(event);
        if (!deltaY || Math.abs(deltaY) < Math.abs(event.deltaX || 0)) {
            return;
        }
        const maxTop = Math.max(target.scrollHeight - target.clientHeight, 0);
        const atTop = target.scrollTop <= 1;
        const atBottom = target.scrollTop >= maxTop - 1;
        if (!((deltaY < 0 && atTop) || (deltaY > 0 && atBottom) || maxTop <= 1)) {
            return;
        }
        const parent = findVerticalScrollParent(target);
        if (!parent || parent === target || !canScrollInDirection(parent, deltaY)) {
            return;
        }
        event.preventDefault();
        if (parent === document.scrollingElement || parent === document.documentElement || parent === document.body) {
            window.scrollBy({ top: deltaY, left: 0, behavior: 'auto' });
        } else {
            parent.scrollTop += deltaY;
        }
        scheduleScrollStateSave();
    };

    const collectScrollablePositions = function () {
        return scrollableElements()
            .map(function (element, index) {
                return {
                    key: scrollablePositionKey(element, index),
                    left: element.scrollLeft || 0,
                    top: element.scrollTop || 0
                };
            })
            .filter(function (entry) {
                return entry.key && (entry.left > 0 || entry.top > 0);
            });
    };

    const restoreScrollablePositions = function (positions, behavior) {
        if (!Array.isArray(positions) || positions.length === 0) {
            return;
        }
        const byKey = new Map(positions.map(function (entry) {
            return [entry.key, entry];
        }));
        scrollableElements().forEach(function (element, index) {
            const entry = byKey.get(scrollablePositionKey(element, index));
            if (!entry) {
                return;
            }
            const targetLeft = Math.max(Number(entry.left) || 0, 0);
            const targetTop = Math.max(Number(entry.top) || 0, 0);
            if (typeof element.scrollTo === 'function') {
                element.scrollTo({
                    left: targetLeft,
                    top: targetTop,
                    behavior: behavior || 'auto'
                });
            } else {
                element.scrollLeft = targetLeft;
                element.scrollTop = targetTop;
            }
        });
    };

    const reMemberscrollForReload = function () {
        try {
            window.sessionStorage.setItem(currentScrollRestoreStorageKey(), JSON.stringify({
                path: currentScrollRestorePath(),
                pathname: window.location.pathname,
                x: window.scrollX || window.pageXOffset || 0,
                y: window.scrollY || window.pageYOffset || 0,
                containers: collectScrollablePositions(),
                at: Date.now()
            }));
        } catch (ignored) {
            // Storage can be unavailable in private browsing or strict browser modes.
        }
    };

    const shouldRestoreSavedScroll = function (saved) {
        if (!saved || Date.now() - Number(saved.at || 0) > restoreStateMaxAgeMs) {
            return false;
        }
        if (saved.path === currentScrollRestorePath()) {
            return true;
        }
        const savedPathname = saved.pathname || scrollRestorePathname(saved.path);
        return savedPathname === window.location.pathname;
    };

    const restoreScrollAfterReload = function () {
        let saved;
        try {
            saved = JSON.parse(
                window.sessionStorage.getItem(currentScrollRestoreStorageKey())
                || window.sessionStorage.getItem(legacyScrollRestoreStorageKey)
                || 'null'
            );
            window.sessionStorage.removeItem(legacyScrollRestoreStorageKey);
        } catch (ignored) {
            return;
        }
        if (!shouldRestoreSavedScroll(saved)) {
            return;
        }
        const targetX = Math.max(Number(saved.x) || 0, 0);
        const targetY = Math.max(Number(saved.y) || 0, 0);
        const behavior = !window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'auto';
        const restore = function () {
            window.scrollTo({ left: targetX, top: targetY, behavior: behavior });
            restoreScrollablePositions(saved.containers, behavior);
            schedulePageTitleRailUpdate();
        };
        window.requestAnimationFrame(function () {
            restore();
            window.setTimeout(restore, 120);
        });
    };

    const scheduleScrollStateSave = function () {
        if (scrollStateSaveTimer) {
            window.clearTimeout(scrollStateSaveTimer);
        }
        scrollStateSaveTimer = window.setTimeout(function () {
            scrollStateSaveTimer = null;
            reMemberscrollForReload();
        }, 120);
    };

    function identifyModal(modal) {
        if (!(modal instanceof HTMLElement)) {
            return null;
        }
        const dataKeys = Object.keys(modal.dataset || {});
        const modalKey = dataKeys.find(function (key) {
            return /Modal$/.test(key) && modal.dataset[key];
        });
        if (modalKey) {
            return {
                type: modalKey,
                value: modal.dataset[modalKey]
            };
        }
        if (modal.id) {
            return {
                type: 'id',
                value: modal.id
            };
        }
        return null;
    }

    const modalMatchesIdentity = function (modal, identity) {
        if (!identity || !(modal instanceof HTMLElement)) {
            return false;
        }
        if (identity.type === 'id') {
            return modal.id === identity.value;
        }
        return modal.dataset && modal.dataset[identity.type] === identity.value;
    };

    const findModalByIdentity = function (identity) {
        if (!identity) {
            return null;
        }
        if (identity.type === 'id') {
            return document.getElementById(identity.value);
        }
        return Array.from(document.querySelectorAll('.app-modal-overlay')).find(function (modal) {
            return modalMatchesIdentity(modal, identity);
        }) || null;
    };

    const isModalOpen = function (modal) {
        return modal instanceof HTMLElement
            && modal.classList.contains('app-modal-overlay')
            && (modal.classList.contains('is-open') || (modal.getAttribute('aria-hidden') === 'false' && !modal.classList.contains('hidden')));
    };

    const rememberOpenModal = function (modal) {
        const identity = identifyModal(modal);
        if (!identity) {
            return;
        }
        try {
            window.sessionStorage.setItem(modalRestoreStorageKey, JSON.stringify({
                path: currentScrollRestorePath(),
                modal: identity,
                at: Date.now()
            }));
        } catch (ignored) {
            // Storage can be unavailable in private browsing or strict browser modes.
        }
    };

    const clearOpenModal = function (modal) {
        try {
            if (!modal) {
                window.sessionStorage.removeItem(modalRestoreStorageKey);
                return;
            }
            const saved = JSON.parse(window.sessionStorage.getItem(modalRestoreStorageKey) || 'null');
            if (!saved || modalMatchesIdentity(modal, saved.modal)) {
                window.sessionStorage.removeItem(modalRestoreStorageKey);
            }
        } catch (ignored) {
            // Storage can be unavailable in private browsing or strict browser modes.
        }
    };

    const reMemberCurrentOpenModal = function () {
        const openModal = Array.from(document.querySelectorAll('.app-modal-overlay')).find(isModalOpen);
        if (openModal) {
            rememberOpenModal(openModal);
            return;
        }
        clearOpenModal();
    };

    const openModalElement = function (modal) {
        if (!(modal instanceof HTMLElement)) {
            return false;
        }
        modal.classList.remove('hidden');
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('overflow-hidden');
        rememberOpenModal(modal);
        return true;
    };

    const restoreModalAfterReload = function () {
        let saved;
        try {
            saved = JSON.parse(window.sessionStorage.getItem(modalRestoreStorageKey) || 'null');
        } catch (ignored) {
            return;
        }
        if (!saved || saved.path !== currentScrollRestorePath() || Date.now() - Number(saved.at || 0) > restoreStateMaxAgeMs) {
            clearOpenModal();
            return;
        }
        const modal = findModalByIdentity(saved.modal);
        if (modal) {
            openModalElement(modal);
        }
    };

    const observeModalState = function (modal) {
        if (!(modal instanceof HTMLElement) || modal.dataset.stateObserved === 'true') {
            return;
        }
        modal.dataset.stateObserved = 'true';
        modalStateObserver.observe(modal, {
            attributes: true,
            attributeFilter: ['class', 'aria-hidden']
        });
    };

    const modalStateObserver = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
            const modal = mutation.target;
            if (!(modal instanceof HTMLElement)) {
                return;
            }
            if (isModalOpen(modal)) {
                rememberOpenModal(modal);
            } else {
                clearOpenModal(modal);
                clearModalToasts(modal);
            }
        });
    });

    const observeExistingModals = function () {
        document.querySelectorAll('.app-modal-overlay').forEach(observeModalState);
        const openModal = Array.from(document.querySelectorAll('.app-modal-overlay')).find(isModalOpen);
        if (openModal) {
            rememberOpenModal(openModal);
        }
    };

    const observeAddedModals = function () {
        const bodyObserver = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                mutation.addedNodes.forEach(function (node) {
                    if (!(node instanceof HTMLElement)) {
                        return;
                    }
                    if (node.classList.contains('app-modal-overlay')) {
                        observeModalState(node);
                    }
                    node.querySelectorAll?.('.app-modal-overlay').forEach(observeModalState);
                });
            });
        });
        bodyObserver.observe(document.body, {
            childList: true,
            subtree: true
        });
    };

    const applyPlaceholderTitles = function () {
        document.querySelectorAll('input[placeholder], textarea[placeholder]').forEach(function (field) {
            const placeholder = field.getAttribute('placeholder');
            if (placeholder && !field.getAttribute('title')) {
                field.setAttribute('title', placeholder);
            }
        });
    };

    window.SaccosUiState = Object.assign({}, window.SaccosUiState, {
        reMemberscrollForReload: reMemberscrollForReload,
        rememberOpenModal: rememberOpenModal,
        clearOpenModal: clearOpenModal,
        restoreOpenModal: restoreModalAfterReload
    });

    observeExistingModals();
    observeAddedModals();
    applyPlaceholderTitles();

    document.addEventListener('submit', function (event) {
        const form = event.target instanceof HTMLFormElement ? event.target : null;
        if (!form || event.defaultPrevented) {
            return;
        }
        if (form.method && form.method.toLowerCase() === 'dialog') {
            return;
        }
        if (form.target && form.target !== '_self') {
            return;
        }
        if (typeof form.checkValidity === 'function' && !form.checkValidity()) {
            return;
        }
        const exportUrl = resolveExportFormUrl(form, event.submitter);
        if (exportUrl) {
            event.preventDefault();
            reMemberscrollForReload();
            if (isConsolePrintAction(event.submitter)) {
                void printWithLocalLoading(exportUrl,event.submitter);
            } else {
                void downloadWithLocalLoading(exportUrl,event.submitter);
            }
            return;
        }
        if (form.matches('[data-no-page-preloader="true"], [data-page-preloader="false"]')) {
            return;
        }
        window.setTimeout(function () {
            if (!event.defaultPrevented) {
                reMemberscrollForReload();
                showSubmissionLoading(form,event.submitter);
            }
        }, 0);
    });

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        const printControl = event.target instanceof Element ? event.target.closest('[data-print-action="true"]') : null;
        if (printControl && !printControl.matches('[target], [data-no-page-preloader="true"], [data-page-preloader="false"]')) {
            const printUrl = resolvePrintControlUrl(printControl);
            if (printUrl) {
                event.preventDefault();
                reMemberscrollForReload();
                void printWithLocalLoading(printUrl,printControl);
                return;
            }
        }
        const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
        if (!link || link.matches('[target], [data-no-page-preloader="true"], [data-page-preloader="false"]')) {
            return;
        }
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) {
            return;
        }
        const target = new URL(link.href, window.location.href);
        if (target.origin !== window.location.origin
            || (target.pathname === window.location.pathname && target.search === window.location.search && target.hash)) {
            return;
        }
        if (link.matches('[download], [data-download-action="true"]')
            || isConsoleDownloadAction(link)
            || /\.(?:csv|pdf|xlsx?)(?:$|\?)/i.test(target.pathname + target.search)) {
            event.preventDefault();
            reMemberscrollForReload();
            void downloadWithLocalLoading(target.toString(),link);
            return;
        }
        reMemberscrollForReload();
    });

    document.addEventListener('click', reMemberscrollForReload, { capture: true, passive: true });

    document.addEventListener('change', function (event) {
        const target = event.target;
        if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
            reMemberscrollForReload();
        }
    }, { capture: true });

    window.addEventListener('scroll', scheduleScrollStateSave, { passive: true });
    document.addEventListener('wheel', handOffTableWheelScroll, { capture: true, passive: false });
    document.addEventListener('scroll', function (event) {
        if (isScrollablePositionElement(event.target)) {
            scheduleScrollStateSave();
        }
    }, { capture: true, passive: true });

    window.addEventListener('pagehide', function () {
        reMemberscrollForReload();
        reMemberCurrentOpenModal();
    });
    window.addEventListener('beforeunload', function () {
        reMemberscrollForReload();
        reMemberCurrentOpenModal();
    });
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            reMemberscrollForReload();
            reMemberCurrentOpenModal();
        }
    });

    window.addEventListener('pageshow', clearSubmissionLoading);
    window.addEventListener('pagehide', clearSubmissionLoading);

    window.scrollToFeedback = function (element) {
        if (!element || !element.textContent || !element.textContent.trim()) {
            return;
        }
        const isHidden = element.classList && element.classList.contains('hidden');
        if (isHidden) {
            return;
        }
        const navHeightValue = getComputedStyle(document.documentElement).getPropertyValue('--shell-nav-height');
        const navHeight = Number.parseFloat(navHeightValue) || 58;
        window.requestAnimationFrame(function () {
            const rect = element.getBoundingClientRect();
            const top = Math.max(window.scrollY + rect.top - navHeight - 18, 0);
            window.scrollTo({ top: top, behavior: 'smooth' });
            element.setAttribute('tabindex', '-1');
            if (typeof element.focus === 'function') {
                try {
                    element.focus({ preventScroll: true });
                } catch (ignored) {
                    element.focus();
                }
            }
        });
    };

    const initSessionInactivityPrompt = function () {
        const prompt = document.getElementById('sessionInactivityPrompt');
        if (!prompt || prompt.dataset.sessionTimerInitialized === 'true' || !document.body) {
            return;
        }
        const stayButton = document.getElementById('sessionInactivityStayButton');
        const logoutForm = document.getElementById('sessionInactivityLogoutForm');
        const countdown = document.getElementById('sessionInactivityCountdown');
        const progress = document.getElementById('sessionInactivityProgress');
        const passwordInput = document.getElementById('sessionInactivityPassword');
        const errorBox = document.getElementById('sessionInactivityError');
        if (!stayButton || !logoutForm || !countdown || !progress || !passwordInput || !errorBox) {
            return;
        }

        const configuredTimeoutMs = Number(prompt.getAttribute('data-timeout-ms'));
        const configuredWarningMs = Number(prompt.getAttribute('data-warning-ms'));
        if (!Number.isFinite(configuredTimeoutMs) || configuredTimeoutMs <= 0) {
            return;
        }

        prompt.dataset.sessionTimerInitialized = 'true';
        if (prompt.parentElement !== document.body) {
            document.body.appendChild(prompt);
        }
        prompt.setAttribute('aria-hidden', 'true');

        const warningMs = Number.isFinite(configuredWarningMs) && configuredWarningMs > 0
            ? Math.max(configuredWarningMs, 1000)
            : 60000;
        const timeoutMs = Math.max(configuredTimeoutMs, warningMs + 1000);
        const progressShell = progress.parentElement;
        let checkTimer = null;
        let countdownTimer = null;
        let deadline = 0;
        let keepalivePending = false;
        let loggingOut = false;
        let previousFocus = null;
        let cycleStartedAt = Date.now();
        let audioContext = null;
        let audioUnlocked = false;
        let stopwatchTickTimer = null;
        let stopwatchTickCount = 0;

        const promptVisible = function () {
            return !prompt.classList.contains('hidden')
                && (prompt.classList.contains('is-open') || prompt.classList.contains('is-entering'));
        };

        const setProgress = function (percent) {
            const clamped = Math.max(0, Math.min(100, percent));
            progress.style.width = clamped.toFixed(1) + '%';
            progressShell?.setAttribute('aria-valuenow', String(Math.round(clamped)));
        };

        const setPromptError = function (message) {
            errorBox.textContent = message || '';
            errorBox.classList.toggle('hidden', !message);
        };

        const clearPromptError = function () {
            setPromptError('');
        };

        const stopStopwatchTicks = function () {
            if (stopwatchTickTimer) {
                window.clearInterval(stopwatchTickTimer);
                stopwatchTickTimer = null;
            }
        };

        const unlockSessionAudio = function () {
            if (audioUnlocked) {
                return;
            }
            const AudioContextConstructor = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextConstructor) {
                return;
            }
            try {
                if (!audioContext) {
                    audioContext = new AudioContextConstructor();
                }
                if (audioContext.state === 'suspended') {
                    audioContext.resume()
                        .then(function () {
                            audioUnlocked = true;
                        })
                        .catch(function () {});
                    return;
                }
                audioUnlocked = true;
            } catch (ignored) {
                audioContext = null;
            }
        };

        const playStopwatchTick = function () {
            const AudioContextConstructor = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextConstructor) {
                return;
            }
            try {
                if (!audioContext) {
                    audioContext = new AudioContextConstructor();
                }
                if (audioContext.state === 'suspended') {
                    audioContext.resume()
                        .then(function () {
                            audioUnlocked = true;
                            if (promptVisible()) {
                                playStopwatchTick();
                            }
                        })
                        .catch(function () {});
                    return;
                }
                audioUnlocked = true;
                const now = audioContext.currentTime;
                const duration = 0.055;
                const sampleRate = audioContext.sampleRate || 44100;
                const frameCount = Math.max(1, Math.ceil(sampleRate * duration));
                const noiseBuffer = audioContext.createBuffer(1, frameCount, sampleRate);
                const noiseData = noiseBuffer.getChannelData(0);
                const tickFrequency = stopwatchTickCount % 2 === 0 ? 2800 : 3400;
                const masterGain = audioContext.createGain();
                const compressor = audioContext.createDynamicsCompressor();
                const noise = audioContext.createBufferSource();
                const highpass = audioContext.createBiquadFilter();
                const bandpass = audioContext.createBiquadFilter();
                const noiseGain = audioContext.createGain();
                const clickOscillator = audioContext.createOscillator();
                const clickGain = audioContext.createGain();
                const recoilOscillator = audioContext.createOscillator();
                const recoilGain = audioContext.createGain();

                for (let index = 0; index < frameCount; index += 1) {
                    const fade = 1 - (index / frameCount);
                    noiseData[index] = (Math.random() * 2 - 1) * Math.pow(fade, 5);
                }

                compressor.threshold.setValueAtTime(-8, now);
                compressor.knee.setValueAtTime(0, now);
                compressor.ratio.setValueAtTime(18, now);
                compressor.attack.setValueAtTime(0.001, now);
                compressor.release.setValueAtTime(0.045, now);
                masterGain.gain.setValueAtTime(0.0001, now);
                masterGain.gain.exponentialRampToValueAtTime(0.98, now + 0.003);
                masterGain.gain.exponentialRampToValueAtTime(0.0001, now + duration + 0.035);

                highpass.type = 'highpass';
                highpass.frequency.setValueAtTime(1500, now);
                highpass.Q.setValueAtTime(0.9, now);
                bandpass.type = 'bandpass';
                bandpass.frequency.setValueAtTime(tickFrequency * 1.7, now);
                bandpass.Q.setValueAtTime(10, now);
                noiseGain.gain.setValueAtTime(0.9, now);

                clickOscillator.type = 'square';
                clickOscillator.frequency.setValueAtTime(tickFrequency, now);
                clickOscillator.frequency.exponentialRampToValueAtTime(tickFrequency * 1.45, now + 0.016);
                clickGain.gain.setValueAtTime(0.0001, now);
                clickGain.gain.exponentialRampToValueAtTime(1, now + 0.002);
                clickGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.032);

                recoilOscillator.type = 'triangle';
                recoilOscillator.frequency.setValueAtTime(tickFrequency * 1.9, now + 0.026);
                recoilOscillator.frequency.exponentialRampToValueAtTime(tickFrequency * 1.25, now + 0.07);
                recoilGain.gain.setValueAtTime(0.0001, now + 0.024);
                recoilGain.gain.exponentialRampToValueAtTime(0.55, now + 0.029);
                recoilGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.075);

                noise.buffer = noiseBuffer;
                noise.connect(highpass);
                highpass.connect(bandpass);
                bandpass.connect(noiseGain);
                noiseGain.connect(masterGain);
                clickOscillator.connect(clickGain);
                clickGain.connect(masterGain);
                recoilOscillator.connect(recoilGain);
                recoilGain.connect(masterGain);

                masterGain.connect(compressor);
                compressor.connect(audioContext.destination);
                noise.start(now);
                noise.stop(now + duration);
                clickOscillator.start(now);
                clickOscillator.stop(now + 0.04);
                recoilOscillator.start(now + 0.024);
                recoilOscillator.stop(now + 0.09);
                window.setTimeout(function () {
                    try {
                        noise.disconnect();
                        highpass.disconnect();
                        bandpass.disconnect();
                        noiseGain.disconnect();
                        clickOscillator.disconnect();
                        clickGain.disconnect();
                        recoilOscillator.disconnect();
                        recoilGain.disconnect();
                        masterGain.disconnect();
                        compressor.disconnect();
                    } catch (ignored) {}
                }, Math.ceil((duration + 0.12) * 1000));
                stopwatchTickCount += 1;
            } catch (ignored) {
                stopStopwatchTicks();
            }
        };

        const startStopwatchTicks = function () {
            stopStopwatchTicks();
            stopwatchTickCount = 0;
            playStopwatchTick();
            stopwatchTickTimer = window.setInterval(playStopwatchTick, 250);
        };

        const clearCountdown = function () {
            if (countdownTimer) {
                window.clearInterval(countdownTimer);
                countdownTimer = null;
            }
            stopStopwatchTicks();
        };

        const expireSession = function () {
            if (loggingOut) return;
            loggingOut = true;
            clearCountdown();
            if (checkTimer) window.clearTimeout(checkTimer);
            setStayPending(true);
            document.dispatchEvent(new Event('session:logout'));
            logoutForm.submit();
        };

        const updateCountdown = function () {
            const remainingMs = Math.max(deadline - Date.now(), 0);
            const remainingSeconds = Math.ceil(remainingMs / 1000);
            countdown.textContent = String(remainingSeconds);
            setProgress(warningMs > 0 ? (remainingMs / warningMs) * 100 : 0);
            if (remainingMs <= 0) {
                expireSession();
            }
        };

        const hidePrompt = function () {
            prompt.classList.add('hidden');
            prompt.classList.remove('flex');
            prompt.classList.remove('is-entering');
            prompt.classList.remove('is-open');
            prompt.setAttribute('aria-hidden', 'true');
            document.body.classList.remove('session-timeout-active');
            document.documentElement.classList.remove('session-timeout-active');
            clearCountdown();
            deadline = 0;
            countdown.textContent = '--';
            setProgress(100);
            passwordInput.value = '';
            clearPromptError();
            previousFocus?.focus({ preventScroll: true });
        };

        const showPrompt = function () {
            if (promptVisible()) {
                return;
            }
            if (checkTimer) {
                window.clearTimeout(checkTimer);
                checkTimer = null;
            }
            deadline = cycleDeadline();
            previousFocus = document.activeElement;
            prompt.classList.remove('hidden');
            prompt.classList.add('flex');
            prompt.classList.add('is-entering');
            prompt.classList.remove('is-open');
            prompt.setAttribute('aria-hidden', 'false');
            document.body.classList.add('session-timeout-active');
            document.documentElement.classList.add('session-timeout-active');
            updateCountdown();
            countdownTimer = window.setInterval(updateCountdown, 250);
            startStopwatchTicks();
            window.requestAnimationFrame(function () {
                window.requestAnimationFrame(function () {
                    if (!prompt.classList.contains('hidden')) {
                        prompt.classList.add('is-open');
                        prompt.classList.remove('is-entering');
                    }
                });
            });
            window.setTimeout(function () {
                try {
                    passwordInput.focus({ preventScroll: true });
                } catch (ignored) {
                    passwordInput.focus();
                }
            }, 0);
        };

        const keepaliveHeaders = function () {
            const headers = {
                'Accept': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            };
            const csrfHeader = prompt.getAttribute('data-csrf-header');
            const csrfToken = prompt.getAttribute('data-csrf-token');
            if (csrfHeader && csrfToken) {
                headers[csrfHeader] = csrfToken;
            }
            return headers;
        };

        const setStayPending = function (pending) {
            stayButton.disabled = pending;
            stayButton.classList.toggle('opacity-60', pending);
            stayButton.classList.toggle('cursor-not-allowed', pending);
            passwordInput.disabled = pending;
        };

        const finalCheckAt = function () {
            return cycleStartedAt + timeoutMs - warningMs;
        };

        const cycleDeadline = function () {
            return cycleStartedAt + timeoutMs;
        };

        const scheduleFinalMinuteCheck = function () {
            if (checkTimer) {
                window.clearTimeout(checkTimer);
            }
            const delayMs = Math.max(finalCheckAt() - Date.now(), 0);
            checkTimer = window.setTimeout(runFinalMinuteCheck, delayMs);
        };

        const startSessionCycle = function () {
            cycleStartedAt = Date.now();
            scheduleFinalMinuteCheck();
        };

        const refreshSession = function () {
            if (keepalivePending || loggingOut) {
                return;
            }
            if (Date.now() >= cycleDeadline()) {
                expireSession();
                return;
            }
            const password = passwordInput.value || '';
            if (!password.trim()) {
                setPromptError(prompt.getAttribute('data-password-required-message') || 'Enter your current password to continue.');
                passwordInput.focus();
                return;
            }
            keepalivePending = true;
            setStayPending(true);
            clearPromptError();
            const body = new URLSearchParams();
            body.set('password', password);
            fetch(prompt.getAttribute('data-keepalive-url') || '/session/keepalive', {
                method: 'POST',
                headers: Object.assign(keepaliveHeaders(), {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                }),
                credentials: 'same-origin',
                body: body.toString()
            })
                .then(function (response) {
                    return response.json()
                        .catch(function () {
                            return {};
                        })
                        .then(function (payload) {
                            if (!response.ok || response.redirected || payload.refreshed !== true) {
                                const message = response.status === 403 && payload.refreshed === false
                                    ? prompt.getAttribute('data-password-invalid-message') || payload.message
                                    : prompt.getAttribute('data-refresh-error-message');
                                const error = new Error(message || 'We could not refresh your session. Please sign in again.');
                                error.sessionMessage = true;
                                error.status = response.redirected ? 401 : response.status;
                                throw error;
                            }
                            return payload;
                        });
                })
                .then(function () {
                    if (loggingOut) return;
                    passwordInput.value = '';
                    hidePrompt();
                    startSessionCycle();
                    if (typeof window.showToast === 'function') {
                        window.showToast('success', prompt.getAttribute('data-refresh-success-message') || 'Session refreshed.');
                    }
                })
                .catch(function (error) {
                    if (loggingOut) return;
                    if (error && error.status === 401) {
                        expireSession();
                        return;
                    }
                    setPromptError(error && error.sessionMessage ? error.message : prompt.getAttribute('data-refresh-error-message'));
                    setStayPending(false);
                    try {
                        passwordInput.focus({ preventScroll: true });
                        passwordInput.select();
                    } catch (ignored) {
                        passwordInput.focus();
                    }
                })
                .finally(function () {
                    keepalivePending = false;
                    if (!loggingOut) setStayPending(false);
                });
        };

        function runFinalMinuteCheck() {
            if (checkTimer) {
                window.clearTimeout(checkTimer);
                checkTimer = null;
            }
            if (promptVisible()) {
                return;
            }
            const now = Date.now();
            if (now >= cycleDeadline()) {
                expireSession();
                return;
            }
            if (now < finalCheckAt()) {
                scheduleFinalMinuteCheck();
                return;
            }
            showPrompt();
        }

        const handleWake = function () {
            if (loggingOut) return;
            if (promptVisible()) {
                updateCountdown();
                return;
            }
            const now = Date.now();
            if (now >= cycleDeadline()) {
                expireSession();
                return;
            }
            if (now >= finalCheckAt()) {
                runFinalMinuteCheck();
                return;
            }
            scheduleFinalMinuteCheck();
        };

        ['click', 'keydown', 'mousedown', 'pointerdown', 'touchstart'].forEach(function (eventName) {
            window.addEventListener(eventName, unlockSessionAudio, { passive: true });
        });
        window.addEventListener('focus', function () {
            handleWake();
        }, { passive: true });
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) {
                handleWake();
            }
        });
        stayButton.addEventListener('click', function () {
            refreshSession();
        });
        passwordInput.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                refreshSession();
            }
        });
        logoutForm.addEventListener('submit', function (event) {
            event.preventDefault();
            event.stopPropagation();
            expireSession();
        });
        prompt.addEventListener('keydown', function (event) {
            if (event.key !== 'Tab') return;
            const controls = Array.from(prompt.querySelectorAll('input:not([type="hidden"]), button'))
                .filter(function (control) { return !control.disabled && control.tabIndex >= 0; });
            const first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault(); last?.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault(); first?.focus();
            }
        });
        document.addEventListener('focusin', function (event) {
            if (promptVisible() && !loggingOut && !prompt.contains(event.target)) {
                (passwordInput.disabled ? logoutForm.querySelector('button') : passwordInput).focus();
            }
        });
        startSessionCycle();
    };

    onReady( function () {
        syncShellNavHeight();
        initConsoleNavigationSearch();
        initUppercaseInputs();
        initAwsFilePickers(document);
        enhanceConsolePagination();
        enhanceConsoleTables();
        initAwsClientTables();
        syncConsoleFiltersFromUrl();
        initConsoleTableLoading();
        schedulePageTitleRailUpdate();
        applyPlaceholderTitles();
        restoreScrollAfterReload();
        restoreModalAfterReload();
        document.querySelectorAll('[data-toast-message]').forEach(function (element) {
            const message = element.getAttribute('data-toast-message');
            if (!message) {
                return;
            }
            window.showToast(
                element.getAttribute('data-toast-type') || 'info',
                message,
                { contextElement: element, preferModal: true }
            );
        });
        const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message]')).find(function (element) {
            return !element.classList.contains('hidden') && element.textContent && element.textContent.trim();
        });
        if (initialAlert) {
            window.scrollToFeedback(initialAlert);
        }
        initSessionInactivityPrompt();
    });

    window.addEventListener('resize', function () {
        syncShellNavHeight();
        schedulePageTitleRailUpdate();
    });
    window.addEventListener('scroll', schedulePageTitleRailUpdate, { passive: true });
    window.requestAnimationFrame(function () {
        syncShellNavHeight();
        schedulePageTitleRailUpdate();
    });
    initSessionInactivityPrompt();

    const notificationToggle = document.getElementById('notificationToggle');
    const notificationPanel = document.getElementById('notificationPanel');
    const notificationBadge = document.getElementById('notificationBadge');
    const notificationPanelBody = document.getElementById('notificationPanelBody');
    const notificationViewAll = document.getElementById('notificationViewAll');
    const profileToggle = document.getElementById('profileToggle');
    const profilePanel = document.getElementById('profilePanel');
    if (!notificationToggle || !notificationPanel || !profileToggle || !profilePanel) {
        return;
    }

    function closePanels() {
        notificationPanel.classList.remove('is-open');
        profilePanel.classList.remove('is-open');
        notificationToggle.setAttribute('aria-expanded', 'false');
        profileToggle.setAttribute('aria-expanded', 'false');
    }

    let notificationsLoaded = false;
    let notificationsLoading = false;

    function appendNotificationText(parent, tagName, className, text) {
        const node = document.createElement(tagName);
        node.className = className;
        node.textContent = text || '';
        parent.appendChild(node);
        return node;
    }

    function renderHeaderNotifications(payload) {
        notificationsLoaded = true;
        notificationViewAll.href = payload.targetUrl || '#';
        notificationPanelBody.innerHTML = '';
        const count = Number(payload.count || 0);
        if (count > 0) {
            notificationBadge.textContent = count > 99 ? '99+' : String(count);
            notificationBadge.classList.remove('hidden');
            notificationBadge.classList.add('inline-flex');
        } else {
            notificationBadge.classList.add('hidden');
            notificationBadge.classList.remove('inline-flex');
        }
        const notifications = Array.isArray(payload.notifications) ? payload.notifications : [];
        if (notifications.length === 0) {
            appendNotificationText(notificationPanelBody, 'div', 'erp-notification-empty', payload.emptyState || 'No notifications yet.');
            return;
        }
        notifications.forEach(function (notification) {
            const link = document.createElement('a');
            link.href = notification.href || payload.targetUrl || '#';
            link.className = 'erp-notification-item';
            const row = document.createElement('div');
            row.className = 'erp-notification-item-row';
            const content = document.createElement('div');
            content.className = 'erp-notification-content';
            appendNotificationText(content, 'p', 'erp-notification-subject', notification.subject);
            appendNotificationText(content, 'p', 'erp-notification-message', notification.message);
            row.appendChild(content);
            link.appendChild(row);
            const meta = document.createElement('div');
            meta.className = 'erp-notification-meta';
            appendNotificationText(meta, 'span', 'erp-notification-source', notification.source);
            appendNotificationText(meta, 'time', 'erp-notification-time', notification.createdAtLabel);
            link.appendChild(meta);
            notificationPanelBody.appendChild(link);
        });
    }

    function loadHeaderNotifications() {
        if (notificationsLoaded || notificationsLoading) {
            return;
        }
        notificationsLoading = true;
        fetch('/header/notifications', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'same-origin'
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Notification request failed');
                }
                return response.json();
            })
            .then(renderHeaderNotifications)
            .catch(function () {
                notificationPanelBody.innerHTML = '';
                appendNotificationText(notificationPanelBody, 'div', 'erp-notification-empty', 'Notifications are temporarily unavailable.');
            })
            .finally(function () {
                notificationsLoading = false;
            });
    }

    if (window.requestIdleCallback) {
        window.requestIdleCallback(loadHeaderNotifications, { timeout: 2500 });
    } else {
        window.setTimeout(loadHeaderNotifications, 1200);
    }

    notificationToggle.addEventListener('click', function (event) {
        event.stopPropagation();
        const willOpen = !notificationPanel.classList.contains('is-open');
        closePanels();
        if (willOpen) {
            loadHeaderNotifications();
            notificationPanel.classList.add('is-open');
            notificationToggle.setAttribute('aria-expanded', 'true');
        }
    });

    profileToggle.addEventListener('click', function (event) {
        event.stopPropagation();
        const willOpen = !profilePanel.classList.contains('is-open');
        closePanels();
        if (willOpen) {
            profilePanel.classList.add('is-open');
            profileToggle.setAttribute('aria-expanded', 'true');
        }
    });

    document.addEventListener('click', function (event) {
        const insideNotification = notificationPanel.contains(event.target) || notificationToggle.contains(event.target);
        const insideProfile = profilePanel.contains(event.target) || profileToggle.contains(event.target);
        if (!insideNotification && !insideProfile) {
            closePanels();
        }
    });

    onReady( function () {
        const adminScopeOptionsNode = document.getElementById('adminScopeOptionsData');
        const adminScopeSaccoSelect = document.getElementById('adminScopesaccoSelect');
        const adminScopeStationSelect = document.getElementById('adminScopestationSelect');
        if (!adminScopeOptionsNode || !adminScopeSaccoSelect || !adminScopeStationSelect) {
            return;
        }
        const adminScopeOptions = JSON.parse(adminScopeOptionsNode.textContent || '[]');
        const syncStationOptions = function () {
            const selectedSacco = adminScopeSaccoSelect.value;
            const scopeOption = adminScopeOptions.find(function (option) { return option.saccoId === selectedSacco; });
            const stations = scopeOption && Array.isArray(scopeOption.stationIds) ? scopeOption.stationIds : [];
            const currentStation = adminScopeStationSelect.value || window.currentAdminScopeStation || '';
            adminScopeStationSelect.innerHTML = '';
            stations.forEach(function (stationId) {
                const option = document.createElement('option');
                option.value = stationId;
                option.textContent = stationId;
                if (stationId === currentStation) {
                    option.selected = true;
                }
                adminScopeStationSelect.appendChild(option);
            });
            if (!stations.includes(currentStation) && stations.length > 0) {
                adminScopeStationSelect.value = stations[0];
            }
            if (typeof window.refreshEnhancedSelects === 'function') {
                window.refreshEnhancedSelects(adminScopeStationSelect.parentElement);
            }
        };
        adminScopeSaccoSelect.addEventListener('change', syncStationOptions);
        syncStationOptions();
    });
}());
