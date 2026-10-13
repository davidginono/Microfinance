(() => {
  'use strict';
  document.querySelector('[data-loan-print]')?.addEventListener('click', () => window.print());
  const root = document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-loan-choice]').forEach(box => {
    const select = box.querySelector('[data-choice-select]'), error = box.querySelector('[data-choice-error]');
    const picker = window.NeoSelect.search(select), search = picker.input, more = picker.more;
    search.setAttribute('aria-label', error.dataset.searchLabel);
    let page = 0, request = 0, controller, loading = false, loadedSearch;
    async function load(targetPage = 0) {
      controller?.abort(); controller = new AbortController();
      const version = ++request, query = search.value, normalized = query.trim().toLocaleLowerCase();
      loading = true; picker.setLoading(true); more.disabled = true;
      try {
        const response = await fetch(root + '/finance/loan-recording/choices/' + box.dataset.category + '?' + new URLSearchParams({search: query, page: targetPage}), {headers: {Accept: 'application/json'}, signal:controller.signal, cache:'no-store', credentials:'same-origin'});
        if (!response.ok || response.redirected) throw new Error('Lookup failed');
        const choices = await response.json(); if (version !== request || controller.signal.aborted || !select.isConnected) return;
        const value = select.value, selected = select.selectedOptions[0]?.cloneNode(true);
        const blank = [...select.options].find(option => option.value === '')?.cloneNode(true) || new Option('', '');
        const previous = targetPage ? [...select.options].filter(option => option.value && option.dataset.searchQuery === normalized) : [];
        const options = new Map(previous.map(option => [option.value, option]));
        choices.slice(0, 25).forEach(choice => {const option = new Option(choice.label, choice.id); option.dataset.searchQuery = normalized; options.set(choice.id, option);});
        const visible = [...options.values()].slice(-100); select.replaceChildren(blank, ...visible);
        if (value && !visible.some(option => option.value === value) && selected) select.add(selected);
        select.value = value; page = targetPage; loadedSearch = query;
        more.hidden = choices.length <= 25; error.textContent = '';
      } catch (failure) { if (version === request && failure.name !== 'AbortError') {error.textContent = error.dataset.error; more.hidden = true; binding.cancel();} }
      finally {if (version === request) {loading = false; picker.setLoading(false); more.disabled = false;}}
    }
    const binding = window.AppSearch.bind(search, () => load(), () => {++request; controller?.abort(); loading = false; picker.setLoading(true); more.hidden = true;});
    more.addEventListener('click', () => {binding.cancel(); load(page + 1);});
    select.addEventListener('neo-select:close', () => {binding.cancel(); ++request; controller?.abort(); loading = false; picker.setLoading(false);});
    select.addEventListener('neo-select:open', () => {if (!loading && (loadedSearch !== search.value || error.textContent)) {binding.cancel(); load();}});
    window.addEventListener('pagehide', () => {binding.cancel(); ++request; controller?.abort();});
    load();
  });
  document.querySelectorAll('[data-loan-form]').forEach(form => {
    const summary = form.querySelector('.loan-error-summary'); if (summary) {form.querySelectorAll('details').forEach(d => d.open = true); summary.focus();}
    const submitButtons = [...form.querySelectorAll('button[type="submit"],button:not([type])')];
    const initiallyDisabled = new Set(submitButtons.filter(button => button.disabled));
    let submitting = false;
    form.addEventListener('submit', event => {
      if (submitting) {event.preventDefault(); return;}
      if (event.submitter?.name === 'action') {
        let action = form.querySelector('input[name="action"]');
        if (!action) {action=document.createElement('input');action.type='hidden';action.name='action';form.append(action);}
        action.value=event.submitter.value;
      }
      submitting = true;
      // Defer disabling so the clicked action remains part of the submitted form.
      setTimeout(() => form.querySelectorAll('button[type="submit"],button:not([type])').forEach(b => b.disabled = true), 0);
    });
    window.addEventListener('pageshow', event => {if(event.persisted){submitting=false;submitButtons.forEach(b=>b.disabled=initiallyDisabled.has(b));}});
  });
})();
