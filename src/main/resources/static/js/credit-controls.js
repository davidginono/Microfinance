(() => {
  'use strict';
  const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once:true}));
  ready(() => document.querySelectorAll('[data-credit-form]').forEach(form => {
    const kind = form.querySelector('[data-credit-kind]');
    if (!kind) return;
    const refresh = () => {
      form.querySelectorAll('[data-credit-kinds]').forEach(group => {
        const shown = group.dataset.creditKinds.split(' ').includes(kind.value);
        group.hidden = !shown;
        group.querySelectorAll('input,select,textarea').forEach(input => {input.disabled = !shown;});
      });
      window.refreshEnhancedSelects?.(form);
    };
    kind.addEventListener('change', refresh);
    refresh();
  }));
})();
