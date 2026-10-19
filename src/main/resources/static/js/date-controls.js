/* Calendar-date presentation only. Named native inputs retain ISO transport and browser bounds. */
(() => {
'use strict';
const states=new WeakMap(),controls=new Set();let sequence=0;
const isoToDisplay=value=>/^\d{4}-\d{2}-\d{2}$/.test(value)?`${value.slice(8)}/${value.slice(5,7)}/${value.slice(0,4)}`:'';
function displayToIso(value){
 const m=/^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);if(!m||+m[3]<1)return null;
 const date=new Date(0);date.setUTCFullYear(+m[3],+m[2]-1,+m[1]);
 return date.getUTCFullYear()===+m[3]&&date.getUTCMonth()===+m[2]-1&&date.getUTCDate()===+m[1]?`${m[3]}-${m[2]}-${m[1]}`:null;
}
function enhance(native){
 if(states.has(native)||native.closest('[data-date-native]'))return;
 const wrapper=document.createElement('span');wrapper.className='erp-date-control';
 const input=document.createElement('input');input.type='text';input.className=native.className;input.placeholder='DD/MM/YYYY';input.inputMode='numeric';input.autocomplete='off';input.maxLength=10;input.dataset.dateDisplay='';
 for(const key of ['aria-label','aria-labelledby','aria-describedby','aria-invalid','title'])if(native.hasAttribute(key))input.setAttribute(key,native.getAttribute(key));
 input.id=(native.id||`erp-date-${++sequence}`)+'-display';
 if(native.id)document.querySelectorAll('label[for]').forEach(label=>{if(label.htmlFor===native.id)label.htmlFor=input.id;});
 const button=document.createElement('button');button.type='button';button.className='erp-date-calendar';button.setAttribute('aria-label',document.body.dataset.dateChoose||'Choose date');
 button.innerHTML='<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><rect x="2" y="3" width="12" height="11" rx="1" stroke="currentColor"/><path d="M5 1v4m6-4v4M2 7h12" stroke="currentColor"/></svg>';
 native.before(wrapper);wrapper.append(input,native,button);native.dataset.dateNative='';native.classList.add('erp-date-native');native.tabIndex=-1;native.setAttribute('aria-hidden','true');
 let emitting=false;
 function validate(){const value=input.value.trim(),iso=displayToIso(value);const invalid=value&&!iso||iso&&native.min&&iso<native.min||iso&&native.max&&iso>native.max;
  input.setCustomValidity(invalid?(document.body.dataset.dateInvalid||'Enter a valid date as DD/MM/YYYY.'):native.validity.stepMismatch?native.validationMessage:'');
 }
 function sync(){input.value=isoToDisplay(native.value);input.disabled=native.disabled;input.readOnly=native.readOnly;input.required=native.required;button.disabled=native.disabled||native.readOnly;validate();}
 const commit=type=>{const value=input.value.trim();emitting=true;native.value=value?(displayToIso(value)||''):'';validate();native.dispatchEvent(new Event(type,{bubbles:true}));emitting=false;};
 input.addEventListener('input',event=>{event.stopPropagation();commit('input');});input.addEventListener('change',event=>{event.stopPropagation();commit('change');});
 for(const event of ['input','change'])native.addEventListener(event,()=>{if(!emitting)sync();});
 native.addEventListener('invalid',event=>{event.preventDefault();input.focus();});
 button.addEventListener('click',()=>{if(!button.disabled){try{native.showPicker();}catch{native.focus();}}});
 const valueProperty=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');
 Object.defineProperty(native,'value',{configurable:true,get(){return valueProperty.get.call(this);},set(value){valueProperty.set.call(this,value);if(!emitting)sync();}});
 states.set(native,{sync});controls.add(native);sync();
 new MutationObserver(sync).observe(native,{attributes:true,attributeFilter:['value','min','max','step','disabled','readonly','required']});
 native.form?.addEventListener('reset',()=>setTimeout(sync,0));
}
function scan(root){if(root.nodeType!==1&&root!==document)return;if(root.matches?.('input[type=date]'))enhance(root);root.querySelectorAll('input[type=date]').forEach(enhance);}
scan(document);
new MutationObserver(records=>{for(const record of records)record.addedNodes.forEach(scan);for(const native of controls)if(!native.isConnected)controls.delete(native);}).observe(document.body,{childList:true,subtree:true});
window.addEventListener('pageshow',()=>controls.forEach(native=>states.get(native).sync()));
})();
