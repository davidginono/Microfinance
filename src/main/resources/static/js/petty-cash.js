(() => {
    'use strict';
    const setup=document.querySelector('[data-petty-setup]');
    if(setup){
        const select=setup.querySelector('[data-petty-account-choice]'),field=setup.elements.accountId;
        const label=select.parentElement.querySelector('[data-account-label]');
        window.VoucherSelect.bind(select,{field,label:()=>label.textContent,url:()=>new URL(select.dataset.url,location.origin),
            choice:item=>({value:item.id,label:item.name}),changed:(_item,text)=>{label.textContent=text;},error:select.dataset.error});
    }
    const workspace=document.querySelector('[data-petty-cash-workspace]');
    if(!workspace)return;
    const table=workspace.querySelector('[data-petty-table]');
    if(!table)return;
    const filters=table.querySelector('[data-petty-query]');
    // A mode has different columns. The shared workspace navigator prepares that destination;
    // ordinary filters, sorting, pagination and retry remain owned by ResourceTable.
    filters.addEventListener('submit',event=>{
        if(filters.elements.view.value===table.dataset.pettyView)return;
        event.preventDefault();event.stopImmediatePropagation();
        const url=new URL(filters.action,location.href);url.search=new URLSearchParams(new FormData(filters));
        url.searchParams.set('page','0');url.searchParams.set('sort','date_desc');
        window.WorkspaceNavigation.navigate(url.href);
    },true);
    const sync=()=>{
        const today=workspace.querySelector('[data-table-query-value="todayIso"]')?.textContent;
        if(today){workspace.dataset.today=today;filters.elements.through.max=today;}
        const closing=workspace.querySelector('[data-petty-closing]');
        if(closing)closing.hidden=filters.elements.through.value>=workspace.dataset.today;
        const warning=workspace.querySelector('[data-petty-coverage]');
        if(warning)warning.hidden=filters.elements.from.value>=workspace.dataset.starts;
    };
    table.addEventListener('resource-table:updated',sync);sync();
})();
