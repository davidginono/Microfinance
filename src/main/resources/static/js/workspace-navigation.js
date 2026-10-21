/* Progressive JSP navigation. The server still renders and authorizes every destination. */
(() => {
    'use strict';
    if (window.WorkspaceNavigation) return;
    const ownScript = document.currentScript || document.querySelector('script[data-workspace-navigation]');
    const native = {add:EventTarget.prototype.addEventListener, fetch:window.fetch.bind(window), ResizeObserver:window.ResizeObserver,
        push:history.pushState.bind(history), replace:history.replaceState.bind(history)};
    let scope, booting=false, committing=false, queued, request, generation=0, serial=Date.now(), collapsed;
    let viewKey=history.state?.workspaceNavigation?.key || ++serial;
    let entry={url:location.href,index:history.state?.workspaceNavigation?.index || 0};
    let reversing=false, pendingDestination, layoutObserver;
    const submissions=new WeakSet();
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
    document.documentElement.dataset.workspaceBooting='true';
    let documentReady=document.readyState==='complete';
    function onReady(callback){
        if(booting)scope.ready.push(callback);
        else if(!documentReady)document.addEventListener('DOMContentLoaded',callback,{once:true});
        else callback();
    }
    function prepare(main){
        if(!main || main.hasAttribute('data-workspace-initializing'))return;
        main.dataset.workspaceInitializing='true';main.inert=true;main.setAttribute('aria-busy','true');
        const status=document.createElement('div');status.dataset.workspaceBootStatus='true';status.className='workspace-initializing-status';
        status.setAttribute('role','status');status.setAttribute('aria-label',document.body.dataset.navigationLoading || '');
        const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='large';spinner.setAttribute('aria-hidden','true');status.append(spinner);main.prepend(status);
    }
    function reveal(){
        document.documentElement.removeAttribute('data-workspace-booting');
        const main=document.querySelector('[data-workspace-initializing]');
        if(main){main.querySelector('[data-workspace-boot-status]')?.remove();main.inert=false;main.removeAttribute('aria-busy');delete main.dataset.workspaceInitializing;}
    }
    native.add.call(document,'DOMContentLoaded',()=>{documentReady=true;timers.requestAnimationFrame(()=>{if(!committing)reveal();});},{once:true});
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
    function eligible(url){return url.origin===location.origin && !/\/(?:logout|login|auth|pdf|print|export|download)(?:\/|$)|\.(?:pdf|xlsx?|csv|zip|png|jpe?g|gif|svg)$/i.test(url.pathname);}
    function handles(link){
        if(!link?.matches('a[href]') || link.matches('[download],[target]:not([target="_self"]),[data-download-action],[data-print-action],[data-native-navigation]'))return false;
        const href=link.getAttribute('href');if(!href || href.startsWith('#'))return false;
        const url=new URL(link.href,location.href);if(!eligible(url))return false;
        if(url.pathname===location.pathname && url.search===location.search && url.hash)return false;
        return document.body.matches('.aws-console') && !!document.querySelector('.shell-main');
    }
    function handlesForm(form,submitter){
        if(!form?.matches('form') || !document.body.matches('.aws-console') || !form.closest('.shell-main,[role="dialog"]') || form.matches('[data-native-navigation]'))return false;
        const method=(submitter?.getAttribute('formmethod') || form.method || 'get').toLowerCase();
        const target=submitter?.getAttribute('formtarget') || form.target;
        if((target && target!=='_self') || (method!=='get' && !(method==='post' && form.hasAttribute('data-workspace-submit'))))return false;
        return eligible(new URL(submitter?.getAttribute('formaction') || form.action || location.href,location.href));
    }
    function navigate(destination,options={}){
        const url=new URL(destination,location.href);
        if(eligible(url))return visit(url.href,options);
        if(url.origin===location.origin && /\/(?:pdf|export|download)(?:\/|$)|\.(?:pdf|xlsx?|csv|zip)$/i.test(url.pathname) && window.downloadExport)return window.downloadExport(url.href);
        if(url.origin===location.origin && /\/print(?:\/|$)/i.test(url.pathname) && window.printExport)return window.printExport(url.href);
        location.assign(url.href);
    }
    // Geometry only, never cloned page HTML or business data. First visits use the
    // normal workspace spinner until the authorized destination HTML arrives.
    const layouts=new Map();
    const sections='.dashboard-widget,.erp-panel,.erp-resource-section,.erp-detail-card,.accounting-section,.loan-flow-panel,[data-resource-table],.erp-table-wrap,.erp-stat-card';
    function layoutSpace(main){
        const rect=main.getBoundingClientRect(),css=getComputedStyle(main),number=name=>parseFloat(css[name]) || 0;
        return {x:rect.left+number('borderLeftWidth')+number('paddingLeft'),y:rect.top+number('borderTopWidth')+number('paddingTop'),
            width:Math.round(rect.width-number('borderLeftWidth')-number('borderRightWidth')-number('paddingLeft')-number('paddingRight'))};
    }
    function layoutKey(url,main){
        const target=new URL(url,location.href);target.hash='';
        return [target.href,innerWidth,layoutSpace(main).width,document.documentElement.lang].join('|');
    }
    function rememberLayout(){
        const main=document.querySelector('.shell-main');
        if(!main || main.hasAttribute('aria-busy') || main.hasAttribute('data-workspace-initializing'))return;
        const space=layoutSpace(main);if(space.width<200)return;
        const blocks=[];
        for(const element of [...main.querySelectorAll(sections)].slice(0,128)){
            if(element.parentElement.closest(sections) || element.closest('[hidden],[aria-hidden="true"],dialog'))continue;
            const rect=element.getBoundingClientRect();
            if(rect.width<80 || rect.height<48 || rect.top-space.y<0 || rect.top-space.y>16000)continue;
            const header=element.querySelector('table thead') || element.querySelector(':scope > .erp-panel-header,:scope > .app-table-titlebar,:scope > h2,:scope > summary');
            blocks.push({x:rect.left-space.x,y:rect.top-space.y,width:rect.width,height:Math.min(4000,rect.height),
                header:header?Math.min(rect.height,Math.max(0,header.getBoundingClientRect().bottom-rect.top)):0});
            if(blocks.length===32)break;
        }
        if(!blocks.length)return;
        const title=main.querySelector('.erp-page-title,h1')?.getBoundingClientRect();
        const heading=title && title.height>0?{x:title.left-space.x,y:Math.max(0,title.top-space.y),width:Math.min(280,title.width),height:title.height}:null;
        const key=layoutKey(entry.url,main);layouts.delete(key);
        layouts.set(key,{blocks,heading,height:Math.max(...blocks.map(block=>block.y+block.height))});
        if(layouts.size>12)layouts.delete(layouts.keys().next().value);
    }
    function layoutPreview(main,url,box){
        const layout=layouts.get(layoutKey(url,main));if(!layout)return false;
        box.classList.add('workspace-layout-preview');box.style.height=layout.height+'px';
        const position=(element,rect)=>{for(const name of ['width','height'])element.style[name]=rect[name]+'px';element.style.left=rect.x+'px';element.style.top=rect.y+'px';};
        if(layout.heading){const heading=document.createElement('div');heading.className='workspace-layout-heading';heading.setAttribute('aria-hidden','true');position(heading,layout.heading);box.append(heading);}
        for(const block of layout.blocks){
            const card=document.createElement('div');card.className='workspace-layout-card';card.setAttribute('aria-hidden','true');position(card,block);
            if(block.header){const header=document.createElement('div');header.className='workspace-layout-card-header';header.style.height=block.header+'px';card.append(header);}
            const body=document.createElement('div');body.className='workspace-layout-card-body';body.style.top=block.header+'px';
            const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='medium';body.append(spinner);card.append(body);box.append(card);
        }
        main.classList.add('is-workspace-preview');return true;
    }
    function clearFeedback(){
        layoutObserver?.disconnect();layoutObserver=null;pendingDestination=null;
        document.getElementById('workspace-navigation-feedback')?.remove();
        const main=document.querySelector('.shell-main.is-workspace-pending');
        if(main){main.classList.remove('is-workspace-pending','is-workspace-preview');main.removeAttribute('aria-busy');}
    }
    const feedback=(failed,url,options={})=>{
        if(!failed)rememberLayout();
        clearFeedback();
        const main=document.querySelector('.shell-main');if(!main)return;
        pendingDestination=failed?null:url;
        const box=document.createElement('div');box.id='workspace-navigation-feedback';box.className='workspace-navigation-feedback';box.setAttribute('role',failed?'alert':'status');
        if(!failed){
            main.classList.add('is-workspace-pending');main.setAttribute('aria-busy','true');
            box.classList.add('workspace-navigation-pending');box.setAttribute('aria-label',document.body.dataset.navigationLoading);
            if(!layoutPreview(main,url,box)){
                box.classList.add('workspace-navigation-spinner');
                const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.loadingSize='large';spinner.setAttribute('aria-hidden','true');box.append(spinner);
            }
            else if(native.ResizeObserver){
                const key=layoutKey(url,main);
                layoutObserver=new native.ResizeObserver(()=>{
                    if(box.isConnected && layoutKey(url,main)!==key)feedback(false,url);
                });layoutObserver.observe(main);
            }
        }else{const label=document.createElement('span');label.textContent=document.body.dataset.navigationError;box.append(label);}
        if(failed){const retry=document.createElement('button');retry.type='button';retry.className='app-btn btn-neutral';retry.textContent=document.body.dataset.navigationRetry;retry.onclick=()=>{
            if(!options.submission)return visit(url,{force:true});
            const {form,submitter}=options.submission;if(submitter)form.requestSubmit(submitter);else form.requestSubmit();
        };box.append(retry);}
        main.prepend(box);
    };
    native.add.call(window,'resize',()=>{
        if(document.getElementById('workspace-navigation-feedback')?.getAttribute('role')==='status')feedback(false,pendingDestination);
    });
    native.add.call(document,'session:logout',()=>layouts.clear());
    const allowed=(url,resume)=>document.dispatchEvent(new CustomEvent('workspace:before-navigate',{cancelable:true,detail:{url,resume}}));
    function undoPop(previous,target){reversing=true;history.go(previous.index-target.index);}
    async function prepareStyles(page,signal){
        // Body-owned page styles must be ready before their new components become visible.
        for(const style of page.body.querySelectorAll('link[rel="stylesheet"],style'))page.head.append(style);
        const current=new Map([...document.head.querySelectorAll('link[rel="stylesheet"]')].map(link=>[link.href,link]));
        const staged=new Map(),pending=new Set();
        try{
            await Promise.all([...page.head.querySelectorAll('link[rel="stylesheet"]')].map(source=>{
                const href=new URL(source.getAttribute('href'),location.href).href;
                if(current.has(href) || staged.has(href))return;
                const link=document.importNode(source,true);link.media='not all';staged.set(href,link);
                return new Promise((resolve,reject)=>{
                    let settled=false;
                    const finish=error=>{if(settled)return;settled=true;clearTimeout(timeout);pending.delete(abort);signal.removeEventListener('abort',abort);error?reject(error):resolve();};
                    const abort=()=>finish(new DOMException('Workspace changed','AbortError'));
                    const timeout=timers.setTimeout(()=>finish(new Error('Workspace stylesheet')),20000);
                    native.add.call(link,'load',()=>finish(),{once:true});native.add.call(link,'error',()=>finish(new Error('Workspace stylesheet')),{once:true});
                    pending.add(abort);signal.addEventListener('abort',abort,{once:true});document.head.append(link);
                    if(signal.aborted)abort();
                });
            }));
            return staged;
        }catch(error){pending.forEach(abort=>abort());staged.forEach(link=>link.remove());throw error;}
    }
    function replaceHead(page,staged){
        const styles=new Map([...document.head.querySelectorAll('link[rel="stylesheet"]')].map(link=>[link.href,link]));
        const keep=new Set(ownScript?[ownScript]:[]), additions=[];
        for(const child of [...page.head.childNodes]){
            const existing=child.nodeType===1 && child.matches('link[rel="stylesheet"]') && styles.get(new URL(child.getAttribute('href'),location.href).href);
            if(existing){
                keep.add(existing);additions.push(existing);
                if(staged.has(existing.href)){if(child.hasAttribute('media'))existing.media=child.media;else existing.removeAttribute('media');}
            }else additions.push(document.importNode(child,true));
        }
        // Shared styles stay connected, so the header and sidebar never lose their layout.
        for(const child of [...document.head.childNodes])if(!keep.has(child))child.remove();
        // Do not detach/reappend unchanged styles: that can reload them and flash the shell.
        let anchor=null;
        for(const child of additions.reverse()){
            if(child.parentNode!==document.head || (anchor && child.compareDocumentPosition(anchor)&Node.DOCUMENT_POSITION_PRECEDING))document.head.insertBefore(child,anchor);
            anchor=child;
        }
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
        let didCommit=false,initialized=false,staged;
        const timeout=timers.setTimeout(()=>controller.abort(),20000);feedback(false,url.href);
        try{
            const response=await native.fetch(url.href,{...options.submission?.request,credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},signal:controller.signal});
            if(version!==generation)return true;
            const finalUrl=new URL(response.url || url.href,location.href);
            if(finalUrl.origin!==location.origin)throw new Error('Workspace origin');
            if(/\/(?:login|auth)(?:\/|$)/i.test(finalUrl.pathname)){location.assign(finalUrl.href);return true;}
            if(!response.ok || !response.headers.get('Content-Type')?.includes('text/html'))throw new Error('Workspace response');
            const page=new DOMParser().parseFromString(await response.text(),'text/html');
            if(version!==generation)return true;
            if(!page.body.matches('.aws-console') || !page.querySelector('.shell-main') || !page.querySelector('#appSidebar'))throw new Error('Workspace shell');
            staged=await prepareStyles(page,controller.signal);
            if(version!==generation)return true;
            if(!allowed(url.href,()=>options.pop?history.go(options.pop.index-options.previous.index):visit(url.href,options))){if(options.pop)undoPop(options.previous,options.pop);return false;}
            clearFeedback();committing=true;didCommit=true;
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
            replaceHead(page,staged);
            replaceBody(page);
            prepare(document.querySelector('.shell-main'));
            viewKey=options.pop?.key || ++serial;
            if(options.pop || options.replace){entry={url:finalUrl.href,index:options.pop?.index ?? entry.index};native.replace(state(history.state),'',finalUrl.href);}
            else{entry={url:finalUrl.href,index:entry.index+1};native.push(state({},viewKey,entry.index),'',finalUrl.href);}
            const deferred=scripts.filter(script=>script.defer || script.type==='module');
            for(const script of scripts.filter(script=>!deferred.includes(script)))await execute(script,version);
            for(const script of deferred)await execute(script,version);
            for(const callback of scope.ready.splice(0))callback();
            for(const callback of scope.load.splice(0))callback();
            await new Promise(resolve=>timers.requestAnimationFrame(resolve));
            reveal();
            initialized=true;
            booting=false;window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:false}));
            const title=document.querySelector('.erp-page-title,h1');if(title){title.setAttribute('tabindex','-1');title.focus({preventScroll:true});}
            if(!options.pop){const anchor=finalUrl.hash && document.getElementById(decodeURIComponent(finalUrl.hash.slice(1)));if(anchor)anchor.scrollIntoView();else window.scrollTo({top:0,left:0,behavior:'instant'});}
            document.dispatchEvent(new CustomEvent('workspace:navigated',{detail:{url:finalUrl.href}}));
        }catch(error){
            if(version===generation){if(options.pop && !didCommit)undoPop(options.previous,options.pop);feedback(true,url.href,options);}
        }finally{
            clearTimeout(timeout);
            if(!didCommit)staged?.forEach(link=>link.remove());
            if(didCommit){
                booting=false;committing=false;
                if(initialized)reveal();
                else{const main=document.querySelector('[data-workspace-initializing]');if(main){main.inert=false;main.removeAttribute('aria-busy');main.querySelector('[data-workspace-boot-status]')?.remove();}}
            }
            if(version===generation && document.getElementById('workspace-navigation-feedback')?.getAttribute('role')==='status')clearFeedback();
            if(didCommit && queued){const next=queued;queued=null;void visit(...next);}
        }
        return true;
    }
    window.WorkspaceNavigation={visit,navigate,handles,handlesForm,onReady,prepare,rememberSidebar(value){collapsed=value;},get sidebarCollapsed(){return collapsed;},get restoring(){return committing;}};
    native.add.call(window,'click',event=>{
        if(event.defaultPrevented || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
        const link=event.target.closest?.('a[href]');if(!handles(link))return;
        event.preventDefault();void visit(link.href);
    });
    native.add.call(window,'submit',event=>{
        const form=event.target,submitter=event.submitter;
        if(event.defaultPrevented || !handlesForm(form,submitter))return;
        if(submissions.has(form)){event.preventDefault();return;}
        const url=new URL(submitter?.getAttribute('formaction') || form.action || location.href,location.href);
        const method=(submitter?.getAttribute('formmethod') || form.method || 'get').toLowerCase();
        const data=new FormData(form);
        if(submitter?.name && !data.has(submitter.name))data.append(submitter.name,submitter.value);
        event.preventDefault();
        if(method==='get'){
            url.search='';for(const [name,value] of data)url.searchParams.append(name,typeof value==='string'?value:value.name);
            void visit(url.href,{force:true});
        }else{
            // Explicitly opted-in report generation retains its CSRF token and idempotency key.
            // Mutation/financial forms keep their existing submit and confirmation controllers.
            const body=new URLSearchParams();for(const [name,value] of data)if(typeof value==='string')body.append(name,value);
            submissions.add(form);
            void visit(url.href,{force:true,submission:{form,submitter,request:{method:'POST',body}}}).finally(()=>submissions.delete(form));
        }
    });
    native.add.call(window,'popstate',event=>{
        const marker=event.state?.workspaceNavigation,previous={...entry};
        if(reversing){reversing=false;entry={url:location.href,index:marker?.index || 0};return;}
        if(!marker || marker.key===viewKey){entry={url:location.href,index:marker?.index || 0};return;}
        event.stopImmediatePropagation();
        void visit(location.href,{pop:{key:marker.key,index:marker.index},previous});
    });
})();
