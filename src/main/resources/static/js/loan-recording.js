(() => {
  'use strict';
  document.querySelector('[data-loan-print]')?.addEventListener('click', () => window.print());
  const root = document.querySelector('meta[name="app-context-path"]')?.content || '';
  document.querySelectorAll('[data-loan-choice]').forEach(box => {
    const select = box.querySelector('[data-choice-select]'), error = box.querySelector('[data-choice-error]');
    const picker = window.NeoSelect.search(select), search = picker.input, more = picker.more;
    search.setAttribute('aria-label', error.dataset.searchLabel);
    let page = 0, request = 0, controller, loading = false, loadedSearch;
    async function load(targetPage = 0) {
      controller?.abort(); controller = new AbortController();
      const version = ++request, query = search.value, normalized = query.trim().toLocaleLowerCase();
      loading = true; picker.setLoading(true); more.disabled = true;
      try {
        const response = await fetch((box.dataset.choiceUrl || root + '/finance/loan-recording/choices/' + box.dataset.category) + '?' + new URLSearchParams({search: query, page: targetPage}), {headers: {Accept: 'application/json'}, signal:controller.signal, cache:'no-store', credentials:'same-origin'});
        if (!response.ok || response.redirected) throw new Error('Lookup failed');
        const choices = await response.json(); if (version !== request || controller.signal.aborted || !select.isConnected) return;
        const value = select.value, selected = select.selectedOptions[0]?.cloneNode(true);
        const blank = [...select.options].find(option => option.value === '')?.cloneNode(true) || new Option('', '');
        const pinned = [...select.options].filter(option => option.dataset.choicePinned === 'true').map(option => option.cloneNode(true));
        const previous = targetPage ? [...select.options].filter(option => option.value && option.dataset.choicePinned !== 'true' && option.dataset.searchQuery === normalized) : [];
        const options = new Map(previous.map(option => [option.value, option]));
        const productFields = ['tenureUnit','minimumAmount','maximumAmount','minimumTenure','maximumTenure'];
        choices.slice(0, 25).forEach(choice => {const option = new Option(choice.label, choice.id); option.dataset.searchQuery = normalized;productFields.forEach(key=>{if(choice[key]!=null)option.dataset[key]=choice[key];}); options.set(choice.id, option);});
        const visible = [...options.values()].filter(option => !pinned.some(fixed => fixed.value === option.value)).slice(-100); select.replaceChildren(blank, ...pinned, ...visible);
        if (value && ![...select.options].some(option => option.value === value) && selected) select.add(selected);
        select.value = value; page = targetPage; loadedSearch = query;
        if(box.dataset.category === 'products' && productFields.some(key=>selected?.dataset[key] !== select.selectedOptions[0]?.dataset[key]))
          select.dispatchEvent(new Event('change', {bubbles:true}));
        more.hidden = choices.length <= 25; error.textContent = '';
      } catch (failure) { if (version === request && failure.name !== 'AbortError') {error.textContent = error.dataset.error; more.hidden = true; binding.cancel();} }
      finally {if (version === request) {loading = false; picker.setLoading(false); more.disabled = false;}}
    }
    const binding = window.AppSearch.bind(search, () => load(), () => {++request; controller?.abort(); loading = false; picker.setLoading(true); more.hidden = true;});
    more.addEventListener('click', () => {binding.cancel(); load(page + 1);});
    select.addEventListener('neo-select:close', () => {binding.cancel(); ++request; controller?.abort(); loading = false; picker.setLoading(false);});
    select.addEventListener('neo-select:open', () => {if (!loading && (loadedSearch !== search.value || error.textContent)) {binding.cancel(); load();}});
    window.addEventListener('pagehide', () => {binding.cancel(); ++request; controller?.abort();});
    if(box.hasAttribute('data-hydrate-selection') && select.value){
      const value=select.value;
      fetch(box.dataset.choiceUrl+'?'+new URLSearchParams({selected:value}),{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store'})
        .then(response=>{if(!response.ok || response.redirected)throw new Error('Lookup failed');return response.json();})
        .then(choices=>{if(select.value===value && select.isConnected){const choice=choices.find(c=>c.id===value);if(choice){select.selectedOptions[0].textContent=choice.label;window.refreshEnhancedSelects?.(box);}else error.textContent=error.dataset.error;}})
        .catch(()=>{if(select.isConnected)error.textContent=error.dataset.error;});
    }
    load();
  });
  const disbursementSelect = document.querySelector('[data-category="disbursement-clients"] select');
  if(disbursementSelect){let sequence=0, controller;disbursementSelect.addEventListener('change',async()=>{
    controller?.abort();controller=new AbortController();const version=++sequence,form=disbursementSelect.form;
    form.elements.amount.value='';form.elements.firstPaymentDate.value='';if(!disbursementSelect.value)return;
    try{const response=await fetch(root+'/finance/loan-recording/loans/'+encodeURIComponent(disbursementSelect.value),{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store',signal:controller.signal});if(!response.ok || response.redirected)return;const data=await response.json();if(version!==sequence)return;form.elements.amount.value=data.amount;form.elements.firstPaymentDate.value=data.firstPaymentDate;}catch(error){if(error.name!=='AbortError')form.querySelector('[data-choice-error]').textContent=form.querySelector('[data-choice-error]').dataset.error;}
  });}
  const recordForm = document.querySelector('form:has(#loanMode)');
  if (recordForm) {
    const mode = recordForm.querySelector('#loanMode'), entry = recordForm.querySelector('#interestEntry'), unit = recordForm.querySelector('#tenureUnit'), method = recordForm.querySelector('#customMethod'), basis = recordForm.querySelector('#interestBasis');
    const product = recordForm.querySelector('#productId'), tenureType = recordForm.querySelector('[data-tenure-type]');
    const initialProduct = product.value, initialUnit = unit.value;
    const region = recordForm.querySelector('[data-interest-preview]'), spinner = region.querySelector('.loan-interest-spinner'), output = region.querySelector('[data-interest-value]'), description = region.querySelector('[data-interest-description]');
    const schedule = recordForm.querySelector('[data-schedule-preview]'), rows = schedule?.querySelector('[data-schedule-rows]'), summary = schedule?.querySelector('[data-schedule-summary]');
    const money = value => new Intl.NumberFormat(document.documentElement.lang || 'en', {minimumFractionDigits:2,maximumFractionDigits:2}).format(value);
    let timer, controller, version = 0;
    const show = (box, visible) => {box.hidden = !visible;box.querySelectorAll('input,select').forEach(input => input.disabled = !visible);};
    function refresh() {
      const approvedRate=recordForm.querySelector('[data-approved-rate]');if(approvedRate)approvedRate.hidden=true;
      const custom = mode.value === 'CUSTOM', reducing = custom && method.value === 'REDUCING_BALANCE';
      tenureType.hidden = !custom;
      unit.required = custom;
      if(!custom){
        const selectedUnit = product.selectedOptions[0]?.dataset.tenureUnit || (product.value === initialProduct ? initialUnit : '');
        const productUnit = ['DAYS','MONTHS','YEARS'].includes(selectedUnit) ? selectedUnit : '';
        if(unit.value !== productUnit){unit.value = productUnit;recordForm.querySelector('#tenureValue').value = '';}
      }
      window.refreshEnhancedSelects?.(tenureType);
      if(reducing)entry.value = 'PERCENTAGE';
      entry.querySelector('option[value="AMOUNT"]').disabled = reducing;
      recordForm.querySelector('[data-reducing-terms-note]').hidden = !reducing || basis.value !== 'WHOLE_LOAN';
      const amount = entry.value === 'AMOUNT';
      show(recordForm.querySelector('[data-configured-product]'), !custom);
      const productNote=recordForm.querySelector('[data-configured-terms-note]');if(productNote)productNote.hidden=custom;
      show(recordForm.querySelector('[data-custom-terms]'), custom);
      recordForm.querySelectorAll('[data-custom-percentage]').forEach(box => show(box, custom && !amount));
      show(recordForm.querySelector('[data-custom-amount]'), custom && amount);
      window.refreshEnhancedSelects?.(recordForm.querySelector('[data-custom-terms]'));
      show(recordForm.querySelector('[data-tenure-number]'), !!unit.value);
      const tenureLabel = recordForm.querySelector('[data-tenure-label]');
      tenureLabel.textContent = tenureLabel.dataset[unit.value.toLowerCase()] || tenureLabel.dataset.empty;
      recordForm.querySelector('#tenureValue').max = unit.value === 'DAYS' ? '18250' : unit.value === 'YEARS' ? '50' : '600';
      const selected = !custom && product.value ? product.selectedOptions[0]?.dataset : null;
      const amountLimits = recordForm.querySelector('[data-product-amount-limits]'), tenureLimits = recordForm.querySelector('[data-product-tenure-limits]');
      if(amountLimits){
        const minimum = selected?.minimumAmount, maximum = selected?.maximumAmount;
        amountLimits.hidden = !minimum && !maximum;
        amountLimits.querySelector('[data-limit-min]').textContent = minimum ? money(minimum) : amountLimits.dataset.unavailable;
        amountLimits.querySelector('[data-limit-max]').textContent = maximum ? money(maximum) : amountLimits.dataset.unavailable;
      }
      if(tenureLimits){
        tenureLimits.hidden = !selected?.minimumTenure || !selected?.maximumTenure || !unit.value;
        tenureLimits.querySelector('[data-limit-min]').textContent = selected?.minimumTenure || '';
        tenureLimits.querySelector('[data-limit-max]').textContent = selected?.maximumTenure || '';
        tenureLimits.querySelector('[data-limit-unit]').textContent = region.dataset[unit.value.toLowerCase()] || '';
      }
      clearTimeout(timer); controller?.abort(); ++version;
      recordForm.dispatchEvent(new CustomEvent('loan:quote-reset'));spinner.hidden = true;region.setAttribute('aria-busy','false'); output.textContent = '\u2014'; description.textContent = '';
      if(schedule){schedule.hidden = true;rows.replaceChildren();summary.textContent = '';}
      if (!recordForm.querySelector('#principal').value || !unit.value || !recordForm.querySelector('#tenureValue').value
        || (!custom && !recordForm.querySelector('#productId').value)
        || (custom && !recordForm.querySelector(amount ? '#interestAmount' : '#interestPercent').value)) return;
      const current = version;
      timer = setTimeout(async () => {
        controller = new AbortController(); spinner.hidden = false; region.setAttribute('aria-busy','true');
        recordForm.dispatchEvent(new CustomEvent('loan:quote-pending'));
        const data = new URLSearchParams();new FormData(recordForm).forEach((value,key) => {if(typeof value === 'string')data.append(key,value);});
        try {
          const response = await fetch(root + '/finance/loan-recording/quote', {method:'POST',body:data,headers:{Accept:'application/json'},signal:controller.signal,credentials:'same-origin',cache:'no-store'});
          if(response.redirected)throw new Error(region.dataset.error);
          const quote = await response.json();if(current !== version)return;
          if(!response.ok)throw new Error(quote.error || region.dataset.error);
          recordForm.dispatchEvent(new CustomEvent('loan:quote',{detail:quote}));output.textContent = money(quote.interest);
          const approvedRate=recordForm.querySelector('[data-approved-rate]');if(approvedRate){approvedRate.hidden=custom;if(!custom)approvedRate.textContent=approvedRate.dataset.label+': '+quote.ratePercent+'% · '+approvedRate.dataset.basis;}
          description.textContent = custom ? (amount ? entry.selectedOptions[0].text : recordForm.querySelector('#interestBasis').selectedOptions[0].text) : quote.product + " \u00b7 " + quote.tenureValue + " " + region.dataset[quote.tenureUnit.toLowerCase()];
          if(schedule && quote.schedule?.length){
            rows.replaceChildren(...quote.schedule.map(payment => {
              const row = document.createElement('tr');
              const period = summary.dataset[quote.frequency.toLowerCase() + 'Period'].replace('{0}', String(payment.number));
              [payment.number,period,money(payment.beginningBalance),money(payment.amount),money(payment.principal),money(payment.interest),money(payment.endingBalance)].forEach(value => {
                const cell = document.createElement('td');cell.textContent = value;row.append(cell);
              });
              return row;
            }));
            summary.textContent = summary.dataset[quote.frequency.toLowerCase()] + ' \u00b7 ' + quote.tenureValue + ' ' + region.dataset[quote.tenureUnit.toLowerCase()];
            schedule.hidden = false;
          }
        } catch(error) {if(current === version && error.name !== 'AbortError'){description.textContent = error.message || region.dataset.error;recordForm.dispatchEvent(new CustomEvent('loan:quote-error',{detail:description.textContent}));}}
        finally {if(current === version){spinner.hidden = true;region.setAttribute('aria-busy','false');recordForm.dispatchEvent(new CustomEvent('loan:quote-settled'));}}
      }, 150);
    }
    const pricing = new Set(['loanMode','productId','principal','tenureUnit','tenureValue','interestEntry','interestBasis','interestPercent','interestAmount','customFrequency','customMethod','applicationDate','customApplicationCharge','customProcessingCharge','insurancePremium','insuranceEvidence','evidence']);
    const changed = event => {if(pricing.has(event.target.name))refresh();};
    recordForm.addEventListener('input', changed); recordForm.addEventListener('change', changed);
    recordForm.addEventListener('loan:quote-retry',refresh);
    window.addEventListener('pagehide', () => {clearTimeout(timer);controller?.abort();++version;spinner.hidden=true;region.setAttribute('aria-busy','false');recordForm.dispatchEvent(new CustomEvent('loan:quote-reset'));});
    window.addEventListener('pageshow', refresh);refresh();
  }
  document.querySelectorAll('[data-loan-form]').forEach(form => {
    const summary = form.querySelector('.loan-error-summary'); if (summary) {form.querySelectorAll('details').forEach(d => d.open = true); summary.focus();}
    const submitButtons = [...form.querySelectorAll('button[type="submit"],button:not([type])')];
    const initiallyDisabled = new Set(submitButtons.filter(button => button.disabled));
    let submitting = false;
    // Shared confirmation runs on document before this deferred script binds.
    // Lock only the accepted submission, not the first confirmation request.
    document.addEventListener('submit', event => {
      if (event.target !== form || event.defaultPrevented) return;
      if (submitting) {event.preventDefault(); return;}
      if (event.submitter?.name === 'action') {
        let action = form.querySelector('input[name="action"]');
        if (!action) {action=document.createElement('input');action.type='hidden';action.name='action';form.append(action);}
        action.value=event.submitter.value;
      }
      submitting = true;
      // Defer disabling so the clicked action remains part of the submitted form.
      setTimeout(() => form.querySelectorAll('button[type="submit"],button:not([type])').forEach(b => b.disabled = true), 0);
    });
    window.addEventListener('pageshow', event => {if(event.persisted){submitting=false;submitButtons.forEach(b=>b.disabled=initiallyDisabled.has(b));}});
  });
})();
