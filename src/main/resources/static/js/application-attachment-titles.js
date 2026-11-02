(function () {
    'use strict';
    function init() {
        document.querySelectorAll('[data-attachment-titles]:not([data-ready])').forEach(function (container) {
            container.dataset.ready='true';var input=container.closest('form').querySelector('input[type=file][name=attachments]');if(!input)return;
            input.addEventListener('change',function(){
                var previous={};container.querySelectorAll('input').forEach(function(i){previous[i.dataset.filename]=i.value;});container.replaceChildren();
                Array.from(input.files).forEach(function(file,index){var row=document.createElement('div'),label=document.createElement('label'),name=document.createElement('input');name.className='aws-control w-full';name.name='attachmentTitle_'+index;name.id='attachment-title-'+index;name.maxLength=120;name.dataset.filename=file.name;name.value=previous[file.name]||file.name.slice(0,120);label.htmlFor=name.id;label.textContent=container.dataset.label+' — '+file.name;row.append(label,name);container.append(row);});
            });
        });
    }
    if(window.WorkspaceNavigation&&window.WorkspaceNavigation.onReady)window.WorkspaceNavigation.onReady(init);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
