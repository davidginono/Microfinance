(() => {
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
            if (modal.dataset.created === 'true') location.replace(url.href);
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
        window.addEventListener('pageshow', () => { submitting = false; modal.querySelectorAll('button').forEach(button => button.disabled = false); renumber(); });
        if (modal.dataset.autoOpen === 'true') open();
    }
    // Search suggestions without navigating away or losing entered form values.
    const timers = new Map(), requests = new Map();
    document.addEventListener('input', event => {
        const input = event.target;
        if (!input.matches('[data-library-lookup]') || !input.dataset.libraryLookup) return;
        const key = input.getAttribute('list'); clearTimeout(timers.get(key)); requests.get(key)?.abort();
        timers.set(key, setTimeout(async () => {
            const controller = new AbortController(); requests.set(key, controller);
            const status = input.closest('form')?.querySelector('.library-lookup-status');
            try {
                const url = new URL(input.dataset.libraryLookup, location.href); url.searchParams.set('search', input.value);
                const response = await fetch(url, {headers: {'Accept':'application/json'}, signal:controller.signal});
                if (!response.ok) throw new Error('Lookup unavailable');
                const choices = await response.json(), list = document.getElementById(key);
                list.replaceChildren(...choices.map(choice => {const option=document.createElement('option'); option.value=choice.code; option.textContent=choice.name; return option;}));
                if (status) status.textContent = '';
            } catch (error) { if (error.name !== 'AbortError' && status) status.textContent = status.dataset.lookupFailure; }
        }, 250));
    });
    const list = document.getElementById('library-rule-list');
    const add = document.getElementById('library-add-rule');
    const renumber = () => {
        if (!list || !add) return;
        const rows = list.querySelectorAll('.library-rule');
        rows.forEach((row, index) => row.querySelectorAll('[name]').forEach(control => {
            control.name = control.name.replace(/rules\[\d+\]/, `rules[${index}]`);
            if (control.id) control.id = control.id.replace(/rules\d+/, `rules${index}`);
        }));
        rows.forEach(row => row.querySelector('.library-remove').disabled = rows.length === 1);
        add.disabled = rows.length >= 5;
    };
    if (!list || !add) return;
    add.addEventListener('click', () => {
        if (list.children.length >= 5) return;
        const row = list.firstElementChild.cloneNode(true);
        const select = row.querySelector('select');
        const enhancedWrapper = select.closest('.neo-select');
        if (enhancedWrapper) enhancedWrapper.replaceWith(select);
        select.removeAttribute('data-neo-enhanced');
        select.classList.remove('neo-select-native');
        row.querySelectorAll('.coa-error').forEach(error => error.remove());
        row.querySelectorAll('[aria-invalid],[aria-describedby]').forEach(control => { control.removeAttribute('aria-invalid'); control.removeAttribute('aria-describedby'); });
        row.querySelectorAll('input').forEach(input => input.value = '');
        select.selectedIndex = 0;
        list.appendChild(row); renumber();
        requestAnimationFrame(() => (row.querySelector('.neo-select-button') || select).focus());
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('.library-remove');
        if (button && list.children.length > 1) {button.closest('.library-rule').remove(); renumber(); add.focus();}
    });
    renumber();
})();
