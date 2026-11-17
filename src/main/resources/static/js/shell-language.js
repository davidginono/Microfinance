/* Use the existing staff preference command; reload the complete localized shell. */
(() => {
    const ready = window.WorkspaceNavigation?.onReady || (callback => callback());
    ready(() => {
        const form = document.querySelector('[data-shell-language]');
        if (!form) return;
        const control = form.closest('details');
        form.addEventListener('submit', () => {
            form.elements.returnTo.value = location.pathname + location.search + location.hash;
        });
        document.addEventListener('click', event => {
            if (!control.contains(event.target)) control.open = false;
        });
        control.addEventListener('keydown', event => {
            if (event.key === 'Escape' && control.open) {
                event.preventDefault();
                control.open = false;
                control.querySelector('summary').focus();
            }
        });
    });
})();
