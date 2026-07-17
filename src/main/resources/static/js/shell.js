(function () {
    const shellTopNav = document.getElementById('shellTopNav');
    const toastContainer = document.getElementById('appToastContainer');
    const pageSubmitPreloader = document.getElementById('pageSubmitPreloader');
    const pageTitleRail = document.getElementById('shellPageTitleRail');
    const pageTitleRailText = document.getElementById('shellPageTitleRailText');
    let titleRailTicking = false;
    let pageSubmitPreloaderActive = false;
    const scrollRestoreStorageKey = 'saccos:restore-scroll';
    const modalRestoreStorageKey = 'saccos:open-modal';
    const restoreStateMaxAgeMs = 24 * 60 * 60 * 1000;
    const isAdminWorkspacePath = function (path) {
        return path === '/admin' || path.indexOf('/admin/') === 0;
    };
    const isAdminWorkspace = function () {
        return isAdminWorkspacePath(window.location.pathname || '');
    };
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
        const requestedDuration = Number(settings.duration);
        const duration = Number.isFinite(requestedDuration) && requestedDuration > 0
            ? Math.min(requestedDuration, 10000)
            : (variant === 'error' ? 5200 : 3600);
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
                    '<p class="text-sm font-semibold leading-5" data-toast-text></p>' +
                '</div>' +
                '<button type="button" class="app-toast-close" aria-label="Dismiss notification">' +
                    '<span aria-hidden="true">&times;</span>' +
                '</button>' +
            '</div>';
        toast.querySelector('[data-toast-text]').textContent = String(message);

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
        window.setTimeout(dismiss, duration);
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

    const currentScrollRestorePath = function () {
        return window.location.pathname + window.location.search;
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
        return Array.from(document.querySelectorAll('[data-view-position-key], .erp-table-scroll, .app-modal-scroll, .shell-sidebar-scroll'))
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
            window.sessionStorage.setItem(scrollRestoreStorageKey, JSON.stringify({
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
        return isAdminWorkspace()
            && isAdminWorkspacePath(savedPathname)
            && savedPathname === window.location.pathname;
    };

    const restoreScrollAfterReload = function () {
        let saved;
        try {
            saved = JSON.parse(window.sessionStorage.getItem(scrollRestoreStorageKey) || 'null');
            window.sessionStorage.removeItem(scrollRestoreStorageKey);
        } catch (ignored) {
            return;
        }
        if (!shouldRestoreSavedScroll(saved)) {
            return;
        }
        const targetX = Math.max(Number(saved.x) || 0, 0);
        const targetY = Math.max(Number(saved.y) || 0, 0);
        const behavior = isAdminWorkspace() && !window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'auto';
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

    document.addEventListener('click', function () {
        if (isAdminWorkspace()) {
            rememberScrollForReload();
        }
    }, { capture: true, passive: true });

    document.addEventListener('change', function (event) {
        if (!isAdminWorkspace()) {
            return;
        }
        const target = event.target;
        if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
            rememberScrollForReload();
        }
    }, { capture: true });

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

        const configuredPromptMs = Number(prompt.getAttribute('data-prompt-ms'));
        const configuredGraceMs = Number(prompt.getAttribute('data-grace-ms'));
        if (!Number.isFinite(configuredPromptMs) || configuredPromptMs <= 0) {
            return;
        }

        prompt.dataset.sessionTimerInitialized = 'true';
        if (prompt.parentElement !== document.body) {
            document.body.appendChild(prompt);
        }
        prompt.setAttribute('aria-hidden', 'true');

        const promptMs = Math.max(configuredPromptMs, 1000);
        const graceMs = Number.isFinite(configuredGraceMs) && configuredGraceMs > 0
            ? Math.max(configuredGraceMs, 1000)
            : 60000;
        const progressShell = progress.parentElement;
        const activityEvents = ['click', 'keydown', 'mousemove', 'touchstart', 'scroll'];
        let promptTimer = null;
        let countdownTimer = null;
        let deadline = 0;
        let keepalivePending = false;
        let audioContext = null;
        let audioUnlocked = false;
        let tickTimer = null;
        let tickCount = 0;

        const promptVisible = function () {
            return prompt.classList.contains('is-open') && !prompt.classList.contains('hidden');
        };

        const setProgress = function (percent) {
            const clamped = Math.max(0, Math.min(100, percent));
            progress.style.width = clamped.toFixed(1) + '%';
            progressShell?.setAttribute('aria-valuenow', String(Math.round(clamped)));
        };

        const stopTickTock = function () {
            if (tickTimer) {
                window.clearInterval(tickTimer);
                tickTimer = null;
            }
        };

        const unlockTickTockAudio = function () {
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

        const playTickTock = function () {
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
                            if (promptVisible()) {
                                playTickTock();
                            }
                        })
                        .catch(function () {});
                    return;
                }
                const now = audioContext.currentTime;
                const oscillator = audioContext.createOscillator();
                const filter = audioContext.createBiquadFilter();
                const gain = audioContext.createGain();
                const isTick = tickCount % 2 === 0;
                const duration = isTick ? 0.085 : 0.105;
                oscillator.type = 'square';
                oscillator.frequency.setValueAtTime(isTick ? 2400 : 1250, now);
                filter.type = 'bandpass';
                filter.frequency.setValueAtTime(isTick ? 1800 : 950, now);
                filter.Q.setValueAtTime(8, now);
                gain.gain.setValueAtTime(0.0001, now);
                gain.gain.exponentialRampToValueAtTime(isTick ? 0.12 : 0.1, now + 0.006);
                gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
                oscillator.connect(filter);
                filter.connect(gain);
                gain.connect(audioContext.destination);
                oscillator.start(now);
                oscillator.stop(now + duration + 0.01);
                tickCount += 1;
            } catch (ignored) {
                stopTickTock();
            }
        };

        const startTickTock = function () {
            stopTickTock();
            tickCount = 0;
            playTickTock();
            tickTimer = window.setInterval(playTickTock, 1000);
        };

        const clearCountdown = function () {
            if (countdownTimer) {
                window.clearInterval(countdownTimer);
                countdownTimer = null;
            }
            stopTickTock();
        };

        const expireSession = function () {
            clearCountdown();
            logoutForm.submit();
        };

        const updateCountdown = function () {
            const remainingMs = Math.max(deadline - Date.now(), 0);
            const remainingSeconds = Math.ceil(remainingMs / 1000);
            countdown.textContent = String(remainingSeconds);
            setProgress(graceMs > 0 ? (remainingMs / graceMs) * 100 : 0);
            if (remainingMs <= 0) {
                expireSession();
            }
        };

        const hidePrompt = function () {
            prompt.classList.add('hidden');
            prompt.classList.remove('flex');
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
            if (promptTimer) {
                window.clearTimeout(promptTimer);
                promptTimer = null;
            }
            deadline = Date.now() + graceMs;
            prompt.classList.remove('hidden');
            prompt.classList.add('flex');
            prompt.classList.add('is-open');
            prompt.setAttribute('aria-hidden', 'false');
            document.body.classList.add('session-timeout-active');
            document.documentElement.classList.add('session-timeout-active');
            updateCountdown();
            countdownTimer = window.setInterval(updateCountdown, 250);
            startTickTock();
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

        const schedulePrompt = function () {
            if (promptTimer) {
                window.clearTimeout(promptTimer);
            }
            promptTimer = window.setTimeout(showPrompt, promptMs);
        };

        const refreshSession = function () {
            if (keepalivePending) {
                return;
            }
            keepalivePending = true;
            setStayPending(true);
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
                    schedulePrompt();
                    if (typeof window.showToast === 'function') {
                        window.showToast('success', prompt.getAttribute('data-refresh-success-message') || 'Session refreshed.');
                    }
                })
                .catch(function () {
                    if (typeof window.showToast === 'function') {
                        window.showToast('error', prompt.getAttribute('data-refresh-error-message') || 'We could not refresh your session. Please sign in again if this continues.');
                    }
                })
                .finally(function () {
                    keepalivePending = false;
                    setStayPending(false);
                });
        };

        const handleActivity = function () {
            if (promptVisible()) {
                return;
            }
            schedulePrompt();
        };

        activityEvents.forEach(function (eventName) {
            window.addEventListener(eventName, handleActivity, { passive: true });
        });
        ['click', 'keydown', 'touchstart'].forEach(function (eventName) {
            window.addEventListener(eventName, unlockTickTockAudio, { passive: true });
        });
        stayButton.addEventListener('click', refreshSession);
        logoutForm.addEventListener('submit', clearCountdown);
        schedulePrompt();
    };

    window.addEventListener('load', function () {
        syncShellNavHeight();
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
