(() => {
  'use strict';
  document.querySelectorAll('.report-print-action').forEach(button => {
    button.addEventListener('click', () => window.print());
  });
})();
