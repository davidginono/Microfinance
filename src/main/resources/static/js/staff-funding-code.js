(function () {
    'use strict';
    function initialize(root) {
        root=root&&root.querySelectorAll?root:document;
        root.querySelectorAll('[data-staff-funding-code]').forEach(function (panel) {
            if(panel.dataset.ready)return;panel.dataset.ready='true';
            var button=panel.querySelector('[data-send-staff-code]'),status=panel.querySelector('[data-code-status]');
            button.addEventListener('click',async function () {
                var form=button.form, token=form.querySelector('input[name="_csrf"]'),data=new URLSearchParams();
                if(token)data.set(token.name,token.value);button.disabled=true;button.setAttribute('aria-busy','true');status.textContent='';
                try{var response=await fetch(panel.dataset.url,{method:'POST',credentials:'same-origin',body:data,headers:{Accept:'application/json'}});if(!response.ok)throw new Error('delivery');status.textContent=panel.dataset.sent;}
                catch(error){status.textContent=panel.dataset.error;}
                finally{button.disabled=false;button.removeAttribute('aria-busy');}
            });
        });
    }
    if(window.WorkspaceNavigation)window.WorkspaceNavigation.onReady(initialize);else if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){initialize(document);});else initialize(document);
}());
