(function () {
    const shellTopNav = document.getElementById('shellTopNav');
    const toastContainer = document.getElementById('appToastContainer');
    const pageSubmitPreloader = document.getElementById('pageSubmitPreloader');
    const pageTitleRail = document.getElementById('shellPageTitleRail');
    const pageTitleRailText = document.getElementById('shellPageTitleRailText');
    let titleRailTicking = false;
    let pageSubmitPreloaderActive = false;
    if (toastContainer && toastContainer.parentElement !== document.body) {
        document.body.appendChild(toastContainer);
    }
    const syncShellNavHeight = function () {
        if (!shellTopNav) {
            return;
        }
        const measuredHeight = Math.max(Math.ceil(shellTopNav.getBoundingClientRect().height), 58);
        document.documentElement.style.setProperty('--shell-nav-height', measuredHeight + 'px');
    };
    const resolveStickyTitleSource = function () {
        return document.querySelector('[data-sticky-title-source]') || document.querySelector('.erp-page-title');
    };
    const updatePageTitleRail = function () {
        if (!pageTitleRail || !pageTitleRailText) {
            return;
        }
        const source = resolveStickyTitleSource();
        const contentFrame = document.querySelector('.shell-content-frame');
        if (!source || !contentFrame) {
            pageTitleRail.classList.remove('is-active');
            pageTitleRailText.textContent = '';
            return;
        }
        const navHeight = Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--shell-nav-height')) || 58;
        const titleText = (source.getAttribute('data-sticky-title') || source.textContent || '').trim();
        const sourceRect = source.getBoundingClientRect();
        const frameRect = contentFrame.getBoundingClientRect();
        const shouldShow = sourceRect.top <= navHeight;
        if (!titleText || !shouldShow) {
            pageTitleRail.classList.remove('is-active');
            pageTitleRailText.textContent = '';
            return;
        }
        document.documentElement.style.setProperty('--shell-page-title-left', Math.round(frameRect.left) + 'px');
        pageTitleRailText.textContent = titleText;
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

    window.showToast = function (type, message, options) {
        if (!toastContainer || !message || !String(message).trim()) {
            return null;
        }
        const settings = options || {};
        const variant = type === 'error' ? 'error' : (type === 'success' ? 'success' : 'info');
        const modalOpen = Boolean(document.querySelector('.app-modal-overlay.is-open'));
        const duration = Number.isFinite(settings.duration)
            ? settings.duration
            : (variant === 'error' && modalOpen ? 0 : (variant === 'error' ? 5200 : 3600));
        const toast = document.createElement('div');
        toast.className = 'app-toast-enter pointer-events-auto relative overflow-hidden rounded-xl border px-4 py-3 shadow-lg backdrop-blur-sm ' +
            (variant === 'success' ? 'app-toast-success' : variant === 'error' ? 'app-toast-error' : 'app-toast-info');
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
                    '<p class="text-sm font-semibold leading-5">' + String(message) + '</p>' +
                '</div>' +
                '<button type="button" class="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-lg text-current/70 transition hover:bg-black/5 hover:text-current" aria-label="Dismiss notification">' +
                    '<svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M4.22 4.22a.75.75 0 011.06 0L10 8.94l4.72-4.72a.75.75 0 011.06 1.06L11.06 10l4.72 4.72a.75.75 0 11-1.06 1.06L10 11.06l-4.72 4.72a.75.75 0 11-1.06-1.06L8.94 10 4.22 5.28a.75.75 0 010-1.06z" clip-rule="evenodd"/></svg>' +
                '</button>' +
            '</div>';

        const dismiss = function () {
            if (!toast.isConnected || toast.classList.contains('app-toast-exit')) {
                return;
            }
            toast.classList.remove('app-toast-enter');
            toast.classList.add('app-toast-exit');
            window.setTimeout(function () {
                toast.remove();
            }, 190);
        };

        const closeButton = toast.querySelector('button');
        closeButton?.addEventListener('click', dismiss);
        toastContainer.appendChild(toast);

        if (duration > 0) {
            window.setTimeout(dismiss, duration);
        }
        return { dismiss: dismiss, element: toast };
    };

    const hidePageSubmitPreloader = function () {
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
                showPageSubmitPreloader(form);
            }
        }, 0);
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

    window.addEventListener('load', function () {
        syncShellNavHeight();
        schedulePageTitleRailUpdate();
        document.querySelectorAll('[data-toast-message]').forEach(function (element) {
            const message = element.getAttribute('data-toast-message');
            if (!message) {
                return;
            }
            window.showToast(
                element.getAttribute('data-toast-type') || 'info',
                message,
                { duration: element.getAttribute('data-toast-persist') === 'true' ? 0 : undefined }
            );
        });
        const initialAlert = Array.from(document.querySelectorAll('[data-auto-scroll-message]')).find(function (element) {
            return !element.classList.contains('hidden') && element.textContent && element.textContent.trim();
        });
        if (initialAlert) {
            window.scrollToFeedback(initialAlert);
        }
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
