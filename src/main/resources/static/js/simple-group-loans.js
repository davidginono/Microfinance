(function () {
    'use strict';
    function initialize(root) {
        root=root&&root.querySelectorAll?root:document;
        root.querySelectorAll('form[data-simple-group]').forEach(function (form) {
            if (form.dataset.simpleGroupReady) return;
            form.dataset.simpleGroupReady = 'true';
            var kind=form.elements.loanFor, panel=form.querySelector('[data-simple-group-fields]'), select=form.querySelector('#simple-group');
            if(!kind||!panel||!select)return;
            var error=form.querySelector('[data-group-load-error]'), pending, request=0;
            var agreement=form.elements.groupAgreement, agreementBox=form.querySelector('[data-group-agreement-upload]');
            function renderAgreement() {
                if(!agreementBox)return;
                var file=agreement.files[0], list=agreementBox.querySelector('[data-group-agreement-selected]');
                var message=file&&(!/\.pdf$/i.test(file.name)||file.size>10*1024*1024)?agreement.dataset.error:'';
                agreement.setCustomValidity(message);
                var localError=agreementBox.querySelector('[data-group-agreement-error]');localError.textContent=message;localError.hidden=!message;
                list.replaceChildren();
                agreementBox.querySelectorAll('[data-group-agreement-saved]').forEach(function(row){row.hidden=!!file||form.elements['groupApplication.removeAgreement'].value==='true';});
                if(!file)return;
                list.append(agreementBox.querySelector('[data-group-agreement-template]').content.cloneNode(true));
                list.querySelector('[data-group-agreement-name]').textContent=file.name;
                list.querySelector('[data-group-agreement-size]').textContent=new Intl.NumberFormat(document.documentElement.lang||'en',{maximumFractionDigits:1}).format(file.size/1024)+' KB';
                var remove=list.querySelector('[data-remove-group-agreement]');remove.setAttribute('aria-label',remove.dataset.removeLabel+' '+file.name);
            }
            function clearAgreement() {
                agreement.value='';
                if(agreementBox){form.elements['groupApplication.removeAgreement'].value='true';renderAgreement();}
                agreement.dispatchEvent(new Event('input',{bubbles:true}));
            }
            if(agreementBox){
                agreement.addEventListener('change',renderAgreement);
                agreementBox.addEventListener('click',function(event){if(event.target.closest('[data-remove-group-agreement]')){clearAgreement();agreement.focus();}});
                renderAgreement();
            }
            function toggle() {
                var group=kind.value==='GROUP';
                form.querySelectorAll('[data-individual-loan]').forEach(function (node) {node.hidden=group;node.querySelectorAll('input,select,textarea').forEach(function (field) {field.disabled=group || (field.name==='clientId' && !!form.elements.topUpSourceLoanId);});});
                panel.hidden=!group;panel.querySelectorAll('input,select,textarea,button').forEach(function (field) {field.disabled=!group;});
                form.querySelectorAll('[data-group-guarantee], [data-group-confirmation], [data-group-agreement-upload]').forEach(function(node){node.hidden=!group;node.querySelectorAll('input,button').forEach(function(field){field.disabled=!group;});});
                form.querySelectorAll('[data-repeat-list="guarantors"] [name$=".fullName"]').forEach(function(field){field.closest('.loan-form-grid > div').hidden=group;field.disabled=group;});
                var draft=form.querySelector('[data-recording-draft]');if(group&&draft)draft.disabled=false;
            }
            var debts=form.querySelector('[data-group-debt-list]'),debtTemplate=form.querySelector('[data-group-debt-template]');
            function numberDebts(){debts.querySelectorAll('[data-group-debt]').forEach(function(row,index){row.querySelectorAll('input,select').forEach(function(input){var field=input.dataset.debtField||input.name.substring(input.name.lastIndexOf('.')+1);input.name=(input.name.charAt(0)==='_'?'_':'')+'groupApplication.debts['+index+'].'+field;});});form.querySelector('[data-add-group-debt]').disabled=debts.children.length>=50;}
            form.querySelector('[data-add-group-debt]').addEventListener('click',function(){if(debts.children.length>=50)return;var row=debtTemplate.content.cloneNode(true);debts.append(row);numberDebts();debts.lastElementChild.querySelector('input').focus();});
            debts.addEventListener('click',function(event){var button=event.target.closest('[data-remove-group-debt]');if(button){button.closest('[data-group-debt]').remove();numberDebts();form.querySelector('[data-add-group-debt]').focus();}});
            async function load() {
                var sequence=++request;if(pending)pending.abort();error.hidden=true;
                debts.replaceChildren();
                panel.removeAttribute('aria-busy');
                form.elements['groupApplication.expectedVersion'].value='';
                form.elements['groupApplication.checked'].checked=false;clearAgreement();
                if(!select.value)return;
                pending=new AbortController();panel.setAttribute('aria-busy','true');
                try {
                    var response=await fetch(form.dataset.groupRoot+'/'+encodeURIComponent(select.value),{credentials:'same-origin',headers:{Accept:'application/json'},signal:pending.signal});
                    if(!response.ok)throw new Error('group');var context=await response.json();if(sequence!==request)return;var data=context.setup;
                    form.elements['groupApplication.expectedVersion'].value=data.version;
                    var options=debtTemplate.content.querySelector('select');options.replaceChildren();data.people.forEach(function(person){options.add(new Option(person.number+' · '+person.name,person.id));});
                } catch (failure) {if(failure.name!=='AbortError'&&sequence===request)error.hidden=false;}
                finally {if(sequence===request)panel.removeAttribute('aria-busy');}
            }
            form.addEventListener('change',toggle);kind.addEventListener('change',toggle);select.addEventListener('change',load);form.querySelector('[data-group-retry]').addEventListener('click',load);toggle();numberDebts();if(debts.children.length)form.querySelector('[data-group-debts]').open=true;
        });
    }
    if(window.WorkspaceNavigation)window.WorkspaceNavigation.onReady(initialize);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){initialize(document);});else initialize(document);
}());
