/* Configurable JSP dashboard. Only layout preferences are persisted; native content stays mounted. */
(() => {
    'use strict';
    const boards=new Map();
    const ready=()=>{for(const root of boards.keys())if(!root.querySelector('[data-dashboard-chart]:not([data-chart-ready])')){root.dataset.dashboardReady='true';root.setAttribute('aria-busy','false');}};
    const node=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;};
    const context=()=>document.getElementById('dashboard-labels')?.dataset || {};
    const hash=value=>{let h=14695981039346656037n;for(const c of value){h^=BigInt(c.codePointAt(0));h=BigInt.asUintN(64,h*1099511628211n);}return h.toString(16);};
    const bounded=(value,min,max,fallback)=>Number.isFinite(Number(value))?Math.max(min,Math.min(max,Math.round(Number(value)))):fallback;
    function init(root) {
        if(boards.has(root))return;
        const widgets=[...root.querySelectorAll('[data-dashboard-widget]')];if(!widgets.length)return;
        const l=context(),lifetime=new AbortController(),signal=lifetime.signal;
        const voucherMenu=root.querySelector('[data-dashboard-voucher-menu]');
        if(voucherMenu){
            const closeVoucherMenu=restore=>{voucherMenu.open=false;if(restore)voucherMenu.querySelector('summary').focus();};
            voucherMenu.addEventListener('keydown',event=>{if(event.key==='Escape'&&voucherMenu.open){event.preventDefault();event.stopPropagation();closeVoucherMenu(true);}},{signal});
            document.addEventListener('click',event=>{if(!voucherMenu.contains(event.target))closeVoucherMenu(false);},{signal});
            document.addEventListener('focusin',event=>{if(!voucherMenu.contains(event.target))closeVoucherMenu(false);},{signal});
            voucherMenu.querySelectorAll('a').forEach(link=>link.addEventListener('click',()=>closeVoucherMenu(false),{signal}));
        }
        const entries=new Map(),grid=node('div','dashboard-grid');root.append(grid);
        const header=root.closest('.shell-content-frame')?.querySelector('.erp-page-header') || document.querySelector('.erp-page-header');
        header?.querySelector('.dashboard-toolbar')?.remove();
        const toolbar=node('div','dashboard-toolbar'),reset=node('button','app-btn btn-neutral',l.reset),add=node('button','app-btn btn-launch',l.add);
        reset.type=add.type='button';toolbar.append(reset,add);(header || root).append(toolbar);
        const icon=name=>{const img=node('img','dashboard-icon');img.src=l.icons+name+'.svg';img.alt='';img.draggable=false;img.setAttribute('aria-hidden','true');return img;};
        add.prepend(icon('add-plus'));
        const button=(name,label,cls='')=>{const b=node('button','dashboard-icon-button '+cls);b.type='button';b.title=label;b.setAttribute('aria-label',label);b.append(icon(name));return b;};
        const feedback=node('span','sr-only');feedback.setAttribute('role','status');root.append(feedback);
        const announce=text=>{feedback.textContent='';queueMicrotask(()=>feedback.textContent=text);};
        const key='dashboard-layout:v1:'+hash((l.scope || '')+'|'+location.pathname)+':'+root.dataset.dashboardBoard;
        const empty=node('div','dashboard-empty',l.empty);empty.hidden=true;root.append(empty);
        const palette=node('aside','dashboard-palette');palette.hidden=true;palette.id='dashboard-palette-'+root.dataset.dashboardBoard;
        palette.setAttribute('role','complementary');palette.setAttribute('aria-label',l.add);add.setAttribute('aria-controls',palette.id);add.setAttribute('aria-expanded','false');
        const paletteHeader=node('div','dashboard-palette-header'),paletteTitle=node('h2','',l.add),close=button('close',l.close);
        paletteHeader.append(paletteTitle,close);
        const paletteBody=node('div','dashboard-palette-body'),search=node('input');search.type='search';search.placeholder=l.search;search.setAttribute('aria-label',l.search);
        const choices=node('div','dashboard-palette-list'),noMatches=node('p','dashboard-data-status',l.noMatches),clearSearch=node('button','app-btn btn-neutral',l.clear);clearSearch.type='button';noMatches.hidden=clearSearch.hidden=true;paletteBody.append(search,choices,noMatches,clearSearch);palette.append(paletteHeader,paletteBody);
        const scrim=node('div','dashboard-palette-scrim');scrim.hidden=true;document.body.append(scrim,palette);
        const narrow=()=>window.innerWidth<1200;
        let background=null;
        const unlock=()=>{if(!background)return;for(const [n,inert] of background.nodes)n.inert=inert;document.body.style.overflow=background.body;document.documentElement.style.overflow=background.html;background=null;};
        const lock=()=>{if(background)return;background={body:document.body.style.overflow,html:document.documentElement.style.overflow,nodes:[...document.body.children].filter(n=>n!==palette && n!==scrim).map(n=>[n,!!n.inert])};background.nodes.forEach(([n])=>n.inert=true);document.body.style.overflow='hidden';document.documentElement.style.overflow='hidden';};
        const closePalette=(restore=true)=>{unlock();palette.hidden=true;scrim.hidden=true;root.classList.remove('dashboard-palette-open');header?.classList.remove('dashboard-palette-heading-open');add.setAttribute('aria-expanded','false');if(restore)add.focus();};
        const setPaletteMode=()=>{if(palette.hidden)return;palette.setAttribute('role',narrow()?'dialog':'complementary');if(narrow()){palette.setAttribute('aria-modal','true');lock();}else{palette.removeAttribute('aria-modal');unlock();}scrim.hidden=!narrow();};
        let dragged=null;
        const order=()=>[...grid.children].map(item=>item.dataset.dashboardWidget);
        const persist=()=>{try{localStorage.setItem(key,JSON.stringify({version:1,items:order().map(id=>{const e=entries.get(id);return {id,width:e.width,height:e.height,hidden:e.widget.hidden};})}));}catch{/* Preferences are optional. */}};
        const updatePalette=()=>{
            let matches=0;const query=search.value.trim().toLocaleLowerCase();
            for(const e of entries.values()){
                e.paletteItem.hidden=!e.title.toLocaleLowerCase().includes(query);if(!e.paletteItem.hidden)matches++;
                e.addButton.disabled=!e.widget.hidden;e.addButton.textContent=e.widget.hidden?l.addItem:l.added;
                e.paletteItem.draggable=e.widget.hidden;
            }
            noMatches.hidden=clearSearch.hidden=matches>0;empty.hidden=[...entries.values()].some(e=>!e.widget.hidden);
        };
        const setSize=e=>{e.widget.dataset.boardWidth=String(e.width);e.widget.style.setProperty('--widget-height',e.height+'px');};
        const show=e=>{const wasHidden=e.widget.hidden;e.widget.hidden=false;setSize(e);updatePalette();if(wasHidden)e.widget.dispatchEvent(new CustomEvent('dashboard:visibility',{bubbles:true}));};
        const visible=()=>[...grid.children].filter(item=>!item.hidden);
        const move=(e,delta)=>{const items=visible(),index=items.indexOf(e.widget),target=items[index+delta];if(!target)return;if(delta<0)target.before(e.widget);else target.after(e.widget);persist();e.drag.focus();announce(l.moved+' '+e.title);};
        const clearDrop=()=>grid.querySelectorAll('[data-drop-side]').forEach(n=>{n.removeAttribute('data-drop-side');n.classList.remove('is-drop-target');});
        const dropPosition=(x,y,entry)=>{
            const bounds=grid.getBoundingClientRect();if(x<bounds.left || x>bounds.right || y<bounds.top-24 || y>bounds.bottom+32)return null;
            const candidates=visible().filter(w=>w!==entry.widget);if(!candidates.length)return null;
            const target=candidates.reduce((best,w)=>{const r=w.getBoundingClientRect(),distance=Math.hypot(Math.max(r.left-x,0,x-r.right),Math.max(r.top-y,0,y-r.bottom));return !best || distance<best.distance?{widget:w,rect:r,distance}:best;},null);
            const r=target.rect,dx=(x-r.left-r.width/2)/Math.max(r.width,1),dy=(y-r.top-r.height/2)/Math.max(r.height,1);
            return {widget:target.widget,after:Math.abs(dx)>Math.abs(dy)?dx>0:dy>0};
        };
        const place=(entry,target)=>{if(!target)return;show(entry);if(target.after)target.widget.after(entry.widget);else target.widget.before(entry.widget);persist();announce(l.moved+' '+entry.title);};
        const pointerMove=(entry,handle,event)=>{
            if(event.button!==0 || event.isPrimary===false)return;event.preventDefault();handle.focus({preventScroll:true});handle.setPointerCapture?.(event.pointerId);
            const operation=new AbortController();let active=false,target=null,x=event.clientX,y=event.clientY,frame;
            const ghost=node('div','dashboard-drag-preview',entry.title);ghost.setAttribute('aria-hidden','true');
            const update=()=>{clearDrop();target=dropPosition(x,y,entry);if(target){target.widget.dataset.dropSide=target.after?'after':'before';target.widget.classList.add('is-drop-target');}ghost.style.left=(x+14)+'px';ghost.style.top=(y+14)+'px';};
            const scroll=()=>{if(!active)return;const edge=64,top=Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--shell-nav-height'))||44;const delta=y<top+edge?-Math.ceil((top+edge-y)/5):y>innerHeight-edge?Math.ceil((y-innerHeight+edge)/5):0;if(delta){window.scrollBy(0,Math.max(-24,Math.min(24,delta)));update();}frame=requestAnimationFrame(scroll);};
            const finish=(commit=false)=>{operation.abort();signal.removeEventListener('abort',cancel);cancelAnimationFrame(frame);ghost.remove();clearDrop();entry.widget.classList.remove('is-dragging');root.classList.remove('dashboard-is-moving');if(handle.hasPointerCapture?.(event.pointerId))handle.releasePointerCapture(event.pointerId);if(active&&commit)place(entry,target);handle.focus({preventScroll:true});};
            const cancel=()=>finish(false);
            window.addEventListener('pointermove',ev=>{if(ev.pointerId!==event.pointerId)return;x=ev.clientX;y=ev.clientY;if(!active&&Math.hypot(x-event.clientX,y-event.clientY)>=6){active=true;entry.widget.classList.add('is-dragging');root.classList.add('dashboard-is-moving');document.body.append(ghost);frame=requestAnimationFrame(scroll);}if(active){ev.preventDefault();update();}},{signal:operation.signal});
            window.addEventListener('pointerup',ev=>{if(ev.pointerId===event.pointerId)finish(true);},{signal:operation.signal});
            for(const name of ['pointercancel','lostpointercapture'])handle.addEventListener(name,ev=>{if(ev.pointerId===event.pointerId)cancel();},{signal:operation.signal});
            document.addEventListener('keydown',ev=>{if(ev.key==='Escape'){ev.preventDefault();cancel();}},{signal:operation.signal});signal.addEventListener('abort',cancel,{once:true});
        };
        const closeMenus=()=>{for(const e of entries.values()){e.menu.hidden=true;e.preferences.hidden=true;e.menuButton.setAttribute('aria-expanded','false');}};
        const openPalette=()=>{closeMenus();updatePalette();palette.hidden=false;root.classList.add('dashboard-palette-open');header?.classList.add('dashboard-palette-heading-open');add.setAttribute('aria-expanded','true');setPaletteMode();search.focus();};
        add.addEventListener('click',()=>palette.hidden?openPalette():closePalette());close.addEventListener('click',()=>closePalette());scrim.addEventListener('click',()=>closePalette());search.addEventListener('input',updatePalette);
        clearSearch.addEventListener('click',()=>{search.value='';updatePalette();search.focus();});
        palette.addEventListener('keydown',event=>{
            if(event.key==='Escape'){event.preventDefault();closePalette();}
            if(event.key==='Tab' && narrow()){
                const items=[...palette.querySelectorAll('button:not(:disabled),input')].filter(n=>!n.closest('[hidden]')),first=items[0],last=items.at(-1);
                if(event.shiftKey && document.activeElement===first){event.preventDefault();last?.focus();}
                else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first?.focus();}
            }
        });
        widgets.forEach((widget,index)=>{
            const id=widget.dataset.dashboardWidget;if(!id || entries.has(id))return;
            widget.classList.add('dashboard-widget');
            let heading=widget.querySelector(':scope > .erp-panel-header');
            if(!heading){heading=node('div','erp-panel-header');heading.append(node('h2','erp-panel-title',widget.dataset.widgetTitle || l.overview));widget.prepend(heading);}
            const title=widget.dataset.widgetTitle || heading.querySelector('h2,h3,.erp-panel-title')?.textContent.trim() || l.overview;
            let body=widget.querySelector(':scope > .erp-panel-body');
            if(!body){body=node('div','erp-panel-body');[...widget.childNodes].filter(child=>child!==heading).forEach(child=>body.append(child));widget.append(body);}
            body.classList.add('dashboard-widget-body');
            if(widget.classList.contains('erp-stat-grid')){widget.classList.remove('erp-stat-grid');body.classList.add('erp-stat-grid');}
            const controls=node('div','dashboard-widget-controls'),drag=button('drag-indicator',l.move+' '+title,'dashboard-drag'),menuButton=button('ellipsis',l.actions+' '+title),resize=button('resize-area',l.resize+' '+title,'dashboard-resize');
            drag.draggable=false;drag.setAttribute('aria-description',l.moveHelp);resize.setAttribute('aria-description',l.resizeHelp);heading.prepend(drag);heading.append(controls);controls.append(menuButton);widget.append(resize);
            const menu=node('div','dashboard-widget-menu');menu.hidden=true;menu.setAttribute('role','menu');menu.id='dashboard-menu-'+root.dataset.dashboardBoard+'-'+index;
            menuButton.setAttribute('aria-controls',menu.id);menuButton.setAttribute('aria-haspopup','menu');menuButton.setAttribute('aria-expanded','false');
            const preferences=node('form','dashboard-widget-preferences');preferences.hidden=true;preferences.setAttribute('aria-label',l.preferences+' '+title);
            const min=bounded(widget.dataset.widgetMinWidth,1,4,1),width=bounded(widget.dataset.widgetWidth,min,4,Math.max(min,2)),height=bounded(widget.dataset.widgetHeight,240,720,360);
            const e={id,title,widget,heading,body,drag,menuButton,menu,preferences,width,height,min,defaultWidth:width,defaultHeight:height,defaultIndex:index};entries.set(id,e);
            const setting=(label,values)=>{const text=node('label','',label),select=node('select');select.dataset.nativeSelect='true';values.forEach(([value,name])=>{const option=node('option','',name);option.value=String(value);select.append(option);});text.append(select);preferences.append(text);return select;};
            const widthSelect=setting(l.width,Array.from({length:5-min},(_,i)=>[i+min,String(i+min)])),heightSelect=setting(l.height,[[280,l.short],[400,l.medium],[560,l.tall]]);
            if(widget.classList.contains('dashboard-auto-widget'))heightSelect.parentElement.hidden=true;
            const footer=node('footer'),cancel=node('button','app-btn btn-neutral',l.cancel),save=node('button','app-btn btn-primary',l.apply);cancel.type='button';save.type='submit';footer.append(cancel,save);preferences.append(footer);
            cancel.addEventListener('click',()=>{preferences.hidden=true;menuButton.focus();});
            preferences.addEventListener('submit',event=>{event.preventDefault();e.width=bounded(widthSelect.value,min,4,width);e.height=bounded(heightSelect.value,240,720,height);setSize(e);preferences.hidden=true;persist();menuButton.focus();});
            const command=(label,run)=>{const b=node('button','',label);b.type='button';b.setAttribute('role','menuitem');b.tabIndex=-1;b.addEventListener('click',()=>{menu.hidden=true;menuButton.setAttribute('aria-expanded','false');run();});menu.append(b);return b;};
            command(l.preferences,()=>{widthSelect.value=String(e.width);heightSelect.value=String([280,400,560].reduce((a,b)=>Math.abs(b-e.height)<Math.abs(a-e.height)?b:a));preferences.hidden=false;widthSelect.focus();});
            command(l.moveEarlier,()=>move(e,-1));command(l.moveLater,()=>move(e,1));
            command(l.remove,()=>{widget.hidden=true;updatePalette();persist();widget.dispatchEvent(new CustomEvent('dashboard:visibility',{bubbles:true}));add.focus();announce(l.removed+' '+title);});
            heading.append(menu,preferences);
            menuButton.addEventListener('click',()=>{const open=menu.hidden;closeMenus();menu.hidden=!open;menuButton.setAttribute('aria-expanded',String(open));if(open)menu.querySelector('button')?.focus();});
            menuButton.addEventListener('keydown',event=>{if(!['ArrowDown','ArrowUp'].includes(event.key))return;event.preventDefault();closeMenus();menu.hidden=false;menuButton.setAttribute('aria-expanded','true');const items=menu.querySelectorAll('button');(event.key==='ArrowUp'?items[items.length-1]:items[0])?.focus();});
            menu.addEventListener('keydown',event=>{
                if(event.key==='Tab')queueMicrotask(closeMenus);
                if(event.key==='Escape'){event.preventDefault();closeMenus();menuButton.focus();}
                if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){event.preventDefault();const items=[...menu.querySelectorAll('button')],i=items.indexOf(document.activeElement);items[event.key==='Home'?0:event.key==='End'?items.length-1:(i+(event.key==='ArrowUp'?-1:1)+items.length)%items.length].focus();}
            });
            drag.addEventListener('keydown',event=>{if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();move(e,['ArrowUp','ArrowLeft'].includes(event.key)?-1:1);});
            drag.addEventListener('pointerdown',event=>pointerMove(e,drag,event));
            widget.addEventListener('dragover',event=>{if(!dragged || dragged===e)return;event.preventDefault();widget.classList.add('is-drop-target');});
            widget.addEventListener('dragleave',()=>widget.classList.remove('is-drop-target'));
            widget.addEventListener('drop',event=>{if(!dragged || dragged===e)return;event.preventDefault();event.stopPropagation();place(dragged,dropPosition(event.clientX,event.clientY,dragged)||{widget,after:false});clearDrop();dragged=null;});
            resize.addEventListener('keydown',event=>{
                if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Home'].includes(event.key))return;event.preventDefault();
                if(event.key==='Home'){e.width=e.defaultWidth;e.height=e.defaultHeight;}
                else if(event.key==='ArrowLeft' || event.key==='ArrowRight')e.width=bounded(e.width+(event.key==='ArrowRight'?1:-1),min,4,e.width);
                else e.height=bounded(e.height+(event.key==='ArrowDown'?40:-40),240,720,e.height);
                setSize(e);persist();announce(title+' '+e.width+' × '+e.height);
            });
            resize.addEventListener('pointerdown',event=>{
                if(event.button!==0 || event.isPrimary===false)return;event.preventDefault();resize.focus({preventScroll:true});resize.setPointerCapture?.(event.pointerId);
                const x=event.clientX,y=event.clientY,w=e.width,h=e.height,columns=Number(getComputedStyle(grid).getPropertyValue('--dashboard-columns')) || 4,step=grid.getBoundingClientRect().width/columns;
                const dragging=new AbortController();
                const finish=()=>{dragging.abort();signal.removeEventListener('abort',finish);if(resize.hasPointerCapture?.(event.pointerId))resize.releasePointerCapture(event.pointerId);persist();};
                window.addEventListener('pointermove',ev=>{if(ev.pointerId!==event.pointerId)return;e.width=bounded(w+Math.round((ev.clientX-x)/Math.max(step,1)),min,4,w);e.height=bounded(h+ev.clientY-y,240,720,h);setSize(e);},{signal:dragging.signal});
                for(const type of ['pointerup','pointercancel','lostpointercapture'])window.addEventListener(type,ev=>{if(ev.pointerId===event.pointerId)finish();},{signal:dragging.signal});
                signal.addEventListener('abort',finish,{once:true});
            });
            const paletteItem=node('div','dashboard-palette-item'),name=node('span','',title),addButton=node('button','app-btn btn-neutral',l.addItem);addButton.type='button';paletteItem.append(name,addButton);choices.append(paletteItem);Object.assign(e,{paletteItem,addButton});
            addButton.addEventListener('click',()=>{show(e);persist();announce(l.added+' '+title);});
            paletteItem.addEventListener('dragstart',event=>{if(!widget.hidden){event.preventDefault();return;}dragged=e;event.dataTransfer?.setData('text/plain',id);});paletteItem.addEventListener('dragend',()=>{dragged=null;});
            setSize(e);grid.append(widget);
        });
        root.querySelectorAll('[data-dashboard-source-layout]').forEach(n=>{if(!n.children.length)n.hidden=true;});
        try{
            const saved=JSON.parse(localStorage.getItem(key));
            if(saved?.version===1 && Array.isArray(saved.items)){
                const seen=new Set();for(const item of saved.items){const e=entries.get(item.id);if(!e || seen.has(item.id))continue;seen.add(item.id);e.width=bounded(item.width,e.min,4,e.defaultWidth);e.height=bounded(item.height,240,720,e.defaultHeight);e.widget.hidden=item.hidden===true;setSize(e);grid.append(e.widget);}
            }
        }catch{/* Ignore obsolete or corrupt preferences. */}
        reset.addEventListener('click',()=>{closeMenus();[...entries.values()].sort((a,b)=>a.defaultIndex-b.defaultIndex).forEach(e=>{e.width=e.defaultWidth;e.height=e.defaultHeight;grid.append(e.widget);show(e);});persist();announce(l.restored);});
        grid.addEventListener('dragover',event=>{if(dragged)event.preventDefault();});
        grid.addEventListener('drop',event=>{if(!dragged)return;event.preventDefault();show(dragged);grid.append(dragged.widget);persist();dragged=null;});
        root.addEventListener('click',event=>{
            const trigger=event.target.closest('[data-dashboard-reveal]');let e=trigger?entries.get(trigger.dataset.dashboardReveal):null;
            const link=event.target.closest('a[href]');
            if(!e && link){const url=new URL(link.href,location.href);if(url.pathname===location.pathname && url.origin===location.origin && url.hash){try{const target=document.getElementById(decodeURIComponent(url.hash.slice(1)));e=entries.get(target?.closest('[data-dashboard-widget]')?.dataset.dashboardWidget);}catch{/* Invalid fragment. */}}}
            if(e?.widget.hidden){show(e);persist();}
        },{capture:true});
        document.addEventListener('click',event=>{if(!event.target.closest('.dashboard-widget-menu,.dashboard-widget-preferences,.dashboard-widget-controls'))closeMenus();},{signal});
        document.addEventListener('keydown',event=>{if(event.key==='Escape'){const active=document.activeElement;closeMenus();if(active?.closest('.dashboard-widget-menu,.dashboard-widget-preferences'))active.closest('[data-dashboard-widget]')?.querySelector('.dashboard-widget-controls button')?.focus();}},{signal});
        window.addEventListener('resize',setPaletteMode,{signal});updatePalette();
        boards.set(root,{destroy(){unlock();header?.classList.remove('dashboard-palette-heading-open');lifetime.abort();palette.remove();scrim.remove();toolbar.remove();}});
        document.dispatchEvent(new Event('dashboard:updated'));ready();
    }
    const scan=()=>{if(!window.document)return;for(const [root,board] of boards)if(!root.isConnected){board.destroy();boards.delete(root);}document.querySelectorAll('[data-dashboard-board]').forEach(init);};
    window.DashboardBoard={init:scan,ready};
    const start=()=>{scan();let queued=false;new MutationObserver(records=>{if(!window.document)return;if(queued || !records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1 && (n.matches?.('[data-dashboard-board]') || n.querySelector?.('[data-dashboard-board]')))))return;queued=true;queueMicrotask(()=>{queued=false;scan();});}).observe(document.body,{childList:true,subtree:true});};
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
