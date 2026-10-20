(() => {
  'use strict';
  document.querySelector('[data-management-print]')?.addEventListener('click', () => window.print());
  document.querySelector('[role="alert"][tabindex="-1"]')?.focus();
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
