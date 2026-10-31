(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (fn => document.addEventListener('DOMContentLoaded', fn, {once:true}));
    ready(() => {
        const product = document.getElementById('intake-product'), unit = document.querySelector('[data-intake-unit]');
        if (!product || !unit) return;
        const refresh = () => {
            const value = product.selectedOptions[0]?.dataset.tenureUnit;
            unit.textContent = value ? '(' + (unit.dataset[value.toLowerCase()] || '') + ')' : '';
        };
        product.addEventListener('change', refresh);refresh();
    });
})();
