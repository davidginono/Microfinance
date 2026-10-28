/* Live, scope-authorized dashboard infolets. Links retain the native resource workflows. */
(() => {
    'use strict';
    const regions=new Map();
    const node=(tag,cls,text)=>{const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined)el.textContent=String(text);return el;};
    function init(root){
        if(regions.has(root))return;
        const labels=Object.fromEntries([...document.querySelector('[data-work-labels]')?.content.querySelectorAll('[data-key]')||[]].map(el=>[el.dataset.key,el.textContent]));
        const general=document.getElementById('dashboard-labels')?.dataset||{};
        const label=key=>labels[key]||labels['dashboard.work.open']||'';
        const l=key=>label('dashboard.work.'+key);
        const canvas=root.querySelector('[data-work-cards]'),chart=root.querySelector('[data-work-chart]'),status=root.querySelector('[data-work-status]');
        const entries=new Map(),lifetime=new AbortController();let request=null,version=0,timer=null;
        const format=value=>new Intl.NumberFormat(document.documentElement.lang||'en',{maximumFractionDigits:2}).format(value);
        const urlFor=path=>{const url=new URL((root.dataset.contextPath||'')+path,location.origin);if(url.origin!==location.origin)throw new Error('scope');return url.href;};
        const button=(text,cls='app-btn btn-neutral')=>{const el=node('button',cls,text);el.type='button';return el;};
        const icon=name=>{const img=node('img','dashboard-icon');img.src=general.icons+name+'.svg';img.alt='';return img;};
        const visible=()=>root.isConnected&&!root.hidden&&!document.hidden;
        function flip(e,back){e.widget.classList.toggle('is-flipped',back);e.front.inert=back;e.back.inert=!back;e.front.setAttribute('aria-hidden',String(back));e.back.setAttribute('aria-hidden',String(!back));(back?e.days:e.filter).focus({preventScroll:true});}
        function update(e,data){
            e.data=data;e.title.textContent=label(data.labelKey);e.link.href=urlFor(data.href);e.link.textContent=format(data.count);
            e.open.href=e.link.href;e.amount.textContent=data.amount===null?'':'TZS '+format(data.amount);
            e.age.textContent=data.oldestDays===null?'':l('oldest')+' '+format(data.oldestDays)+' '+l('days');
            e.widget.dataset.tone=data.tone;
            e.breakdown.replaceChildren();
            const groups=data.groups.length?data.groups:[{labelKey:data.labelKey,count:data.count,href:data.href}];
            const max=Math.max(1,...groups.map(g=>g.count));
            for(const group of groups){
                const link=node('a','dashboard-infolet-bar');link.href=urlFor(group.href);
                const name=node('span','',label(group.labelKey)),track=node('span','dashboard-infolet-track'),bar=node('span','dashboard-infolet-fill'),count=node('strong','',format(group.count));
                bar.style.width=(group.count/max*100)+'%';track.append(bar);link.append(name,track,count);e.breakdown.append(link);
            }
            e.criteria.textContent=e.minimumDays||Number(e.minimumAmount)?l('minimumDays')+': '+e.minimumDays+(data.id==='guarantees'?'':' · '+l('minimumAmount')+': '+format(e.minimumAmount)):l('all');
        }
        function create(data){
            const widget=node('article','dashboard-infolet'),inner=node('div','dashboard-infolet-inner'),front=node('div','dashboard-infolet-front'),back=node('form','dashboard-infolet-back');
            widget.dataset.infolet=data.id;widget.setAttribute('aria-label',label(data.labelKey));back.inert=true;back.setAttribute('aria-hidden','true');
            const heading=node('header'),title=node('h3'),tools=node('div','dashboard-infolet-tools'),expand=button(l('expand'),'dashboard-infolet-command'),filter=button(l('filters'),'dashboard-infolet-command');
            expand.prepend(icon('resize-area'));expand.setAttribute('aria-expanded','false');filter.prepend(icon('ellipsis'));tools.append(expand,filter);heading.append(title,tools);
            const body=node('div','dashboard-infolet-body'),summary=node('div','dashboard-infolet-summary'),link=node('a','dashboard-infolet-count'),amount=node('p','dashboard-infolet-amount'),age=node('p','dashboard-infolet-age'),open=node('a','dashboard-infolet-open',l('open')),breakdown=node('div','dashboard-infolet-breakdown');
            breakdown.hidden=true;summary.append(link,amount,age,open);body.append(summary,breakdown);const criteria=node('footer','dashboard-infolet-criteria');front.append(heading,body,criteria);
            const backHeader=node('header'),backTitle=node('h3','',l('filters')),cancel=button(l('back'),'dashboard-infolet-command');backHeader.append(backTitle,cancel);
            const fields=node('div','dashboard-infolet-fields'),daysLabel=node('label','',l('minimumDays')),days=node('input'),amountLabel=node('label','',l('minimumAmount')),minimum=node('input');
            days.type=minimum.type='number';minimum.dataset.amountInput='true';days.min=minimum.min='0';days.max='3650';minimum.max='9999999999999999.99';days.step='1';minimum.step='0.01';days.value=minimum.value='0';days.required=minimum.required=true;
            if(data.id==='guarantees'){amountLabel.hidden=true;minimum.disabled=true;}
            daysLabel.append(days);amountLabel.append(minimum);fields.append(daysLabel,amountLabel);const footer=node('footer'),reset=button(l('reset')),apply=button(l('apply'),'app-btn btn-launch');apply.type='submit';footer.append(reset,apply);back.append(backHeader,fields,footer);inner.append(front,back);widget.append(inner);canvas.append(widget);
            const e={widget,front,back,title,link,open,amount,age,breakdown,filter,days,minimum,criteria,minimumDays:0,minimumAmount:0,version:0,request:null};entries.set(data.id,e);update(e,data);
            expand.addEventListener('click',()=>{
                const boxes=new Map([...canvas.children].map(el=>[el,el.getBoundingClientRect()]));const expanded=widget.classList.toggle('is-expanded');breakdown.hidden=!expanded;expand.setAttribute('aria-expanded',String(expanded));expand.lastChild.textContent=expanded?l('collapse'):l('expand');
                if(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)for(const el of canvas.children){const old=boxes.get(el),now=el.getBoundingClientRect();if(old&&el.animate)el.animate([{transform:`translate(${old.left-now.left}px,${old.top-now.top}px)`},{transform:'translate(0,0)'}],{duration:240,easing:'ease-out'});}
            });
            filter.addEventListener('click',()=>{days.value=e.minimumDays;minimum.value=e.minimumAmount;flip(e,true);});cancel.addEventListener('click',()=>flip(e,false));
            reset.addEventListener('click',()=>{days.value='0';minimum.value='0';});
            back.addEventListener('submit',async event=>{event.preventDefault();if(!back.reportValidity())return;e.minimumDays=Number(days.value);e.minimumAmount=minimum.value;flip(e,false);await loadCard(e);});
            back.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();flip(e,false);}});
        }
        async function fetchWork(controller,days=0,amount=0){
            const url=new URL(root.dataset.workUrl,location.href);if(url.origin!==location.origin)throw new Error('scope');url.searchParams.set('days',days);url.searchParams.set('amount',amount);
            const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:controller.signal});
            if(!response.ok||response.redirected)throw new Error('request');const result=await response.json();if(!Array.isArray(result.infolets))throw new Error('data');return result;
        }
        async function loadCard(e){
            e.request?.abort();const controller=new AbortController(),current=++e.version;e.request=controller;const timeout=setTimeout(()=>controller.abort(),20000);
            window.AppLoading?.begin(e.front,{replace:true,table:false,label:general.loading});
            try{const data=await fetchWork(controller,e.minimumDays,e.minimumAmount);if(e.version!==current||!root.isConnected)return;const item=data.infolets.find(item=>item.id===e.data.id);if(!item){e.widget.remove();entries.delete(e.data.id);return;}update(e,item);}
            catch(error){if(e.version===current){status.textContent=general.loadError;status.setAttribute('role','alert');}}
            finally{clearTimeout(timeout);if(e.version===current){window.AppLoading?.end(e.front);e.request=null;}}
        }
        async function load(){
            if(!visible()||request)return;const controller=new AbortController(),current=++version;request=controller;const timeout=setTimeout(()=>controller.abort(),20000);status.textContent='';status.setAttribute('role','status');root.setAttribute('aria-busy','true');
            if(!entries.size)window.AppLoading?.begin(canvas,{replace:true,table:false,label:general.loading});
            try{
                const data=await fetchWork(controller);if(current!==version||!visible())return;
                const ids=new Set(data.infolets.map(item=>item.id));for(const [id,e] of entries)if(!ids.has(id)){e.widget.remove();entries.delete(id);}
                for(const item of data.infolets){const e=entries.get(item.id);if(!e)create(item);else if(!e.minimumDays&&!Number(e.minimumAmount))update(e,item);}
                status.textContent=data.infolets.length?'':l('empty');
                if(data.infolets.length)window.DashboardCharts?.render(chart,{type:'bar',height:240,title:l('chart'),integer:true,xLabel:l('title'),labels:data.infolets.map(item=>label(item.labelKey)),series:[{label:l('title'),values:data.infolets.map(item=>item.count)}]});else chart.replaceChildren();
                const captured=new Date(data.capturedAt);if(!Number.isNaN(captured.getTime()))root.querySelector('[data-work-updated]').textContent=l('updated')+' '+new Intl.DateTimeFormat(document.documentElement.lang||'en',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'Africa/Nairobi'}).format(captured);
                for(const e of entries.values())if(e.minimumDays||Number(e.minimumAmount))void loadCard(e);
            }catch(error){if(current===version){status.textContent=general.loadError;status.setAttribute('role','alert');}}
            finally{clearTimeout(timeout);if(current===version){request=null;root.removeAttribute('aria-busy');window.AppLoading?.end(canvas);clearTimeout(timer);timer=setTimeout(load,30000);}}
        }
        const stop=()=>{version++;request?.abort();request=null;clearTimeout(timer);window.AppLoading?.end(canvas);for(const e of entries.values()){e.version++;e.request?.abort();window.AppLoading?.end(e.front);}};
        root.querySelector('[data-work-refresh]').addEventListener('click',()=>{stop();void load();},{signal:lifetime.signal});
        root.addEventListener('dashboard:visibility',()=>{stop();if(visible())void load();},{signal:lifetime.signal});
        document.addEventListener('visibilitychange',()=>{stop();if(visible())void load();},{signal:lifetime.signal});
        regions.set(root,{destroy(){stop();lifetime.abort();}});void load();
    }
    function scan(){for(const [root,state] of regions)if(!root.isConnected){state.destroy();regions.delete(root);}document.querySelectorAll('[data-dashboard-work]').forEach(init);}
    function start(){scan();new MutationObserver(records=>{if(records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1&&(n.matches?.('[data-dashboard-board]')||n.querySelector?.('[data-dashboard-board]')))))scan();}).observe(document.body,{childList:true,subtree:true});}
    if(window.WorkspaceNavigation?.onReady)window.WorkspaceNavigation.onReady(start);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
    window.DashboardWork={init:scan};
})();
