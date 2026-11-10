(() => {
    const focusNewChoice = () => requestAnimationFrame(() => {
        const choice = document.querySelector('[data-group-new-choice="true"]');
        if (!choice || choice.closest('form').querySelector('.coa-error')) return;
        (choice.querySelector('.neo-select-button') || choice.querySelector('select'))?.focus();
    });

    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(focusNewChoice);
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', focusNewChoice, { once: true });
    else focusNewChoice();
})();
