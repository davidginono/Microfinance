(() => {
  'use strict';
  document.querySelector('[data-loan-print]')?.addEventListener('click', () => window.print());
  const root = document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-loan-choice]').forEach(box => {
    const select = box.querySelector('[data-choice-select]'), search = box.querySelector('[data-choice-search]');
    const next = box.querySelector('[data-choice-next]'), error = box.querySelector('[data-choice-error]');
    let page = 0, request = 0;
    async function load(reset) {
      if (reset) page = 0;
      const version = ++request;
      try {
        const response = await fetch(root + '/finance/loan-recording/choices/' + box.dataset.category + '?' + new URLSearchParams({search: search.value, page}), {headers: {Accept: 'application/json'}});
        if (!response.ok) throw new Error('Lookup failed');
        const choices = await response.json(); if (version !== request) return;
        const value = select.value, selected = select.selectedOptions[0];
        const blank = select.options[0].cloneNode(true); select.replaceChildren(blank);
        if (value && !choices.some(c => c.id === value)) select.add(new Option(selected.textContent, value));
        choices.slice(0, 25).forEach(c => select.add(new Option(c.label, c.id)));
        select.value = value; next.disabled = choices.length <= 25; error.textContent = '';
      } catch (_) { if (version === request) error.textContent = error.dataset.error; }
    }
    box.querySelector('[data-choice-load]').addEventListener('click', () => load(true));
    next.addEventListener('click', () => {page++; load(false);});
    search.addEventListener('keydown', event => {if (event.key === 'Enter') {event.preventDefault(); load(true);}});
    load(true);
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
