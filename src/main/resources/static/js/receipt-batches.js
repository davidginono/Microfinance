/* Receipt drafts contain inputs only. Allocations and posting decisions are server-owned. */
function initializeReceiptBatches() {
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
                const choiceUrl = base + '/choices/' + category;
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
    const globalError = message => {const target = document.querySelector('[data-batch-error]');if(target){target.hidden = !message;target.textContent = message;}};
    document.querySelectorAll('[data-batch-choice]').forEach(select => picker(select,select.dataset.batchChoice,()=>null,()=>{},globalError));
    if(!editor)return;
    const id = editor.dataset.receiptBatch, url = base + '/' + id, body = editor.querySelector('[data-batch-rows]'), tableRegion = editor.querySelector('[data-resource-table]');
    let summary, page = 0, filter = '', hasNext = false, canEdit = false, loadedBusy = false, loading = false, removing = false;
    const rows = new Map(), dirty = new Set(), pending = new Set(), balanceRequests = new WeakMap();let cleanups = [], lastFocus;
    const busy = () => ['VALIDATING','POSTING'].includes(summary?.batch.state);
    const editable = () => canEdit && !busy() && summary?.batch.state !== 'COMPLETED';
    const canRemove = item => editable() && editor.dataset.payrollSealed !== 'true' && item.state !== 'POSTED';
    const input = (type, value, label, change) => {const n = el('input',null,'aws-control');n.type = type;n.value = value ?? '';n.setAttribute('aria-label',label);n.addEventListener(type === 'number' || type === 'text' ? 'input' : 'change',change);return n;};
    const button = (label, action, cls = 'app-btn btn-neutral') => {const b = el('button',label,cls);b.type = 'button';b.addEventListener('click',action);return b;};
    function selectControl(value, label, category, client, change, issue, auto = false) {
        const s = el('select',null,'aws-control');s.setAttribute('aria-label',label);s.add(new Option(t('choose'),''));
        if(value?.id){s.add(new Option(value.label || value.id,value.id));s.value = value.id;}
        queueMicrotask(()=>{if(s.isConnected && !s.disabled)cleanups.push(picker(s,category,client,change,issue,auto));});return s;
    }
    function focusReceipt(item,loan = false){requestAnimationFrame(()=>{const tr = body.querySelector('[data-item="' + item.id + '"]');tr?.scrollIntoView({block:'nearest'});const s = tr?.querySelector(loan?'td:nth-child(3) select':'td:first-child select');(s?.closest('.neo-select')?.querySelector('button') || s)?.focus();});}
    function saveStatus(){const node = editor.querySelector('[data-save-status]');node.textContent = removing || pending.size?t('saving'):dirty.size?t('unsaved'):t('savedStatus');}
    function syncField(binding){
        const controls = [binding.control,binding.control.closest('.neo-select')?.querySelector('button'),binding.control.closest('.erp-date-control')?.querySelector('[data-date-display]'),binding.control.closest('.erp-amount-control')?.querySelector('[data-amount-display]')].filter(Boolean);
        controls.forEach(control=>{control.setAttribute('aria-describedby',binding.issue.id);if(!binding.issue.hidden && binding.issue.dataset.state !== 'checking')control.setAttribute('aria-invalid','true');else control.removeAttribute('aria-invalid');});
    }
    function bindField(item,field,control,host){
        const issue = el('span',null,'batch-field-error');issue.id = 'receipt-' + item.id + '-' + field;issue.hidden = true;issue.setAttribute('role','status');
        const binding = {field,control,issue};item.fields.push(binding);host.append(issue);
        if(field==='loan'){
            binding.link=button(t('linkAccounts'),()=>linkConfiguredAccounts(item,binding.link),'app-btn btn-neutral');binding.link.hidden=true;host.append(binding.link);
        }
        queueMicrotask(()=>syncField(binding));
    }
    function fieldIssue(item,field,key,checking = false){
        const binding = item.fields.find(binding=>binding.field === field);
        if(!binding)return;
        binding.issue.textContent = t('field.' + key);binding.issue.hidden = false;binding.issue.dataset.state = checking?'checking':'error';syncField(binding);
    }
    function showIssue(item,message,checking = false,code = '',field){
        if(!item.fields)return;item.fields.forEach(binding=>{binding.issue.hidden = true;binding.issue.textContent = '';binding.issue.removeAttribute('title');if(binding.link)binding.link.hidden=true;syncField(binding);});
        item.referenceIssue.hidden = true;
        if(!message)return;
        if(item.form.rows.length !== 1){
            item.referenceIssue.textContent = message;
            item.referenceIssue.hidden = false;
            return;
        }
        if(checking){fieldIssue(item,'amount','checking',true);return;}
        if(field){fieldIssue(item,field,'lookup');return;}
        if(/\.stale$|\.changed$|\.conflict$/.test(code)){globalError(t('field.refresh'));return;}
        if(/\.permission$/.test(code)){globalError(t('field.permission'));return;}
        let missing = false;
        const requireField = (condition,name,key) => {if(condition){fieldIssue(item,name,key);missing = true;}};
        requireField(!item.form.clientId,'client','clientRequired');
        const dateText = item.fields.find(binding=>binding.field === 'date')?.control.closest('.erp-date-control')?.querySelector('[data-date-display]')?.value;
        requireField(!item.form.effectiveDate,'date',dateText?'dateFormat':'dateRequired');
        requireField(item.form.effectiveDate > editor.dataset.today,'date','dateFuture');
        requireField(!item.form.moneyAccountId,'account','accountRequired');
        const [row] = item.form.rows, amount = String(row.amount || '');
        requireField(!row.loanId,'loan','loanRequired');
        if(!amount)requireField(true,'amount','amountRequired');
        else if(/^(-|0+(\.0*)?$)/.test(amount))requireField(true,'amount','amountPositive');
        else requireField(!/^\d+(\.\d{1,2})?$/.test(amount),'amount','amountFormat');
        if(missing)return;
        if(/loanAccountsMissing/.test(code)){fieldIssue(item,'loan','accounts');item.fields.filter(binding=>binding.link).forEach(binding=>binding.link.hidden=!editable() || item.state==='POSTED');}
        else if(/productPrincipalMismatch/.test(code))fieldIssue(item,'loan','principalAccount');
        else if(/productAccountsMissing/.test(code))fieldIssue(item,'loan','productAccounts');
        else if(/productAccountsInvalid/.test(code))fieldIssue(item,'loan','inactiveAccounts');
        else if(/\.policy$/.test(code))fieldIssue(item,'date','policy');
        else if(/\.period$/.test(code))fieldIssue(item,'date','closedPeriod');
        else if(/\.adjustmentDate$/.test(code))fieldIssue(item,'date','adjustmentDate');
        else if(/\.date$/.test(code))fieldIssue(item,'date','date');
        else if(/\.advance$/.test(code))fieldIssue(item,'amount','debt');
        else if(/\.amount$/.test(code))fieldIssue(item,'amount','amount');
        else if(/\.(duplicate|reference)$/.test(code)){
            item.referenceIssue.textContent = t(code.endsWith('.duplicate')?'field.duplicate':'field.reference');
            item.referenceIssue.title = message;
            item.referenceIssue.hidden = false;
        }
        else if(/\.retry$/.test(code)){globalError(t('field.refresh'));return;}
        else if(/\.loanClient$/.test(code))fieldIssue(item,'client','loanClient');
        else if(/\.client$/.test(code))fieldIssue(item,'client','client');
        else if(/\.account$|\.moneyAccount$|\.interestAccount$/.test(code))fieldIssue(item,'account','account');
        else if(/\.unavailable$/.test(code))fieldIssue(item,'loan','opening');
        else if(/\.schedule$/.test(code))fieldIssue(item,'loan','schedule');
        else if(/\.(loanRow|state)$/.test(code))fieldIssue(item,'loan','loan');
        else if((item.form.evidence || '').length > 500)fieldIssue(item,'evidence','evidence');
        else {globalError(t('field.receipt'));return;}
        item.fields.filter(binding=>!binding.issue.hidden).forEach(binding=>binding.issue.title = message);
    }
    function clearPreview(item, refreshBalances = false) {
        refreshBalances ||= !!item.preview && !item.estimated;
        item.preview = null;item.estimated = false;
        if(refreshBalances && item.balance && item.form.rows.length === 1)context(item,item.form.rows[0],item.balance);
    }
    function mark(item, refreshBalances = false) {
        item.generation = (item.generation || 0) + 1;dirty.add(item.id);item.state = 'DRAFT';item.failureKey = null;
        showIssue(item,'');
        clearPreview(item,refreshBalances);
        for(const other of rows.values())if(other !== item && other.state !== 'POSTED'){
            other.state = 'DRAFT';clearPreview(other);paintStatus(other);
        }
        clearTimeout(item.timer);item.timer = setTimeout(()=>save(item),800);paintStatus(item);saveStatus();controls();
    }
    async function save(item) {
        clearTimeout(item.timer);if(item.discarding)return;if(item.saving)return item.saving;if(!dirty.has(item.id))return;
        const generation = item.generation, snapshot = structuredClone(item.form);
        delete snapshot.reference;
        pending.add(item.id);saveStatus();
        item.saving = (async()=>{
            try {
                const saved = await api(url + '/items/' + item.id,'PUT',{expectedVersion:item.version,form:snapshot});item.version = saved.version;item.labels = saved.labels;item.persisted = true;
                if(generation === item.generation){dirty.delete(item.id);item.failureKey = null;showIssue(item,'');}
                await refreshSummary();
                if(generation === item.generation && snapshot.clientId && snapshot.moneyAccountId && snapshot.effectiveDate && snapshot.rows.every(r=>r.loanId && r.amount)){
                    showIssue(item,t('field.checking'),true);
                    try {const estimate = await api(url + '/items/' + item.id + '/estimate','POST');if(generation === item.generation){item.preview = estimate;item.estimated = true;showIssue(item,'');paintReceiptBalance(item);}}
                    catch(error){if(generation === item.generation)showIssue(item,error.message,false,error.code);}
                }
                paintStatus(item);
            }catch(error){dirty.add(item.id);if(generation === item.generation)showIssue(item,error.message,false,error.code);globalError(t('saveFailed'));}
            finally {pending.delete(item.id);item.saving = null;if(!pending.size && !dirty.size && editor.querySelector('[data-batch-error]')?.textContent === t('saveFailed'))globalError('');saveStatus();controls();}
        })();await item.saving;
        if(dirty.has(item.id) && item.generation !== generation)return save(item);
    }
    async function flush(){for(const item of rows.values())if(dirty.has(item.id) || item.saving)await save(item);if(dirty.size)throw new Error(t('saveFailed'));}
    async function linkConfiguredAccounts(item,trigger){
        if(trigger.disabled)return;trigger.disabled=true;
        try{
            await flush();const generation=item.generation;pending.add(item.id);controls();
            const saved=await api(url+'/items/'+item.id+'/link-accounts','POST',{expectedVersion:item.version});item.version=saved.version;item.state=saved.state||'DRAFT';item.failureKey=null;
            for(const other of rows.values())if(other!==item && other.state!=='POSTED'){other.state='DRAFT';clearPreview(other);paintStatus(other);}
            const estimate=await api(url+'/items/'+item.id+'/estimate','POST');
            if(generation===item.generation){item.preview=estimate;item.estimated=true;showIssue(item,'');paintReceiptBalance(item);paintStatus(item);}
            await refreshSummary();
        }catch(error){showIssue(item,error.message,false,error.code);}
        finally{pending.delete(item.id);trigger.disabled=false;saveStatus();controls();}
    }
    function paintStatus(item){if(!item.status)return;const state=el('span',t('state.' + item.state));state.dataset.resourceStatus=item.state;item.status.replaceChildren(state,item.referenceIssue);if(item.receiptId){const a = el('a',t('posted'));a.href = root + '/finance/vouchers/receipts/' + item.receiptId;item.status.append(a);}}
    function paintBalance(target,balance,totalLabel = 'totalLoanOutstanding'){
        balanceRequests.delete(target);
        let select = target.querySelector('.batch-balance');
        if(!select){
            select = el('select',null,'aws-control batch-balance');
            select.dataset.nativeSelect = 'true';
            select.setAttribute('aria-label',t('balances'));
            select.addEventListener('change',()=>{select.value = '';});
            target.replaceChildren(select);
        }
        select.replaceChildren(new Option(t('viewBalances'),''));
        const amounts = [[totalLabel,balance.totalLoanOutstanding],['principalOutstanding',balance.outstandingPrincipal],['interestDue',balance.dueInterest]];
        if('chargesOutstanding' in balance)amounts.push(['chargesOutstanding',balance.chargesOutstanding]);
        for(const [label,value] of amounts){
            select.add(new Option(t(label) + ' · ' + (value == null?'—':money(value)),label));
        }
    }
    function paintReceiptBalance(item){
        if(!item.preview || item.form.rows.length !== 1)return;
        const line = item.preview.lines.find(line=>line.loanId === item.form.rows[0].loanId);
        const totalLabel = item.state === 'POSTED'?'totalBeforePayment':item.estimated?'totalLoanOutstanding':'projectedTotal';
        if(line)paintBalance(item.balance,line,totalLabel);
    }
    async function context(item, loan, target){
        const {loanId} = loan, date = item.form.effectiveDate;
        target.replaceChildren();
        balanceRequests.delete(target);
        if(!loanId || !date)return;
        const request = {};
        balanceRequests.set(target,request);
        const current = ()=>balanceRequests.get(target) === request && target.isConnected;
        try {
            const [balance] = await api(base + '/loan-context?' + new URLSearchParams({loanId,date}));
            if(current())paintBalance(target,balance || {});
        }catch(error){if(current()){paintBalance(target,{});showIssue(item,error.message,false,error.code || 'recording.error.state');}}
    }
    async function removeReceipt(item){
        if(loading || removing || !canRemove(item))return;
        const displayed = [...rows.values()], index = displayed.indexOf(item);
        const neighbor = displayed[index+1] || displayed[index-1];
        const row = item.removeButton.closest('tr');
        removing = true;item.discarding = true;row.inert = true;
        clearTimeout(item.timer);saveStatus();controls();
        try {
            await item.saving;
            if(item.persisted){
                await api(url + '/items/' + item.id + '?version=' + item.version,'DELETE');
                summary.batch.state = 'DRAFT';
                for(const other of rows.values())if(other !== item && other.state !== 'POSTED'){
                    other.state = 'DRAFT';other.failureKey = null;clearPreview(other);
                }
            }
            dirty.delete(item.id);rows.delete(item.id);
            if(!rows.size && !hasNext && page > 0)page--;
            rerender();
            await load();
        }catch(error){globalError(error.message);}
        finally {
            item.discarding = false;row.inert = false;removing = false;saveStatus();controls();
            if(rows.has(item.id))item.removeButton.focus();
            else if(neighbor && rows.has(neighbor.id))focusReceipt(neighbor);
            else editor.querySelector('[data-batch-add]').focus();
        }
    }
    function renderItem(item) {
        const tr = el('tr',null,'batch-row');tr.dataset.item = item.id;
        const legacy = item.form.rows.length !== 1;
        item.fields = [];item.balance = el('div');
        const cells = ['client','date','loan','balances','amount','receivingAccount','evidence','status'].map(label=>{const td = el('td');td.dataset.label = t(label);tr.append(td);return td;});
        const wrapper = el('div',null,'batch-client');
        const remove = button('−',()=>removeReceipt(item),'app-btn btn-neutral erp-icon-btn batch-remove');
        remove.setAttribute('aria-label',t('removeReceipt'));remove.title = t('removeReceipt');
        item.removeButton = remove;wrapper.append(remove);
        const client = selectControl({id:item.form.clientId,label:item.clientLabel || item.labels?.[item.form.clientId]},t('client'),'clients',()=>null,()=>{
            item.form.clientId = client.value || null;item.form.rows = [{loanId:null,amount:null,description:''}];
            const moneyLabel = item.labels?.[item.form.moneyAccountId];item.labels = moneyLabel?{[item.form.moneyAccountId]:moneyLabel}:{};
            item.clientLabel = client.selectedOptions[0]?.textContent;mark(item);rerender();focusReceipt(item,true);
        },message=>showIssue(item,message,false,'','client'));
        wrapper.append(client);cells[0].append(wrapper);bindField(item,'client',client,cells[0]);
        const date = input('date',item.form.effectiveDate,t('date'),()=>{item.form.effectiveDate = date.value || null;mark(item,true);});date.max = editor.dataset.today;cells[1].append(date);bindField(item,'date',date,cells[1]);
        cells[3].append(item.balance);
        if(legacy){
            const labels = item.form.rows.map(row=>item.labels?.[row.loanId] || row.loanId).filter(Boolean).join(', ');
            const list = el('span',labels || '—','batch-loan-list');list.title = labels;cells[2].append(list);
            item.balance.textContent = '—';
            cells[4].append(el('strong',item.total == null?'—':money(item.total)));
        }else{
            const [row] = item.form.rows;
            const loan = selectControl({id:row.loanId,label:item.labels?.[row.loanId]},t('loan'),'loans',()=>item.form.clientId,()=>{
                row.loanId = loan.value || null;item.labels ||= {};item.labels[row.loanId] = loan.selectedOptions[0]?.textContent;mark(item,true);
            },message=>showIssue(item,message,false,'','loan'),!!item.form.clientId && !row.loanId);
            loan.disabled = !item.form.clientId;
            cells[2].append(loan);bindField(item,'loan',loan,cells[2]);
            const amount = input('number',row.amount,t('amount'),()=>{row.amount = amount.value || null;mark(item);});
            amount.min = '.01';amount.step = '.01';amount.dataset.amountInput = 'true';cells[4].append(amount);bindField(item,'amount',amount,cells[4]);
            if(row.loanId && !item.preview)context(item,row,item.balance);
        }
        item.status = cells[7];item.referenceIssue = el('span',null,'batch-field-error');item.referenceIssue.setAttribute('role','status');paintStatus(item);
        const receiving = selectControl({id:item.form.moneyAccountId,label:item.labels?.[item.form.moneyAccountId]},t('receivingAccount'),'money',()=>null,()=>{
            const account = receiving.value ? receiving : editor.querySelector('[data-default-account]');
            item.form.moneyAccountId = account.value || null;item.labels ||= {};
            if(account.value){item.labels[account.value] = account.selectedOptions[0].textContent;if(![...receiving.options].some(option=>option.value === account.value))receiving.add(account.selectedOptions[0].cloneNode(true));receiving.value = account.value;}
            mark(item);
        },message=>showIssue(item,message,false,'','account'));cells[5].append(receiving);bindField(item,'account',receiving,cells[5]);
        const evidence = input('text',item.form.evidence,t('evidence'),()=>{item.form.evidence = evidence.value;mark(item);});evidence.maxLength = 500;cells[6].append(evidence);bindField(item,'evidence',evidence,cells[6]);
        if(legacy || !canRemove(item))tr.querySelectorAll('input,select').forEach(n=>n.disabled = true);
        paintReceiptBalance(item);body.append(tr);showIssue(item,item.failureKey?item.failureMessage || t('error.failed'):'',false,item.failureKey);
    }
    function rerender(){cleanups.forEach(fn=>fn());cleanups = [];body.replaceChildren();for(const item of rows.values())renderItem(item);if(!rows.size){const tr = el('tr'), td = el('td',null,'batch-empty-cell');td.colSpan = 8;td.append(el('span',t('empty'),'batch-empty-message'));tr.append(td);body.append(tr);}}
    function controls(){
        const updating = loading || removing;
        const spreadsheet = editor.querySelector('[data-batch-spreadsheet]');
        if (spreadsheet) spreadsheet.hidden = removing || !editable();
        if(editor.dataset.payrollSealed === 'true')editor.querySelectorAll('[data-default-date],[data-default-account]').forEach(n=>{n.disabled = true;n.closest('label').hidden = true;});
        editor.querySelector('[data-batch-add]').disabled = updating || !editable() || editor.dataset.payrollSealed === 'true';
        editor.querySelector('[data-batch-save]').disabled = updating || !editable() || editor.dataset.payrollSealed === 'true' || (!dirty.size && !pending.size);
        editor.querySelector('[data-batch-validate]').disabled = updating || !editable() || !summary || summary.receipts === summary.posted || pending.size>0;
        editor.querySelector('[data-batch-post]').disabled = updating || !editable() || dirty.size>0 || pending.size>0 || summary?.batch.state !== 'READY';
        editor.querySelector('[data-batch-filter]').disabled = removing;
        for(const item of rows.values())if(item.removeButton)item.removeButton.disabled = updating || !canRemove(item);
        const stop = editor.querySelector('[data-batch-stop]');stop.hidden = !busy() || !canEdit;stop.disabled = !!summary?.batch.cancelRequested;
        window.ResourceTable.setPage(tableRegion,{page,hasNext,loading:updating});
    }
    async function refreshSummary(){summary = await api(url + '/summary');canEdit = editor.dataset.canEdit === 'true';
        const area = editor.querySelector('[data-batch-summary]');area.replaceChildren();for(const [label,value] of [['status',t('state.' + summary.batch.state)],['receipts',summary.receipts],['total',money(summary.total)],['posted',summary.posted],['ready',summary.ready],['failed',summary.failed]]){const fact = el('div'),valueNode=el('dd');if(label==='status'){const state=el('span',String(value));state.dataset.resourceStatus=summary.batch.state;valueNode.append(state);}else valueNode.textContent=String(value);fact.append(el('dt',t(label)),valueNode);area.append(fact);}editor.querySelector('[data-batch-progress]').textContent = busy()?t('state.' + summary.batch.state):'';controls();}
    async function load(){if(loading)return;loading = true;try{controls();await flush();await refreshSummary();const data = await api(url + '/items?' + new URLSearchParams({page,state:filter}));rows.clear();data.rows.forEach(item=>rows.set(item.id,{...item,persisted:true}));hasNext = data.hasNext;rerender();loadedBusy = busy();globalError('');}catch(error){body.querySelector('[data-batch-loading]')?.replaceWith(document.createTextNode(t('error.failed')));globalError(error.message);}finally{loading = false;controls();}}
    editor.querySelector('[data-default-account]').addEventListener('change',event=>{
        const account = event.target;if(!editable() || editor.dataset.payrollSealed === 'true' || !account.value)return;
        if(removing)return;
        for(const item of rows.values())if(item.form.rows.length === 1 && item.state !== 'POSTED' && !item.form.moneyAccountId){item.form.moneyAccountId = account.value;item.labels ||= {};item.labels[account.value] = account.selectedOptions[0].textContent;mark(item);}
        rerender();
    });
    editor.querySelector('[data-batch-add]').addEventListener('click',async()=>{try{await flush();if(filter || hasNext || rows.size>=25){filter = '';editor.querySelector('[data-batch-filter]').value = '';page = Math.floor(summary.receipts/25);await load();}const account = editor.querySelector('[data-default-account]'), item = {id:crypto.randomUUID(),version:0,state:'DRAFT',labels:{},form:{clientId:null,effectiveDate:editor.querySelector('[data-default-date]').value,moneyAccountId:account.value || null,description:'',evidence:'',rows:[{loanId:null,amount:null,description:''}]}};if(account.value)item.labels[account.value] = account.selectedOptions[0].textContent;rows.set(item.id,item);mark(item);rerender();focusReceipt(item);await save(item);}catch(error){globalError(error.message);}});
    editor.querySelector('[data-batch-save]').addEventListener('click',()=>flush().catch(error=>globalError(error.message)));
    tableRegion.addEventListener('resource-table:refresh',event=>{event.preventDefault();if(removing)return;if(!dirty.size && !pending.size){load();return;}openConfirm(t('reloadTitle'),t('reloadMessage'),t('reload'),async()=>{for(const item of rows.values()){item.discarding = true;clearTimeout(item.timer);}await Promise.allSettled([...rows.values()].map(item=>item.saving).filter(Boolean));dirty.clear();await load();});});
    tableRegion.addEventListener('resource-table:page',async event=>{if(loading || removing)return;try{await flush();page = event.detail.page;await load();}catch(error){globalError(error.message);}});
    editor.querySelector('[data-batch-filter]').addEventListener('change',async event=>{try{await flush();filter = event.target.value;page = 0;await load();}catch(error){globalError(error.message);}});
    async function action(kind,version){await flush();if(kind !== 'post')await refreshSummary();await api(url + '/' + kind + (kind === 'stop'?'':'?version=' + (version ?? summary.batch.version)),'POST');await load();}
    editor.querySelector('[data-batch-validate]').addEventListener('click',()=>action('validate').catch(error=>globalError(error.message)));
    editor.querySelector('[data-batch-stop]').addEventListener('click',()=>action('stop').catch(error=>globalError(error.message)));
    const modal = document.querySelector('[data-batch-confirm]');document.body.append(modal);const dialog = modal.querySelector('[role="dialog"]');let priorOverflow,reviewedVersion,confirmedAction,modalBusy = false;
    const close = () => {if(modalBusy)return;modal.hidden = true;modal.classList.remove('is-open');document.body.style.overflow = priorOverflow;lastFocus?.focus();};
    const openConfirm = (title,message,actionText,action) => {confirmedAction = action;lastFocus = document.activeElement;priorOverflow = document.body.style.overflow;document.body.style.overflow = 'hidden';modal.hidden = false;modal.classList.add('is-open');modal.querySelector('#batch-confirm-title').textContent = title;modal.querySelector('[data-confirm-message]').textContent = message;modal.querySelector('[data-confirm-post]').textContent = actionText;modal.querySelector('.app-modal-actions [data-confirm-cancel]').focus();};
    modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.addEventListener('click',close));
    editor.querySelector('[data-batch-post]').addEventListener('click',()=>{reviewedVersion = summary.batch.version;openConfirm(t('postTitle'),t('postMessage') + ' ' + (summary.receipts-summary.posted) + ' · ' + money(summary.remainingTotal) + ' TZS',t('postConfirm'),()=>action('post',reviewedVersion));});
    modal.querySelector('[data-confirm-post]').addEventListener('click',async event=>{event.target.disabled = true;modalBusy = true;modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.disabled = true);try{await confirmedAction();}catch(error){globalError(error.message);}finally{modalBusy = false;event.target.disabled = false;modal.querySelectorAll('[data-confirm-cancel]').forEach(b=>b.disabled = false);close();}});
    modal.addEventListener('keydown',event=>{if(modalBusy && (event.key === 'Escape' || event.key === 'Tab')){event.preventDefault();dialog.focus();return;}if(event.key === 'Escape'){event.preventDefault();close();}if(event.key === 'Tab'){const buttons = [...dialog.querySelectorAll('button:not([disabled])')],first = buttons[0],last = buttons.at(-1);if(event.shiftKey && document.activeElement === first){event.preventDefault();last.focus();}else if(!event.shiftKey && document.activeElement === last){event.preventDefault();first.focus();}}});
    editor.addEventListener('keydown',event=>{if(event.key === 'Enter' && event.target.matches('input,select'))event.preventDefault();if(event.altKey && event.key.toLowerCase() === 'n'){event.preventDefault();editor.querySelector('[data-batch-add]').click();}});
    window.addEventListener('beforeunload',event=>{if(dirty.size || pending.size || removing){event.preventDefault();event.returnValue = '';}});
    document.addEventListener('click',event=>{const a = event.target.closest('a[href]');if(!a || a.target === '_blank' || (!dirty.size && !pending.size && !removing) || event.ctrlKey || event.metaKey)return;event.preventDefault();if(!removing)flush().then(()=>a.click()).catch(error=>globalError(error.message));});
    document.addEventListener('workspace:before-navigate',event=>{if(dirty.size || pending.size || removing){event.preventDefault();if(!removing)flush().then(event.detail.resume).catch(error=>globalError(error.message));}});
    const poll = setInterval(async()=>{if(loading || removing || dirty.size || pending.size || document.hidden)return;try{await refreshSummary();if(!removing && (busy() || loadedBusy))await load();}catch(error){globalError(error.message);}},2500);
    window.addEventListener('pagehide',()=>{clearInterval(poll);cleanups.forEach(fn=>fn());rows.forEach(item=>clearTimeout(item.timer));});
    load();
}
if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(initializeReceiptBatches);
else document.addEventListener('DOMContentLoaded', initializeReceiptBatches, {once: true});
