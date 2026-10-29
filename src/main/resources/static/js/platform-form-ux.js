/* Platform commands retain native forms, server authorization and POST/redirect/GET. */
(() => {
    'use strict';
    if(!window.WorkspaceNavigation)return;
    document.querySelectorAll('form[method="post"]').forEach((form,index)=>{
        const action=new URL(form.action,location.href);
        if(action.origin!==location.origin || !action.pathname.startsWith('/admin/') || form.hasAttribute('data-native-navigation') || form.hasAttribute('data-workspace-submit'))return;
        form.dataset.workspaceSubmit='recover';
        if(!form.closest('.app-modal-overlay,[role="dialog"]')){
            if(!form.id)form.id='platform-form-'+index;
            form.dataset.workspaceReturn=form.id;
        }
    });
    const ready=()=>{
        const register=document.querySelector('[data-platform-topup-register]');
        if(register){
            const inputs=()=>[...register.querySelectorAll('input[name="platform-topup-selection"]')];
            const command=register.querySelector('[data-platform-topup-edit]');
            const announcement=register.querySelector('[data-platform-topup-selection-status]');
            const selected=()=>inputs().find(input=>input.checked && !input.closest('tr').hidden)?.closest('[data-platform-topup-row]');
            const update=()=>{
                const row=selected();if(command)command.disabled=!row;
                if(announcement)announcement.textContent=row?row.cells[1].textContent.trim():announcement.dataset.empty;
                inputs().forEach(input=>{input.closest('tr').classList.toggle('is-selected',input.checked);input.closest('tr').setAttribute('aria-selected',String(input.checked));});
            };
            const reset=()=>{inputs().forEach(input=>input.checked=false);update();};
            register.addEventListener('change',event=>{
                const input=event.target;if(!input.matches('input[name="platform-topup-selection"]'))return;
                if(input.checked)inputs().forEach(other=>{if(other!==input)other.checked=false;});update();
            });
            register.addEventListener('click',event=>{
                if(event.target.closest('input,button,a,label'))return;
                const row=event.target.closest('[data-platform-topup-row]');if(!row)return;
                const input=row.querySelector('input'),checked=!input.checked;inputs().forEach(other=>other.checked=false);input.checked=checked;input.focus();update();
            });
            command?.addEventListener('click',()=>{const row=selected();if(row)window.WorkspaceNavigation.navigate(row.dataset.editUrl);});
            register.addEventListener('resource-table:reset',reset);register.addEventListener('resource-table:updated',reset);window.addEventListener('pageshow',reset);reset();
        }
        const flashbar=document.querySelector('[data-workspace-flashbar]');
        const anchor=location.hash ? document.getElementById(decodeURIComponent(location.hash.slice(1))) : null;
        if(flashbar?.querySelector('[data-app-toast]') && anchor?.matches('form[data-workspace-submit="recover"],.platform-settings-overview > section')){
            anchor.before(flashbar);flashbar.scrollIntoView?.({block:'nearest'});
        }
        const failure=document.querySelector('[data-workspace-response-error],[data-workspace-form-error]');
        if(failure)requestAnimationFrame(()=>{failure.focus();failure.scrollIntoView?.({block:'nearest'});});
    };
    window.WorkspaceNavigation.onReady(ready);
})();
