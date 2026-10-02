(() => {
  'use strict';
  const form = document.getElementById('report-designer');
  if (!form) return;
  let definition = JSON.parse(document.getElementById('report-seed').value);
  const catalogs = JSON.parse(document.getElementById('report-catalog').value);
  const labels = Object.fromEntries([...document.querySelectorAll('#report-labels [data-field]')].map(e => [e.dataset.field, e.textContent]));
  const words = Object.fromEntries([...document.querySelectorAll('#report-words [data-word]')].map(e => [e.dataset.word, e.textContent]));
  const money = new Set(['AMOUNT', 'PRINCIPAL', 'INTEREST', 'OUTSTANDING_PRINCIPAL']);
  const grouped = new Set(['CHANNEL', 'KIND']);
  const byId = id => document.getElementById(id);
  function element(tag, text, attrs = {}) {
    const e = document.createElement(tag);
    if (text !== null) e.textContent = text;
    for (const [key,value] of Object.entries(attrs)) e.setAttribute(key,value);
    return e;
  }
  function labelled(text, control) { const e = element('label',text);e.append(control);return e; }
  function options(values, current) {
    const select = element('select',null,{class:'aws-control'});
    values.forEach(([value,label]) => { const e = element('option',label,{value}); select.append(e); });
    select.value = current; return select;
  }
  function collectMeta() {
    definition.title = byId('report-name').value;
    definition.footer = byId('report-footer').value;
    definition.language = byId('report-language').value;
    definition.landscape = byId('report-landscape').value === 'true';
    const field = byId('report-sort').value;
    definition.sorts = field ? [{field,descending:byId('report-descending').value === 'true'}] : [];
  }
  function drawColumns() {
    const area = byId('report-columns'); area.replaceChildren();
    definition.columns.forEach((column,index) => {
      const row = element('div',null,{class:'report-column'});
      row.append(element('strong',labels[column.field]+(money.has(column.field)?' (TZS)':'')));
      const shown = element('input',null,{type:'checkbox'}); shown.checked = column.visible;
      shown.addEventListener('change',()=>{column.visible=shown.checked;if(!shown.checked){definition.totals=definition.totals.filter(f=>f!==column.field);definition.groups=definition.groups.filter(f=>f!==column.field);}drawColumns();});
      row.append(labelled(words.show,shown));
      const alias = element('input',null,{class:'aws-control',type:'text',maxlength:'50'});alias.value=column.label;
      alias.addEventListener('input',()=>column.label=alias.value);row.append(labelled(words.alias,alias));
      const width = element('input',null,{class:'aws-control',type:'number',min:'60',max:'300'});width.value=column.width;
      width.addEventListener('input',()=>column.width=Number(width.value));row.append(labelled(words.width,width));
      if(money.has(column.field)) {
        const total=element('input',null,{type:'checkbox'});total.checked=definition.totals.includes(column.field);
        total.addEventListener('change',()=>{definition.totals=definition.totals.filter(f=>f!==column.field);if(total.checked){definition.totals.push(column.field);column.visible=true;}drawColumns();});row.append(labelled(words.total,total));
      }
      if(grouped.has(column.field)) {
        const group=element('input',null,{type:'checkbox'});group.checked=definition.groups.includes(column.field);
        group.addEventListener('change',()=>{definition.groups=definition.groups.filter(f=>f!==column.field);if(group.checked)definition.groups.push(column.field);
          if(definition.groups.length){definition.columns.forEach(c=>c.visible=definition.groups.includes(c.field)||definition.totals.includes(c.field));definition.sorts=[];}
          drawColumns();});row.append(labelled(words.group,group));
      }
      for(const [delta,text] of [[-1,words.up],[1,words.down]]) {
        const move=element('button',text,{type:'button',class:'app-btn btn-neutral','aria-label':text+' '+labels[column.field]});
        move.disabled=index+delta<0||index+delta>=definition.columns.length;
        move.addEventListener('click',()=>{const target=index+delta;[definition.columns[index],definition.columns[target]]=[definition.columns[target],definition.columns[index]];drawColumns();
          byId('report-columns').children[target].querySelector('button').focus();});row.append(move);
      }
      area.append(row);
    });
    const visible=definition.columns.filter(c=>c.visible).map(c=>[c.field,labels[c.field]]);
    const previous=definition.sorts[0]?.field;
    const select=byId('report-sort');select.replaceChildren(...options(visible,previous).children);select.value=previous||visible[0]?.[0]||'';
  }
  function drawFilters() {
    const area=byId('report-filters');area.replaceChildren();
    definition.filters.forEach((filter,index)=>{
      const row=element('div',null,{class:'report-filter'});
      const fields=catalogs.find(c=>c.dataset===definition.dataset).fields;
      const field=options(fields.map(f=>[f,labels[f]]),filter.field);
      field.addEventListener('change',()=>{filter.field=field.value;filter.operator='EQ';filter.value='';drawFilters();});
      row.append(labelled(words.field,field));
      const allowed=money.has(filter.field)||filter.field==='EFFECTIVE_DATE'?['EQ','GE','LE']:['EQ'];
      const op=options(allowed.map(o=>[o,words[o]]),filter.operator);op.addEventListener('change',()=>filter.operator=op.value);row.append(labelled(words.operator,op));
      const input=element('input',null,{class:'aws-control',type:filter.field==='EFFECTIVE_DATE'?'date':money.has(filter.field)?'number':'text',maxlength:'64',required:'required'});
      if(money.has(filter.field))input.setAttribute('step','0.01');input.value=filter.value;input.addEventListener('input',()=>filter.value=input.value);row.append(labelled(words.value,input));
      const remove=element('button',words.remove,{type:'button',class:'app-btn btn-neutral'});remove.addEventListener('click',()=>{definition.filters.splice(index,1);drawFilters();});row.append(remove);area.append(row);
    });
    byId('report-add-filter').disabled=definition.filters.length>=8;
  }
  function hydrate() {
    byId('report-dataset').value=definition.dataset;byId('report-name').value=definition.title;byId('report-footer').value=definition.footer;
    byId('report-language').value=definition.language;byId('report-landscape').value=String(definition.landscape);
    byId('report-descending').value=String(definition.sorts[0]?.descending||false);drawColumns();drawFilters();
  }
  byId('report-dataset').addEventListener('change',()=>{
    collectMeta();definition.dataset=byId('report-dataset').value;definition.groups=[];definition.filters=[];definition.sorts=[];
    const fields=catalogs.find(c=>c.dataset===definition.dataset).fields;
    definition.columns=fields.map(field=>({field,label:'',width:130,visible:true}));definition.totals=fields.filter(f=>money.has(f));hydrate();
  });
  byId('report-add-filter').addEventListener('click',()=>{definition.filters.push({field:'LOAN_ID',operator:'EQ',value:''});drawFilters();});
  form.addEventListener('submit',()=>{collectMeta();byId('report-definition').value=JSON.stringify(definition);});
  hydrate();
})();
