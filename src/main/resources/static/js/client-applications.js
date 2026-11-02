(function () {
    'use strict';
    function init() {
        document.querySelectorAll('[data-client-application]:not([data-application-ready])').forEach(function (form) {
            form.dataset.applicationReady = 'true';
            var root = form.dataset.root, error = form.querySelector('[data-application-error]'), requirements = [], timers = new WeakMap();
            function fail() { error.textContent = error.dataset.errorText; error.hidden = false; }
            function valueLabel(value){var node=form.querySelector('[data-application-value="'+value+'"]');return node?node.textContent:String(value);}
            function panel(name) {
                form.querySelectorAll('[data-application-panel]').forEach(function (p) { p.hidden = p.dataset.applicationPanel !== name; });
                form.querySelectorAll('[data-application-tab]').forEach(function (t) { t.setAttribute('aria-selected', String(t.dataset.applicationTab === name)); t.tabIndex = t.dataset.applicationTab === name ? 0 : -1; });
            }
            form.querySelectorAll('[data-application-tab]').forEach(function (tab) {
                tab.addEventListener('click', function () { panel(tab.dataset.applicationTab); });
                tab.addEventListener('keydown', function (event) { var tabs = Array.from(form.querySelectorAll('[data-application-tab]')), index = tabs.indexOf(tab); if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { event.preventDefault(); var next = tabs[(index + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length]; next.click(); next.focus(); } });
            });
            panel('information');
            form.addEventListener('invalid', function (e) { var p = e.target.closest('[data-application-panel]'); if (p) panel(p.dataset.applicationPanel); }, true);
            function reindex(list) {
                var kind = list.dataset.repeatList;
                list.querySelectorAll('[data-repeat-row]').forEach(function (row, i) {
                    row.querySelectorAll('input,select,textarea').forEach(function (input, fieldIndex) {
                        if (input.dataset.field) input.name = kind + '[' + i + '].' + input.dataset.field;
                        else if (input.dataset.documentField) input.name = input.dataset.documentField + '[' + i + ']';
                        else if (input.name && kind !== 'documents') input.name = input.name.replace(/\[\d+\]/, '[' + i + ']');
                        input.id = 'application-' + kind + '-' + i + '-' + fieldIndex;
                        var label = input.parentElement.querySelector('label'); if (label && input.type !== 'search') label.htmlFor = input.id;
                    });
                });
            }
            function requiredOptions() {
                form.querySelectorAll('[data-document-field="requiredAttachmentIds"]').forEach(function (select) {
                    var old = select.value; while (select.options.length > 1) select.remove(1);
                    requirements.forEach(function (r) { select.add(new Option(r.name + ' (' + r.maxSizeMb + ' MB)', r.id)); }); select.value = old;
                });
            }
            form.querySelectorAll('[data-add-row]').forEach(function (button) { button.addEventListener('click', function () {
                var kind = button.dataset.addRow, list = form.querySelector('[data-repeat-list="' + kind + '"]');
                if (list.children.length >= (kind === 'documents' ? 10 : 20)) return;
                list.appendChild(form.querySelector('[data-row-template="' + kind + '"]').content.cloneNode(true)); reindex(list); requiredOptions();
                var first = list.lastElementChild.querySelector('input,select'); if (first) first.focus();
            }); });
            form.querySelectorAll('[data-repeat-list]').forEach(reindex);
            form.addEventListener('click', function (e) { var remove = e.target.closest('[data-remove-row]'); if (remove) { var list = remove.closest('[data-repeat-list]'); remove.closest('[data-repeat-row]').remove(); reindex(list); form.dispatchEvent(new Event('change', {bubbles:true})); } });
            function choices(search, select) {
                if (!select) return;
                clearTimeout(timers.get(search)); timers.set(search, setTimeout(async function () {
                    try {
                        var client = form.elements.namedItem('clientId').value, url = root + '/choices/' + select.dataset.applicationChoice + '?search=' + encodeURIComponent(search.value) + (client ? '&clientId=' + encodeURIComponent(client) : '');
                        var result = await fetch(url, {credentials:'same-origin',headers:{Accept:'application/json'}}); if (!result.ok) throw new Error();
                        var rows = await result.json(), selected = select.selectedOptions[0] && select.selectedOptions[0].cloneNode(true);
                        select.replaceChildren(new Option('', '')); if (selected && selected.value) select.add(selected);
                        rows.slice(0,25).forEach(function (r) { if (!selected || r.id !== selected.value) select.add(new Option(r.label,r.id)); });
                        if (selected) select.value = selected.value;
                        if (window.NeoSelect && typeof window.NeoSelect.refresh === 'function') window.NeoSelect.refresh(select);
                    } catch (_) { fail(); }
                },250));
            }
            form.addEventListener('input', function (event) {
                var input = event.target;
                if (input.dataset.choiceSearch) choices(input, form.querySelector('#' + input.dataset.choiceSearch));
                if (input.hasAttribute('data-guarantor-search')) choices(input, input.parentElement.querySelector('select'));
            });
            var standard = new Set(['purpose','business','monthlyIncome','monthlyExpenses','otherDebtRepayments','collateralDescription','collateralEstimatedValue']);
            var productRequest = 0;
            async function product(changed) {
                var id = form.elements.namedItem('productId').value, sequence = ++productRequest; if (!id) return;
                try {
                    var response = await fetch(root + '/product/' + encodeURIComponent(id), {headers:{Accept:'application/json'}}); if (!response.ok) throw new Error();
                    var data = await response.json(); if (sequence !== productRequest) return;
                    requirements = data.requirements; requiredOptions();
                    var summary=form.querySelector('[data-product-summary]');summary.textContent=summary.dataset.prefix+': '+data.product.minimumTenure+'-'+data.product.maximumTenure+' '+valueLabel(data.product.tenureUnit)+' | '+valueLabel(data.product.frequency)+' | '+summary.dataset.guarantors+': '+data.product.guarantors;
                    form.querySelector('[data-required-documents]').textContent = requirements.map(function (r) { return r.name + ' (' + r.maxSizeMb + ' MB)'; }).join('; ');
                    if (changed) {
                        var fields = form.querySelector('[data-product-fields]'); fields.replaceChildren();
                        data.fields.filter(function (f) { return !standard.has(f.name); }).forEach(function (field) {
                            var wrap = document.createElement('div'), label = document.createElement('label'), input;
                            if (field.enumOptions && field.enumOptions.length) { input=document.createElement('select'); input.add(new Option('','')); field.enumOptions.forEach(function (v) {input.add(new Option(v,v));}); }
                            else if(field.type==='boolean'){input=document.createElement('select');input.add(new Option('',''));input.add(new Option(valueLabel('true'),'true'));input.add(new Option(valueLabel('false'),'false'));}else if(field.type==='textarea'){input=document.createElement('textarea');input.rows=2;input.maxLength=1000;}else {input=document.createElement('input');input.type=field.type==='number'||field.type==='integer'?'number':field.type==='date'?'date':'text'; input.maxLength=1000; if(input.type==='number'){input.step=field.type==='integer'?'1':'0.01';if(field.min!==null)input.min=field.min;if(field.max!==null)input.max=field.max;} }
                            input.className='aws-control'; input.name='fields['+field.name+']'; input.id='product-field-'+fields.children.length;label.htmlFor=input.id;label.textContent=(field.label||field.name)+(field.required?' *':'');wrap.append(label,input);fields.append(wrap);
                        });
                        form.elements.namedItem('tenureUnit').value = data.product.tenureUnit;
                        form.elements.namedItem('tenureValue').value = data.product.minimumTenure;
                    }
                } catch (_) { fail(); }
            }
            form.elements.namedItem('productId').addEventListener('change',function(){product(true);}); product(false);
            form.querySelector('[data-application-quote]').addEventListener('click',async function () {
                error.hidden=true;
                try {
                    var data=new FormData(form);data.delete('files');var response=await fetch(root+'/quote',{method:'POST',body:data,headers:{Accept:'application/json'}});if(!response.ok)throw new Error();var quote=await response.json(),out=form.querySelector('[data-quote-output]');out.replaceChildren();
                    ['principalAmount','interestAmount','applicationFee','insuranceFee','processingFee','totalDeductions','periodicRepaymentAmount','interestRate','interestMethod','repaymentFrequency'].forEach(function (key) {var entry=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=form.querySelector('[data-quote-label="'+key+'"]').textContent;dd.textContent=quote[key]===undefined?'':key==='interestRate'?new Intl.NumberFormat(document.documentElement.lang,{maximumFractionDigits:4}).format(Number(quote[key])*100)+'%':valueLabel(quote[key]);entry.append(dt,dd);out.append(entry);});
                } catch (_) {fail();}
            });
        });
    }
    if(window.WorkspaceNavigation&&window.WorkspaceNavigation.onReady)window.WorkspaceNavigation.onReady(init);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
