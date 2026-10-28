/* The permission catalog is a finite enum. Keep every native checkbox in its original form while paging. */
(() => {
    'use strict';
    function ready() {
        const matrix=document.querySelector('[data-access-matrix]'),form=matrix?.closest('form');if(!form)return;
        const rows=[...matrix.querySelectorAll('[data-claim-row]')],search=matrix.querySelector('[data-access-search]');
        const headers=[...matrix.querySelectorAll('[data-access-sort]')],body=matrix.querySelector('tbody');
        const count=matrix.querySelector('[data-access-count]'),empty=matrix.querySelector('[data-access-empty]');
        const save=matrix.querySelector('[data-access-save]'),discard=matrix.querySelector('[data-access-discard]');
        const controls=[...form.elements].filter(control=>control.name&&!['hidden','submit','button'].includes(control.type));
        const baseline=controls.map(control=>({control,value:control.value,checked:control.checked,disabled:control.disabled}));
        const signature=()=>JSON.stringify(controls.map(control=>[control.value,control.checked]));
        const initial=signature();let page=0,size=Number(matrix.dataset.tablePageSize)||10,sort='claim',descending=false;
        const collator=new Intl.Collator(document.documentElement.lang||'en',{numeric:true,sensitivity:'base'});
        const syncActions=()=>{const clean=signature()===initial;save.disabled=discard.disabled=clean;};
        function render(){
            const query=search.value.trim().toLocaleLowerCase();
            const matches=rows.filter(row=>(row.dataset.claimKey+' '+row.cells[0].textContent).toLocaleLowerCase().includes(query));
            const value=row=>sort==='claim'?row.dataset.claimKey:row.querySelector(`[data-claim-action="${sort}"] input`)?.checked?2:row.querySelector(`[data-claim-action="${sort}"] input`)?1:0;
            matches.sort((a,b)=>{const result=sort==='claim'?collator.compare(value(a),value(b)):value(a)-value(b);return (descending?-result:result)||collator.compare(a.dataset.claimKey,b.dataset.claimKey);});
            const totalPages=Math.max(1,Math.ceil(matches.length/size));page=Math.max(0,Math.min(page,totalPages-1));
            rows.forEach(row=>row.hidden=true);matches.slice(page*size,(page+1)*size).forEach(row=>row.hidden=false);
            // Move existing nodes only; hidden pages remain successful controls when Save submits.
            const matching=new Set(matches),current=[...body.rows].filter(row=>matching.has(row));
            if(matches.some((row,index)=>row!==current[index]))body.append(...matches);
            count.textContent='('+new Intl.NumberFormat(document.documentElement.lang||'en').format(matches.length)+')';empty.hidden=matches.length>0;
            headers.forEach(header=>header.setAttribute('aria-sort',header.dataset.accessSort===sort?(descending?'descending':'ascending'):'none'));
            window.ResourceTable.setPage(matrix,{page,hasNext:page+1<totalPages,totalPages,loading:false});
            syncActions();
        }
        headers.forEach(header=>{
            const label=header.textContent.trim(),button=document.createElement('button');button.type='button';button.className='access-matrix-sort';
            button.append(document.createTextNode(label));button.setAttribute('aria-label',label);
            button.innerHTML+='<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 11L12 5H4L8 11Z" /></svg>';
            button.addEventListener('click',()=>{if(sort===header.dataset.accessSort)descending=!descending;else{sort=header.dataset.accessSort;descending=false;}page=0;render();});header.replaceChildren(button);
        });
        search.addEventListener('input',()=>{page=0;render();});
        search.addEventListener('keydown',event=>{if(event.key==='Enter')event.preventDefault();});
        matrix.addEventListener('resource-table:page',event=>{page=event.detail.page;render();});
        matrix.addEventListener('resource-table:page-size',event=>{size=event.detail.size;page=0;render();});
        matrix.addEventListener('access-matrix:changed',render);
        form.addEventListener('input',syncActions);form.addEventListener('change',syncActions);
        discard.addEventListener('click',()=>{
            for(const {control,value,checked,disabled} of baseline){control.value=value;control.disabled=disabled;if(control.type==='checkbox'||control.type==='radio')control.checked=checked;}
            render();form.dispatchEvent(new Event('input',{bubbles:true}));
        });
        render();
    }
    if(window.WorkspaceNavigation)window.WorkspaceNavigation.onReady(ready);
    else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
