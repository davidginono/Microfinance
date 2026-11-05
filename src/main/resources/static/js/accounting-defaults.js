(() => {
    const setup = () => document.querySelector('[data-coa-defaults]')?.requestSubmit();
    if (window.WorkspaceNavigation?.onReady) window.WorkspaceNavigation.onReady(setup);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', setup, {once: true});
    else setup();
})();
