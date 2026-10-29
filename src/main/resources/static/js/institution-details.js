/* Data-dependent institution tabs retain the summary and live branch access form. */
(() => {
    'use strict';
    const ready = () => {
        const root = document.querySelector('[data-institution-details]');
        if (!root || root.dataset.tabsReady) return;
        root.dataset.tabsReady = 'true';
        const tabs = [...root.querySelectorAll('[data-institution-tab]')];
        const tablist = root.querySelector('[data-institution-tabs]');
        tablist.setAttribute('role', 'tablist');
        let pending;
        const panel = () => root.querySelector('[data-institution-section]');
        const clear = () => {
            panel().removeAttribute('aria-busy');
            panel().querySelector('.institution-section-status')?.remove();
        };
        const select = section => tabs.forEach(tab => {
            const active = tab.dataset.institutionTab === section;
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
            if (active) tab.setAttribute('aria-current', 'page');
            else tab.removeAttribute('aria-current');
        });
        const switchSection = async tab => {
            pending?.abort();
            clear();
            if (panel().dataset.institutionSection === tab.dataset.institutionTab) {
                pending = null;
                return;
            }
            const request = new AbortController();
            pending = request;
            const current = panel(), status = document.createElement('div');
            status.className = 'institution-section-status';
            status.setAttribute('role', 'status');
            status.setAttribute('aria-label', root.dataset.loading);
            const spinner = document.createElement('span');
            spinner.className = 'app-loading-spinner';
            spinner.dataset.loadingSize = 'large';
            spinner.setAttribute('aria-hidden', 'true');
            status.append(spinner);
            current.prepend(status);
            current.setAttribute('aria-busy', 'true');
            try {
                const response = await fetch(tab.href, {
                    credentials: 'same-origin', cache: 'no-store',
                    signal: AbortSignal.any([request.signal, AbortSignal.timeout(20000)])
                });
                if (!response.ok || response.redirected || !response.headers.get('Content-Type')?.includes('text/html')) throw new Error('Section unavailable');
                const page = new DOMParser().parseFromString(await response.text(), 'text/html');
                const next = page.querySelector('[data-institution-section]');
                if (!next || next.dataset.institutionSection !== tab.dataset.institutionTab) throw new Error('Section unavailable');
                if (request.signal.aborted || !root.isConnected) return;
                current.replaceWith(next);
                window.ResourceTable?.init(next);
                window.refreshEnhancedSelects?.(next);
                window.ConsoleResourceDetails?.init(next);
                select(tab.dataset.institutionTab);
                root.querySelector('[data-institution-scope] [name=section]').value = tab.dataset.institutionTab;
                const reset = root.querySelector('[data-institution-reset]'), resetUrl = new URL(reset.href);
                resetUrl.searchParams.set('section', tab.dataset.institutionTab);
                reset.href = resetUrl.href;
                history.replaceState(history.state, '', tab.href);
                document.dispatchEvent(new Event('resource-table:query'));
            } catch (error) {
                if (request.signal.aborted || !root.isConnected) return;
                current.removeAttribute('aria-busy');
                status.className = 'institution-section-status console-message mb-4';
                status.removeAttribute('aria-label');
                status.dataset.messageType = 'error';
                status.setAttribute('role', 'alert');
                status.tabIndex = -1;
                status.textContent = root.dataset.error + ' ';
                const retry = document.createElement('button');
                retry.type = 'button'; retry.className = 'app-btn btn-neutral';
                retry.textContent = root.dataset.retry;
                retry.addEventListener('click', () => void switchSection(tab));
                status.append(retry);
                window.ConsoleResourceDetails?.init(current);
                status.focus({preventScroll: true});
            } finally {
                if (pending === request) pending = null;
            }
        };
        tablist.addEventListener('click', event => {
            const tab = event.target.closest('[data-institution-tab]');
            if (!tab || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            void switchSection(tab);
        });
        tablist.addEventListener('keydown', event => {
            const index = tabs.indexOf(event.target);
            if (index < 0 || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
            event.preventDefault();
            const target = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowLeft' ? -1 : 1) + tabs.length) % tabs.length;
            tabs[target].focus();
            void switchSection(tabs[target]);
        });
        document.addEventListener('workspace:before-navigate', () => {pending?.abort(); clear();});
        window.addEventListener('pagehide', () => pending?.abort());
        select(panel().dataset.institutionSection);
    };
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else document.addEventListener('DOMContentLoaded', ready, {once: true});
})();
