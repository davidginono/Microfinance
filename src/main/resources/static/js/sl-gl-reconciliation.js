/* Report-wide criteria change the heading, cutoff and exports together. Paging uses
   the shared table adapter and keeps that same report context. */
(() => {
    'use strict';
    document.addEventListener('submit', event => {
        const form=event.target.closest('[data-reconciliation-query]');
        if (!form) return;
        event.preventDefault();event.stopImmediatePropagation();
        const url=new URL(form.action,location.href);
        new FormData(form).forEach((value,name)=>{if(typeof value==='string' && name!=='cutoff')url.searchParams.set(name,value);});
        location.assign(url.href);
    },true);
    document.addEventListener('resource-table:refresh',event=>{
        if(!event.target.matches('[data-resource-table="sl-gl-reconciliation"]'))return;
        event.preventDefault();event.stopImmediatePropagation();
        const url=new URL(location.href);url.searchParams.delete('cutoff');url.searchParams.set('run','true');url.searchParams.set('page','0');
        location.assign(url.href);
    },true);
    document.querySelector('[role="alert"][tabindex="-1"]')?.focus();
})();
