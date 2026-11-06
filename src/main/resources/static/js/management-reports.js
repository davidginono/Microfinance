(() => {
  'use strict';
  document.querySelector('[data-management-print]')?.addEventListener('click', () => window.print());
  document.querySelector('[role="alert"][tabindex="-1"]')?.focus();
  const selectedAccounts = document.querySelector('[data-report-selected]');
  const accountPicker = document.querySelector('[data-report-account]');
  const groupPicker = document.querySelector('[data-report-group]');
  if (selectedAccounts && accountPicker) {
    const container = accountPicker.closest('[data-report-accounts]');
    const limit = container.querySelector('[data-report-selection-limit]');
    selectedAccounts.addEventListener('click', event => {
      if (event.target.closest('[data-remove-account]')) {event.target.closest('li').remove();limit.hidden = true;}
    });
    accountPicker.addEventListener('change', () => {
      const id = accountPicker.value, label = accountPicker.selectedOptions[0]?.textContent;
      if (!id) return;
      if (![...selectedAccounts.querySelectorAll('input')].some(input => input.value === id)) {
        if (selectedAccounts.children.length >= 20) {limit.hidden = false;accountPicker.value = '';window.refreshEnhancedSelects?.(container);return;}
        const item = document.createElement('li'), input = document.createElement('input'), text = document.createElement('span'), remove = document.createElement('button');
        input.type = 'hidden';input.name = 'accountIds';input.value = id;text.textContent = label;
        remove.type = 'button';remove.className = 'app-btn btn-neutral';remove.dataset.removeAccount = '';remove.textContent = container.dataset.removeLabel;
        item.append(input,text,remove);selectedAccounts.append(item);
      }
      accountPicker.value = '';window.refreshEnhancedSelects?.(container);
    });
    groupPicker.addEventListener('change', () => {
      selectedAccounts.replaceChildren();limit.hidden = true;accountPicker.value = '';
      const blank = [...accountPicker.options].find(option => option.value === '')?.cloneNode(true) || new Option('', '');
      accountPicker.replaceChildren(blank);
      const box = accountPicker.closest('[data-loan-choice]'), url = new URL(box.dataset.choiceUrl, location.href);
      if (groupPicker.value) url.searchParams.set('groupId',groupPicker.value);else url.searchParams.delete('groupId');
      box.dataset.choiceUrl = url.pathname + url.search;accountPicker.dispatchEvent(new Event('choice:reload'));
      window.refreshEnhancedSelects?.(container);
    });
  }
  const table = document.querySelector('[data-budget-register]');
  if (!table) return;
  const view = table.querySelector('[data-budget-view]'), status = table.querySelector('[data-budget-status]');
  let selected;
  const reset = () => {
    selected = null; view.disabled = true; status.textContent = status.dataset.empty;
    table.querySelectorAll('[data-budget-id]').forEach(row => {row.classList.remove('is-selected'); row.querySelector('input').checked = false;});
  };
  table.addEventListener('click', event => {
    const row = event.target.closest('[data-budget-id]');
    if (!row || event.target.closest('a,button,select') || event.target.closest('input:not([type=checkbox])')) return;
    const next = selected === row.dataset.budgetId ? null : row.dataset.budgetId;
    reset(); if (!next) return;
    selected = next; row.classList.add('is-selected'); row.querySelector('input').checked = true;
    view.disabled = false; status.textContent = status.dataset.selected;
  });
  view.addEventListener('click', () => {if (selected) window.WorkspaceNavigation.navigate(view.dataset.url + encodeURIComponent(selected));});
  table.querySelector('form')?.addEventListener('input', reset);
  table.addEventListener('resource-table:reset', reset);
  table.addEventListener('resource-table:updated', reset);
  window.addEventListener('pageshow', reset);
})();
