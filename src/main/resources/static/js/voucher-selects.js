/* Voucher lookups share the application's searchable Cloudscape-style select. */
(() => {
 'use strict';
 const bindings=new Map();
 function bind(select,{field,label,url,choice,changed,error,fixed=[]}){
  if(bindings.has(select))return bindings.get(select);
  const placeholder=select.options[0]?.textContent || document.body.dataset.shellSelect;
  const ui=window.NeoSelect.search(select);
  const initial=()=>new Map(fixed.map(entry=>[entry.value,entry]));
  let page=0,version=0,controller,loaded=false,values=initial();
  const sync=()=>{
   if(select.value===field.value)return;
   let option=[...select.options].find(o=>o.value===field.value);
   if(field.value && !option){option=new Option(label(),field.value);select.add(option);}
   select.value=field.value;window.refreshEnhancedSelects?.(select.parentElement);
  };
  const cancel=()=>{binding.cancel();++version;controller?.abort();loaded=false;ui.setLoading(false);};
  async function load(next=0){
   controller?.abort();controller=new AbortController();const token=++version,query=ui.input.value,endpoint=new URL(url(),location.origin);
   if(endpoint.origin!==location.origin)return;
   endpoint.searchParams.set('search',query);endpoint.searchParams.set('page',next);
   ui.setError('');ui.setLoading(true);ui.more.disabled=true;
   try{
    const response=await fetch(endpoint,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store',signal:controller.signal});
    if(!response.ok || response.redirected)throw Error();const data=await response.json();if(token!==version || !select.isConnected)return;
    const current=select.value,selected=select.selectedOptions[0]?.cloneNode(true);
    if(!next)values=initial();
    data.slice(0,25).forEach(item=>{const option=choice(item);if(option)values.set(String(option.value),{...option,item});});
    values=new Map([...initial(),...[...values].filter(([value])=>!fixed.some(entry=>entry.value===value)).slice(-100)]);
    const options=[...values].map(([value,entry])=>{const option=new Option(entry.label,value);if(!fixed.some(entry=>entry.value===value))option.dataset.searchQuery=query.trim().toLocaleLowerCase();return option;});
    select.replaceChildren(new Option(placeholder,''),...options);
    if(current && !values.has(current) && selected)select.add(selected);select.value=current;
    page=next;loaded=true;ui.more.hidden=data.length<=25;
   }catch(e){if(token===version && e.name!=='AbortError'){ui.setError(error);ui.more.hidden=true;binding.cancel();}}
   finally{if(token===version){ui.setLoading(false);ui.more.disabled=false;}}
  }
  const binding=window.AppSearch.bind(ui.input,()=>load(),()=>{++version;controller?.abort();loaded=false;ui.setError('');ui.setLoading(true);ui.more.hidden=true;});
  select.addEventListener('neo-select:open',()=>{sync();if(!loaded)void load();});
  select.addEventListener('neo-select:close',cancel);
  const commit=()=>{const selected=values.get(select.value);if(!selected || field.value===selected.value)return;field.value=selected.value;changed(selected.item,selected.label);};
  select.addEventListener('input',commit);select.addEventListener('change',commit);
  ui.more.addEventListener('click',()=>{binding.cancel();void load(page+1);});
  const state={sync,cancel,reset(){cancel();ui.setError('');values=initial();select.replaceChildren(new Option(placeholder,''),...fixed.map(entry=>new Option(entry.label,entry.value)));sync();},dispose(){cancel();bindings.delete(select);}};
  bindings.set(select,state);sync();return state;
 }
 new MutationObserver(()=>{for(const [select,state] of bindings)if(!select.isConnected)state.dispose();}).observe(document.body,{childList:true,subtree:true});
 window.addEventListener('pagehide',()=>{for(const state of bindings.values())state.cancel();});
 function cleanClone(root){root.querySelectorAll('.neo-select').forEach(wrapper=>{const select=wrapper.querySelector('select');select.removeAttribute('data-neo-enhanced');select.classList.remove('neo-select-native');select.removeAttribute('tabindex');wrapper.replaceWith(select);});}
 window.VoucherSelect={bind,cleanClone};
})();
