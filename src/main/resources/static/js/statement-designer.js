(() => {
 'use strict';
 document.getElementById('statement-back')?.addEventListener('click',()=>history.back());
 const form=document.getElementById('statement-form');if(!form)return;
 const definition=JSON.parse(document.getElementById('statement-seed').value),catalog=JSON.parse(document.getElementById('statement-catalog').value);
 const words=Object.fromEntries([...document.querySelectorAll('#statement-words [data-word]')].map(e=>[e.dataset.word,e.textContent]));
 let serial=definition.rows.length;
 const node=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e;};
 const label=(parent,text,control)=>{const e=node('label',text);e.append(control);parent.append(e);return control;};
 const input=(value,change,max=120)=>{const e=node('input');e.value=value||'';e.maxLength=max;e.required=true;e.addEventListener('input',()=>change(e.value));return e;};
 const select=(values,value,change,multiple=false)=>{const e=node('select');e.multiple=multiple;values.forEach(([id,title])=>{const o=node('option',title);o.value=id;o.selected=multiple?value.includes(id):id===value;e.append(o);});e.addEventListener('change',()=>change(multiple?[...e.selectedOptions].map(o=>o.value):e.value));return e;};
 const checkbox=(parent,key,value,change)=>{const e=node('input');e.type='checkbox';e.checked=value;e.addEventListener('change',()=>change(e.checked));label(parent,words[key],e);};
 const button=(text,action)=>{const e=node('button',text);e.type='button';e.className='app-btn btn-neutral';e.addEventListener('click',action);return e;};
 const types=['HEADING','ACCOUNT_GROUP','SUBTOTAL','RATIO','NOTE'],sections=['ASSETS','LIABILITIES','EQUITY','INCOME','EXPENSES','OPERATING','INVESTING','FINANCING','NOTES'];
 function changeKind(row,kind){row.kind=kind;row.accounts=[];row.expression=null;row.sign=null;row.memo=false;row.unit='NONE';if(kind==='ACCOUNT_GROUP'){row.unit='TZS';row.sign='DEBIT_POSITIVE';}if(kind==='SUBTOTAL'||kind==='RATIO'){row.unit=kind==='RATIO'?'RATIO':'TZS';row.expression={operation:kind==='RATIO'?'RATIO':'SUM',lines:[]};}if(kind==='NOTE'){row.noteEn='';row.noteSw='';}render();}
 function render(){
  const parent=document.getElementById('statement-rows');parent.replaceChildren();
  definition.rows.forEach((row,index)=>{
   const panel=node('section');panel.className='statement-row';const heading=node('div');heading.className='statement-row-heading';heading.append(node('strong',row.labelEn));const actions=node('div');actions.className='statement-actions';
   const up=button(words.up,()=>{[definition.rows[index-1],definition.rows[index]]=[row,definition.rows[index-1]];render();});up.disabled=index===0;
   const down=button(words.down,()=>{[definition.rows[index+1],definition.rows[index]]=[row,definition.rows[index+1]];render();});down.disabled=index===definition.rows.length-1;
   actions.append(up,down,button(words.remove,()=>{definition.rows.splice(index,1);render();}));heading.append(actions);panel.append(heading);
   const fields=node('div');fields.className='statement-fields';label(fields,words.labelEn,input(row.labelEn,v=>{row.labelEn=v;heading.firstChild.textContent=v;}));label(fields,words.labelSw,input(row.labelSw,v=>row.labelSw=v));
   label(fields,words.kind,select(types.map(k=>[k,words['kind_'+k]]),row.kind,v=>changeKind(row,v)));label(fields,words.section,select(sections.map(k=>[k,words['section_'+k]]),row.section,v=>{row.section=v;if(row.kind==='ACCOUNT_GROUP')row.sign=['LIABILITIES','EQUITY','INCOME'].includes(v)?'CREDIT_POSITIVE':'DEBIT_POSITIVE';render();}));panel.append(fields);
   if(row.kind==='ACCOUNT_GROUP'){
    const fields=node('div');fields.className='statement-fields';label(fields,words.accounts,select(catalog.map(a=>[a.id,a.code+' · '+a.name]),row.accounts,v=>row.accounts=v,true));label(fields,words.sign,select([['DEBIT_POSITIVE',words.normal],['CREDIT_POSITIVE',words.reverse]],row.sign,v=>row.sign=v));panel.append(fields);
    checkbox(panel,'memo',row.memo,v=>{row.memo=v;if(v)row.section='NOTES';render();});if(row.memo)label(panel,words.exceptionEvidence,input(row.exceptionEvidence,v=>row.exceptionEvidence=v,500));
   }
   if(row.expression){const fields=node('div');fields.className='statement-fields';label(fields,words.kind,select((row.kind==='RATIO'?['RATIO']:['SUM','DIFFERENCE']).map(k=>[k,words['op_'+k]]),row.expression.operation,v=>{row.expression.operation=v;render();}));const eligible=definition.rows.filter(r=>r.id!==row.id&&r.unit==='TZS'&&!r.memo).map(r=>[r.id,r.labelEn]);
    if(row.expression.operation==='SUM')label(fields,words.lines,select(eligible,row.expression.lines,v=>row.expression.lines=v,true));
    else for(let operand=0;operand<2;operand++){const control=select([['',words.lines],...eligible],row.expression.lines[operand]||'',v=>row.expression.lines[operand]=v);control.required=true;label(fields,words.lines+' '+(operand+1),control);}panel.append(fields);}
   if(row.kind==='NOTE'){label(panel,words.noteEn,input(row.noteEn,v=>row.noteEn=v,1000));label(panel,words.noteSw,input(row.noteSw,v=>row.noteSw=v,1000));}
   checkbox(panel,'visible',row.visible,v=>row.visible=v);checkbox(panel,'collapsed',row.collapsed,v=>row.collapsed=v);parent.append(panel);
  });
  const ex=document.getElementById('statement-exclusions');ex.replaceChildren();definition.exclusions.forEach((item,index)=>{const panel=node('div');panel.className='statement-fields';label(panel,words.account,select(catalog.map(a=>[a.id,a.code+' · '+a.name]),item.account,v=>item.account=v));label(panel,words.treatmentEn,input(item.treatmentEn,v=>item.treatmentEn=v,500));label(panel,words.treatmentSw,input(item.treatmentSw,v=>item.treatmentSw=v,500));label(panel,words.evidence,input(item.evidence,v=>item.evidence=v,500));panel.append(button(words.remove,()=>{definition.exclusions.splice(index,1);render();}));ex.append(panel);});
 }
 document.getElementById('statement-title-en').value=definition.titleEn;document.getElementById('statement-title-sw').value=definition.titleSw;document.getElementById('statement-kind').value=definition.kind;document.getElementById('statement-comparison').checked=definition.comparison;
 document.getElementById('statement-add-row').addEventListener('click',()=>{definition.rows.push({id:'LINE_'+(++serial)+'_'+crypto.randomUUID().slice(0,8).toUpperCase(),labelEn:words['kind_HEADING'],labelSw:words['kind_HEADING'],kind:'HEADING',section:'ASSETS',unit:'NONE',accounts:[],sign:null,expression:null,noteEn:null,noteSw:null,visible:true,collapsed:false,memo:false,exceptionEvidence:null});render();});
 document.getElementById('statement-add-exclusion').addEventListener('click',()=>{if(catalog.length)definition.exclusions.push({account:catalog[0].id,treatmentEn:'',treatmentSw:'',evidence:''});render();});
 form.addEventListener('submit',()=>{definition.titleEn=document.getElementById('statement-title-en').value;definition.titleSw=document.getElementById('statement-title-sw').value;definition.kind=document.getElementById('statement-kind').value;definition.comparison=document.getElementById('statement-comparison').checked;document.getElementById('statement-definition').value=JSON.stringify(definition);});render();
})();
