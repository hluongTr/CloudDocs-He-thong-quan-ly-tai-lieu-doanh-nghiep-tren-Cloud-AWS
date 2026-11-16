const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(process.env.NAV_APP_SOURCE||path.join(__dirname,'../js/app.js'),'utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function screen(href='http://127.0.0.1:5500/FrontEnd/index.html') {
 const names=['dashboard','documents','favorites','shared','recent','users','departments','profile','settings','activity','trash','storage','tasks','sent'];
 const node=()=>({textContent:'',dataset:{},classList:{values:new Set(),add(v){this.values.add(v);},remove(v){this.values.delete(v);},toggle(v,on){on?this.values.add(v):this.values.delete(v);}},scrollTo(){}});
 const elements=new Map(),menus=names.map(page=>({...node(),dataset:{page}}));
 const pages=names.map(page=>{const el=node();elements.set(page+'Page',el);return el;});
 elements.set('collectionPage',node());
 const listeners={},history=[href];let index=0,token='token',authShows=0;
 const window={location:new URL(href),addEventListener:(name,fn)=>listeners[name]=fn};
 window.history={
  pushState(state,title,url){history.splice(index+1);history.push(new URL(url,window.location).href);index++;window.location=new URL(history[index]);},
  replaceState(state,title,url){history[index]=new URL(url,window.location).href;window.location=new URL(history[index]);},
 };
 const context=vm.createContext({URL,window,currentUser:{role:'ADMIN'},authPhase:'ready',getToken:()=>token,activePage:'dashboard',
  pageNames:Object.fromEntries(names.map(page=>[page,[page,'','']])),selectedDocumentIds:new Set(),selectedTrashIds:new Set(),
  document:{getElementById(id){if(!elements.has(id))elements.set(id,node());return elements.get(id);},querySelector:()=>node(),querySelectorAll:selector=>selector==='.page'?pages:selector==='.nav-item'?menus:[]},
  updateBulkToolbar(){},filterDocs(){},showToast(){},showAuth(){authShows++;},renderProfile(){},renderSettings(){},
  loadDashboard:async()=>{},loadDocuments:async()=>{},loadCollection:async()=>{},loadUsers:async()=>{},loadActivityLogs:async()=>{},loadTrash:async()=>{},
 });
 for(const name of ['loadStorageManagement','loadDepartmentManagement','loadSentShares','loadWorkTasks'])window[name]=async()=>{};
 const start=source.indexOf('function browserPage()');
 vm.runInContext(source.slice(start,source.indexOf('document.querySelectorAll("[data-page]")',start)),context);
 return {c:context,window,history,menus,authShows:()=>authShows,setToken:v=>token=v,
  async travel(delta){index+=delta;window.location=new URL(history[index]);listeners.popstate();await tick();},
  async address(hash){window.location.hash=hash;listeners.popstate();await tick();},
 };
}

test('Back and Forward traverse sections without adding history entries',async()=>{
 const s=screen(),c=s.c;
 await c.showPage('dashboard',{historyMode:'replace'});await c.showPage('documents');await c.showPage('favorites');
 assert.equal(s.history.length,3);await s.travel(-1);assert.equal(c.activePage,'documents');
 await s.travel(-1);assert.equal(c.activePage,'dashboard');await s.travel(1);assert.equal(c.activePage,'documents');
 assert.equal(s.history.length,3);
});

test('same-section clicks do not duplicate history and a new branch replaces Forward history',async()=>{
 const s=screen(),c=s.c;await c.showPage('documents');await c.showPage('documents');
 assert.equal(s.history.length,2);await c.showPage('favorites');await s.travel(-1);await c.showPage('profile');
 assert.equal(s.history.length,3);assert.ok(s.history.at(-1).endsWith('#/profile'));
});

test('direct URLs restore the section on both local and deployed paths',async()=>{
 for(const href of ['http://127.0.0.1:5500/FrontEnd/index.html#/favorites','https://example.com/index.html#/favorites']){
  const s=screen(href);await s.c.showPage(s.c.browserPage(),{historyMode:'replace'});
  assert.equal(s.c.activePage,'favorites');assert.equal(s.history.length,1);
 }
 assert.match(source,/showPage\(browserPage\(\), \{ historyMode: "replace" \}\)/);
});

test('invalid and admin-only history routes safely fall back for ordinary users',async()=>{
 const s=screen();s.c.currentUser={role:'USER'};
 for(const page of ['users','departments','unknown','__proto__']){
  await s.address('#/'+page);assert.equal(s.c.activePage,'dashboard');assert.equal(s.window.location.hash,'#/dashboard');
 }
 await s.c.showPage('users');assert.equal(s.c.activePage,'dashboard');
});

test('history cannot reopen protected sections after logout',async()=>{
 const s=screen();await s.c.showPage('documents');s.setToken(null);s.c.currentUser=null;
 await s.travel(-1);assert.equal(s.authShows(),1);
});

test('a slow previous navigation cannot overwrite the latest active menu',async()=>{
 const s=screen();let done;s.c.loadDashboard=()=>new Promise(resolve=>done=resolve);
 const old=s.c.showPage('dashboard');await s.c.showPage('profile');done();await old;
 const active=s.menus.filter(n=>n.classList.values.has('active')).map(n=>n.dataset.page);
 assert.deepEqual(active,['profile']);
});
