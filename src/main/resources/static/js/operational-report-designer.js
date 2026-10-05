(() => {
    'use strict';
    const root = document.getElementById('opreportDesigner');
    if (!root) return;
    const form = document.getElementById('reportDefinitionForm');
    const json = document.getElementById('reportDefinitionJson');
    const original = JSON.parse(json.value);
    const control = id => document.getElementById(id);
    const valueLabels = Object.fromEntries(Array.from(root.querySelectorAll('[data-report-value]')).map(el => [el.dataset.reportValue,el.textContent]));
    const fieldLabels = Object.fromEntries(Array.from(root.querySelectorAll('[data-report-label]')).map(el => [el.dataset.reportLabel.toLowerCase(),el.textContent]));
    function money(value,language) {
        const text=String(value); const negative=text.startsWith('-');
        const [whole,fraction='00']=text.replace(/^-/,'').split('.');
        const separator=new Intl.NumberFormat(language).formatToParts(1.1).find(part => part.type==='decimal')?.value || '.';
        return (negative ? '-' : '')+new Intl.NumberFormat(language).format(BigInt(whole))+separator+fraction.padEnd(2,'0');
    }
    const values = {reportTitle:'title',reportFrom:'dateFrom',reportTo:'dateTo',reportLoan:'loanId',reportChannel:'channel',reportStatus:'status',reportSort:'sortBy',reportDirection:'direction',reportLanguage:'language',reportOrientation:'orientation',reportPaper:'paper',reportFooter:'footer'};
    Object.entries(values).forEach(([id,key]) => { if (control(id)) control(id).value = original[key]; });
    control('reportBranding').checked = original.showInstitutionBranding;
    const list = control('reportColumns');
    original.columns.forEach(c => {
        const row = list.querySelector(`[data-field="${c.field}"]`);
        row.querySelector('[data-column-selected]').checked = true;
        row.querySelector('[data-column-label]').value = c.label;
        row.querySelector('[data-column-width]').value = c.width;
        list.appendChild(row);
    });
    list.querySelectorAll('li').forEach(row => { if (!original.columns.some(c => c.field === row.dataset.field)) list.appendChild(row); });
    root.querySelectorAll('[data-group]').forEach(el => el.checked = original.groupBy.includes(el.dataset.group));
    root.querySelectorAll('[data-total]').forEach(el => el.checked = original.totals.includes(el.dataset.total));
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-move]');
        if (!button) return;
        const row = button.closest('li');
        if (button.dataset.move === 'up' && row.previousElementSibling) list.insertBefore(row,row.previousElementSibling);
        if (button.dataset.move === 'down' && row.nextElementSibling) list.insertBefore(row.nextElementSibling,row);
        button.focus();
    });
    function definition() {
        const d = {...original};
        Object.entries(values).forEach(([id,key]) => { if (control(id)) d[key] = control(id).value; });
        if (d.dataset === 'LOAN_PORTFOLIO') d.dateFrom = d.dateTo;
        d.showInstitutionBranding = control('reportBranding').checked;
        d.columns = Array.from(list.children).filter(row => row.querySelector('[data-column-selected]').checked).map(row => ({field:row.dataset.field,label:row.querySelector('[data-column-label]').value,width:Number(row.querySelector('[data-column-width]').value),format:row.dataset.format}));
        d.groupBy = Array.from(root.querySelectorAll('[data-group]:checked')).map(el => el.dataset.group);
        d.totals = Array.from(root.querySelectorAll('[data-total]:checked')).map(el => el.dataset.total);
        return d;
    }
    form.addEventListener('submit', () => { json.value = JSON.stringify(definition()); });
    const preview = control('reportPreview');
    if (!preview) return;
    preview.addEventListener('click', async () => {
        const feedback = control('reportPreviewFeedback');
        feedback.textContent = feedback.dataset.loading;
        preview.disabled = true;
        control('reportPreviewTable').replaceChildren(); control('reportPreviewTotals').replaceChildren(); control('reportPreviewGroups').replaceChildren(); control('reportPreviewGroups').hidden=true;
        try {
            const csrf = control('opreportCsrf');
            const response = await fetch(root.dataset.previewUrl,{method:'POST',headers:{'Content-Type':'application/json','Accept':'application/json','X-Requested-With':'XMLHttpRequest',[csrf.dataset.csrfHeader]:csrf.value},body:JSON.stringify({definitionJson:JSON.stringify(definition()),page:0,size:25})});
            if (!response.ok) throw new Error('preview');
            const result = await response.json();
            const table = control('reportPreviewTable');
            const head = table.createTHead().insertRow();
            result.columns.forEach(column => { const cell=document.createElement('th'); cell.textContent=column.label; cell.style.minWidth=`${column.width}px`; head.appendChild(cell); });
            const body = table.createTBody();
            result.rows.forEach(row => {
                const tr = body.insertRow();
                result.columns.forEach(column => { const cell=tr.insertCell(); const value=row[column.key]; cell.textContent=value === null ? result.labels.unavailable : column.format === 'MONEY' ? money(value,result.definition.language.toLowerCase()) : result.labels['value.'+value] || valueLabels[value] || String(value); if(column.format==='MONEY') cell.classList.add('money'); });
            });
            if (!result.rows.length) { const cell=body.insertRow().insertCell(); cell.colSpan=result.columns.length; cell.textContent=feedback.dataset.empty; }
            feedback.textContent = `${result.labels.filteredRows}: ${result.rowCount} · ${result.labels.tracked}: ${result.coverage.trackedLoans} · ${result.labels.untracked}: ${result.coverage.untrackedLoans} · ${result.labels.unknownDates}: ${result.coverage.unknownDateLoans}`;
            Object.entries(result.totals).forEach(([key,total]) => { const item=document.createElement('span'); const label=result.columns.find(c => c.key===key)?.label || result.labels['field.'+key] || fieldLabels[key]; item.textContent=`${label}: ${total.value === null ? result.labels.unavailable : money(total.value,result.definition.language.toLowerCase())} · ${result.labels.missingRows}: ${total.unavailableRows}`; control('reportPreviewTotals').appendChild(item); });
            if (result.groups.length) {
                const table=document.createElement('table');table.className='erp-table';const header=table.createTHead().insertRow();const keys=Object.keys(result.groups[0]);
                keys.forEach(key => {const th=document.createElement('th');th.textContent=key==='row_count' ? result.labels.filteredRows : key.endsWith('_unavailable') ? result.labels['field.'+key.slice(0,-12)]+' · '+result.labels.missingRows : result.labels['field.'+key];header.appendChild(th);});
                const body=table.createTBody();result.groups.forEach(group => {const tr=body.insertRow();keys.forEach(key => {const td=tr.insertCell();const value=group[key];td.textContent=value===null ? result.labels.unavailable : result.totals[key] ? money(value,result.definition.language.toLowerCase()) : result.labels['value.'+value] || String(value);});});
                control('reportPreviewGroups').appendChild(table);control('reportPreviewGroups').hidden=false;
            }
        } catch (_) { feedback.textContent=feedback.dataset.failure; }
        finally { preview.disabled=false; }
    });
})();
