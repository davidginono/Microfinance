/* Progressive JSP navigation. The server still renders and authorizes every destination. */
(() => {
    'use strict';
    if (window.WorkspaceNavigation) return;
    const ownScript = document.currentScript || document.querySelector('script[data-workspace-navigation]');
    const native = {add:EventTarget.prototype.addEventListener, fetch:window.fetch.bind(window),
        push:history.pushState.bind(history), replace:history.replaceState.bind(history)};
    let scope, booting=false, committing=false, queued, request, generation=0, serial=Date.now(), collapsed;
    let viewKey=history.state?.workspaceNavigation?.key || ++serial;
    let entry={url:location.href,index:history.state?.workspaceNavigation?.index || 0};
    let reversing=false;
    const timers = {};
    for (const [schedule,cancel] of [['setTimeout','clearTimeout'],['setInterval','clearInterval'],['requestAnimationFrame','cancelAnimationFrame'],['requestIdleCallback','cancelIdleCallback']]) {
        if (!window[schedule]) continue;
        const start=window[schedule].bind(window), stop=window[cancel].bind(window);timers[schedule]=start;
        window[schedule]=(callback,...args)=>{
            const owner=scope;let id;
            id=start(typeof callback==='function' ? (...values)=>{
                if(schedule!=='setInterval')owner?.timers.delete(schedule+':'+id);
                if(owner?.alive!==false)callback(...values);
            } : callback,...args);
            owner?.timers.set(schedule+':'+id,()=>stop(id));return id;
        };
        window[cancel]=id=>{scope?.timers.delete(schedule+':'+id);stop(id);};
    }
    const createScope=()=>({alive:true,controller:new AbortController(),timers:new Map(),observers:new Set(),ready:[],load:[]});
    scope=createScope();
    EventTarget.prototype.addEventListener=function(type,callback,options){
        if (!callback) return;
        const owner=scope, settings=typeof options==='boolean'?{capture:options}:{...options};
        if(booting && ((this===document && type==='DOMContentLoaded') || (this===window && type==='load'))){
            owner[type==='load'?'load':'ready'].push(()=>typeof callback==='function'?callback.call(this,new Event(type)):callback.handleEvent(new Event(type)));
            return;
        }
        settings.signal=settings.signal ? AbortSignal.any([settings.signal,owner.controller.signal]) : owner.controller.signal;
        return native.add.call(this,type,callback,settings);
    };
    for (const name of ['MutationObserver','ResizeObserver','IntersectionObserver']) {
        const Original=window[name];if(!Original)continue;
        window[name]=class extends Original {
            constructor(callback){const owner=scope;super((...args)=>{if(owner.alive)callback(...args);});owner.observers.add(this);}
        };
    }
    window.fetch=async(url,options={})=>{
        const owner=scope, signal=options.signal ? AbortSignal.any([options.signal,owner.controller.signal]) : owner.controller.signal;
        const response=await native.fetch(url,{...options,signal});
        if(!owner.alive)throw new DOMException('Workspace changed','AbortError');return response;
    };
    const state=(value,key=viewKey,index=entry.index)=>({...value,workspaceNavigation:{key,index}});
    history.pushState=(value,title,url)=>{native.push(state(value,viewKey,entry.index+1),title,url);entry={url:location.href,index:entry.index+1};};
    history.replaceState=(value,title,url)=>{native.replace(state(value),title,url);entry.url=location.href;};
    native.replace(state(history.state),'',location.href);
    function dispose(){
        window.dispatchEvent(new PageTransitionEvent('pagehide'));
        scope.alive=false;scope.controller.abort();scope.timers.forEach(stop=>stop());scope.observers.forEach(observer=>observer.disconnect());
        // These app singletons otherwise retain observers or closures from the previous JSP.
        for(const key of ['__neoSelectEnhancerLoaded','bindAppConfirmModal','SaccosRepaymentSchedule'])delete window[key];
    }
    function eligible(url){return url.origin===location.origin && !/\/(?:logout|login|auth)(?:\/|$)|\.(?:pdf|xlsx?|csv|zip)$/i.test(url.pathname);}
    function handles(link){
        if(!link?.matches('a[href]') || link.matches('[download],[target]:not([target="_self"]),[data-download-action],[data-print-action]'))return false;
        const href=link.getAttribute('href');if(!href || href.startsWith('#'))return false;
        const url=new URL(link.href,location.href);if(!eligible(url))return false;
        return !!link.closest('#appSidebar') || [...document.querySelectorAll('#appSidebar a[href]')].some(item=>item.href===url.href);
    }
    function clearFeedback(){
        document.getElementById('workspace-navigation-feedback')?.remove();
        const main=document.querySelector('.shell-main.is-workspace-pending');
        if(main){main.classList.remove('is-workspace-pending');main.removeAttribute('aria-busy');}
    }
    const feedback=(failed,url)=>{
        clearFeedback();
        const main=document.querySelector('.shell-main');if(!main)return;
        const box=document.createElement('div');box.id='workspace-navigation-feedback';box.className='workspace-navigation-feedback';box.setAttribute('role',failed?'alert':'status');
        if(!failed){
            main.classList.add('is-workspace-pending');main.setAttribute('aria-busy','true');
            box.classList.add('workspace-navigation-pending');box.setAttribute('aria-label',document.body.dataset.navigationLoading);
            const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='large';spinner.setAttribute('aria-hidden','true');box.append(spinner);
        }else{const label=document.createElement('span');label.textContent=document.body.dataset.navigationError;box.append(label);}
        if(failed){const retry=document.createElement('button');retry.type='button';retry.className='app-btn btn-neutral';retry.textContent=document.body.dataset.navigationRetry;retry.onclick=()=>visit(url,{force:true});box.append(retry);}
        main.prepend(box);
    };
    const allowed=(url,resume)=>document.dispatchEvent(new CustomEvent('workspace:before-navigate',{cancelable:true,detail:{url,resume}}));
    function undoPop(previous,target){reversing=true;history.go(previous.index-target.index);}
    function replaceHead(page){
        const styles=new Map([...document.head.querySelectorAll('link[rel="stylesheet"]')].map(link=>[link.href,link]));
        const keep=new Set(ownScript?[ownScript]:[]), additions=[];
        for(const child of [...page.head.childNodes]){
            const existing=child.nodeType===1 && child.matches('link[rel="stylesheet"]') && styles.get(new URL(child.getAttribute('href'),location.href).href);
            if(existing)keep.add(existing);else additions.push(document.importNode(child,true));
        }
        // Shared styles stay connected, so the header and sidebar never lose their layout.
        for(const child of [...document.head.childNodes])if(!keep.has(child))child.remove();
        document.head.append(...additions);
    }
    function replaceBody(page){
        const fragment=document.createDocumentFragment();
        fragment.append(...[...page.body.childNodes].map(child=>document.importNode(child,true)));
        // Retain the visible chrome roots; refresh their authorized contents and rebind their controls.
        for(const id of ['shellTopNav','appSidebar']){
            const current=document.getElementById(id),fresh=fragment.querySelector('#'+id);if(!current || !fresh)continue;
            [...current.attributes].forEach(attribute=>current.removeAttribute(attribute.name));
            [...fresh.attributes].forEach(attribute=>current.setAttribute(attribute.name,attribute.value));
            current.replaceChildren(...fresh.childNodes);fresh.replaceWith(current);
        }
        document.body.replaceChildren(fragment);
    }
    async function execute(source,key){
        if(source.hasAttribute('src') && new URL(source.getAttribute('src'),location.href).href===ownScript?.src)return;
        await new Promise((resolve,reject)=>{
            const script=document.createElement('script');
            for(const attribute of source.attributes)if(!['async','defer','src'].includes(attribute.name))script.setAttribute(attribute.name,attribute.value);
            if(source.src){
                let url=new URL(source.getAttribute('src'),location.href);
                // Modules are otherwise evaluated once per document, including form-specific modules.
                if(source.type==='module')url.searchParams.set('workspace',String(key));
                script.src=url.href;script.async=false;
                const timeout=timers.setTimeout(()=>{script.remove();reject(new Error('Workspace script'));},20000);
                native.add.call(script,'load',()=>{clearTimeout(timeout);resolve();},{once:true});
                native.add.call(script,'error',()=>{clearTimeout(timeout);reject(new Error('Workspace script'));},{once:true});
            }else script.textContent='{\n'+source.textContent+'\n}';
            document.head.append(script);
            if(!source.src && source.type!=='module')resolve();
        });
    }
    async function visit(destination,options={}){
        const url=new URL(destination,location.href);if(!eligible(url))return false;
        if(committing){queued=[url.href,options];return true;}
        if(!options.force && !options.pop && url.href===location.href){++generation;request?.abort();clearFeedback();return true;}
        if(!allowed(url.href,()=>options.pop?history.go(options.pop.index-options.previous.index):visit(url.href,options))){
            if(options.pop)undoPop(options.previous,options.pop);return false;
        }
        request?.abort();const controller=new AbortController();request=controller;const version=++generation;
        let didCommit=false;
        const timeout=timers.setTimeout(()=>controller.abort(),20000);feedback(false,url.href);
        try{
            const response=await native.fetch(url.href,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},signal:controller.signal});
            if(version!==generation)return true;
            const finalUrl=new URL(response.url || url.href,location.href);
            if(finalUrl.origin!==location.origin)throw new Error('Workspace origin');
            if(/\/(?:login|auth)(?:\/|$)/i.test(finalUrl.pathname)){location.assign(finalUrl.href);return true;}
            if(!response.ok || !response.headers.get('Content-Type')?.includes('text/html'))throw new Error('Workspace response');
            const page=new DOMParser().parseFromString(await response.text(),'text/html');
            if(version!==generation)return true;
            if(!page.body.matches('.aws-console') || !page.querySelector('.shell-main') || !page.querySelector('#appSidebar'))throw new Error('Workspace shell');
            if(!allowed(url.href,()=>options.pop?history.go(options.pop.index-options.previous.index):visit(url.href,options))){if(options.pop)undoPop(options.previous,options.pop);return false;}
            committing=true;didCommit=true;
            collapsed=window.matchMedia('(min-width: 1024px)').matches?document.documentElement.classList.contains('shell-sidebar-collapsed'):(collapsed ?? true);
            const scripts=[...page.querySelectorAll('script')].filter(script=>!script.type || /^(?:text\/javascript|application\/javascript|module)$/.test(script.type));
            scripts.forEach(script=>script.remove());
            dispose();scope=createScope();booting=true;
            for(const element of [document.documentElement,document.body]){
                const fresh=element===document.body?page.body:page.documentElement;
                [...element.attributes].forEach(attribute=>element.removeAttribute(attribute.name));
                [...fresh.attributes].forEach(attribute=>element.setAttribute(attribute.name,attribute.value));
            }
            document.documentElement.classList.toggle('shell-sidebar-collapsed',collapsed && window.matchMedia('(min-width: 1024px)').matches);
            replaceHead(page);
            replaceBody(page);
            const main=document.querySelector('.shell-main');main.inert=true;main.dataset.workspaceInitializing='true';
            viewKey=options.pop?.key || ++serial;
            if(options.pop){entry={url:finalUrl.href,index:options.pop.index};native.replace(state(history.state),'',finalUrl.href);}
            else{entry={url:finalUrl.href,index:entry.index+1};native.push(state({},viewKey,entry.index),'',finalUrl.href);}
            const deferred=scripts.filter(script=>script.defer || script.type==='module');
            for(const script of scripts.filter(script=>!deferred.includes(script)))await execute(script,version);
            for(const script of deferred)await execute(script,version);
            for(const callback of scope.ready.splice(0))callback();
            for(const callback of scope.load.splice(0))callback();
            booting=false;window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:false}));
            const title=document.querySelector('.erp-page-title,h1');if(title){title.setAttribute('tabindex','-1');title.focus({preventScroll:true});}
            if(!options.pop)window.scrollTo({top:0,left:0,behavior:'instant'});
            document.dispatchEvent(new CustomEvent('workspace:navigated',{detail:{url:finalUrl.href}}));
        }catch(error){
            if(version===generation){if(options.pop && !didCommit)undoPop(options.previous,options.pop);feedback(true,url.href);}
        }finally{
            clearTimeout(timeout);
            if(didCommit){booting=false;committing=false;const main=document.querySelector('[data-workspace-initializing]');if(main){main.inert=false;delete main.dataset.workspaceInitializing;}}
            if(version===generation && document.getElementById('workspace-navigation-feedback')?.getAttribute('role')==='status')clearFeedback();
            if(didCommit && queued){const next=queued;queued=null;void visit(...next);}
        }
        return true;
    }
    window.WorkspaceNavigation={visit,handles,rememberSidebar(value){collapsed=value;},get sidebarCollapsed(){return collapsed;},get restoring(){return committing;}};
    native.add.call(window,'click',event=>{
        if(event.defaultPrevented || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
        const link=event.target.closest?.('a[href]');if(!handles(link))return;
        event.preventDefault();void visit(link.href);
    });
    native.add.call(window,'popstate',event=>{
        const marker=event.state?.workspaceNavigation,previous={...entry};
        if(reversing){reversing=false;entry={url:location.href,index:marker?.index || 0};return;}
        if(!marker || marker.key===viewKey){entry={url:location.href,index:marker?.index || 0};return;}
        event.stopImmediatePropagation();
        void visit(location.href,{pop:{key:marker.key,index:marker.index},previous});
    });
})();
