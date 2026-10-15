/* Read-only details from the selected, already-authorized resource row. */
(() => {
    'use strict';
    const el = (tag, cls, text) => { const n=document.createElement(tag); n.className=cls || ''; if(text)n.textContent=text; return n; };
    window.ResourceSplitView = {init(region, {headers, originalCells, selectable, labels:l}) {
        if (!region.hasAttribute('data-resource-split') || !selectable || region.dataset.splitReady) return;
        region.dataset.splitReady='true';
        const table=region.querySelector('table'), scroller=table.closest('.erp-table-scroll');
        const layout=el('div','resource-split-layout'); scroller.before(layout); layout.append(scroller);
        const panel=el('section','resource-split-panel'); panel.hidden=true; layout.append(panel);
        const id='resource-split-'+region.dataset.resourceTable; panel.id=id;
        const button=(label, symbol) => { const n=el('button','resource-table-icon',symbol); n.type='button'; n.title=label; n.setAttribute('aria-label',label); return n; };
        const show=button(l.showDetails,'');
        show.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="1"/><path d="M3 15h18"/></svg>';
        show.setAttribute('aria-controls',id); show.setAttribute('aria-expanded','false');
        region.querySelector('.resource-table-utilities').prepend(show);
        const resize=el('div','resource-split-resize'); resize.tabIndex=0; resize.setAttribute('role','separator'); resize.setAttribute('aria-label',l.resize); resize.setAttribute('aria-controls',id);
        const top=el('div','resource-split-header'), title=el('h2'); title.id=id+'-title'; panel.setAttribute('aria-labelledby',title.id);
        const commands=el('div','resource-split-commands'), gear=button(l.panelPosition,''), close=button(l.close,'');
        gear.append(region.querySelector('.resource-table-preferences-button svg').cloneNode(true));
        close.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 5 14 14M5 19 19 5"/></svg>';
        const positions=el('div','resource-split-positions'); positions.hidden=true;
        const positionLabel=el('label','sr-only',l.panelPosition), position=el('select'); position.id=id+'-position'; positionLabel.htmlFor=position.id;
        [['bottom',l.bottom],['side',l.side]].forEach(([value,label])=>{const option=el('option','',label);option.value=value;position.append(option);}); positions.append(positionLabel,position);
        commands.append(gear,positions,close); top.append(title,commands);
        const tabs=el('div','resource-split-tabs'), tab=el('span','',l.details); tabs.append(tab);
        const content=el('div','resource-split-content'); panel.append(resize,top,tabs,content);
        const key='resource-split:v1:'+region.dataset.resourceTable;
        let state={position:'bottom',size:320};
        const wide=window.matchMedia?.('(min-width:1100px)');
        const side=()=>state.position==='side' && wide?.matches;
        const bottomTotal=()=>Math.max(160,Math.min(window.innerHeight*.38,360))+320;
        const limits=()=>side()
            ? {min:280,max:Math.max(280,Math.min(640,(layout.clientWidth || window.innerWidth)-240))}
            : {min:200,max:Math.min(640,bottomTotal()-96)};
        try { const saved=JSON.parse(localStorage.getItem(key)); if(saved && ['bottom','side'].includes(saved.position)) state={position:saved.position,size:Number(saved.size)||320}; } catch { /* Optional display storage. */ }
        const save=()=>{try{localStorage.setItem(key,JSON.stringify(state));}catch{/* Optional. */}};
        const apply=()=>{
            const {min,max}=limits();
            state.size=Math.min(max,Math.max(min,state.size));
            layout.classList.toggle('resource-split-side',state.position==='side'); position.value=state.position;
            layout.style.setProperty('--split-size',state.size+'px');
            // Move the divider by exchanging space with the table, keeping the total height stable.
            if (!panel.hidden && !side()) scroller.style.height=(bottomTotal()-state.size)+'px';
            else scroller.style.removeProperty('height');
            resize.setAttribute('aria-orientation',side()?'vertical':'horizontal');
            resize.setAttribute('aria-valuemin',String(min));resize.setAttribute('aria-valuemax',String(max));resize.setAttribute('aria-valuenow',String(state.size));
        };
        const selected=()=>[...table.querySelectorAll('tbody input[type=checkbox]')].find(input=>input.checked && input.closest('td').cellIndex===0 && !input.closest('tr').hidden)?.closest('tr');
        const render=()=>{
            const row=selected();
            content.replaceChildren(); title.textContent=l.details;
            if(!row){content.append(el('p','resource-split-empty',l.selectRecord));return;}
            const cells=originalCells(row), identifier=cells[1]?.cloneNode(true);
            identifier?.querySelectorAll('button,input,select,textarea').forEach(n=>n.remove());
            title.textContent=identifier?.textContent.trim() || l.details;
            const list=el('dl','resource-split-values');
            headers.forEach((header,index)=>{
                if(index===0 || !cells[index])return;
                const group=el('div'), term=el('dt','',header.textContent.trim()), value=el('dd');
                // Commands and controls never move or duplicate into the preview.
                const copy=cells[index].cloneNode(true); copy.querySelectorAll('button,input,select,textarea,script').forEach(n=>n.remove());
                value.textContent=copy.textContent.trim() || '—'; group.append(term,value);list.append(group);
            }); content.append(list);
        };
        const open=()=>{panel.hidden=false;layout.classList.add('resource-split-open');show.setAttribute('aria-expanded','true');render();apply();};
        const sync=()=>{if(!panel.hidden)render();};
        show.addEventListener('click',()=>{open();close.focus();});
        close.addEventListener('click',()=>{panel.hidden=true;layout.classList.remove('resource-split-open');show.setAttribute('aria-expanded','false');apply();show.focus();});
        gear.setAttribute('aria-expanded','false'); gear.addEventListener('click',()=>{positions.hidden=!positions.hidden;gear.setAttribute('aria-expanded',String(!positions.hidden));if(!positions.hidden)position.focus();});
        position.addEventListener('change',()=>{state.position=position.value;apply();save();});
        resize.addEventListener('keydown',event=>{
            if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
            const {min,max}=limits();
            event.preventDefault();state.size=event.key==='Home'?min:event.key==='End'?max:state.size+(['ArrowUp','ArrowLeft'].includes(event.key)?20:-20);apply();save();
        });
        let dragging=false;
        resize.addEventListener('pointerdown',event=>{
            if(event.button!==0 || event.isPrimary===false || dragging)return;
            event.preventDefault();resize.focus({preventScroll:true});resize.setPointerCapture?.(event.pointerId);
            dragging=true;layout.classList.add('resource-split-dragging');
            const horizontal=side(), pointerId=event.pointerId;
            const start=horizontal?event.clientX:event.clientY, size=state.size;
            const move=e=>{if(e.pointerId!==pointerId)return;state.size=size+start-(horizontal?e.clientX:e.clientY);apply();};
            const end=e=>{
                if(e.pointerId!==pointerId)return;
                window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',end);window.removeEventListener('pointercancel',end);resize.removeEventListener('lostpointercapture',end);
                dragging=false;layout.classList.remove('resource-split-dragging');
                if(resize.hasPointerCapture?.(pointerId))resize.releasePointerCapture(pointerId);
                save();
            };
            window.addEventListener('pointermove',move);window.addEventListener('pointerup',end);window.addEventListener('pointercancel',end);resize.addEventListener('lostpointercapture',end);
        });
        table.addEventListener('click',()=>queueMicrotask(sync));table.addEventListener('change',()=>queueMicrotask(sync));
        region.addEventListener('resource-table:reset',()=>queueMicrotask(sync));
        wide?.addEventListener('change',apply);
        window.addEventListener('resize',apply);
        apply();
    }};
})();
