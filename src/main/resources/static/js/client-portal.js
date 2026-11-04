(() => {
    'use strict';

    function ready() {
        const institution = document.querySelector('[data-portal-institution]');
        const branch = document.querySelector('[data-portal-branch]');
        if (institution && branch && window.NeoSelect && window.AppSearch) {
            const root = document.querySelector('meta[name="app-context-path"]').content;
            bindChoices(institution, root);
            const resetBranches = bindChoices(branch, root, institution);
            let institutionId = institution.value;
            institution.addEventListener('change', () => {
                if (institution.value === institutionId) return;
                institutionId = institution.value;
                resetBranches();
            });
        }
        document.body.classList.add('portal-ready');
    }

    function bindChoices(select, root, parent) {
        const picker = window.NeoSelect.search(select);
        const {input, more} = picker;
        const placeholder = select.options[0];
        let controller, loadedSearch, page = 0;
        const searchValue = () => input.value.trim();
        const cancelRequest = () => {
            controller?.abort();
            controller = undefined;
            picker.setLoading(false);
            more.disabled = false;
        };

        async function load(targetPage = 0) {
            if (parent && !parent.value) return;
            controller?.abort();
            const request = new AbortController();
            controller = request;
            const search = searchValue();
            const normalized = search.toLocaleLowerCase();
            const query = new URLSearchParams({search, page: targetPage});
            if (parent) query.set('institution', parent.value);
            more.disabled = true;
            if (!targetPage) more.hidden = true;
            picker.setLoading(true);
            try {
                const response = await fetch(root + '/client-portal/choices?' + query, {
                    headers: {Accept: 'application/json'}, signal: request.signal
                });
                if (!response.ok || response.redirected) throw new Error('Lookup failed');
                const rows = await response.json();
                if (controller !== request || !select.isConnected) return;
                const selected = select.selectedOptions[0];
                const value = select.value;
                const previous = targetPage ? [...select.options].filter(option => option.value && !option.hidden) : [];
                const options = new Map(previous.map(option => [option.value, option]));
                rows.slice(0, 25).forEach(row => {
                    const option = new Option(row.label, row.id);
                    option.dataset.searchQuery = normalized;
                    options.set(row.id, option);
                });
                const visible = [...options.values()].slice(-100);
                select.replaceChildren(placeholder, ...visible);
                if (value && !visible.some(option => option.value === value)) {
                    selected.hidden = true;
                    select.add(selected);
                }
                select.value = value;
                page = targetPage;
                loadedSearch = search;
                more.hidden = rows.length <= 25;
                picker.setError('');
            } catch (error) {
                if (controller !== request || error.name === 'AbortError') return;
                loadedSearch = undefined;
                more.hidden = true;
                picker.setError(document.body.dataset.searchUnavailable);
            } finally {
                if (controller === request) cancelRequest();
            }
        }

        const binding = window.AppSearch.bind(input, () => load(), () => {
            cancelRequest();
            more.hidden = true;
            if (!parent || parent.value) picker.setLoading(true);
        });
        more.addEventListener('click', () => load(page + 1));
        select.addEventListener('neo-select:open', () => {
            if (!controller && loadedSearch !== searchValue()) load();
        });
        select.addEventListener('neo-select:close', () => {
            binding.cancel();
            if (input.value || controller) loadedSearch = undefined;
            input.value = '';
            cancelRequest();
        });
        window.addEventListener('pagehide', () => {
            binding.cancel();
            if (controller) loadedSearch = undefined;
            cancelRequest();
        });

        load();
        return () => {
            binding.cancel();
            cancelRequest();
            loadedSearch = undefined;
            page = 0;
            input.value = '';
            more.hidden = true;
            select.replaceChildren(placeholder);
            select.value = '';
            picker.setError('');
            load();
        };
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once: true});
    else ready();
})();
