/* Pending requests stay inside their data region; native navigation keeps its shell feedback. */
(() => {
    'use strict';
    const pending = new Map();
    const status = (label, animate=false, size='medium') => {
        const element=document.createElement('div');element.className='app-loading-status';
        element.dataset.loadingSize=size;
        element.setAttribute('role','status');element.setAttribute('aria-label',label || document.body.dataset.shellLoading);
        if(animate){const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize=size;spinner.setAttribute('aria-hidden','true');element.append(spinner);}
        else element.textContent=label || document.body.dataset.shellLoading;
        return element;
    };
    function begin(region, options={}) {
        if(!region || !region.isConnected || pending.has(region))return;
        const table=options.table===false ? null : region.matches('table') ? region : region.querySelector('table');
        if(!table && !region.hasAttribute('data-ui-loading-region'))return;
        region.querySelector(':scope > [data-ui-loading-error]')?.remove();
        const state={loader:null,placeholders:[],busy:region.getAttribute('aria-busy'),content:null};pending.set(region,state);
        if(table) {
            const body=table.tBodies[0] || table.createTBody(),row=document.createElement('tr'),cell=document.createElement('td');
            row.className='app-loading-row';row.dataset.uiLoadingIndicator='true';
            cell.colSpan=[...(table.tHead?.rows[0]?.cells || [])].reduce((count,heading)=>count+(heading.hidden?0:heading.colSpan),0) || 1;
            const height=body.getBoundingClientRect().height, scroller=table.closest('.erp-table-scroll');
            cell.style.height=Math.max(96,Math.min(height,scroller?.clientHeight ? scroller.clientHeight-(table.tHead?.getBoundingClientRect().height || 0) : height))+'px';
            cell.append(status(options.label,true,options.size));row.append(cell);
            [...body.rows].filter(item=>!item.hidden).forEach(item=>{item.hidden=true;state.placeholders.push(item);});
            body.prepend(row);state.loader=row;
        } else {
            if(options.replace){
                state.content=document.createElement('span');state.content.hidden=true;
                state.content.append(...region.childNodes);region.append(state.content);
            }
            state.loader=status(options.label,true,options.size);
            state.loader.dataset.uiLoadingIndicator='true';
            const heading=region.querySelector(':scope > h2,:scope > h3');
            if(heading)heading.after(state.loader);else region.prepend(state.loader);
        }
        region.classList.add('is-loading');region.setAttribute('aria-busy','true');
    }
    function end(region) {
        const state=pending.get(region);if(!state)return;
        pending.delete(region);state.loader.remove();state.placeholders.forEach(row=>{if(row.isConnected)row.hidden=false;});
        if(state.content?.isConnected)state.content.replaceWith(...state.content.childNodes);
        region.classList.remove('is-loading');
        if(state.busy===null)region.removeAttribute('aria-busy');else region.setAttribute('aria-busy',state.busy==='true'?'false':state.busy);
    }
    function clear() {for(const region of [...pending.keys()])end(region);}
    function fail(region,{label,retry}) {
        if(!region?.isConnected)return;end(region);
        region.querySelector(':scope > [data-ui-loading-error]')?.remove();
        const error=document.createElement('div');error.className='app-loading-error';error.dataset.uiLoadingError='true';error.setAttribute('role','alert');
        const message=document.createElement('span');message.textContent=label;error.append(message);
        if(retry){const button=document.createElement('button');button.type='button';button.className='app-btn btn-neutral';button.textContent=document.body.dataset.navigationRetry;button.addEventListener('click',retry);error.append(button);}
        const heading=region.querySelector(':scope > h2,:scope > h3');if(heading)heading.after(error);else region.prepend(error);
    }
    window.AppLoading={begin,end,status,clear,fail};
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
