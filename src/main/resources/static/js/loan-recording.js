(() => {
  'use strict';
  document.querySelector('[data-loan-print]')?.addEventListener('click', () => window.print());
  const root = document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-loan-choice]').forEach(box => {
    const select = box.querySelector('[data-choice-select]'), error = box.querySelector('[data-choice-error]');
    const controls = document.createElement('div');
    controls.className = 'loan-choice-controls'; controls.dataset.neoSelectControls = 'true';
    const search = document.createElement('input');
    search.type = 'search'; search.className = 'aws-control'; search.maxLength = 100;
    search.setAttribute('aria-label', error.dataset.searchLabel);
    const previous = document.createElement('button'), next = document.createElement('button');
    [previous, next].forEach(button => {button.type = 'button'; button.className = 'app-btn btn-neutral'; button.disabled = true;});
    previous.textContent = error.dataset.previousLabel; next.textContent = error.dataset.nextLabel;
    const pagination = document.createElement('div'); pagination.className = 'accounting-actions';
    pagination.append(previous, next); controls.append(search, pagination);
    select.addEventListener('neo-select:render', event => {
      if (!event.detail.menu.contains(controls)) event.detail.menu.prepend(controls);
    });
    let page = 0, request = 0, timer;
    async function load(targetPage = 0) {
      clearTimeout(timer);
      const version = ++request;
      previous.disabled = true; next.disabled = true;
      try {
        const response = await fetch(root + '/finance/loan-recording/choices/' + box.dataset.category + '?' + new URLSearchParams({search: search.value, page: targetPage}), {headers: {Accept: 'application/json'}});
        if (!response.ok) throw new Error('Lookup failed');
        const choices = await response.json(); if (version !== request) return;
        const value = select.value, selected = select.selectedOptions[0];
        const blank = select.options[0].cloneNode(true); select.replaceChildren(blank);
        if (value && !choices.some(c => c.id === value)) select.add(new Option(selected.textContent, value));
        choices.slice(0, 25).forEach(c => select.add(new Option(c.label, c.id)));
        select.value = value; page = targetPage;
        previous.disabled = page === 0; next.disabled = choices.length <= 25; error.textContent = '';
      } catch (_) { if (version === request) {error.textContent = error.dataset.error; previous.disabled = page === 0;} }
    }
    previous.addEventListener('click', () => load(page - 1));
    next.addEventListener('click', () => load(page + 1));
    search.addEventListener('input', () => {++request; previous.disabled = true; next.disabled = true; timer = setTimeout(() => load(), 250);});
    search.addEventListener('keydown', event => {if (event.key === 'Enter') {event.preventDefault(); load();}});
    load();
  });
  document.querySelectorAll('[data-loan-form]').forEach(form => {
    const summary = form.querySelector('.loan-error-summary'); if (summary) {form.querySelectorAll('details').forEach(d => d.open = true); summary.focus();}
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
    window.addEventListener('pageshow', event => {if(event.persisted){submitting=false;form.querySelectorAll('button[type="submit"],button:not([type])').forEach(b=>b.disabled=false);}});
  });
})();
