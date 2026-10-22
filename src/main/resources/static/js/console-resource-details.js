/* Shared classic AWS resource presentation. Icons: Cloudscape Design System, Apache-2.0.
 * https://github.com/cloudscape-design/components/tree/main/src/icon/icons
 * See vendor/cloudscape-icons.LICENSE and vendor/cloudscape-icons.NOTICE. */
(() => {
    'use strict';
    const onReady = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener("load", callback, {once:true}));

    const artwork = {
    "status-positive": "<circle cx=\"8\" cy=\"8\" r=\"7\" />\n<path d=\"M4.5 7.5L7 10L11 5\" class=\"stroke-linejoin-round\"/>",
    "status-negative": "<circle cx=\"8\" cy=\"8\" r=\"7\"/>\n<path d=\"M5.5 5.5L10.5 10.5\" />\n<path d=\"M10.5 5.5L5.5 10.5\" />",
    "status-warning": "<path class=\"stroke-linejoin-round\" d=\"M8 5V9\" />\n<path class=\"stroke-linejoin-round\" d=\"M8 10L8 12\" />\n<path class=\"stroke-linejoin-round\" d=\"M6.52 1.88L1.19 11.64C1.06 11.87 1 12.14 1 12.4C1 13.28 1.71 13.99 2.59 13.99H13.4C14.28 13.99 14.99 13.28 14.99 12.4C14.99 12.13 14.92 11.87 14.8 11.64L9.48 1.88C9.18 1.34 8.62 1 8 1C7.38 1 6.82 1.34 6.52 1.88Z\" />",
    "status-pending": "<circle cx=\"8\" cy=\"8\" r=\"7\" />\n<path d=\"M8 4V9H4\" class=\"stroke-linejoin-round\"/>",
    "status-stopped": "<circle cx=\"8\" cy=\"8\" r=\"7\"/>\n<path d=\"M5 8H11\" />",
    "status-in-progress": "<circle cx=\"8\" cy=\"8\" r=\"7\"/>\n<path d=\"M9 7H7V9H9V7Z\" class=\"filled no-stroke\"/>\n<path d=\"M6 7H4V9H6V7Z\" class=\"filled no-stroke\"/>\n<path d=\"M12 7H10V9H12V7Z\" class=\"filled no-stroke\"/>",
    "status-info": "<circle cx=\"8\" cy=\"8\" r=\"7\"/>\n<path  d=\"M8 12V7\" />\n<path d=\"M8 6V4\" />",
    "copy": "<path d=\"M15 5H5V15H15V5Z\" class=\"stroke-linejoin-round awsui-icon-front\"/>\n<path d=\"M13 1H1V12\" class=\"stroke-linejoin-round awsui-icon-back\"/>",
    "external": "<path d=\"M13 9.01172V3.01172H7\" class=\"stroke-linejoin-round awsui-icon-arrow\"/>\n<path d=\"M13.02 3L7 9.01\" class=\"stroke-linejoin-round awsui-icon-arrow\"/>\n<path d=\"M3 5.01172V13.0117H9H11.01\" class=\"stroke-linejoin-round\"/>",
    "angle-right": "<path d=\"M5 2L11 8L5 14\" class=\"stroke-linejoin-round\"/>"
};
    const icon = name => {
        const node=document.createElementNS('http://www.w3.org/2000/svg','svg');
        node.setAttribute('viewBox','0 0 16 16');node.setAttribute('aria-hidden','true');
        node.classList.add('console-icon');node.innerHTML=artwork[name].replace(/>\s+</g,'><');return node;
    };
    const label = () => document.getElementById('console-resource-labels')?.dataset || {};
    const type = value => {
        const state=(value || '').trim().toUpperCase().replace(/[ -]+/g,'_');
        if (/^(ACTIVE|APPROVE[D]?|ALL_GUARANTORS_APPROVED|SUCCESS|SUCCESSFUL|ASSOCIATED|POSTED|PAID|DISBURSED|COMPLETED|COMPLETE|VALIDATED|ENABLED|LINKED|HEALTHY|OK|READY|ACCEPTED|DELIVERED|SENT|RESOLVED|ON_TRACK)$/.test(state))return 'success';
        if (/REJECT|FAIL|ERROR|DEFAULT|DEPLETED|CRITICAL|INVALID|DECLINED/.test(state))return 'error';
        if (/OVERDUE|ARREARS|^PAR$|WARNING|LOW|INCOMPLETE|NOT_VALIDATED|NOT_LINKED|RETRY/.test(state))return 'warning';
        if (/REVER|CANCEL|SUSPEND|RETIRED|INACTIVE|DISABLED|CLOSED|WRITTEN_OFF|STOPPED|SKIPPED/.test(state))return 'stopped';
        if (/REVIEW|PROCESSING|IN_PROGRESS|RUNNING/.test(state))return 'in-progress';
        if (/PENDING|AWAITING|READY_FOR|INVITED|DRAFT|RECORDED|QUEUED|WAIT|NOT_POSTED|UNPOSTED/.test(state))return 'pending';
        return 'info';
    };
    function status(node) {
        const state=node.dataset.resourceStatus;
        if(node.dataset.statusRendered===state && node.querySelector(':scope > .console-icon'))return;
        node.querySelector(':scope > .console-icon')?.remove();
        const kind=type(state);node.classList.add('console-status');node.dataset.statusType=kind;node.dataset.statusRendered=state;
        node.prepend(icon({'success':'status-positive','error':'status-negative','warning':'status-warning','pending':'status-pending','stopped':'status-stopped','in-progress':'status-in-progress','info':'status-info'}[kind]));
        // Retire the old pill surface as well as any legacy parent badge.
        const parent=node.parentElement;
        if(parent?.tagName==='SPAN' && parent.childElementCount===1)parent.classList.add('console-status-host');
    }
    function messages(scope) {
        scope.querySelectorAll('[data-message-type],.aws-inline-notice,.admin-status-callout,.admin-delete-callout,.loan-empty-state,.live-account-status-message,.app-loading-error,.workspace-navigation-feedback[role="alert"],.loan-error-summary,.accounting-error-summary,.accounting-resource-workspace > .coa-error,.accounting-resource-workspace > .coa-success,.library-modal .coa-success').forEach(message=>{
            if(!message.textContent.trim())return;
            const classes=message.className;
            const declared=message.dataset.messageAuto==='true'?null:message.dataset.messageType;
            if(!declared)message.dataset.messageAuto='true';
            const kind=/danger|error|delete-callout/.test(classes) || message.getAttribute('role')==='alert'?'error':/warning|is-unavailable|admin-status-callout/.test(classes)?'warning':/success/.test(classes)?'success':declared || 'info';
            message.classList.add('console-message');message.dataset.messageType=kind;
            if(!message.hasAttribute('role'))message.setAttribute('role',kind==='error'?'alert':'status');
            const current=message.querySelector(':scope > .console-message-icon');
            if(current?.dataset.kind===kind)return;
            current?.remove();const symbol=icon({'success':'status-positive','error':'status-negative','warning':'status-warning','info':'status-info'}[kind] || 'status-info');
            symbol.classList.add('console-message-icon');symbol.dataset.kind=kind;message.prepend(symbol);
        });
    }
    function breadcrumbs() {
        document.querySelectorAll('.erp-page-path').forEach(path=>{
            if(!path.hasAttribute('aria-label'))path.setAttribute('aria-label',label().breadcrumb || 'Breadcrumb');
            [...path.children].forEach(child=>{
                if(child.classList.contains('erp-page-path__separator') || /^[›>\/]$/.test(child.textContent.trim())){
                    if(!child.querySelector('svg')){child.replaceChildren(icon('angle-right'));child.classList.add('erp-page-path__separator');child.setAttribute('aria-hidden','true');}
                }
            });
        });
    }
    function values(scope) {
        scope.querySelectorAll('.erp-detail-facts dd,.resource-split-values dd,.voucher-metadata dd,[data-detail-value]').forEach(value=>{
            if(value.dataset.copyReady || value.querySelector('input,select,textarea,form') || !value.textContent.trim() || /^[-–—]$/.test(value.textContent.trim()))return;
            value.dataset.copyReady='true';
            if(value.querySelector('[data-resource-status]'))return;
            const text=value.textContent.trim();
            const content=document.createElement('span');content.className='console-detail-value';content.append(...value.childNodes);
            const button=document.createElement('button');button.type='button';button.className='console-copy';button.append(icon('copy'));
            const term=value.previousElementSibling?.textContent.trim() || '';
            button.setAttribute('aria-label',(label().copy || 'Copy')+(term?': '+term:''));button.title=button.getAttribute('aria-label');
            button.addEventListener('click',async()=>{
                try {await navigator.clipboard.writeText(text);announce(label().copied || 'Copied');}
                catch {announce(label().copyFailed || 'Unable to copy');}
            });
            content.querySelectorAll('a[href]:not(.app-btn)').forEach(link=>link.classList.add('console-resource-link'));
            value.append(button,content);
        });
    }
    function links(scope) {
        scope.querySelectorAll('a[href][target="_blank"]').forEach(link=>{
            if(link.hasAttribute('download') || link.hasAttribute('data-download-action') || link.hasAttribute('data-print-action'))return;
            link.relList.add('noopener','noreferrer');
            if(link.querySelector(':scope > .console-external-icon'))return;
            const artwork=icon('external');artwork.classList.add('console-external-icon');link.append(artwork);
            const hint=document.createElement('span');hint.className='sr-only';hint.textContent=' ('+(label().opensNewTab || 'opens in a new tab')+')';link.append(hint);
        });
    }
    function announce(text) {
        let region=document.getElementById('console-copy-feedback');
        if(!region){region=document.createElement('span');region.id='console-copy-feedback';region.className='sr-only';region.setAttribute('role','status');document.body.append(region);}
        region.textContent='';queueMicrotask(()=>region.textContent=text);
    }
    function details() {
        if(document.body.dataset.resourceDetails!=='true' && !document.querySelector('[data-resource-details-page]'))return;
        const scope=document.querySelector('.shell-content-frame') || document.querySelector('main');if(!scope || scope.dataset.detailsReady)return;
        scope.dataset.detailsReady='true';
        const selector='article.voucher-document,section.voucher-document,.erp-form-wrap,.loan-view-summary-card,.loan-view-hero-summary,section.repayment-section,section.accounting-form,.erp-panel,.erp-section,section.accounting-section,details.accounting-section,section.erp-resource-section,section.erp-table-wrap,div.erp-table-wrap,section.loan-flow-panel,section.erp-detail-card';
        const candidates=[...scope.querySelectorAll(selector)].filter(section=>!section.parentElement.closest(selector) && !section.closest('dialog,[role=dialog],[data-detail-persistent]') && section.querySelector('h2,h3,summary'));
        const summary=candidates.find(section=>section.hasAttribute('data-detail-summary') || section.querySelector('.erp-detail-facts,.voucher-metadata,.loan-staff-review-overview,.loan-view-hero-summary'));
        const panels=candidates.filter(section=>section!==summary && !section.matches('[data-detail-persistent],.accounting-filter-section'));
        if(!panels.length || (!summary && panels.length<2))return;
        const tabs=document.createElement('div');tabs.className='console-detail-tabs';tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label',label().sections || 'Details sections');
        const host=document.createElement('div');host.className='console-detail-panels';
        const anchor=summary || panels[0];anchor[summary?'after':'before'](tabs,host);
        const buttons=[];let active;
        const activate=(id,focus=false,remember=true)=>{
            const index=panels.findIndex(panel=>panel.id===id);if(index<0)return;active=index;
            panels.forEach((panel,i)=>{panel.hidden=i!==index;buttons[i].setAttribute('aria-selected',String(i===index));buttons[i].tabIndex=i===index?0:-1;});
            if(focus)buttons[index].focus();
            if(remember){const url=new URL(location.href);url.searchParams.set('detailTab',id);history.replaceState(history.state,'',url.href);document.dispatchEvent(new Event('resource-table:query'));}
            window.dispatchEvent(new Event('resize'));
        };
        panels.forEach((panel,index)=>{
            const heading=panel.querySelector('h2,h3,summary');
            if(!panel.id)panel.id='detail-section-'+(panel.dataset.resourceTable || index);
            const button=document.createElement('button');button.type='button';button.id=panel.id+'-tab';button.textContent=heading.textContent.trim();button.setAttribute('role','tab');button.setAttribute('aria-controls',panel.id);
            panel.setAttribute('role','tabpanel');panel.setAttribute('aria-labelledby',button.id);panel.tabIndex=0;
            const disclosure=panel.tagName==='DETAILS'?panel:panel.querySelector(':scope > details');if(disclosure)disclosure.open=true;
            button.addEventListener('click',()=>activate(panel.id));
            button.addEventListener('keydown',event=>{
                if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();
                const next=event.key==='Home'?0:event.key==='End'?panels.length-1:(active+(event.key==='ArrowLeft'?-1:1)+panels.length)%panels.length;activate(panels[next].id,true);
            });
            tabs.append(button);buttons.push(button);host.append(panel);
        });
        const selected=new URL(location.href).searchParams.get('detailTab');
        activate(panels.find(panel=>panel.id===selected || panel.querySelector('[aria-invalid=true],.field-error,[data-detail-error]'))?.id || panels[0].id,false,false);
        scope.addEventListener('invalid',event=>{const panel=panels.find(panel=>panel.contains(event.target));if(panel)activate(panel.id);},true);
        window.addEventListener('popstate',()=>activate(new URL(location.href).searchParams.get('detailTab') || panels[0].id,false,false));
    }
    function init(scope=document) {
        scope.querySelectorAll('.loan-status-badge,.voucher-status,.staff-recent-disbursements-status').forEach(node=>{
            if(!node.querySelector('[data-resource-status]') && (!node.hasAttribute('data-resource-status') || node.dataset.statusAuto==='true')){node.dataset.statusAuto='true';node.dataset.resourceStatus=node.textContent.trim();}
        });
        scope.querySelectorAll('[data-resource-status]').forEach(status);messages(scope);values(scope);links(scope);breadcrumbs();
    }
    window.ConsoleResourceDetails={init,icon,status};
    onReady(()=>{window.ResourceTable?.init();details();init();
        let queued=false;new MutationObserver(records=>{if(queued || !records.some(record=>record.addedNodes.length))return;queued=true;queueMicrotask(()=>{queued=false;init();});}).observe(document.querySelector('main') || document.body,{childList:true,subtree:true});
    });
})();
