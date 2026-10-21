const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {JSDOM}=require('jsdom');
const source=readFileSync('src/main/webapp/WEB-INF/jsp/fragments/footer.jspf','utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const initial=readFileSync('src/main/webapp/WEB-INF/jsp/fragments/header.jspf','utf8').match(/<script>([\s\S]*?)<\/script>/)[1];

function setup(width,menu=''){
 const dom=new JSDOM('<button id="sidebarToggle" data-expand-label="Open navigation" data-collapse-label="Close navigation"></button><div id="sidebarOverlay" class="hidden"></div><aside id="appSidebar" class="-translate-x-full"><button id="desktopSidebarToggle" data-expand-label="Open navigation" data-collapse-label="Close navigation"></button><nav class="shell-sidebar-scroll"><a href="/app/dashboard">Dashboard</a>'+menu+'</nav></aside>',{url:'http://localhost/app/dashboard',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,changes=[];w.innerWidth=width;
 w.matchMedia=()=>({get matches(){return w.innerWidth>=1024;},addEventListener(_,fn){changes.push(fn);}});
 w.localStorage.setItem('shell:desktopSidebarCollapsed','false');
 w.eval(initial);w.eval(source);
 return {dom,w,d:w.document,resize(width){w.innerWidth=width;changes.forEach(fn=>fn());}};
}

test('desktop navigation starts as a closed rail and its in-page choice survives viewport changes',()=>{
 const {dom,w,d,resize}=setup(1366),toggle=d.querySelector('#desktopSidebarToggle');
 assert.equal(d.documentElement.classList.contains('shell-sidebar-collapsed'),true);assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(toggle.getAttribute('aria-label'),'Open navigation');
 toggle.click();assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(d.documentElement.classList.contains('shell-sidebar-collapsed'),false);
 d.dispatchEvent(new w.Event('resource-table:query'));assert.equal(toggle.getAttribute('aria-expanded'),'true');
 resize(360);resize(1366);assert.equal(toggle.getAttribute('aria-expanded'),'true');
 toggle.click();resize(768);assert.equal(d.documentElement.classList.contains('shell-sidebar-collapsed'),false);
 resize(1366);assert.equal(d.documentElement.classList.contains('shell-sidebar-collapsed'),true);dom.window.close();
});

test('mobile drawer exposes accurate state and removes closed navigation from keyboard access',()=>{
 const {dom,w,d}=setup(360),sidebar=d.querySelector('aside'),toggle=d.querySelector('#sidebarToggle');
 assert.equal(sidebar.inert,true);assert.equal(sidebar.getAttribute('aria-hidden'),'true');
 toggle.click();assert.equal(sidebar.inert,false);assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(sidebar.hasAttribute('aria-hidden'),false);
 d.querySelector('a').focus();d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
 assert.equal(sidebar.inert,true);assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(d.activeElement,toggle);assert.equal(d.querySelector('#sidebarOverlay').classList.contains('hidden'),true);dom.window.close();
});

test('expanded navigation keeps nested menus inline and preserves their state across drawer changes',()=>{
 for(const width of [360,1366]){
  const {dom,w,d}=setup(width,'<button id="memberSettingsToggle" aria-controls="memberSettingsPanel" data-menu-active="true">Settings</button><div id="memberSettingsPanel"><a href="/app/settings">Preferences</a></div>');
  try{
   d.querySelector(width<1024?'#sidebarToggle':'#desktopSidebarToggle').click();
   const toggle=d.querySelector('#memberSettingsToggle'),panel=d.querySelector('#memberSettingsPanel');
   assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(panel.classList.contains('hidden'),false);
   toggle.click();assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(panel.classList.contains('hidden'),true);
   toggle.click();assert.equal(panel.classList.contains('hidden'),false);
   d.querySelector(width<1024?'#sidebarToggle':'#desktopSidebarToggle').click();
   d.querySelector(width<1024?'#sidebarToggle':'#desktopSidebarToggle').click();
   assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(panel.closest('#appSidebar'),d.querySelector('#appSidebar'));
  }finally{dom.window.close();}
 }
});
