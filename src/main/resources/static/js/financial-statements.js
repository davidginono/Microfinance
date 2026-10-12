(() => {
 'use strict';
 const root=document.querySelector('[data-statement-register]');if(!root)return;
 let selected=null;const form=document.getElementById('statement-export'),view=root.querySelector('[data-statement-view]'),buttons=root.querySelectorAll('[data-statement-export]');
 const rows=[...root.querySelectorAll('[data-statement-id]')],status=root.querySelector('[data-statement-selection]');
 function choose(row){selected=row;rows.forEach(r=>{r.classList.toggle('statement-selected',r===row);r.querySelector('input').checked=r===row;});status.textContent=row?status.dataset.selected:status.dataset.empty;view.disabled=!row;buttons.forEach(b=>b.disabled=!row);if(row)form.action=new URL('results/'+row.dataset.statementId+'/pdf',location.origin+root.dataset.base+'/').href;else form.removeAttribute('action');}
 // Preserve the servlet context when constructing resource commands.
 root.dataset.base=location.pathname.replace(/\/$/,'');
 rows.forEach(row=>{row.querySelector('input').addEventListener('change',()=>choose(selected===row?null:row));row.addEventListener('click',event=>{if(event.target.closest('input,a,button,select'))return;choose(selected===row?null:row);});});
 view.addEventListener('click',()=>{if(selected)location.assign(root.dataset.base+'/results/'+selected.dataset.statementId);});
 form.addEventListener('submit',event=>{if(!selected)event.preventDefault();});
 root.querySelectorAll('form:not(#statement-export)').forEach(f=>f.addEventListener('change',()=>choose(null)));
 window.addEventListener('pageshow',()=>choose(null));choose(null);
})();
