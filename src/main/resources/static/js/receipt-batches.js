/* Receipt drafts contain inputs only. Allocations and posting decisions are server-owned. */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('meta[name="app-context-path"]')?.content || '';
    const base = root + '/finance/batches/receipts';
    const texts = new Map([...document.querySelectorAll('[data-batch-messages] [data-message]')].map(el => [el.dataset.message, el.textContent]));
    const t = key => texts.get(key) || key;
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const el = (tag, text, cls) => {const node = document.createElement(tag);if(text != null)node.textContent = text;if(cls)node.className = cls;return node;};
    const money = value => {const [whole, fraction = ''] = String(value ?? '0').split('.');return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction.padEnd(2, '0').slice(0, 2);};
    async function api(url, method = 'GET', body) {
        const headers = {Accept:'application/json'};if(csrf)headers[csrfHeader] = csrf;if(body !== undefined)headers['Content-Type'] = 'application/json';
        const response = await fetch(url, {method, headers, body:body === undefined ? undefined : JSON.stringify(body), cache:'no-store', credentials:'same-origin'});
        if(response.redirected || !response.headers.get('Content-Type')?.includes('application/json'))throw new Error(t('error.failed'));
        const data = await response.json();if(!response.ok){const error = new Error(data.message || t('error.failed'));error.code = data.code;throw error;}return data;
    }
    // Use the shared, body-mounted searchable selector, including its keyboard and focus behaviour.
    function picker(select, category, client, changed, issue, autoSelect = false) {
        select.dataset.searchable = 'true';const ui = window.NeoSelect.search(select);
        let page = 0, sequence = 0, controller, loaded = false;
        async function load(next = 0) {
            if(category === 'loans' && !client())return;
            controller?.abort();controller = new AbortController();const current = ++sequence, query = ui.input.value;
            ui.setLoading(true);ui.more.disabled = true;
            try {
                const params = new URLSearchParams({search:query,page:next});if(category === 'loans')params.set('clientId',client());
                const choiceUrl = category === 'settings-interest'?base + '/settings/accounts':base + '/choices/' + category;
                const response = await fetch(choiceUrl + '?' + params,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store',signal:controller.signal});
                if(!response.ok || response.redirected)throw new Error(t('lookupFailed'));const data = await response.json();
                if(current !== sequence || !select.isConnected)return;
                const value = select.value, selected = select.selectedOptions[0]?.cloneNode(true);
                const previous = next ? [...select.options].filter(o => o.value && o.dataset.query === query) : [];
                const options = new Map(previous.map(o => [o.value,o]));
                data.slice(0,25).forEach(choice => {const o = new Option(choice.label,choice.id);o.dataset.query = query;options.set(choice.id,o);});
                const visible = [...options.values()].slice(-100);select.replaceChildren(new Option(t('choose'),''),...visible);
                if(value && !visible.some(o => o.value === value) && selected)select.add(selected);select.value = value;
                page = next;loaded = true;ui.more.hidden = data.length <= 25;
                if(autoSelect && !value && !query && !next && data.length === 1){select.value = data[0].id;select.dispatchEvent(new Event('change',{bubbles:true}));}
            } catch(error) {if(error.name !== 'AbortError' && current === sequence)issue(t('lookupFailed'));}
            finally {if(current === sequence){ui.setLoading(false);ui.more.disabled = false;}}
        }
        const binding = window.AppSearch.bind(ui.input,()=>load(),()=>{++sequence;controller?.abort();ui.setLoading(true);ui.more.hidden = true;});
        ui.more.addEventListener('click',()=>{binding.cancel();load(page + 1);});
        select.addEventListener('neo-select:open',()=>{if(!loaded)load();});
        select.addEventListener('neo-select:close',()=>{binding.cancel();++sequence;controller?.abort();ui.setLoading(false);loaded = false;});
        select.addEventListener('change',changed);
        const dispose = () => {binding.cancel();++sequence;controller?.abort();};
        if(autoSelect)load();return dispose;
    }
    const editor = document.querySelector('[data-receipt-batch]');
    const normal = document.querySelector('[data-normal-repayment]');
    if(normal){let sequence = 0;const form = normal.closest('form'), purpose = form.querySelector('[name="receiptPurpose"]'), date = form.querySelector('[name="effectiveDate"]');
        const updatePolicy = async()=>{const current = ++sequence;if(purpose.value !== 'LOAN_REPAYMENT' || !date.value)return;try{const policy = await api(base + '/effective-policy?' + new URLSearchParams({date:date.value}));if(current !== sequence)return;normal.querySelector('[data-loan-choice]').hidden = !!policy.id;normal.querySelector('[data-policy-order]').textContent = t('order.' + policy.allocationOrder);if(policy.id){normal.querySelector('[name="interestAccountId"]').value = policy.interestAccountId;normal.querySelector('[data-account-label]').textContent = policy.interestAccount;normal.querySelector('[data-policy-order]').textContent = t('order.' + policy.allocationOrder);}}catch{if(current === sequence)normal.querySelector('[data-policy-order]').textContent = t('lookupFailed');}};
        purpose.addEventListener('change',updatePolicy);date.addEventListener('change',updatePolicy);updatePolicy();
    }
    const globalError = message => {const target = document.querySelector('[data-batch-error]');if(target){target.hidden = !message;target.textContent = message;}else if(message){let p = document.querySelector('[data-picker-error]');if(!p){p = el('p',null,'coa-error');p.dataset.pickerError = '';p.setAttribute('role','alert');document.querySelector('[data-batch-settings]')?.prepend(p);}p.textContent = message;}};
    document.querySelectorAll('[data-batch-choice]').forEach(select => picker(select,select.dataset.batchChoice,()=>null,()=>{},globalError));
    if(!editor)return;
    const id = editor.dataset.receiptBatch, url = base + '/' + id, body = editor.querySelector('[data-batch-rows]'), tableRegion = editor.querySelector('[data-resource-table]');
    let summary, page = 0, filter = '', hasNext = false, canEdit = false, loadedBusy = false, loading = false;
    const rows = new Map(), dirty = new Set(), pending = new Set();let cleanups = [], lastFocus;
    const busy = () => ['VALIDATING','POSTING'].includes(summary?.batch.state);
    const editable = () => canEdit && !busy() && summary?.batch.state !== 'COMPLETED';
    const input = (type, value, label, change) => {const n = el('input',null,'aws-control');n.type = type;n.value = value ?? '';n.setAttribute('aria-label',label);n.addEventListener(type === 'number' || type === 'text' ? 'input' : 'change',change);return n;};
    const button = (label, action, cls = 'app-btn btn-neutral') => {const b = el('button',label,cls);b.type = 'button';b.addEventListener('click',action);return b;};
    function selectControl(value, label, category, client, change, issue, auto = false) {
        const s = el('select',null,'aws-control');s.setAttribute('aria-label',label);s.add(new Option(t('choose'),''));
        if(value?.id){s.add(new Option(value.label || value.id,value.id));s.value = value.id;}
        queueMicrotask(()=>{if(s.isConnected && !s.disabled)cleanups.push(picker(s,category,client,change,issue,auto));});return s;
    }
    function focusReceipt(item,loan = false){requestAnimationFrame(()=>{const tr = body.querySelector('[data-item="' + item.id + '"]');tr?.scrollIntoView({block:'nearest'});const s = tr?.querySelector(loan?'td:nth-child(3) select':'td:first-child select');(s?.closest('.neo-select')?.querySelector('button') || s)?.focus();});}
    const total = item => {
        try {let cents = 0n;for(const row of item.form.rows){if(!row.amount)continue;if(!/^\d+(\.\d{1,2})?$/.test(String(row.amount)))return '—';const [a,b = ''] = String(row.amount).split('.');cents += BigInt(a)*100n + BigInt(b.padEnd(2,'0'));}return money((cents/100n).toString()+'.'+(cents%100n).toString().padStart(2,'0'));}catch{return '—';}
    };
    function saveStatus(){const node = editor.querySelector('[data-save-status]');node.textContent = pending.size?t('saving'):dirty.size?t('unsaved'):t('savedStatus');}
    function mark(item) {
        item.generation = (item.generation || 0) + 1;dirty.add(item.id);item.state = 'DRAFT';item.preview = null;item.failureKey = null;
        item.allocation?.replaceChildren();if(item.mapping)item.mapping.textContent = '';item.estimated = false;
        for(const other of rows.values())if(other !== item && other.state !== 'POSTED'){other.state = 'DRAFT';other.preview = null;other.allocation?.replaceChildren();if(other.mapping)other.mapping.textContent = '';paintStatus(other);}
        clearTimeout(item.timer);item.timer = setTimeout(()=>save(item),800);paintStatus(item);saveStatus();controls();
    }
    async function save(item) {
        clearTimeout(item.timer);if(item.discarding)return;if(item.saving)return item.saving;if(!dirty.has(item.id))return;
        const generation = item.generation, snapshot = structuredClone(item.form);pending.add(item.id);saveStatus();
        item.saving = (async()=>{
            try {
                const saved = await api(url + '/items/' + item.id,'PUT',{expectedVersion:item.version,form:snapshot});item.version = saved.version;item.labels = saved.labels;item.persisted = true;
                if(generation === item.generation){dirty.delete(item.id);item.failureKey = null;item.issue.hidden = true;item.issue.textContent = "";}
                await refreshSummary();
                if(generation === item.generation && snapshot.clientId && snapshot.moneyAccountId && snapshot.effectiveDate && snapshot.reference && snapshot.rows.every(r=>r.loanId && r.amount)){
                    try {const estimate = await api(url + '/items/' + item.id + '/estimate','POST');if(generation === item.generation){item.preview = estimate;item.estimated = true;paintPreview(item);}}
                    catch(error){if(generation === item.generation){item.issue.textContent = error.message;item.issue.hidden = false;}}
                }
                paintStatus(item);
            }catch(error){dirty.add(item.id);item.issue.textContent = error.message;item.issue.hidden = false;globalError(t('saveFailed'));}
            finally {pending.delete(item.id);item.saving = null;saveStatus();controls();}
        })();await item.saving;
        if(dirty.has(item.id) && item.generation !== generation)return save(item);
    }
    async function flush(){for(const item of rows.values())if(dirty.has(item.id) || item.saving)await save(item);if(dirty.size)throw new Error(t('saveFailed'));}
    function paintStatus(item){if(!item.status)return;item.status.replaceChildren(el('span',t('state.' + item.state)));if(item.receiptId){const a = el('a',t('posted'));a.href = root + '/finance/vouchers/receipts/' + item.receiptId;item.status.append(a);}if(item.issue)item.status.append(item.issue);}
    function paintPreview(item){
        const plan = item.preview;item.amountTotal && (item.amountTotal.textContent = total(item));
        if(!plan)return;
        item.mapping.replaceChildren(el('span',t('policyVersion') + ' ' + plan.policyVersion + ' · ' + t('order.' + plan.allocationOrder) + ' · ' + plan.moneyAccount));
        item.balance.replaceChildren();item.allocation.replaceChildren();
        item.balance.append(el('small',t(item.state === 'POSTED'?'beforePosting':item.estimated?'recordedBalances':'projectedBalances')));
        for(const line of plan.lines){const b = el('span',line.loanNumber + ': ' + t('principalOutstanding') + ' ' + money(line.outstandingPrincipal) + ' · ' + t('principalDue') + ' ' + money(line.duePrincipal) + ' · ' + t('interestDue') + ' ' + money(line.dueInterest),'batch-balance');item.balance.append(b);
            item.allocation.append(el('span',line.loanNumber + (line.product ? ' - ' + line.product : '') + ': ' + t('principal') + ' ' + money(line.principal) + ' · ' + t('interest') + ' ' + money(line.interest) + ' · ' + t('after') + ' ' + money(line.principalBalance),'batch-allocation'));
        }item.allocation.append(el('small',t(item.estimated?'recordedPreview':'batchPreview')));
    }
    async function context(item, loan, target){
        if(!loan.loanId || !item.form.effectiveDate)return;const generation = item.generation;
        try {const data = await api(base + '/loan-context?' + new URLSearchParams({loanId:loan.loanId,date:item.form.effectiveDate}));if(generation !== item.generation || !target.isConnected)return;const b = data[0];target.textContent = t('recordedBalances') + ' · ' + t('principalOutstanding') + ' ' + money(b.outstandingPrincipal) + ' · ' + t('principalDue') + ' ' + money(b.duePrincipal) + ' · ' + t('interestDue') + ' ' + money(b.dueInterest) + ' · ' + t('charges') + ' ' + money(b.charges || '0.00');}
        catch(error){if(target.isConnected)target.textContent = error.message;}
    }
    function renderItem(item) {
        const tr = el('tr',null,'batch-row'), details = el('tr',null,'batch-detail-row');tr.dataset.item = item.id;details.hidden = !item.expanded;
        const issue = el('span',null,'batch-issue');issue.id = 'receipt-issue-' + item.id;tr.setAttribute('aria-describedby',issue.id);issue.hidden = !item.failureKey;issue.setAttribute('role','status');item.issue = issue;
        if(item.failureKey)issue.textContent = item.failureMessage || t('error.failed');
        const cells = ['client','date','loan','balances','amount','reference','status'].map(label=>{const td = el('td');td.dataset.label = t(label);tr.append(td);return td;});
        const expand = button(item.expanded?'−':'+',()=>{item.expanded = !item.expanded;details.hidden = !item.expanded;expand.textContent = item.expanded?'−':'+';expand.setAttribute('aria-expanded',String(item.expanded));},'app-btn btn-neutral batch-expand');
        expand.setAttribute('aria-label',t('details'));expand.setAttribute('aria-expanded',String(!!item.expanded));details.id = 'receipt-details-' + item.id;expand.setAttribute('aria-controls',details.id);
        const wrapper = el('div',null,'batch-client');wrapper.append(expand);
        const client = selectControl({id:item.form.clientId,label:item.clientLabel || item.labels?.[item.form.clientId]},t('client'),'clients',()=>null,()=>{item.form.clientId = client.value || null;item.form.rows = [{loanId:null,amount:null,description:''}];const moneyLabel = item.labels?.[item.form.moneyAccountId];item.labels = moneyLabel?{[item.form.moneyAccountId]:moneyLabel}:{};item.clientLabel = client.selectedOptions[0]?.textContent;mark(item);rerender();focusReceipt(item,true);},message=>{issue.hidden = false;issue.textContent = message;});wrapper.append(client);cells[0].append(wrapper);
        const date = input('date',item.form.effectiveDate,t('date'),()=>{item.form.effectiveDate = date.value || null;mark(item);item.balance.textContent = '';item.allocation.textContent = '';item.form.rows.forEach(r=>context(item,r,item.balance));});date.max = editor.dataset.today;cells[1].append(date);
        const balance = el('div',null,'batch-balance'), allocation = el('div',null,'batch-allocation');item.balance = balance;item.allocation = allocation;cells[3].append(balance,allocation);
        const loanPicker = (row,where,balanceTarget) => {const select = selectControl({id:row.loanId,label:item.labels?.[row.loanId]},t('loan'),'loans',()=>item.form.clientId,()=>{row.loanId = select.value || null;item.labels ||= {};item.labels[row.loanId] = select.selectedOptions[0]?.textContent;mark(item);balanceTarget.textContent = '';allocation.textContent = '';context(item,row,balanceTarget);},message=>{issue.hidden = false;issue.textContent = message;},!!item.form.clientId && !row.loanId);select.disabled = !item.form.clientId || !editable() || item.state === 'POSTED';where.append(select);if(row.loanId && !item.preview)context(item,row,balanceTarget);};
        const amountInput = (row,where) => {const n = input('number',row.amount,t('amount'),()=>{row.amount = n.value || null;mark(item);if(item.amountTotal)item.amountTotal.textContent = total(item);});n.min = '.01';n.step = '.01';where.append(n);};
        if(item.form.rows.length === 1){loanPicker(item.form.rows[0],cells[2],balance);amountInput(item.form.rows[0],cells[4]);}
        else {cells[2].append(el('span',item.form.rows.length + ' ' + t('transactions')));item.amountTotal = el('strong',total(item));cells[4].append(item.amountTotal);}
        const reference = input('text',item.form.reference,t('reference'),()=>{item.form.reference = reference.value;mark(item);});reference.maxLength = 80;cells[5].append(reference);
        item.status = cells[6];paintStatus(item);cells[6].append(issue);
        const detailCell = el('td',null,'batch-detail-cell');detailCell.colSpan = 7;details.append(detailCell);const fields = el('div',null,'batch-detail-fields');detailCell.append(fields);
        const moneyLabel = el('label',t('receivingAccount'));const receiving = selectControl({id:item.form.moneyAccountId,label:item.labels?.[item.form.moneyAccountId]},t('receivingAccount'),'money',()=>null,()=>{item.form.moneyAccountId = receiving.value || null;item.labels ||= {};item.labels[receiving.value] = receiving.selectedOptions[0]?.textContent;mark(item);},message=>{issue.hidden = false;issue.textContent = message;});moneyLabel.append(receiving);fields.append(moneyLabel);
        const evidenceLabel = el('label',t('evidence')), evidence = input('text',item.form.evidence,t('evidence'),()=>{item.form.evidence = evidence.value;mark(item);});evidence.maxLength = 500;evidenceLabel.append(evidence);fields.append(evidenceLabel);
        const mapping = el('p',null,'batch-mapping');item.mapping = mapping;detailCell.append(mapping);
        if(item.form.rows.length>1){const table = el('table',null,'erp-table batch-transactions'), head = el('thead'), hr = el('tr'), tb = el('tbody');for(const label of ['loan','balances','amount','transaction'])hr.append(el('th',t(label)));head.append(hr);table.append(head,tb);detailCell.append(table);
            item.form.rows.forEach((row,index)=>{const r = el('tr'), c = ['loan','balances','amount','transaction'].map(label=>{const cell = el('td');cell.dataset.label = t(label);r.append(cell);return cell;});loanPicker(row,c[0],c[1]);amountInput(row,c[2]);c[3].append(button(t('removeTransaction'),()=>{item.form.rows.splice(index,1);mark(item);rerender();}));tb.append(r);});
        }
        const actions = el('div',null,'batch-detail-actions');actions.append(button(t('addTransaction'),()=>{if(item.form.rows.length>=50)return;item.form.rows.push({loanId:null,amount:null,description:''});item.expanded = true;mark(item);rerender();}));actions.append(button(t('removeReceipt'),async()=>{try{clearTimeout(item.timer);if(item.saving)await item.saving;if(item.persisted)await api(url + '/items/' + item.id + '?version=' + item.version,'DELETE');dirty.delete(item.id);rows.delete(item.id);await load();}catch(error){globalError(error.message);}}));detailCell.append(actions);
        if(!editable() || item.state === 'POSTED'){tr.querySelectorAll('input,select').forEach(n=>n.disabled = true);details.querySelectorAll('input,select,button').forEach(n=>n.disabled = true);}
        tr.querySelectorAll('input,select').forEach(n=>n.setAttribute('aria-describedby',issue.id));details.querySelectorAll('input,select').forEach(n=>n.setAttribute('aria-describedby',issue.id));paintPreview(item);body.append(tr,details);
    }
    function rerender(){cleanups.forEach(fn=>fn());cleanups = [];body.replaceChildren();for(const item of rows.values())renderItem(item);if(!rows.size){const tr = el('tr'), td = el('td',t('empty'));td.colSpan = 7;tr.append(td);body.append(tr);}}
    function controls(){
        editor.querySelector('[data-batch-add]').disabled = !editable();
        editor.querySelector('[data-batch-save]').disabled = !editable() || (!dirty.size && !pending.size);
        editor.querySelector('[data-batch-validate]').disabled = !editable() || !summary || summary.receipts === summary.posted || pending.size>0;
        editor.querySelector('[data-batch-post]').disabled = !editable() || dirty.size>0 || pending.size>0 || summary?.batch.state !== 'READY';
        const stop = editor.querySelector('[data-batch-stop]');stop.hidden = !busy() || !canEdit;stop.disabled = !!summary?.batch.cancelRequested;
        window.ResourceTable.setPage(tableRegion,{page,hasNext,loading});
    }
    async function refreshSummary(){summary = await api(url + '/summary');canEdit = editor.dataset.canEdit === 'true';document.querySelector('[data-batch-state]').textContent = t('state.' + summary.batch.state);
        const area = editor.querySelector('[data-batch-summary]');area.replaceChildren();for(const [label,value] of [['receipts',summary.receipts],['total',money(summary.total)+' TZS'],['posted',summary.posted],['ready',summary.ready],['failed',summary.failed],['remaining',summary.receipts-summary.posted]]){const s = el('span',t(label));s.append(el('strong',String(value)));area.append(s);}editor.querySelector('[data-batch-progress]').textContent = busy()?t('state.' + summary.batch.state):'';controls();}
    async function load(){if(loading)return;loading = true;controls();try{await flush();await refreshSummary();const data = await api(url + '/items?' + new URLSearchParams({page,state:filter}));rows.clear();data.rows.forEach(item=>rows.set(item.id,{...item,persisted:true}));hasNext = data.hasNext;rerender();loadedBusy = busy();globalError('');}catch(error){globalError(error.message);}finally{loading = false;controls();}}
    editor.querySelector('[data-batch-add]').addEventListener('click',async()=>{try{await flush();if(filter || hasNext || rows.size>=25){filter = '';editor.querySelector('[data-batch-filter]').value = '';page = Math.floor(summary.receipts/25);await load();}const account = editor.querySelector('[data-default-account]'), item = {id:crypto.randomUUID(),version:0,state:'DRAFT',labels:{},form:{clientId:null,effectiveDate:editor.querySelector('[data-default-date]').value,moneyAccountId:account.value || null,reference:'',description:'',evidence:'',rows:[{loanId:null,amount:null,description:''}]}};item.expanded = !account.value;if(account.value)item.labels[account.value] = account.selectedOptions[0].textContent;rows.set(item.id,item);mark(item);rerender();focusReceipt(item);await save(item);}catch(error){globalError(error.message);}});
    editor.querySelector('[data-batch-save]').addEventListener('click',()=>flush().catch(error=>globalError(error.message)));
    tableRegion.addEventListener('resource-table:refresh',event=>{event.preventDefault();if(!dirty.size && !pending.size){load();return;}openConfirm(t('reloadTitle'),t('reloadMessage'),t('reload'),async()=>{for(const item of rows.values()){item.discarding = true;clearTimeout(item.timer);}await Promise.allSettled([...rows.values()].map(item=>item.saving).filter(Boolean));dirty.clear();await load();});});
    tableRegion.addEventListener('resource-table:page',async event=>{if(loading)return;try{await flush();page = event.detail.page;await load();}catch(error){globalError(error.message);}});
    editor.querySelector('[data-batch-filter]').addEventListener('change',async event=>{try{await flush();filter = event.target.value;page = 0;await load();}catch(error){globalError(error.message);}});
    async function action(kind,version){await flush();if(kind !== 'post')await refreshSummary();await api(url + '/' + kind + (kind === 'stop'?'':'?version=' + (version ?? summary.batch.version)),'POST');await load();}
    editor.querySelector('[data-batch-validate]').addEventListener('click',()=>action('validate').catch(error=>globalError(error.message)));
    editor.querySelector('[data-batch-stop]').addEventListener('click',()=>action('stop').catch(error=>globalError(error.message)));
    const modal = editor.querySelector('[data-batch-confirm]');document.body.append(modal);const dialog = modal.querySelector('[role="dialog"]');let priorOverflow,reviewedVersion,confirmedAction,modalBusy = false;
    const close = () => {if(modalBusy)return;modal.hidden = true;modal.classList.remove('is-open');document.body.style.overflow = priorOverflow;lastFocus?.focus();};
    const openConfirm = (title,message,actionText,action) => {confirmedAction = action;lastFocus = document.activeElement;priorOverflow = document.body.style.overflow;document.body.style.overflow = 'hidden';modal.hidden = false;modal.classList.add('is-open');modal.querySelector('#batch-confirm-title').textContent = title;modal.querySelector('[data-confirm-message]').textContent = message;modal.querySelector('[data-confirm-post]').textContent = actionText;modal.querySelector('.app-modal-actions [data-confirm-cancel]').focus();};
    modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.addEventListener('click',close));
    editor.querySelector('[data-batch-post]').addEventListener('click',()=>{reviewedVersion = summary.batch.version;openConfirm(t('postTitle'),t('postMessage') + ' ' + (summary.receipts-summary.posted) + ' · ' + money(summary.remainingTotal) + ' TZS',t('postConfirm'),()=>action('post',reviewedVersion));});
    modal.querySelector('[data-confirm-post]').addEventListener('click',async event=>{event.target.disabled = true;modalBusy = true;modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.disabled = true);try{await confirmedAction();}catch(error){globalError(error.message);}finally{modalBusy = false;event.target.disabled = false;modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.disabled = false);close();}});
    modal.addEventListener('keydown',event=>{if(modalBusy && (event.key === 'Escape' || event.key === 'Tab')){event.preventDefault();dialog.focus();return;}if(event.key === 'Escape'){event.preventDefault();close();}if(event.key === 'Tab'){const buttons = [...dialog.querySelectorAll('button:not([disabled])')],first = buttons[0],last = buttons.at(-1);if(event.shiftKey && document.activeElement === first){event.preventDefault();last.focus();}else if(!event.shiftKey && document.activeElement === last){event.preventDefault();first.focus();}}});
    editor.addEventListener('keydown',event=>{if(event.key === 'Enter' && event.target.matches('input,select'))event.preventDefault();if(event.altKey && event.key.toLowerCase() === 'n'){event.preventDefault();editor.querySelector('[data-batch-add]').click();}});
    window.addEventListener('beforeunload',event=>{if(dirty.size || pending.size){event.preventDefault();event.returnValue = '';}});
    document.addEventListener('click',event=>{const a = event.target.closest('a[href]');if(!a || a.target === '_blank' || (!dirty.size && !pending.size) || event.ctrlKey || event.metaKey)return;event.preventDefault();flush().then(()=>location.assign(a.href)).catch(error=>globalError(error.message));});
    const poll = setInterval(async()=>{if(loading || dirty.size || pending.size || document.hidden)return;try{await refreshSummary();if(busy() || loadedBusy)await load();}catch(error){globalError(error.message);}},2500);
    window.addEventListener('pagehide',()=>{clearInterval(poll);cleanups.forEach(fn=>fn());rows.forEach(item=>clearTimeout(item.timer));});
    load();
});
