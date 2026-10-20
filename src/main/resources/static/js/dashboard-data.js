/* Scoped, bounded existing dashboard endpoints. Resizing never performs a request. */
(() => {
    'use strict';
    const regions=new Map(),labels=()=>document.getElementById('dashboard-labels')?.dataset || {};
    const number=value=>value===null || value===undefined || value==='' || (!Number.isFinite(Number(value)) || Number(value)<0)?null:Number(value);
    const format=(value,decimals=0)=>number(value)===null?'—':new Intl.NumberFormat(document.documentElement.lang || 'en',{maximumFractionDigits:decimals}).format(Number(value));
    function init(root){
        if(regions.has(root))return;
        const l=labels(),status=root.querySelector('[data-dashboard-data-status]'),retry=root.querySelector('[data-dashboard-retry]'),range=root.querySelector('[data-dashboard-range]');
        let version=0,request=null,timer=null;
        const visible=()=>root.isConnected && !root.hidden && !document.hidden;
        const text=(key,value)=>root.querySelectorAll('[data-dashboard-metric="'+key+'"]').forEach(n=>n.textContent=value);
        const stop=()=>{version++;request?.abort();request=null;clearTimeout(timer);timer=null;root.removeAttribute('aria-busy');if(status?.querySelector('.app-loading-status')){status.replaceChildren();status.hidden=true;}};
        const chart=(key,data)=>{window.DashboardCharts?.render(root.querySelector('[data-live-chart="'+key+'"]'),data);};
        const render=payload=>{
            if(root.dataset.dashboardSource==='usage'){
                for(const key of ['activeUsers','activeSessions','pageViews','logins'])text(key,format(payload[key]));
                const timeline=Array.isArray(payload.timeline)?payload.timeline:[];
                chart('timeline',{type:'bar',height:200,title:l.logins,integer:true,xLabel:l.period,labels:timeline.map(r=>String(r.label || '')),series:[{label:l.logins,values:timeline.map(r=>number(r.total))}]});
                text('updated',payload.capturedAtLabel || '—');
            }else{
                const latest=payload.latest;if(!latest)throw new Error('metrics');
                text('size',format(latest.sizeMb,2)+' MB');text('connections',format(latest.activeConnections)+' / '+format(latest.maxConnections));text('usage',number(latest.utilizationPercent)===null?'—':format(latest.utilizationPercent,2)+'%');text('updated',latest.capturedAtLabel || '—');
                const history=Array.isArray(payload.history)?payload.history:[];
                const common={type:'line',integer:false,xLabel:l.period,labels:history.map(r=>String(r.label || ''))};
                chart('size',{...common,title:l.databaseSize,unit:'MB',series:[{label:l.databaseSize,values:history.map(r=>number(r.sizeMb))}]});
                chart('connections',{...common,title:l.connectionUsage,unit:'%',series:[{label:l.connectionUsage,values:history.map(r=>number(r.utilizationPercent))}]});
            }
            window.DashboardCharts?.init();
        };
        const schedule=()=>{clearTimeout(timer);timer=setTimeout(()=>void load(),root.dataset.dashboardSource==='database'?15000:30000);};
        async function load(force=false){
            if(!visible())return;
            if(request && !force)return;
            request?.abort();const current=++version,controller=new AbortController();request=controller;
            const timeout=setTimeout(()=>controller.abort(),20000);
            root.setAttribute('aria-busy','true');if(status){status.hidden=false;status.classList.remove('dashboard-data-error');status.replaceChildren(window.AppLoading?.status(l.loading,true) || document.createTextNode(l.loading));}if(retry)retry.hidden=true;
            try{
                const url=new URL(root.dataset.dashboardUrl,location.href);if(url.origin!==location.origin)throw new Error('scope');if(range)url.searchParams.set('range',range.value);
                const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:controller.signal});
                if(!response.ok || response.redirected)throw new Error('request');const payload=await response.json();
                if(current!==version || !visible())return;render(payload);if(status){status.hidden=true;status.replaceChildren();}
            }catch(error){if(current===version && visible()){if(status){status.hidden=false;status.classList.add('dashboard-data-error');status.textContent=l.loadError;}if(retry)retry.hidden=false;}}
            finally{clearTimeout(timeout);if(current===version){request=null;root.removeAttribute('aria-busy');schedule();}}
        }
        root.querySelectorAll('[data-dashboard-refresh],[data-dashboard-retry]').forEach(b=>b.addEventListener('click',()=>void load(true)));
        range?.addEventListener('change',()=>void load(true));
        root.querySelector('[data-dashboard-view]')?.addEventListener('change',event=>{root.querySelectorAll('[data-dashboard-chart-panel]').forEach(panel=>panel.hidden=panel.dataset.dashboardChartPanel!==event.target.value);});
        root.addEventListener('dashboard:visibility',()=>{if(visible())void load(true);else stop();});
        const state={load,stop,start(){stop();if(visible())void load();}};regions.set(root,state);void load();
    }
    function scan(){if(!window.document)return;for(const [root,state] of regions)if(!root.isConnected){state.stop();regions.delete(root);}document.querySelectorAll('[data-dashboard-source]').forEach(init);}
    const start=()=>{scan();let queued=false;new MutationObserver(records=>{if(!window.document)return;if(queued || !records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1 && (n.matches?.('[data-dashboard-source]') || n.querySelector?.('[data-dashboard-source]')))))return;queued=true;queueMicrotask(()=>{queued=false;scan();});}).observe(document.body,{childList:true,subtree:true});};
    document.addEventListener('visibilitychange',()=>{for(const state of regions.values())if(document.hidden)state.stop();else state.start();});
    window.addEventListener('pagehide',()=>{for(const state of regions.values())state.stop();});
    window.addEventListener('pageshow',event=>{if(event.persisted)for(const state of regions.values())state.start();});
    window.DashboardData={init:scan};
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
