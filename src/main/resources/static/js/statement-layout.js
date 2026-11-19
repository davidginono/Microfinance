(() => {
    'use strict';
    const ready=window.WorkspaceNavigation?.onReady || (callback=>document.readyState==='loading'?document.addEventListener('DOMContentLoaded',callback,{once:true}):callback());
    ready(()=>{
        document.querySelectorAll('[data-layout-register]').forEach(region=>{
            if(region.dataset.layoutReady)return;region.dataset.layoutReady='true';
            const inputs=()=>[...region.querySelectorAll('[data-layout-select]')];
            const commands=[...region.querySelectorAll('[data-layout-command]')];
            const selected=()=>inputs().find(input=>input.checked && !input.closest('tr').hidden);
            const update=()=>{const checked=selected();inputs().forEach(input=>{input.closest('tr').classList.toggle('resource-table-selected',input===checked);});commands.forEach(command=>command.disabled=!checked);region.querySelector('[data-layout-selection]').textContent=checked?.getAttribute('aria-label')||'';};
            const clear=()=>{inputs().forEach(input=>input.checked=false);update();};
            region.addEventListener('change',event=>{const input=event.target.closest('[data-layout-select]');if(!input)return;if(input.checked)inputs().forEach(other=>{if(other!==input)other.checked=false;});update();});
            region.addEventListener('click',event=>{if(event.target.closest('a,button,input,label,select,textarea'))return;event.target.closest('tbody tr')?.querySelector('[data-layout-select]')?.click();});
            commands.forEach(command=>command.addEventListener('click',()=>{const row=selected()?.closest('[data-layout-row]');const target=row?.dataset[command.dataset.layoutCommand+'Url'];if(target&&!command.disabled){if(window.WorkspaceNavigation)window.WorkspaceNavigation.navigate(target);else location.assign(target);}}));
            region.addEventListener('resource-table:reset',clear);region.addEventListener('resource-table:updated',clear);window.addEventListener('pageshow',clear);clear();
        });
        document.querySelectorAll('[data-layout-picker]').forEach(picker=>{
            if(picker.dataset.layoutReady)return;picker.dataset.layoutReady='true';
            const search=picker.querySelector('[data-layout-account-search]');
            const rows=[...picker.querySelectorAll('[data-layout-account]')];
            const filter=()=>{const query=search.value.trim().toLocaleLowerCase();let visible=0;rows.forEach(row=>{row.hidden=query!==''&&!row.textContent.toLocaleLowerCase().includes(query);if(!row.hidden)visible++;});picker.querySelector('[data-layout-account-empty]').hidden=visible>0;};
            if(window.AppSearch)window.AppSearch.bind(search,filter);else search.addEventListener('input',filter);
        });
        const note=document.querySelector('[data-layout-note-form]');
        if(note&&!note.dataset.figuresReady){
            note.dataset.figuresReady='true';
            const update=()=>{const basis=note.elements.namedItem('basis').value;const narrative=basis==='NARRATIVE';const accounts=note.querySelector('[data-layout-note-accounts]');accounts.hidden=narrative;accounts.disabled=narrative;note.querySelector('[data-layout-profit]').hidden=basis!=='BALANCE';note.elements.namedItem('includeProfit').disabled=basis!=='BALANCE';note.querySelector('[data-layout-note-text]').required=narrative;};
            note.addEventListener('change',event=>{if(event.target.name==='basis'){update();note.querySelector('[data-layout-note-text]').dispatchEvent(new Event('input',{bubbles:true}));}});update();
        }
    });
})();
