(() => {
    const register = document.getElementById('library-register');
    if (register) {
        const inputs = () => Array.from(register.querySelectorAll('input[name="library-selection"]'));
        const view = document.getElementById('library-view'), status = document.getElementById('library-status');
        const edit = document.getElementById('library-edit');
        const statusForm = document.getElementById('library-status-form'), summary = document.getElementById('library-selection-status');
        const selected = () => inputs().find(input => input.checked && !input.closest('tr').hidden)?.closest('[data-library-row]');
        const update = () => {
            const row = selected();
            inputs().forEach(input => input.closest('tr').classList.toggle('coa-row-selected', input.checked));
            view.disabled = !row;
            if(edit)edit.disabled=!row || row.dataset.system==='true';
            if (status) {
                status.disabled = !row || row.dataset.system === 'true';
                const action = row?.dataset.active === 'false' ? 'reactivate' : 'deactivate';
                status.textContent = status.dataset[action];
                if (row) statusForm.action = statusForm.dataset.url + row.dataset.id + '/' + action;
                else statusForm.removeAttribute('action');
            }
            summary.textContent = row ? summary.dataset.selected + ' ' + row.querySelector('[data-library-code]').textContent.trim() + ' · ' + row.querySelector('[data-library-name]').firstChild.textContent.trim() : summary.dataset.empty;
        };
        register.addEventListener('change', event => {
            const input=event.target;if(!input.matches('input[name="library-selection"]'))return;
            if(input.checked)inputs().forEach(other=>{if(other!==input)other.checked=false;});update();
        });
        register.addEventListener('click', event => {
            if(event.target.closest('a,button,input,label'))return;
            const row=event.target.closest('[data-library-row]');if(!row)return;
            const input=row.querySelector('input[name="library-selection"]'),checked=!input.checked;
            inputs().forEach(other=>other.checked=false);input.checked=checked;input.focus();update();
        });
        view.addEventListener('click', () => {
            const row = selected(); if (!row || view.disabled) return;
            window.WorkspaceNavigation.navigate(row.dataset.url);
        });
        edit?.addEventListener('click',()=>{
            const row=selected();if(!row || edit.disabled)return;
            window.WorkspaceNavigation.navigate(edit.dataset.url+encodeURIComponent(row.dataset.id)+'/edit');
        });
        statusForm?.addEventListener('submit', event => { if (!selected() || status.disabled) event.preventDefault(); else status.disabled = true; });
        const reset = () => { inputs().forEach(input => input.checked = false); update(); };
        window.addEventListener('pageshow', reset);
        register.addEventListener('resource-table:reset',reset);
        register.addEventListener('resource-table:updated',reset);
        reset();
    }
    const modal = document.getElementById('library-create-modal');
    if (modal) {
        document.body.appendChild(modal);
        const panel = modal.querySelector('[role="dialog"]');
        const triggers = document.querySelectorAll('[data-library-open]');
        let previousFocus;
        let previousOverflow;
        let previousRootOverflow;
        let submitting = false;
        const focusable = () => Array.from(panel.querySelectorAll('button:not([disabled]), input:not([type="hidden"]), textarea, select, a[href], [tabindex="0"]')).filter(el => el.getClientRects().length);
        const open = () => {
            previousFocus = document.activeElement; previousOverflow = document.body.style.overflow; previousRootOverflow = document.documentElement.style.overflow;
            modal.hidden = false; modal.classList.add('is-open'); document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
            (panel.querySelector('.coa-error') ? panel : panel.querySelector('input:not([type="hidden"])') || focusable()[0] || panel).focus();
        };
        const close = (discard = false) => {
            if (submitting) return;
            if (!discard && !document.dispatchEvent(new CustomEvent('accounting:before-close', {cancelable:true, detail:{close:() => close(true)}}))) return;
            modal.classList.remove('is-open'); modal.hidden = true; document.body.style.overflow = previousOverflow || ''; document.documentElement.style.overflow = previousRootOverflow || '';
            (previousFocus?.isConnected && previousFocus !== document.body ? previousFocus : triggers[0])?.focus();
            const url = new URL(location.href); url.searchParams.delete('create');
            if (modal.dataset.created === 'true') window.WorkspaceNavigation.navigate(url.href,{replace:true,force:true});
            else history.replaceState(null, '', url.href);
        };
        triggers.forEach(button => button.addEventListener('click', open));
        modal.querySelectorAll('[data-library-close]').forEach(button => button.addEventListener('click', () => close()));
        modal.addEventListener('keydown', event => {
            if (event.key === 'Escape') { event.preventDefault(); close(); }
            if (event.key === 'Tab') {
                const controls = focusable(), first = controls[0], last = controls.at(-1);
                if (!first) { event.preventDefault(); panel.focus(); }
                else if (event.shiftKey && (document.activeElement === first || document.activeElement === panel)) { event.preventDefault(); last.focus(); }
                else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
            }
        });
        modal.querySelector('form')?.addEventListener('submit', event => {
            if (submitting) { event.preventDefault(); return; }
            submitting = true; modal.querySelectorAll('button').forEach(button => button.disabled = true);
        });
        window.addEventListener('pageshow', () => { submitting = false; modal.querySelectorAll('button').forEach(button => button.disabled = false); });
        if (modal.dataset.autoOpen === 'true') open();
    }
    document.querySelectorAll('[data-library-account-select]').forEach(select=>{
        const holder=select.closest('[data-library-account]'),field=holder.querySelector('input[type="hidden"]'),label=holder.querySelector('[data-library-account-label]');
        window.VoucherSelect.bind(select,{field,label:()=>label.textContent,url:()=>select.dataset.url,error:select.dataset.error,
            choice:item=>({value:item.code,label:item.code+' · '+(document.documentElement.lang==='sw'&&item.nameSw?item.nameSw:item.name)}),
            changed:(item,text)=>{label.textContent=text;field.dispatchEvent(new Event('input',{bubbles:true}));}});
    });
})();
