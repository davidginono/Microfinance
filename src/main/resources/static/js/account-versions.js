(() => {
    const region = document.getElementById('account-version-register');
    if (!region) return;
    const view = document.getElementById('account-version-view');
    const inputs = () => Array.from(region.querySelectorAll('input[name="account-version-selection"]'));
    const update = () => {
        inputs().forEach(input => input.closest('tr').classList.toggle('coa-row-selected', input.checked));
        view.disabled = !inputs().some(input => input.checked);
    };
    region.addEventListener('change', event => {
        if (!event.target.matches('input[name="account-version-selection"]')) return;
        if (event.target.checked) inputs().forEach(input => { if (input !== event.target) input.checked = false; });
        update();
    });
    region.addEventListener('click', event => {
        if (event.target.closest('input,button,a,label')) return;
        const input = event.target.closest('[data-version-id]')?.querySelector('input');
        if (!input) return;
        const checked = !input.checked;
        inputs().forEach(other => other.checked = false);
        input.checked = checked; input.focus(); update();
    });
    view.addEventListener('click', () => {
        const input = inputs().find(input => input.checked);
        if (input) window.WorkspaceNavigation.navigate(region.dataset.baseUrl + input.closest('tr').dataset.versionId);
    });
    const reset = () => { inputs().forEach(input => input.checked = false); update(); };
    ['resource-table:reset','resource-table:updated','input'].forEach(name => region.addEventListener(name, event => { if (name !== 'input' || event.target.closest('.coa-filters')) reset(); }));
    window.addEventListener('pageshow', reset);
    reset();
})();
