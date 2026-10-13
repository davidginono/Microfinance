/* Component-scoped loading for real requests; never mask the page or delay ready data. */
(() => {
    'use strict';
    const pending = new Map();
    const status = label => {
        const element=document.createElement('div');element.className='app-loading-status';
        element.setAttribute('role','status');element.setAttribute('aria-label',label || document.body.dataset.shellLoading);
        const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.setAttribute('aria-hidden','true');
        element.append(spinner);return element;
    };
    function begin(region, options={}) {
        if(!region || !region.isConnected || pending.has(region))return;
        const state={loader:null,placeholders:[],busy:region.getAttribute('aria-busy')};pending.set(region,state);
        const table=options.table===false ? null : region.matches('table') ? region : region.querySelector('table');
        if(table) {
            const body=table.tBodies[0] || table.createTBody(),row=document.createElement('tr'),cell=document.createElement('td');
            row.className='app-loading-row';row.dataset.uiLoadingIndicator='true';
            cell.colSpan=[...(table.tHead?.rows[0]?.cells || [])].reduce((count,heading)=>count+(heading.hidden?0:heading.colSpan),0) || 1;
            cell.append(status(options.label));row.append(cell);
            [...body.rows].filter(item=>item.cells.length===1 && item.cells[0].colSpan>1 && !item.hidden).forEach(item=>{item.hidden=true;state.placeholders.push(item);});
            body.prepend(row);state.loader=row;
        } else {
            state.loader=status(options.label);state.loader.dataset.uiLoadingIndicator='true';region.append(state.loader);
        }
        region.classList.add('is-loading');region.setAttribute('aria-busy','true');
    }
    function end(region) {
        const state=pending.get(region);if(!state)return;
        pending.delete(region);state.loader.remove();state.placeholders.forEach(row=>{if(row.isConnected)row.hidden=false;});
        region.classList.remove('is-loading');
        if(state.busy===null)region.removeAttribute('aria-busy');else region.setAttribute('aria-busy',state.busy==='true'?'false':state.busy);
    }
    function clear() {for(const region of [...pending.keys()])end(region);}
    window.AppLoading={begin,end,status,clear};
    const sync=region=>{if(region.getAttribute('aria-busy')==='true')begin(region,{table:region.matches('.erp-table-wrap')});else end(region);};
    function init() {
        document.querySelectorAll('[data-ui-loading-region], .erp-table-wrap[aria-busy="true"]').forEach(sync);
        const observer=new MutationObserver(records=>{
            for(const record of records) {
                if(record.type==='attributes' && record.target.matches('[data-ui-loading-region], .erp-table-wrap'))sync(record.target);
                if(record.type==='childList')for(const added of record.addedNodes)if(added.nodeType===1){
                    if(added.matches('[data-ui-loading-region], .erp-table-wrap[aria-busy="true"]'))sync(added);
                    added.querySelectorAll('[data-ui-loading-region], .erp-table-wrap[aria-busy="true"]').forEach(sync);
                }
            }
            for(const region of [...pending.keys()])if(!region.isConnected){pending.delete(region);}
        });
        observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['aria-busy']});
    }
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
    window.addEventListener('pageshow',event=>{if(event.persisted)clear();});
    window.addEventListener('pagehide',clear);
})();
