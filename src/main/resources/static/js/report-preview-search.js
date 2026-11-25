(() => {
    const selector = '[data-report-preview-results]';
    function ready() {
        const workspace = document.querySelector('[data-report-preview]');
        const input = workspace?.querySelector('[data-report-preview-search]');
        if (!input || input.dataset.ready) return;
        input.dataset.ready = 'true';
        let pending;
        const section = () => workspace.querySelector(selector);
        const invalidate = () => {
            pending?.abort();
            pending = null;
            window.AppLoading.end(section());
        };
        async function refresh(url) {
            invalidate();
            const request = new AbortController();
            pending = request;
            const current = section();
            window.AppLoading.begin(current, {table:false});
            try {
                const response = await fetch(url, {
                    credentials:'same-origin', cache:'no-store',
                    signal:AbortSignal.any([request.signal, AbortSignal.timeout(20000)])
                });
                if (!response.ok || response.redirected) throw new Error('Report preview unavailable');
                const next = new DOMParser().parseFromString(await response.text(), 'text/html').querySelector(selector);
                if (!next) throw new Error('Report preview unavailable');
                if (request.signal.aborted || !current.isConnected) return;
                window.AppLoading.end(current);
                const filters = current.querySelector('[data-table-filter-for]');
                if (filters) current.before(filters);
                current.replaceWith(next);
                history.replaceState(history.state, '', url);
                window.ResourceTable?.init(workspace);
            } catch (failure) {
                if (!request.signal.aborted && current.isConnected) {
                    window.AppLoading.fail(current, {label:input.dataset.error, retry:() => refresh(url)});
                }
            } finally {
                if (pending === request) pending = null;
            }
        }
        const search = () => {
            const url = new URL(location.href);
            url.searchParams.delete('page');
            const {reportCutoff,reportFrom,reportThrough}=section().dataset;
            if(reportCutoff)url.searchParams.set('cutoff',reportCutoff);
            if(reportFrom)url.searchParams.set('from',reportFrom);
            if(reportThrough)url.searchParams.set('through',reportThrough);
            const term = input.value.trim();
            if (term) url.searchParams.set('previewSearch', term);
            else url.searchParams.delete('previewSearch');
            void refresh(url);
        };
        const binding = window.AppSearch.bind(input, search, invalidate);
        workspace.addEventListener('click', event => {
            const link = event.target.closest('[data-report-preview-page]');
            if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            binding.cancel();
            void refresh(link.href);
        });
        window.addEventListener('pagehide', () => {binding.cancel();invalidate();}, {once:true});
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(ready);
    else document.addEventListener('DOMContentLoaded', ready, {once:true});
})();
