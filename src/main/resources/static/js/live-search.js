(() => {
    'use strict';
    const delay = 350;
    function bind(input, search, invalidate = () => {}) {
        let timer, pending = false, composing = false, lastValue;
        const stop = () => { clearTimeout(timer); pending = false; };
        const cancel = () => { stop(); lastValue = undefined; };
        const run = (force = false) => {
            stop();
            if (!force && lastValue === input.value) return;
            lastValue = input.value; invalidate(); search();
        };
        const flush = () => { if (pending && !composing) run(); };
        const schedule = event => {
            cancel(); invalidate();
            if (composing || event?.isComposing) return;
            pending = true; timer = setTimeout(flush, delay);
        };
        input.addEventListener('input', schedule);
        input.addEventListener('blur', flush);
        input.addEventListener('compositionstart', () => { composing = true; cancel(); invalidate(); });
        input.addEventListener('compositionend', () => { composing = false; schedule(); });
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !composing && !event.isComposing) {
                event.preventDefault(); run();
            }
        });
        return {cancel, flush, run};
    }
    window.AppSearch = {bind, delay};

    // GET registries retain their existing server filters, authorization and pagination.
    // Embedded account finders refresh only their result section, preserving unsaved forms.
    document.addEventListener('DOMContentLoaded', () => {
        const focusKey = 'app-search-focus';
        let saved;
        try { saved = JSON.parse(sessionStorage.getItem(focusKey)); sessionStorage.removeItem(focusKey); } catch (_) {}
        document.querySelectorAll('form').forEach(form => {
            if (form.method.toLowerCase() !== 'get' || form.hasAttribute('data-picker-search')) return;
            const input = [...form.querySelectorAll('input')].find(field =>
                /^(search|searchId|parentSearch|accountSearch|q|query|keyword|term)$/i.test(field.name)
                && ['text', 'search'].includes(field.type) && !field.hasAttribute('data-library-lookup'));
            if (!input || form.querySelector('[name="format"], [data-download-action], [data-print-action]')) return;
            const finder = form.closest('.coa-parent-picker')?.querySelector('.erp-table-wrap');
            let controller, version = 0, submitting = false;
            const invalidate = () => { version++; controller?.abort();window.AppLoading?.end(finder?.querySelector('.erp-table-wrap')); finder?.removeAttribute('aria-busy'); };
            const search = async (pageUrl) => {
                form.querySelectorAll('[name="page"], [name="parentPage"], [name="accountPage"]').forEach(field => field.value = '0');
                const url = new URL(pageUrl || form.action, location.href);
                if (url.origin !== location.origin) return;
                if (!pageUrl) {
                    url.search = '';
                    new FormData(form).forEach((value, key) => url.searchParams.append(key, value));
                }
                if (!finder) {
                    if (document.activeElement === input) {
                        try { sessionStorage.setItem(focusKey, JSON.stringify({url:url.href, name:input.name, start:input.selectionStart, end:input.selectionEnd})); } catch (_) {}
                    }
                    form.requestSubmit();
                    return;
                }
                const current = ++version;
                controller = new AbortController(); finder.setAttribute('aria-busy', 'true');
                window.AppLoading?.begin(finder.querySelector('.erp-table-wrap'));
                try {
                    const response = await fetch(url, {signal:controller.signal, credentials:'same-origin', cache:'no-store'});
                    if (!response.ok || response.redirected) throw new Error('Search unavailable');
                    const next = new DOMParser().parseFromString(await response.text(), 'text/html');
                    if (current !== version) return;
                    const result = next.querySelector('.coa-parent-picker .erp-table-wrap');
                    if (!result) throw new Error('Search unavailable');
                    finder.querySelector('.erp-table-scroll').replaceChildren(...result.querySelector('.erp-table-scroll').childNodes);
                    finder.querySelector('.erp-table-footer').replaceChildren(...result.querySelector('.erp-table-footer').childNodes);
                    finder.querySelector('[data-search-status]')?.remove();
                } catch (error) {
                    if (current === version && error.name !== 'AbortError') {
                        let status = finder.querySelector('[data-search-status]');
                        if (!status) {
                            status = document.createElement('p'); status.dataset.searchStatus = '';
                            status.className = 'coa-error'; status.setAttribute('role', 'status'); form.after(status);
                        }
                        status.textContent = document.body.dataset.searchUnavailable;
                        binding.cancel();
                    }
                } finally { if (current === version) {window.AppLoading?.end(finder.querySelector('.erp-table-wrap'));finder.removeAttribute('aria-busy');} }
            };
            const binding = bind(input, () => void search(), invalidate);
            form.addEventListener('submit', event => {
                if (finder) { event.preventDefault(); binding.run(); }
                else {
                    binding.cancel();
                    if (submitting) event.preventDefault();
                    else submitting = true;
                }
            });
            window.addEventListener('pageshow', () => { submitting = false; });
            window.addEventListener('pagehide', () => { binding.cancel(); invalidate(); });
            finder?.addEventListener('click', event => {
                const link = event.target.closest('.erp-table-footer a[href]');
                if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                event.preventDefault(); binding.cancel(); invalidate(); void search(link.href);
            });
            if (saved?.url === location.href && saved.name === input.name) {
                input.focus({preventScroll:true}); input.setSelectionRange(saved.start, saved.end);
            }
        });
    });
})();
