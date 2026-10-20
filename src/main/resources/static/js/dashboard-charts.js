/* Code-native JSP charts following Cloudscape data-vis guidance; no React runtime. */
(() => {
    'use strict';
    const states=new Map();
    // Cloudscape classic categorical palette: blue, pink, teal, purple, orange.
    const palette=['#688ae8','#c33d69','#2ea597','#8456ce','#e07941','#3759ce','#962249','#096f64'];
    const labels=()=>document.getElementById('dashboard-labels')?.dataset || {};
    const node=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
    const svg=(tag,attrs={},text)=>{const n=document.createElementNS('http://www.w3.org/2000/svg',tag);Object.entries(attrs).forEach(([key,value])=>n.setAttribute(key,String(value)));if(text!==undefined)n.textContent=text;return n;};
    const number=value=>value===null || value===undefined || value==='' || !Number.isFinite(Number(value)) || Number(value)<0?null:Number(value);
    const format=(value,integer=false)=>value===null?'—':new Intl.NumberFormat(document.documentElement.lang || 'en',{maximumFractionDigits:integer?0:2}).format(value);
    function axis(max,integer){
        if(max<=0)return {max:1,step:integer?1:.25};
        const raw=max/4,magnitude=10**Math.floor(Math.log10(raw)),normal=raw/magnitude;
        const step=Math.max(integer?1:0,(normal<=1?1:normal<=2?2:normal<=5?5:10)*magnitude);
        return {max:Math.ceil(max/step)*step,step};
    }
    function draw(state){
        const {root,data,hidden,graphic,feedback}=state,l=labels();
        const focused=graphic.contains(document.activeElement)?document.activeElement:null;
        const focusKey=focused?.getAttribute('data-point-key');
        graphic.replaceChildren();
        const series=data.series.slice(0,8),observations=series.filter((_,i)=>!hidden.has(i)).flatMap(s=>s.values.map(number)),values=observations.filter(v=>v!==null),hasMissing=observations.some(v=>v===null);
        if(series.length && series.every((_,i)=>hidden.has(i))){graphic.append(node('p','dashboard-chart-empty',l.noSeries));return;}
        if(!data.labels.length || !values.length || !values.some(v=>v>0)){
            graphic.append(node('p','dashboard-chart-empty',values.length && !hasMissing && values.every(v=>v===0)?l.noActivity:l.noData));return;
        }
        const width=Math.max(200,Math.round(root.getBoundingClientRect().width || 640)),height=Math.max(180,Math.min(320,Number(data.height) || 240)),left=54,right=16,top=16,bottom=48,w=width-left-right,h=height-top-bottom;
        const scale=axis(Math.max(...values),data.integer),chart=svg('svg',{viewBox:`0 0 ${width} ${height}`,role:'img','aria-label':data.title || root.dataset.chartLabel || l.chart});
        chart.style.height=height+'px';
        chart.append(svg('title',{},data.title || root.dataset.chartLabel || l.chart));
        const y=value=>top+h-h*value/scale.max;
        for(let tick=0;tick<=scale.max+scale.step/2;tick+=scale.step){const yy=y(tick);chart.append(svg('line',{x1:left,y1:yy,x2:width-right,y2:yy,class:'chart-grid'}),svg('text',{x:left-8,y:yy+4,'text-anchor':'end'},format(tick,data.integer)));}
        chart.append(svg('line',{x1:left,y1:top+h,x2:width-right,y2:top+h,class:'chart-axis'}));
        const count=data.labels.length,slot=w/Math.max(count,1),x=index=>left+slot*(index+.5);
        const ticks=Math.max(2,Math.floor(w/90)),every=Math.max(1,Math.ceil(count/ticks));
        data.labels.forEach((label,i)=>{if(i%every!==0 && i!==count-1)return;const labelNode=svg('text',{x:x(i),y:top+h+22,'text-anchor':'middle'},label.length>18?label.slice(0,16)+'…':label);labelNode.append(svg('title',{},label));chart.append(labelNode);});
        const active=series.map((s,i)=>({...s,index:i})).filter(s=>!hidden.has(s.index));
        let firstPoint=true;
        active.forEach((s,visibleIndex)=>{
            const color=palette[s.index%palette.length],group=svg('g',{'data-series':s.index});
            if(data.type!=='bar'){
                let path='',start=true;
                s.values.forEach((raw,i)=>{const value=number(raw);if(value===null){start=true;return;}path+=(start?'M':'L')+x(i)+' '+y(value)+' ';start=false;});
                group.append(svg('path',{d:path,fill:'none',stroke:color,'stroke-width':2.5}));
            }
            s.values.forEach((raw,i)=>{
                if(i>=count)return;const value=number(raw);if(value===null)return;
                const description=data.labels[i]+': '+s.label+' '+format(value,data.integer)+(data.unit?' '+data.unit:'');
                const attrs={tabindex:firstPoint?0:-1,role:'img','aria-label':description,'data-point-key':s.index+':'+i,'data-point-index':i};firstPoint=false;
                const shape=data.type==='bar'
                    ?svg('rect',{...attrs,x:left+i*slot+slot*.15+visibleIndex*(slot*.7/active.length),y:y(value),width:Math.max(1,slot*.7/active.length-2),height:Math.max(0,top+h-y(value)),fill:color})
                    :svg('circle',{...attrs,cx:x(i),cy:y(value),r:3.5,fill:color,stroke:'#fff','stroke-width':1.5});
                shape.append(svg('title',{},description));shape.addEventListener('focus',()=>feedback.textContent=description);shape.addEventListener('mouseenter',()=>feedback.textContent=description);shape.addEventListener('mouseleave',()=>feedback.textContent='');group.append(shape);
                shape.addEventListener('keydown',event=>{
                    if(event.key==='Escape'){event.preventDefault();state.legend.querySelector('button')?.focus();return;}
                    if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(event.key))return;event.preventDefault();
                    const points=[...group.querySelectorAll('[data-point-key]')],current=points.indexOf(shape);
                    let target;
                    if(event.key==='ArrowUp' || event.key==='ArrowDown'){
                        const other=active[(visibleIndex+(event.key==='ArrowDown'?1:-1)+active.length)%active.length];
                        target=chart.querySelector('[data-point-key="'+other.index+':'+i+'"]') || chart.querySelector('[data-series="'+other.index+'"] [data-point-key]');
                    }else target=points[event.key==='Home'?0:event.key==='End'?points.length-1:(current+(event.key==='ArrowRight'?1:-1)+points.length)%points.length];
                    if(target){chart.querySelectorAll('[data-point-key]').forEach(n=>n.setAttribute('tabindex','-1'));target.setAttribute('tabindex','0');target.focus();}
                });
            });chart.append(group);
        });
        graphic.append(chart);
        if(hasMissing)graphic.append(node('p','dashboard-data-status',l.partialData));
        if(focusKey){const target=chart.querySelector('[data-point-key="'+focusKey+'"]');if(target){chart.querySelectorAll('[data-point-key]').forEach(n=>n.setAttribute('tabindex','-1'));target.setAttribute('tabindex','0');target.focus();}}
    }
    function render(root,options){
        if(!root)return;let state=states.get(root);const l=labels();
        if(!state){
            const graphic=node('div'),legend=node('div','dashboard-chart-legend'),feedback=node('div','dashboard-chart-tooltip');feedback.setAttribute('role','status');
            const details=node('details','dashboard-chart-data'),summary=node('summary','',l.viewData),scroll=node('div'),table=node('table');scroll.append(table);details.append(summary,scroll);
            root.classList.add('dashboard-chart');root.replaceChildren(graphic,legend,feedback,details);
            state={root,graphic,legend,feedback,details,table,hidden:new Set(),data:null};states.set(root,state);
        }
        state.data={...options,labels:options.labels.map(String),series:options.series.map(s=>({label:String(s.label),values:s.values.map(number)}))};
        const expected=state.data.labels.length;
        state.data.series.forEach(s=>{s.values=Array.from({length:expected},(_,i)=>s.values[i]??null);});
        state.legend.replaceChildren();state.feedback.textContent='';
        state.data.series.slice(0,8).forEach((s,i)=>{
            const button=node('button');button.type='button';button.setAttribute('aria-pressed',String(!state.hidden.has(i)));button.setAttribute('aria-label',l.series+' '+s.label);
            const key=node('span','dashboard-chart-key');key.style.setProperty('--series-color',palette[i]);key.setAttribute('aria-hidden','true');button.append(key,document.createTextNode(s.label+(options.unit?' ('+options.unit+')':'')));
            button.addEventListener('click',()=>{if(state.hidden.has(i))state.hidden.delete(i);else state.hidden.add(i);button.setAttribute('aria-pressed',String(!state.hidden.has(i)));draw(state);});state.legend.append(button);
        });
        const head=document.createElement('thead'),header=document.createElement('tr');header.append(node('th','',options.xLabel || l.category));
        state.data.series.forEach(s=>header.append(node('th','',s.label+(options.unit?' ('+options.unit+')':''))));head.append(header);
        const body=document.createElement('tbody');
        state.data.labels.forEach((label,i)=>{const row=document.createElement('tr');row.append(node('th','',label));state.data.series.forEach(s=>row.append(node('td','',format(s.values[i],options.integer))));body.append(row);});
        state.table.replaceChildren(head,body);draw(state);
    }
    function init(scope=document){
        scope.querySelectorAll('[data-dashboard-chart]:not([data-chart-ready])').forEach(root=>{
            const table=root.querySelector('table[data-chart-source]');if(!table)return;
            const rows=[...table.tBodies].flatMap(body=>[...body.rows]),head=[...(table.tHead?.rows[0]?.cells || [])];
            root.dataset.chartReady='true';render(root,{type:root.dataset.chartType || 'bar',title:root.dataset.chartLabel,integer:root.dataset.chartInteger!=='false',unit:root.dataset.chartUnit || '',xLabel:head[0]?.textContent.trim(),labels:rows.map(r=>r.cells[0].textContent.trim()),series:head.slice(1).map((h,i)=>({label:h.textContent.trim(),values:rows.map(r=>r.cells[i+1]?.getAttribute('data-value') ?? r.cells[i+1]?.textContent.trim())}))});
        });
        for(const [root,state] of states)if(!root.isConnected){state.observer?.disconnect();states.delete(root);}
        if(window.ResizeObserver)for(const state of states.values())if(!state.observer){state.observer=new ResizeObserver(()=>draw(state));state.observer.observe(state.root);}
    }
    window.DashboardCharts={render,init,palette};
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>init(),{once:true});else init();
    document.addEventListener('dashboard:updated',()=>init());
})();
