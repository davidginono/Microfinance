(() => {
    'use strict';
    const register = document.querySelector('[data-payroll-register]');
    if (register) {
        const inputs = () => [...register.querySelectorAll('input[name="payroll-selection"]')];
        const buttons = [...register.querySelectorAll('[data-payroll-open],[data-payroll-pdf],[data-payroll-xlsx]')];
        const status = register.querySelector('[data-payroll-selection]');
        const selected = () => inputs().find(input => input.checked)?.closest('[data-payroll-id]');
        const update = () => {
            const row = selected();
            inputs().forEach(input => input.closest('tr').classList.toggle('is-selected', input.checked));
            buttons.forEach(button => button.disabled = !row);
            status.textContent = row ? status.dataset.selected + ' ' + row.querySelector('a').textContent : status.dataset.empty;
        };
        const reset = () => {inputs().forEach(input => input.checked = false);update();};
        register.addEventListener('change', event => {
            if (!event.target.matches('input[name="payroll-selection"]')) return;
            if (event.target.checked) inputs().forEach(input => {if(input !== event.target)input.checked = false;});
            update();
        });
        register.addEventListener('click', event => {
            if (event.target.closest('a,button,input,label')) return;
            const input = event.target.closest('[data-payroll-id]')?.querySelector('input[name="payroll-selection"]');
            if (!input) return;
            const next = !input.checked;reset();input.checked = next;input.focus();update();
        });
        buttons.forEach(button => button.addEventListener('click', () => {
            const row = selected();if (!row || button.disabled) return;
            if (button.hasAttribute('data-payroll-xlsx')) {
                window.downloadExport(register.dataset.base + '/' + encodeURIComponent(row.dataset.payrollId) + '/xlsx', button);return;
            }
            window.WorkspaceNavigation.navigate(register.dataset.base + '/' + encodeURIComponent(row.dataset.payrollId) + (button.hasAttribute('data-payroll-pdf') ? '/pdf' : ''));
        }));
        ['resource-table:reset','resource-table:updated'].forEach(name => register.addEventListener(name,reset));
        window.addEventListener('pageshow',reset);reset();
    }
    document.querySelector('[data-payroll-print]')?.addEventListener('click', event => window.printExport(event.currentTarget.dataset.payrollPrint));
    const form = document.querySelector('[data-payroll-form]');
    if (form) {
        const freeze = form.querySelector('[data-payroll-freeze]');
        const invalidate = event => {
            if (['employerId','month','deductionDate','includeArrears'].includes(event.target.name)) {
                form.elements.previewHash.value = '';if(freeze)freeze.disabled = true;
            }
        };
        form.addEventListener('input',invalidate);form.addEventListener('change',invalidate);
        const preview = form.querySelector('[data-payroll-preview]');
        if (preview) {
            window.ResourceTable?.setPage(preview,{page:Number(preview.dataset.page),hasNext:preview.dataset.hasNext === 'true',loading:false});
            preview.addEventListener('resource-table:page',event => {
                const action = new URL(form.action,location.href);action.searchParams.set('previewPage',event.detail.page);
                form.action = action.href;form.requestSubmit();
            });
            preview.addEventListener('resource-table:refresh',event => {event.preventDefault();form.requestSubmit();});
        }
        window.addEventListener('pageshow',() => {if(freeze && !form.elements.previewHash.value)freeze.disabled = true;});
    }
})();
