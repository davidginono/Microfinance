(() => {
  'use strict';
  document.querySelectorAll('[data-tenure-config]').forEach(box => {
    const unit = box.querySelector('[data-tenure-unit]'), values = box.querySelector('[data-tenure-values]');
    const refresh = () => {
      values.hidden = !unit.value;
      values.querySelectorAll('input').forEach(input => {input.disabled = !unit.value; input.max = unit.value === 'DAYS' ? '18250' : unit.value === 'YEARS' ? '50' : '600';});
    };
    unit.addEventListener('change', refresh);
    refresh();
  });
})();
