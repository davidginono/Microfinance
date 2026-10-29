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
        let cancelInteraction=null,suppressClick=null,columns=4;
        const order=()=>[...grid.children].map(item=>item.dataset.dashboardWidget);
        const persist=()=>{try{localStorage.setItem(key,JSON.stringify({version:2,items:order().map(id=>{const e=entries.get(id);return {id,width:e.width,height:e.height,hidden:e.widget.hidden,positions:e.positions};})}));}catch{/* Preferences are optional. */}};
        const updatePalette=()=>{
            let matches=0;const query=search.value.trim().toLocaleLowerCase();
            for(const e of entries.values()){
                e.paletteItem.hidden=!e.title.toLocaleLowerCase().includes(query);if(!e.paletteItem.hidden)matches++;
                e.addButton.disabled=!e.widget.hidden;e.addButton.textContent=e.widget.hidden?l.addItem:l.added;
                e.paletteItem.hidden=e.paletteItem.hidden || !e.widget.hidden;
                e.paletteItem.draggable=false;
            }
            matches=[...entries.values()].filter(e=>!e.paletteItem.hidden).length;
            noMatches.hidden=matches>0;clearSearch.hidden=matches>0 || !query;empty.hidden=[...entries.values()].some(e=>!e.widget.hidden);
        };
        const setSize=e=>{e.widget.dataset.boardWidth=String(e.width);e.widget.style.setProperty('--widget-height',e.height+'px');};
        const show=e=>{const wasHidden=e.widget.hidden;e.widget.hidden=false;setSize(e);layout();updatePalette();if(wasHidden)e.widget.dispatchEvent(new CustomEvent('dashboard:visibility',{bubbles:true}));};
        const visible=()=>[...grid.children].filter(item=>!item.hidden);
        const gap=20,snap=20;
        const overlap=(a,b)=>a.x<b.x+b.w && a.x+a.w>b.x && a.y<b.y+b.h+gap && a.y+a.h+gap>b.y;
        const geometry=e=>({x:e.positions[columns]?.x || 0,y:e.positions[columns]?.y || 0,w:Math.min(e.width,columns),h:e.auto?Math.ceil(e.widget.getBoundingClientRect().height)||e.height:e.height});
        const firstSpace=(rect,placed)=>{
            const rows=[0,...placed.map(p=>p.y+p.h+gap)].sort((a,b)=>a-b);
            for(const y of rows)for(let x=0;x<=columns-rect.w;x++){const p={...rect,x,y};if(!placed.some(other=>overlap(p,other)))return p;}
        };
        function layout(proposal,activeId){
            const placed=[];
            for(const widget of visible()){
                const e=entries.get(widget.dataset.dashboardWidget),rect=e.id===activeId?geometry(e):proposal?.get(e.id) || geometry(e);
                let p=rect;
                if(!proposal && (!e.positions[columns] || rect.x+rect.w>columns || placed.some(other=>overlap(rect,other))))p=firstSpace(rect,placed);
                if(!proposal)e.positions[columns]={x:p.x,y:p.y};
                widget.style.gridColumn=(p.x+1)+' / span '+p.w;widget.style.gridRow=(p.y+1)+' / span '+p.h;
                widget.style.setProperty('--widget-height',p.h+'px');placed.push(p);
            }
            grid.style.minHeight=Math.max(240,...(proposal?[...proposal.values()]:placed).map(p=>p.y+p.h))+'px';
        }
        const snapshot=()=>new Map([...entries.values()].filter(e=>!e.widget.hidden).map(e=>[e.id,geometry(e)]));
        const propose=(e,target,base,swap=true)=>{
            const next=new Map([...base].map(([id,p])=>[id,{...p}])),origin=next.get(e.id);next.delete(e.id);
            const collisions=[...next].filter(([,p])=>overlap(target,p));
            if(swap && origin && collisions.length && origin.w===target.w && origin.h===target.h){
                const ids=new Set(collisions.map(([id])=>id));
                const replacements=collisions.map(([id,p])=>[id,{...p,x:p.x+origin.x-target.x,y:p.y+origin.y-target.y}]);
                if(collisions.every(([,p])=>p.x>=target.x && p.x+p.w<=target.x+target.w && p.y>=target.y && p.y+p.h<=target.y+target.h) && replacements.every(([,p])=>!overlap(p,target) && ![...next].some(([id,q])=>!ids.has(id) && overlap(p,q))))for(const [id,p] of replacements)next.set(id,p);
            }
            // The active item stays at the requested cell; collisions push down in stable order.
            const pending=[...next].sort((a,b)=>a[1].y-b[1].y || a[1].x-b[1].x),placed=[target];next.clear();next.set(e.id,target);
            for(const [id,rect] of pending){let p={...rect},hits;while((hits=placed.filter(q=>overlap(p,q))).length)p.y=Math.max(...hits.map(q=>q.y+q.h+gap));next.set(id,p);placed.push(p);}
            return next;
        };
        const commit=next=>{
            for(const [id,p] of next)entries.get(id).positions[columns]={x:p.x,y:p.y};
            [...next].sort((a,b)=>a[1].y-b[1].y || a[1].x-b[1].x).forEach(([id])=>grid.append(entries.get(id).widget));
            layout();persist();
        };
        const move=(e,dx,dy)=>{const p=geometry(e),x=bounded(p.x+dx,0,columns-p.w,p.x),y=Math.max(0,p.y+dy);if(x===p.x && y===p.y)return;commit(propose(e,{...p,x,y},snapshot()));e.drag.focus({preventScroll:true});announce(l.moved+' '+e.title);};
        const clearDrop=()=>{root.querySelector('.dashboard-drop-preview')?.remove();};
        const animateFrom=(widget,from)=>{
            if(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)return;
            const to=widget.getBoundingClientRect(),dx=from.left-to.left,dy=from.top-to.top;
            if(dx || dy)entries.get(widget.dataset.dashboardWidget).motion=widget.animate?.([{transform:'translate('+dx+'px,'+dy+'px)'},{transform:'translate(0,0)'}],{duration:160,easing:'cubic-bezier(.2,0,0,1)'});
        };
        const dropPosition=(x,y,e,offset,base)=>{
            const r=grid.getBoundingClientRect(),p=geometry(e),step=(r.width+gap)/columns;
            if(!r.width || x<r.left-24 || x>r.right+24 || y<r.top-24 || y>Math.max(r.bottom,r.top+240)+80)return null;
            const column=bounded(Math.round((x-r.left-offset.x)/step),0,columns-p.w,0),top=Math.max(0,y-r.top-offset.y);
            // Target the original row edges, so displaced widgets never chase the pointer.
            const rows=[0,...[...base].filter(([id,q])=>id!==e.id && column<q.x+q.w && column+p.w>q.x).flatMap(([,q])=>[q.y,q.y+q.h+gap])];
            const nearest=rows.reduce((a,b)=>Math.abs(b-top)<Math.abs(a-top)?b:a);
            return {...p,x:column,y:Math.abs(nearest-top)<=56?nearest:Math.round(top/snap)*snap};
        };
        const preview=(e,target,base,activeId)=>{
            const before=new Map(visible().filter(w=>w.dataset.dashboardWidget!==activeId).map(w=>[w,w.getBoundingClientRect()]));
            for(const w of before.keys())entries.get(w.dataset.dashboardWidget).motion?.cancel();
            const next=target?propose(e,target,base):null;layout(next || base,activeId);
            for(const [w,r] of before)animateFrom(w,r);
            if(!target){clearDrop();return null;}
            let marker=root.querySelector('.dashboard-drop-preview');
            if(!marker){marker=node('div','dashboard-drop-preview');marker.setAttribute('aria-hidden','true');root.append(marker);}
            const step=(grid.getBoundingClientRect().width+gap)/columns;
            Object.assign(marker.style,{left:(grid.offsetLeft+target.x*step)+'px',top:(grid.offsetTop+target.y)+'px',width:(target.w*step-gap)+'px',height:target.h+'px'});return next;
        };
        const pointerMove=(entry,handle,event)=>{
            if(event.button!==0 || event.isPrimary===false)return;event.preventDefault();handle.focus({preventScroll:true});handle.setPointerCapture?.(event.pointerId);
            cancelInteraction?.();const operation=new AbortController(),base=snapshot(),fromPalette=entry.widget.hidden,rect=(fromPalette?handle.closest('.dashboard-palette-item'):entry.widget).getBoundingClientRect(),offset={x:event.clientX-rect.left,y:event.clientY-rect.top};let active=false,target=null,x=event.clientX,y=event.clientY,frame,lastCell,finished=false;
            const moving=fromPalette?node('div','dashboard-palette-drag-preview',entry.title):entry.widget;
            if(fromPalette){moving.setAttribute('aria-hidden','true');Object.assign(moving.style,{left:rect.left+'px',top:rect.top+'px',width:rect.width+'px',height:rect.height+'px'});}
            const update=()=>{
                const cell=dropPosition(x,y,entry,offset,base),identity=cell?cell.x+':'+cell.y:'outside';
                if(identity!==lastCell){target=preview(entry,cell,base,fromPalette?null:entry.id);lastCell=identity;}
                const r=grid.getBoundingClientRect(),origin=base.get(entry.id),step=(r.width+gap)/columns;
                const left=fromPalette?rect.left:r.left+origin.x*step,top=fromPalette?rect.top:r.top+origin.y;
                moving.style.transform='translate3d('+(x-offset.x-left)+'px,'+(y-offset.y-top)+'px,0)';
            };
            const tick=()=>{if(!active)return;const edge=64,top=Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--shell-nav-height'))||44;const delta=y<top+edge?-Math.ceil((top+edge-y)/5):y>innerHeight-edge?Math.ceil((y-innerHeight+edge)/5):0;if(delta)window.scrollBy(0,Math.max(-24,Math.min(24,delta)));update();frame=requestAnimationFrame(tick);};
            const finish=(accept=false)=>{
                if(finished)return;finished=true;operation.abort();signal.removeEventListener('abort',cancel);cancelInteraction=null;cancelAnimationFrame(frame);if(active && accept)update();
                const lifted=active && !fromPalette?moving.getBoundingClientRect():null;
                moving.style.removeProperty('transform');if(fromPalette)moving.remove();clearDrop();entry.widget.classList.remove('is-dragging');root.classList.remove('dashboard-is-moving');
                if(!accept || !target)for(const e of entries.values())e.motion?.cancel();
                if(handle.hasPointerCapture?.(event.pointerId))handle.releasePointerCapture(event.pointerId);
                if(active){suppressClick=handle;setTimeout(()=>{if(suppressClick===handle)suppressClick=null;},0);if(accept && target){if(entry.widget.hidden)show(entry);commit(target);announce(l.moved+' '+entry.title);}else layout();if(lifted && root.isConnected)animateFrom(entry.widget,lifted);}
                (handle.closest('[hidden]')?entry.drag:handle).focus({preventScroll:true});
            };
            const cancel=()=>finish(false);
            cancelInteraction=cancel;
            window.addEventListener('pointermove',ev=>{if(ev.pointerId!==event.pointerId)return;x=ev.clientX;y=ev.clientY;if(!active&&Math.hypot(x-event.clientX,y-event.clientY)>=4){active=true;closeMenus();entry.motion?.cancel();if(fromPalette)document.body.append(moving);else entry.widget.classList.add('is-dragging');root.classList.add('dashboard-is-moving');update();frame=requestAnimationFrame(tick);}if(active)ev.preventDefault();},{signal:operation.signal});
            window.addEventListener('pointerup',ev=>{if(ev.pointerId===event.pointerId){x=ev.clientX;y=ev.clientY;finish(true);}},{signal:operation.signal});
            for(const name of ['pointercancel','lostpointercapture'])handle.addEventListener(name,ev=>{if(ev.pointerId===event.pointerId)cancel();},{signal:operation.signal});
            document.addEventListener('keydown',ev=>{if(ev.key==='Escape'){ev.preventDefault();cancel();}},{signal:operation.signal});signal.addEventListener('abort',cancel,{once:true});
            window.addEventListener('blur',cancel,{signal:operation.signal});
        };
        const closeMenus=()=>{for(const e of entries.values()){e.menu.hidden=true;e.preferences.hidden=true;e.directions.hidden=true;e.menuButton.setAttribute('aria-expanded','false');}};
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
            const min=bounded(widget.dataset.widgetMinWidth,1,6,1),width=bounded(widget.dataset.widgetWidth,min,6,Math.max(min,2)),height=bounded(widget.dataset.widgetHeight,240,720,360);
            const directions=node('div','dashboard-direction-controls');directions.hidden=true;directions.setAttribute('role','group');heading.append(directions);
            const e={id,title,widget,heading,body,drag,menuButton,menu,preferences,directions,width,height,min,positions:{},auto:widget.classList.contains('dashboard-auto-widget'),defaultWidth:width,defaultHeight:height,defaultIndex:index};entries.set(id,e);
            const setting=(label,values)=>{const text=node('label','',label),select=node('select');select.dataset.nativeSelect='true';values.forEach(([value,name])=>{const option=node('option','',name);option.value=String(value);select.append(option);});text.append(select);preferences.append(text);return select;};
            const widthSelect=setting(l.width,Array.from({length:7-min},(_,i)=>[i+min,String(i+min)])),heightSelect=setting(l.height,[[280,l.short],[400,l.medium],[560,l.tall]]);
            if(widget.classList.contains('dashboard-auto-widget'))heightSelect.parentElement.hidden=true;
            const footer=node('footer'),cancel=node('button','app-btn btn-neutral',l.cancel),save=node('button','app-btn btn-primary',l.apply);cancel.type='button';save.type='submit';footer.append(cancel,save);preferences.append(footer);
            cancel.addEventListener('click',()=>{preferences.hidden=true;menuButton.focus();});
            const resizeTo=(width,height)=>{const base=snapshot();e.width=width;e.height=height;setSize(e);const p=geometry(e);p.x=Math.min(p.x,columns-p.w);commit(propose(e,p,base,false));};
            preferences.addEventListener('submit',event=>{event.preventDefault();resizeTo(bounded(widthSelect.value,min,6,width),bounded(heightSelect.value,240,720,height));preferences.hidden=true;menuButton.focus();});
            const command=(label,run)=>{const b=node('button','',label);b.type='button';b.setAttribute('role','menuitem');b.tabIndex=-1;b.addEventListener('click',()=>{menu.hidden=true;menuButton.setAttribute('aria-expanded','false');run();});menu.append(b);return b;};
            command(l.preferences,()=>{widthSelect.value=String(e.width);heightSelect.value=String([280,400,560].reduce((a,b)=>Math.abs(b-e.height)<Math.abs(a-e.height)?b:a));preferences.hidden=false;widthSelect.focus();});
            command(l.moveEarlier,()=>move(e,0,-snap));command(l.moveLater,()=>move(e,0,snap));
            command(l.remove,()=>{widget.hidden=true;layout();updatePalette();persist();widget.dispatchEvent(new CustomEvent('dashboard:visibility',{bubbles:true}));add.focus();announce(l.removed+' '+title);});
            heading.append(menu,preferences);
            menuButton.addEventListener('click',()=>{const open=menu.hidden;closeMenus();menu.hidden=!open;menuButton.setAttribute('aria-expanded',String(open));if(open)menu.querySelector('button')?.focus();});
            menuButton.addEventListener('keydown',event=>{if(!['ArrowDown','ArrowUp'].includes(event.key))return;event.preventDefault();closeMenus();menu.hidden=false;menuButton.setAttribute('aria-expanded','true');const items=menu.querySelectorAll('button');(event.key==='ArrowUp'?items[items.length-1]:items[0])?.focus();});
            menu.addEventListener('keydown',event=>{
                if(event.key==='Tab')queueMicrotask(closeMenus);
                if(event.key==='Escape'){event.preventDefault();closeMenus();menuButton.focus();}
                if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){event.preventDefault();const items=[...menu.querySelectorAll('button')],i=items.indexOf(document.activeElement);items[event.key==='Home'?0:event.key==='End'?items.length-1:(i+(event.key==='ArrowUp'?-1:1)+items.length)%items.length].focus();}
            });
            const adjust=(key,resizing=false)=>{
                if(!resizing){move(e,key==='ArrowLeft'?-1:key==='ArrowRight'?1:0,key==='ArrowUp'?-snap:key==='ArrowDown'?snap:0);return;}
                let w=e.width,h=e.height;
                if(key==='Home'){w=e.defaultWidth;h=e.defaultHeight;}
                else if(key==='ArrowLeft' || key==='ArrowRight'){const effective=Math.min(w,columns),next=bounded(effective+(key==='ArrowRight'?1:-1),Math.min(min,columns),columns-geometry(e).x,w);if(next!==effective)w=next;}
                else if(!e.auto)h=bounded(h+(key==='ArrowDown'?40:-40),240,720,h);
                resizeTo(w,h);announce(title+' '+Math.min(w,columns)+' × '+h);
            };
            drag.addEventListener('keydown',event=>{if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();adjust(event.key);});
            drag.addEventListener('pointerdown',event=>pointerMove(e,drag,event));
            heading.addEventListener('pointerdown',event=>{if(!event.target.closest('button,a,input,select,textarea,summary,[role="button"],[contenteditable="true"],.dashboard-widget-menu,.dashboard-widget-preferences,.dashboard-direction-controls'))pointerMove(e,drag,event);});
            for(const [handle,resizing] of [[drag,false],[resize,true]])handle.addEventListener('click',()=>{
                if(suppressClick===handle){suppressClick=null;return;}
                const open=directions.hidden || directions.dataset.mode!==String(resizing);closeMenus();if(!open)return;
                directions.dataset.mode=String(resizing);directions.setAttribute('aria-label',(resizing?l.resize:l.move)+' '+title);directions.replaceChildren();
                const updateDirections=()=>{const p=geometry(e);for(const b of directions.querySelectorAll('[data-direction]')){const key=b.dataset.direction;b.disabled=resizing?(key==='ArrowLeft'?p.w<=Math.min(min,columns):key==='ArrowRight'?p.x+p.w>=columns:key==='ArrowUp'?e.height<=240:e.height>=720):(key==='ArrowLeft'?p.x===0:key==='ArrowRight'?p.x+p.w===columns:key==='ArrowUp'?p.y===0:false);}};
                for(const [key,glyph,label] of [['ArrowLeft','←',l.left],['ArrowUp','↑',l.up],['ArrowDown','↓',l.down],['ArrowRight','→',l.right]]){
                    if(resizing && e.auto && ['ArrowUp','ArrowDown'].includes(key))continue;
                    const b=node('button','dashboard-icon-button',glyph);b.type='button';b.dataset.direction=key;b.setAttribute('aria-label',(resizing?l.resize:l.move)+' '+label);b.title=b.getAttribute('aria-label');b.addEventListener('click',()=>{adjust(key,resizing);updateDirections();(b.disabled?directions.querySelector('button:not(:disabled)'):b).focus();});directions.append(b);
                }
                const done=node('button','app-btn btn-neutral',l.apply);done.type='button';done.addEventListener('click',()=>{directions.hidden=true;handle.focus();});directions.append(done);updateDirections();directions.hidden=false;directions.querySelector('button:not(:disabled)').focus();
            });
            resize.addEventListener('keydown',event=>{
                if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Home'].includes(event.key))return;event.preventDefault();
                adjust(event.key,true);
            });
            resize.addEventListener('pointerdown',event=>{
                if(event.button!==0 || event.isPrimary===false)return;event.preventDefault();resize.focus({preventScroll:true});resize.setPointerCapture?.(event.pointerId);
                cancelInteraction?.();const x=event.clientX,y=event.clientY,w=e.width,h=e.height,base=snapshot(),step=(grid.getBoundingClientRect().width+gap)/columns,maxWidth=columns-geometry(e).x;
                const dragging=new AbortController();let next=null,changed=false;
                const finish=(accept=false)=>{dragging.abort();signal.removeEventListener('abort',cancel);cancelInteraction=null;if(resize.hasPointerCapture?.(event.pointerId))resize.releasePointerCapture(event.pointerId);if(changed)suppressClick=resize;if(accept && next){commit(next);announce(title+' '+Math.min(e.width,columns)+' × '+e.height);}else{e.width=w;e.height=h;setSize(e);layout();}resize.focus({preventScroll:true});};
                const cancel=()=>finish(false);cancelInteraction=cancel;
                window.addEventListener('pointermove',ev=>{if(ev.pointerId!==event.pointerId)return;const dx=Math.round((ev.clientX-x)/Math.max(step,1)),effective=Math.min(w,columns),width=bounded(effective+dx,Math.min(min,columns),maxWidth,effective);e.width=width===effective?w:width;e.height=e.auto?h:bounded(h+Math.round((ev.clientY-y)/40)*40,240,720,h);changed=e.width!==w || e.height!==h;setSize(e);const p=geometry(e);next=propose(e,p,base,false);layout(next);},{signal:dragging.signal});
                window.addEventListener('pointerup',ev=>{if(ev.pointerId===event.pointerId)finish(true);},{signal:dragging.signal});
                for(const type of ['pointercancel','lostpointercapture'])resize.addEventListener(type,ev=>{if(ev.pointerId===event.pointerId)cancel();},{signal:dragging.signal});
                document.addEventListener('keydown',ev=>{if(ev.key==='Escape'){ev.preventDefault();cancel();}},{signal:dragging.signal});signal.addEventListener('abort',cancel,{once:true});
                window.addEventListener('blur',cancel,{signal:dragging.signal});
            });
            const paletteItem=node('div','dashboard-palette-item'),name=node('span','',title),addButton=node('button','app-btn btn-neutral',l.addItem);addButton.type='button';paletteItem.append(name,addButton);choices.append(paletteItem);Object.assign(e,{paletteItem,addButton});
            addButton.addEventListener('click',()=>{show(e);persist();if(narrow())closePalette(false);e.drag.focus();announce(l.added+' '+title);});
            const paletteDrag=button('drag-indicator',l.move+' '+title,'dashboard-drag');paletteItem.prepend(paletteDrag);
            paletteDrag.addEventListener('pointerdown',event=>{if(widget.hidden && !narrow())pointerMove(e,paletteDrag,event);});
            paletteDrag.addEventListener('click',()=>{if(suppressClick===paletteDrag){suppressClick=null;return;}show(e);persist();if(narrow())closePalette(false);e.drag.focus();announce(l.added+' '+title);});
            setSize(e);grid.append(widget);
        });
        root.querySelectorAll('[data-dashboard-source-layout]').forEach(n=>{if(!n.children.length)n.hidden=true;});
        try{
            const saved=JSON.parse(localStorage.getItem(key));
            if([1,2].includes(saved?.version) && Array.isArray(saved.items)){
                const seen=new Set();for(const item of saved.items){const e=entries.get(item?.id);if(!e || seen.has(item.id))continue;seen.add(item.id);e.width=bounded(item.width,e.min,6,e.defaultWidth);e.height=bounded(item.height,240,720,e.defaultHeight);e.widget.hidden=item.hidden===true;
                    for(const count of [1,2,4,6]){const p=item.positions?.[count];if(p && Number.isInteger(p.x) && Number.isInteger(p.y) && p.x>=0 && p.x<count && p.y>=0 && p.y<=entries.size*1000)e.positions[count]={x:p.x,y:p.y};}
                    setSize(e);grid.append(e.widget);}
            }
        }catch{/* Ignore obsolete or corrupt preferences. */}
        reset.addEventListener('click',()=>{cancelInteraction?.();closeMenus();[...entries.values()].sort((a,b)=>a.defaultIndex-b.defaultIndex).forEach(e=>{e.motion?.cancel();e.width=e.defaultWidth;e.height=e.defaultHeight;e.positions={};grid.append(e.widget);e.widget.hidden=false;setSize(e);e.widget.dispatchEvent(new CustomEvent('dashboard:visibility',{bubbles:true}));});layout();updatePalette();persist();announce(l.restored);});
        root.addEventListener('click',event=>{
            const trigger=event.target.closest('[data-dashboard-reveal]');let e=trigger?entries.get(trigger.dataset.dashboardReveal):null;
            const link=event.target.closest('a[href]');
            if(!e && link){const url=new URL(link.href,location.href);if(url.pathname===location.pathname && url.origin===location.origin && url.hash){try{const target=document.getElementById(decodeURIComponent(url.hash.slice(1)));e=entries.get(target?.closest('[data-dashboard-widget]')?.dataset.dashboardWidget);}catch{/* Invalid fragment. */}}}
            if(e?.widget.hidden){show(e);persist();}
        },{capture:true});
        document.addEventListener('click',event=>{if(!event.target.closest('.dashboard-widget-menu,.dashboard-widget-preferences,.dashboard-widget-controls,.dashboard-direction-controls,.dashboard-drag,.dashboard-resize'))closeMenus();},{signal});
        document.addEventListener('keydown',event=>{if(event.key==='Escape'){const active=document.activeElement,restore=active?.closest('.dashboard-widget-menu,.dashboard-widget-preferences,.dashboard-direction-controls');closeMenus();if(restore)active.closest('[data-dashboard-widget]')?.querySelector('.dashboard-widget-controls button')?.focus();}},{signal});
        const reflow=()=>{
            const width=grid.getBoundingClientRect().width || root.getBoundingClientRect().width || innerWidth;
            const count=width>=1600?6:width>=1050?4:width>=600?2:1;
            if(count!==columns){cancelInteraction?.();for(const e of entries.values())e.motion?.cancel();columns=count;grid.style.setProperty('--dashboard-columns',String(count));}
            if(!cancelInteraction)layout();setPaletteMode();
        };
        const observer=window.ResizeObserver?new ResizeObserver(reflow):null;observer?.observe(grid);
        for(const e of entries.values())if(e.auto)observer?.observe(e.widget);
        window.addEventListener('resize',reflow,{signal});reflow();updatePalette();
        boards.set(root,{destroy(){unlock();header?.classList.remove('dashboard-palette-heading-open');lifetime.abort();for(const e of entries.values())e.motion?.cancel();observer?.disconnect();palette.remove();scrim.remove();toolbar.remove();}});
        document.dispatchEvent(new Event('dashboard:updated'));ready();
    }
    const scan=()=>{if(!window.document)return;for(const [root,board] of boards)if(!root.isConnected){board.destroy();boards.delete(root);}document.querySelectorAll('[data-dashboard-board]').forEach(init);};
    window.DashboardBoard={init:scan,ready};
    const start=()=>{scan();let queued=false;new MutationObserver(records=>{if(!window.document)return;if(queued || !records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1 && (n.matches?.('[data-dashboard-board]') || n.querySelector?.('[data-dashboard-board]')))))return;queued=true;queueMicrotask(()=>{queued=false;scan();});}).observe(document.body,{childList:true,subtree:true});};
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();
