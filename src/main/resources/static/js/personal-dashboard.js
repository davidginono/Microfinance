/* Personal activity counts reuse scoped queues. Only two visible widgets load at once. */
(() => {
    'use strict';
    const regions=new Map(),queue=[];let active=0;
    const drain=()=>{while(active<2&&queue.length){const state=queue.shift();state.queued=false;if(!state.visible())continue;active++;state.load().finally(()=>{active--;drain();});}};
    function init(root){
        if(regions.has(root))return;
        const labels=document.getElementById('dashboard-labels')?.dataset||{},status=root.querySelector('[data-activity-status]'),refresh=root.querySelector('[data-activity-refresh]');
        const targets=[...root.querySelectorAll('[data-activity-count]')];targets.forEach(t=>t.setAttribute('data-ui-loading-region',''));
        let request=null,version=0,loaded=false;
        const visible=()=>root.isConnected&&!root.hidden&&!document.hidden;
        const end=()=>{targets.forEach(t=>window.AppLoading?.end(t));root.removeAttribute('aria-busy');refresh.disabled=false;};
        const state={queued:false,visible,stop(){version++;request?.abort();request=null;const index=queue.indexOf(state);if(index>=0)queue.splice(index,1);state.queued=false;end();},
            enqueue(force=false){if(!visible()||request||state.queued||loaded&&!force)return;state.queued=true;queue.push(state);drain();},
            async load(){
                const current=++version,controller=new AbortController();request=controller;
                const timeout=setTimeout(()=>controller.abort(),20000);root.setAttribute('aria-busy','true');refresh.disabled=true;status.textContent='';status.setAttribute('role','status');
                targets.forEach(t=>window.AppLoading?.begin(t,{table:false,replace:true,label:labels.loading,size:'small'}));
                try{
                    const url=new URL(root.dataset.activityUrl,location.href);if(url.origin!==location.origin)throw new Error('scope');
                    const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:controller.signal});
                    if((response.status===403||response.status===401||response.redirected)&&current===version){end();targets.forEach(t=>t.textContent='—');}
                    if(!response.ok||response.redirected)throw new Error('request');
                    const payload=await response.json();
                    if(current!==version||!visible())return;
                    if(payload.source!==root.dataset.personalActivity||!Array.isArray(payload.entries)||payload.entries.length!==targets.length)throw new Error('payload');
                    const values=targets.map(target=>{
                        const matches=payload.entries.filter(e=>e.office===target.dataset.activityCount),entry=matches[0];
                        if(matches.length!==1||!Number.isInteger(entry.shown)||entry.shown<0||entry.shown>25||typeof entry.more!=='boolean')throw new Error('count');
                        return new Intl.NumberFormat(document.documentElement.lang||'en').format(entry.shown)+(entry.more?'+':'');
                    });
                    // End loading first so its original-node recovery cannot replace the fresh counts.
                    end();targets.forEach((target,index)=>target.textContent=values[index]);loaded=true;
                }catch(error){if(current===version&&visible()){status.setAttribute('role','alert');status.textContent=labels.loadError;}}
                finally{clearTimeout(timeout);if(current===version){end();request=null;}}
            }};
        refresh.addEventListener('click',()=>state.enqueue(true));
        root.addEventListener('dashboard:visibility',()=>{if(visible())state.enqueue(true);else state.stop();});
        regions.set(root,state);state.enqueue();
    }
    function scan(){if(!window.document)return;for(const [root,state] of regions)if(!root.isConnected){state.stop();regions.delete(root);}document.querySelectorAll('[data-personal-activity]').forEach(init);}
    const start=()=>{scan();new MutationObserver(records=>{if(records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1&&(n.matches?.('[data-personal-activity]')||n.querySelector?.('[data-personal-activity]')))))scan();}).observe(document.body,{childList:true,subtree:true});};
    document.addEventListener('visibilitychange',()=>{for(const state of regions.values())if(document.hidden)state.stop();else state.enqueue(true);});
    window.addEventListener('pagehide',()=>{for(const state of regions.values())state.stop();});
    window.addEventListener('pageshow',event=>{if(event.persisted)for(const state of regions.values())state.enqueue(true);});
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
