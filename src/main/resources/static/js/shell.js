(function () {
    const shellTopNav = document.getElementById('shellTopNav');
    const toastContainer = document.getElementById('appToastContainer');
    const pageSubmitPreloader = document.getElementById('pageSubmitPreloader');
    const pageTitleRail = document.getElementById('shellPageTitleRail');
    const pageTitleRailText = document.getElementById('shellPageTitleRailText');
    const pageBreadcrumbRailText = document.getElementById('shellPageBreadcrumbRailText');
    const toastQueue = [];
    const visibleToasts = [];
    const maxVisibleToasts = 3;
    let titleRailTicking = false;
    let pageSubmitPreloaderActive = false;
    let transientPagePreloaderTimer = null;
    const legacyScrollRestoreStorageKey = 'saccos:restore-scroll';
    const scrollRestoreStorageKeyPrefix = 'saccos:restore-scroll:';
    const modalRestoreStorageKey = 'saccos:open-modal';
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
            'sacco registration': '/admin/saccos/registry',
            'sacco workspace': '/admin/scope/select',
            'saccos': '/admin/saccos',
            'settings & controls': '/admin/settings-controls',
            'sms usage': '/admin/sms-usage',
            'support': '/admin/support',
            'users & roles': '/admin/users'
        },
        'member workspace': {
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
        },
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
        if (root === 'member workspace') {
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
        if (!search || !sidebar) {
            return;
        }
        search.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter') {
                return;
            }
            event.preventDefault();
            const query = search.value.trim().toLocaleLowerCase();
            if (!query) {
                return;
            }
            const destination = Array.from(sidebar.querySelectorAll('a[href]')).find(function (link) {
                return !link.closest('[hidden]') && (link.textContent || '').trim().toLocaleLowerCase().includes(query);
            });
            if (destination) {
                window.location.assign(destination.href);
                return;
            }
            search.setCustomValidity('No matching navigation item was found.');
            search.reportValidity();
            window.setTimeout(function () {
                search.setCustomValidity('');
            }, 1800);
        });
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
        return pageTitle ? pageTitle + ' results' : 'Results ' + String(index + 1);
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
            || /^(export|print|csv|pdf|excel|download)/.test(label);
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
            return region.querySelector('table');
        });
        regions.forEach(function (region, index) {
            ensureConsoleTableScroller(region, index);
            region.setAttribute('data-aws-table-region', '');
            region.setAttribute('aria-busy', 'false');
            let titlebar = region.querySelector(':scope > .app-table-titlebar');
            const suppressTitlebar = region.getAttribute('data-aws-no-titlebar') === 'true';
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
                    refresh.setAttribute('aria-label', 'Refresh table');
                    refresh.appendChild(createConsoleIcon([
                        'M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5',
                        'M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5'
                    ]));
                    refresh.addEventListener('click', function () {
                        showConsoleTableLoading(refresh);
                        window.location.reload();
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
            if (control.closest('table')
                || control.closest('.app-table-titlebar')
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
    const clearConsoleTableLoading = function () {
        document.querySelectorAll('.erp-table-wrap.is-loading').forEach(function (region) {
            region.classList.remove('is-loading');
            region.setAttribute('aria-busy', 'false');
            region.querySelectorAll('.aws-table-loader').forEach(function (loader) {
                loader.remove();
            });
        });
    };
    const showConsoleTableLoading = function (formOrControl) {
        if (!formOrControl) {
            return;
        }
        const nearbyRegion = formOrControl.closest('.erp-table-wrap')
            || formOrControl.parentElement?.querySelector('.erp-table-wrap')
            || document.querySelector('.erp-table-wrap');
        if (!nearbyRegion || nearbyRegion.classList.contains('is-loading')) {
            return;
        }
        nearbyRegion.classList.add('is-loading');
        nearbyRegion.setAttribute('aria-busy', 'true');
        hidePageSubmitPreloader();
        const loader = document.createElement('div');
        loader.className = 'aws-table-loader';
        loader.setAttribute('role', 'status');
        const spinner = document.createElement('span');
        spinner.className = 'aws-table-loader__spinner';
        spinner.setAttribute('aria-hidden', 'true');
        const label = document.createElement('span');
        label.textContent = nearbyRegion.getAttribute('data-loading-label') || 'Loading results...';
        loader.append(spinner, label);
        nearbyRegion.appendChild(loader);
    };
    const initConsoleTableLoading = function () {
        clearConsoleTableLoading();
        document.querySelectorAll('form[method="get"], form:not([method])').forEach(function (form) {
            form.addEventListener('submit', function () {
                showConsoleTableLoading(form);
            });
        });
        document.querySelectorAll('.erp-table-wrap a[href], .erp-table-toolbar a[href], .aws-pagination-chevron[href]').forEach(function (link) {
            link.addEventListener('click', function () {
                if (isConsoleDownloadAction(link)) {
                    clearConsoleTableLoading();
                    return;
                }
                showConsoleTableLoading(link);
            });
        });
        window.addEventListener('pagehide', clearConsoleTableLoading);
        window.addEventListener('pageshow', function () {
            clearConsoleTableLoading();
            syncConsoleFiltersFromUrl();
            window.requestAnimationFrame(clearConsoleTableLoading);
        });
        window.addEventListener('popstate', function () {
            clearConsoleTableLoading();
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
            segments.forEach(function (segment, index) {
                if (index > 0) {
                    const separator = document.createElement('span');
                    separator.className = 'erp-page-path__separator';
                    separator.setAttribute('aria-hidden', 'true');
                    separator.textContent = '>';
                    path.appendChild(separator);
                }
                const href = resolveBreadcrumbHref(segment, index, segments);
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
            const path = header.previousElementSibling?.classList.contains('erp-page-path')
                ? header.previousElementSibling
                : null;
            if (path && path.parentElement === contentFrame) {
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

    const refreshToastLayers = function () {
        visibleToasts.forEach(function (entry, index) {
            const depth = Math.max(visibleToasts.length - 1 - index, 0);
            entry.toast.style.setProperty('--toast-depth', String(depth));
            entry.toast.style.zIndex = String(100 + index);
        });
    };

    const promoteQueuedToast = function () {
        while (visibleToasts.length < maxVisibleToasts && toastQueue.length > 0) {
            const entry = toastQueue.shift();
            visibleToasts.push(entry);
            toastContainer.appendChild(entry.toast);
            refreshToastLayers();
        }
    };

    window.showToast = function (type, message, options) {
        if (!toastContainer || !message || !String(message).trim()) {
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
                const queuedIndex = toastQueue.indexOf(entry);
                if (queuedIndex >= 0) {
                    toastQueue.splice(queuedIndex, 1);
                }
                return;
            }
            toast.classList.remove('app-toast-enter');
            toast.classList.add('app-toast-exit');
            window.setTimeout(function () {
                toast.remove();
                const visibleIndex = visibleToasts.indexOf(entry);
                if (visibleIndex >= 0) {
                    visibleToasts.splice(visibleIndex, 1);
                }
                refreshToastLayers();
                promoteQueuedToast();
            }, 190);
        };
        entry.dismiss = dismiss;

        const closeButton = toast.querySelector('button');
        closeButton?.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            dismiss();
        });
        if (visibleToasts.length < maxVisibleToasts) {
            visibleToasts.push(entry);
            toastContainer.appendChild(toast);
            refreshToastLayers();
        } else {
            toastQueue.push(entry);
        }
        return { dismiss: dismiss, element: toast };
    };

    const hidePageSubmitPreloader = function () {
        if (transientPagePreloaderTimer) {
            window.clearTimeout(transientPagePreloaderTimer);
            transientPagePreloaderTimer = null;
        }
        pageSubmitPreloaderActive = false;
        document.documentElement.classList.remove('page-submit-preloader-active');
        document.body.removeAttribute('aria-busy');
        if (pageSubmitPreloader) {
            pageSubmitPreloader.classList.remove('is-active');
            pageSubmitPreloader.setAttribute('aria-hidden', 'true');
        }
    };

    const showPageSubmitPreloader = function (form) {
        if (!pageSubmitPreloader || pageSubmitPreloaderActive) {
            return;
        }
        const regionalLoaderActive = document.querySelector('.erp-table-wrap.is-loading, [data-aws-table-region][aria-busy="true"]');
        const regionalForm = form && (
            form.matches('form[method="get"], form:not([method]), [data-aws-filter-toolbar], .aws-filter-toolbar, .admin-filter-form, .loan-analytics-filter, .staff-analytics-filter')
            || form.closest('[data-aws-table-region], .erp-table-wrap')
            || form.parentElement?.querySelector('[data-aws-table-region], .erp-table-wrap')
        );
        if (regionalLoaderActive || regionalForm) {
            return;
        }
        pageSubmitPreloaderActive = true;
        document.body.setAttribute('aria-busy', 'true');
        pageSubmitPreloader.classList.add('is-active');
        pageSubmitPreloader.setAttribute('aria-hidden', 'false');
        if (form) {
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (control) {
                control.disabled = true;
                control.setAttribute('aria-disabled', 'true');
                control.classList.add('opacity-60', 'cursor-not-allowed');
            });
        }
    };

    const showTransientDownloadPreloader = function () {
        if (!pageSubmitPreloader) {
            return;
        }
        if (transientPagePreloaderTimer) {
            window.clearTimeout(transientPagePreloaderTimer);
        }
        showPageSubmitPreloader(null);
        transientPagePreloaderTimer = window.setTimeout(hidePageSubmitPreloader, 1800);
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

    const scrollableElements = function () {
        return Array.from(document.querySelectorAll('[data-view-position-key], [data-aws-persist-scroll], [data-fcms-persist-scroll], .erp-table-scroll, .app-modal-scroll, .shell-sidebar-scroll, .shell-main, .aws-filter-toolbar'))
            .filter(function (element) {
                return element instanceof HTMLElement;
            });
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

    const rememberScrollForReload = function () {
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
            rememberScrollForReload();
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

    const rememberCurrentOpenModal = function () {
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
        rememberScrollForReload: rememberScrollForReload,
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
        if (form.matches('[data-no-page-preloader="true"], [data-page-preloader="false"]')) {
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
        window.setTimeout(function () {
            if (!event.defaultPrevented) {
                rememberScrollForReload();
                showPageSubmitPreloader(form);
            }
        }, 0);
    });

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
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
            rememberScrollForReload();
            window.setTimeout(function () {
                if (!event.defaultPrevented) {
                    showTransientDownloadPreloader();
                }
            }, 0);
            return;
        }
        rememberScrollForReload();
        window.setTimeout(function () {
            if (!event.defaultPrevented) {
                showPageSubmitPreloader(null);
            }
        }, 0);
    });

    document.addEventListener('click', rememberScrollForReload, { capture: true, passive: true });

    document.addEventListener('change', function (event) {
        const target = event.target;
        if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
            rememberScrollForReload();
        }
    }, { capture: true });

    window.addEventListener('scroll', scheduleScrollStateSave, { passive: true });
    document.addEventListener('scroll', function (event) {
        if (event.target instanceof HTMLElement && scrollableElements().includes(event.target)) {
            scheduleScrollStateSave();
        }
    }, { capture: true, passive: true });

    window.addEventListener('pagehide', function () {
        rememberScrollForReload();
        rememberCurrentOpenModal();
    });
    window.addEventListener('beforeunload', function () {
        rememberScrollForReload();
        rememberCurrentOpenModal();
    });
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            rememberScrollForReload();
            rememberCurrentOpenModal();
        }
    });

    window.addEventListener('pageshow', hidePageSubmitPreloader);

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
        if (!stayButton || !logoutForm || !countdown || !progress) {
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
        const activityEvents = ['click', 'keydown', 'mousedown', 'mousemove', 'pointerdown', 'touchstart', 'scroll', 'wheel'];
        let checkTimer = null;
        let countdownTimer = null;
        let deadline = 0;
        let keepalivePending = false;
        let cycleStartedAt = Date.now();
        let activityInCycle = false;
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
            clearCountdown();
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
        };

        const showPrompt = function () {
            if (promptVisible()) {
                return;
            }
            if (checkTimer) {
                window.clearTimeout(checkTimer);
                checkTimer = null;
            }
            deadline = Date.now() + warningMs;
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
                    stayButton.focus({ preventScroll: true });
                } catch (ignored) {
                    stayButton.focus();
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
            activityInCycle = false;
            scheduleFinalMinuteCheck();
        };

        const refreshSession = function (silent) {
            if (keepalivePending) {
                return;
            }
            keepalivePending = true;
            if (!silent) {
                setStayPending(true);
            }
            fetch(prompt.getAttribute('data-keepalive-url') || '/session/keepalive', {
                method: 'POST',
                headers: keepaliveHeaders(),
                credentials: 'same-origin'
            })
                .then(function (response) {
                    if (!response.ok || response.redirected) {
                        throw new Error('Session keepalive failed');
                    }
                    hidePrompt();
                    startSessionCycle();
                    if (!silent && typeof window.showToast === 'function') {
                        window.showToast('success', prompt.getAttribute('data-refresh-success-message') || 'Session refreshed.');
                    }
                })
                .catch(function () {
                    if (silent && !promptVisible()) {
                        showPrompt();
                    }
                    if (!silent && typeof window.showToast === 'function') {
                        window.showToast('error', prompt.getAttribute('data-refresh-error-message') || 'We could not refresh your session. Please sign in again if this continues.');
                    }
                })
                .finally(function () {
                    keepalivePending = false;
                    if (!silent) {
                        setStayPending(false);
                    }
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
            if (activityInCycle) {
                refreshSession(true);
                return;
            }
            showPrompt();
        }

        const handleActivity = function () {
            if (promptVisible()) {
                return;
            }
            activityInCycle = true;
        };

        const handleWake = function () {
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

        activityEvents.forEach(function (eventName) {
            window.addEventListener(eventName, handleActivity, { passive: true });
        });
        ['click', 'keydown', 'mousedown', 'pointerdown', 'touchstart'].forEach(function (eventName) {
            window.addEventListener(eventName, unlockSessionAudio, { passive: true });
        });
        window.addEventListener('focus', function () {
            handleActivity();
            handleWake();
        }, { passive: true });
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) {
                handleWake();
            }
        });
        stayButton.addEventListener('click', function () {
            refreshSession(false);
        });
        logoutForm.addEventListener('submit', clearCountdown);
        startSessionCycle();
    };

    window.addEventListener('load', function () {
        syncShellNavHeight();
        initConsoleNavigationSearch();
        initUppercaseInputs();
        initAwsFilePickers(document);
        enhanceConsolePagination();
        enhanceConsoleTables();
        syncConsoleFiltersFromUrl();
        initConsoleTableLoading();
        schedulePageTitleRailUpdate();
        applyPlaceholderTitles();
        document.querySelectorAll('[data-toast-message]').forEach(function (element) {
            const message = element.getAttribute('data-toast-message');
            if (!message) {
                return;
            }
            window.showToast(
                element.getAttribute('data-toast-type') || 'info',
                message,
                {}
            );
        });
        const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message]')).find(function (element) {
            return !element.classList.contains('hidden') && element.textContent && element.textContent.trim();
        });
        if (initialAlert) {
            window.scrollToFeedback(initialAlert);
        }
        restoreScrollAfterReload();
        restoreModalAfterReload();
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
    const notificationPanelSubtitle = document.getElementById('notificationPanelSubtitle');
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
        notificationPanelSubtitle.textContent = payload.subtitle || '';
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
            link.className = 'block rounded-md border border-slate-300 bg-slate-50 px-3 py-3 transition hover:border-slate-300 hover:bg-slate-50';
            const row = document.createElement('div');
            row.className = 'flex items-start justify-between gap-3';
            const content = document.createElement('div');
            appendNotificationText(content, 'p', 'text-sm font-semibold text-slate-800', notification.subject);
            appendNotificationText(content, 'p', 'mt-1 text-xs text-slate-600', notification.message);
            row.appendChild(content);
            appendNotificationText(row, 'span', 'shrink-0 rounded-sm border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-600', notification.source);
            link.appendChild(row);
            appendNotificationText(link, 'p', 'mt-2 text-[11px] text-slate-400', notification.createdAtLabel);
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
        }
    });

    profileToggle.addEventListener('click', function (event) {
        event.stopPropagation();
        const willOpen = !profilePanel.classList.contains('is-open');
        closePanels();
        if (willOpen) {
            profilePanel.classList.add('is-open');
        }
    });

    document.addEventListener('click', function (event) {
        const insideNotification = notificationPanel.contains(event.target) || notificationToggle.contains(event.target);
        const insideProfile = profilePanel.contains(event.target) || profileToggle.contains(event.target);
        if (!insideNotification && !insideProfile) {
            closePanels();
        }
    });

    window.addEventListener('load', function () {
        const adminScopeOptionsNode = document.getElementById('adminScopeOptionsData');
        const adminScopeSaccoSelect = document.getElementById('adminScopeSaccoSelect');
        const adminScopeStationSelect = document.getElementById('adminScopeStationSelect');
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
