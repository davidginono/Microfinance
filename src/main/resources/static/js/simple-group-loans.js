(function () {
    'use strict';
    function initialize(root) {
        root=root&&root.querySelectorAll?root:document;
        root.querySelectorAll('form[data-simple-group]').forEach(function (form) {
            if (form.dataset.simpleGroupReady) return;
            form.dataset.simpleGroupReady = 'true';
            var kind=form.elements.loanFor, panel=form.querySelector('[data-simple-group-fields]'), select=form.querySelector('#simple-group');
            if(!kind||!panel||!select)return;
            var roster=form.querySelector('[data-group-roster]'), error=form.querySelector('[data-group-load-error]'), pending, request=0;
            function toggle() {
                var group=kind.value==='GROUP';
                form.querySelectorAll('[data-individual-loan]').forEach(function (node) {node.hidden=group;node.querySelectorAll('input,select,textarea').forEach(function (field) {field.disabled=group;});});
                panel.hidden=!group;panel.querySelectorAll('input,select,textarea,button').forEach(function (field) {field.disabled=!group;});
                form.querySelectorAll('[data-group-guarantee]').forEach(function(node){node.hidden=!group;node.querySelectorAll('input').forEach(function(field){field.disabled=!group;});});
                form.querySelectorAll('[data-repeat-list="guarantors"] [name$=".fullName"]').forEach(function(field){field.closest('.loan-form-grid > div').hidden=group;field.disabled=group;});
                var draft=form.querySelector('[data-recording-draft]');if(group&&draft)draft.disabled=false;
            }
            function text(tag,value,parent) {var node=document.createElement(tag);node.textContent=value==null?'':value;parent.append(node);return node;}
            function field(row,index,name,label,type,value) {
                var wrapper=type==='hidden'?row:text('label',label,row), input=document.createElement('input');
                input.name='groupApplication.signers['+index+'].'+name;input.type=type;input.className='aws-control';input.value=value||'';
                if(type==='text')input.maxLength=name==='incomeSource'?200:500;
                wrapper.append(input);
            }
            var debts=form.querySelector('[data-group-debt-list]'),debtTemplate=form.querySelector('[data-group-debt-template]');
            function numberDebts(){debts.querySelectorAll('[data-group-debt]').forEach(function(row,index){row.querySelectorAll('input,select').forEach(function(input){var field=input.dataset.debtField||input.name.substring(input.name.lastIndexOf('.')+1);input.name=(input.name.charAt(0)==='_'?'_':'')+'groupApplication.debts['+index+'].'+field;});});form.querySelector('[data-add-group-debt]').disabled=debts.children.length>=50;}
            form.querySelector('[data-add-group-debt]').addEventListener('click',function(){if(debts.children.length>=50)return;var row=debtTemplate.content.cloneNode(true);debts.append(row);numberDebts();debts.lastElementChild.querySelector('input').focus();});
            debts.addEventListener('click',function(event){var button=event.target.closest('[data-remove-group-debt]');if(button){button.closest('[data-group-debt]').remove();numberDebts();form.querySelector('[data-add-group-debt]').focus();}});
            async function load() {
                var sequence=++request;if(pending)pending.abort();roster.replaceChildren();error.hidden=true;
                debts.replaceChildren();
                if(!select.value)return;
                form.elements['groupApplication.checked'].checked=false;form.elements.groupAgreement.value='';
                pending=new AbortController();roster.setAttribute('aria-busy','true');
                try {
                    var response=await fetch(form.dataset.groupRoot+'/'+encodeURIComponent(select.value),{credentials:'same-origin',headers:{Accept:'application/json'},signal:pending.signal});
                    if(!response.ok)throw new Error('group');var data=await response.json();if(sequence!==request)return;
                    form.elements['groupApplication.expectedVersion'].value=data.version;
                    text('p',form.dataset.leaderLabel+': '+(data.leader||''),roster);
                    text('p',[data.holder,data.provider,data.identifier].filter(Boolean).join(' · '),roster);
                    var options=debtTemplate.content.querySelector('select');options.replaceChildren();data.people.forEach(function(person){options.add(new Option(person.number+' · '+person.name,person.id));});
                    data.people.forEach(function (person,index) {var row=text('div','',roster);row.className='simple-group-borrower';text('span',person.number+' · '+person.name,row);field(row,index,'clientId','','hidden',person.id);field(row,index,'signedOn',form.dataset.signedLabel,'date','');field(row,index,'incomeSource',form.dataset.sourceLabel,'text','');field(row,index,'evidence',form.dataset.evidenceLabel,'text','');});
                } catch (failure) {if(failure.name!=='AbortError'&&sequence===request)error.hidden=false;}
                finally {if(sequence===request)roster.removeAttribute('aria-busy');}
            }
            form.addEventListener('change',toggle);kind.addEventListener('change',toggle);select.addEventListener('change',load);form.querySelector('[data-group-retry]').addEventListener('click',load);toggle();numberDebts();if(debts.children.length)form.querySelector('[data-group-debts]').open=true;
        });
    }
    if(window.WorkspaceNavigation)window.WorkspaceNavigation.onReady(initialize);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){initialize(document);});else initialize(document);
}());
