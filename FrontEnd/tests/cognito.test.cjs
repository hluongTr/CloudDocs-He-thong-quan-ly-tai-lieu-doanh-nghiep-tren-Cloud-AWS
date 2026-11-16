const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const readProjectFile = file => {
  const stage = process.env.AUTH_TEST_STAGE;
  if (stage && file === 'FrontEnd/js/app.js') return fs.readFileSync(require('node:path').join(stage, 'app.js'), 'utf8');
  if (stage && file === 'FrontEnd/index.html') return fs.readFileSync(require('node:path').join(stage, 'index.html'), 'utf8');
  return fs.readFileSync(file, 'utf8');
};
const {webcrypto} = require('node:crypto');
function setup() {
  const storage = () => { const m = new Map(); return {getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,v),removeItem:k=>m.delete(k)}; };
  const localStorage=storage(),sessionStorage=storage();
  const location={href:'http://localhost:5500/',origin:'http://localhost:5500',assign(url){this.assigned=url;}};
  let calls=0, body;
  const window={COGNITO_CONFIG:{domain:'https://example.auth.ap-southeast-1.amazoncognito.com',clientId:'public-client',redirectUri:location.href,logoutUri:location.href}};
  const ctx={window,location,localStorage,sessionStorage,crypto:webcrypto,TextEncoder,Uint8Array,URL,URLSearchParams,btoa,Date,
    history:{replaceState(a,b,url){location.cleaned=url;}},fetch:async(url,opts)=>{calls++;body=opts.body;return {ok:true,json:async()=>({access_token:'access-only',id_token:'never-use',token_type:'Bearer',expires_in:3600})};}};
  vm.runInNewContext(fs.readFileSync('FrontEnd/js/cognito-auth.js','utf8'),ctx);
  return {...ctx,auth:window.CognitoAuth,calls:()=>calls,body:()=>body};
}
test('landing screen contains one login action and no local form',()=>{
  const html=readProjectFile('FrontEnd/index.html').split('<div class="app-shell')[0];
  assert.equal((html.match(/<button/g)||[]).length,1); assert.ok(html.includes('>Đăng nhập</button>'));
  assert.ok(!/<input|<form|Đăng ký/.test(html));
  assert.ok(!/<h2>Đăng nhập<\/h2>/.test(html));
  assert.ok(html.includes('Hệ thống quản lý<span>tài liệu doanh nghiệp</span>'));
});
test('no automatic redirect; secure state, S256 challenge and access token exchange',async()=>{
  const c=setup();assert.equal(c.location.assigned,undefined);await c.auth.login();
  const url=new URL(c.location.assigned);const t=JSON.parse(c.sessionStorage.getItem('clouddocs_cognito_transaction'));
  assert.equal(url.searchParams.get('code_challenge_method'),'S256');assert.equal(t.verifier.length,43);
  const digest=Buffer.from(await webcrypto.subtle.digest('SHA-256',new TextEncoder().encode(t.verifier))).toString('base64url');
  assert.equal(url.searchParams.get('code_challenge'),digest);
  c.location.href=`http://localhost:5500/?code=valid&state=${t.state}&keep=yes`;
  assert.equal(await c.auth.callback(),true);assert.equal(c.auth.token(),'access-only');
  assert.equal(c.body().get('code_verifier'),t.verifier);assert.equal(c.location.cleaned,'/?keep=yes');
  await assert.rejects(c.auth.callback());assert.equal(c.calls(),1);assert.equal(c.auth.token(),null);
});
for (const failure of ['wrong-state','missing-state','expired','future','missing-code','duplicate-code','aws-error']) {
  test(`callback rejects ${failure}, consumes transaction, clears stale session and URL`,async()=>{
    const c=setup();await c.auth.login();
    const t=JSON.parse(c.sessionStorage.getItem('clouddocs_cognito_transaction'));
    if(failure==='expired')t.createdAt-=601000;if(failure==='future')t.createdAt+=100000;
    c.sessionStorage.setItem('clouddocs_cognito_transaction',JSON.stringify(t));
    c.localStorage.setItem('clouddocs_cognito_session',JSON.stringify({accessToken:'old',expiresAt:Date.now()+100000}));
    const q=new URLSearchParams({state:t.state,code:'code'});
    if(failure==='wrong-state')q.set('state','bad');if(failure==='missing-state')q.delete('state');
    if(failure==='missing-code')q.delete('code');if(failure==='duplicate-code')q.append('code','second');
    if(failure==='aws-error')q.set('error','access_denied');
    c.location.href='http://localhost:5500/?'+q;await assert.rejects(c.auth.callback());
    assert.equal(c.calls(),0);assert.equal(c.auth.token(),null);assert.equal(c.sessionStorage.getItem('clouddocs_cognito_transaction'),null);
    assert.equal(c.location.cleaned,'/');
  });
}
test('logout uses Cognito endpoint and only removes owned session keys',()=>{
  const c=setup();c.localStorage.setItem('unrelated','keep');c.sessionStorage.setItem('unrelated','keep');
  c.auth.logout();const url=new URL(c.location.assigned);assert.equal(url.pathname,'/logout');
  assert.equal(url.searchParams.get('logout_uri'),'http://localhost:5500/');
  assert.equal(c.localStorage.getItem('unrelated'),'keep');assert.equal(c.sessionStorage.getItem('unrelated'),'keep');
});
test('misconfigured callback is rejected before redirect',async()=>{
  const c=setup();c.window.COGNITO_CONFIG.redirectUri='http://localhost:9999/';await assert.rejects(c.auth.login());
  assert.equal(c.location.assigned,undefined);
});
test('deployed configuration generates the expected Cognito authorization request',async()=>{
  const c=setup();
  vm.runInNewContext(fs.readFileSync('FrontEnd/js/cognito-config.js','utf8'),{window:c.window});
  // Test the deployed configuration from its configured website origin.
  c.location.origin=new URL(c.window.COGNITO_CONFIG.redirectUri).origin;
  c.location.href=c.window.COGNITO_CONFIG.redirectUri;
  await c.auth.login();
  const url=new URL(c.location.assigned);
  assert.equal(url.origin,c.window.COGNITO_CONFIG.domain);
  assert.equal(url.pathname,'/oauth2/authorize');
  assert.equal(url.searchParams.get('client_id'),c.window.COGNITO_CONFIG.clientId);
  assert.equal(url.searchParams.get('redirect_uri'),c.window.COGNITO_CONFIG.redirectUri);
  assert.equal(url.searchParams.get('response_type'),'code');
  assert.equal(url.searchParams.get('scope'),'openid');
  assert.equal(url.searchParams.get('code_challenge_method'),'S256');
  assert.ok(!url.searchParams.has('client_secret'));
});
function application(authOverrides = {}) {
  const html=readProjectFile('FrontEnd/index.html');
  const node=()=>({style:{},dataset:{},childNodes:[],classList:{add(){this.hidden=true;},remove(){this.hidden=false;},toggle(name,hidden){this.hidden=hidden;}},reset(){},replaceChildren(){this.innerHTML="";this.textContent="";},addEventListener(){},setAttribute(){},getAttribute(){return null;}});
  const elements=new Map([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],node()]));
  const selectors=new Map();
  const document={getElementById:id=>elements.get(id)??null,querySelectorAll:()=>[],querySelector:selector=>{if(!selectors.has(selector))selectors.set(selector,node());return selectors.get(selector);},documentElement:node(),body:node(),addEventListener(){}};
  const auth={callback:async()=>false,token:()=>null,clear(){},login:async()=>{},...authOverrides};
  const listeners={};
  const context={document,window:{addEventListener(name,handler){listeners[name]=handler;}},CognitoAuth:auth,localStorage:{getItem:()=>null},MutationObserver:class{observe(){}},Node:{TEXT_NODE:3,ELEMENT_NODE:1},clearInterval(){},setTimeout,clearTimeout,console};
  vm.createContext(context);
  vm.runInContext(readProjectFile('FrontEnd/js/app.js'),context);
  return {elements,auth,listeners,context};
}
test('returning from Cognito restores the login button instead of leaving a spinner',async()=>{
  const app=application();await new Promise(setImmediate);
  const button=app.elements.get('cognitoLogin');button.disabled=true;button.textContent='Đang chuyển…';
  app.listeners.pageshow();
  assert.equal(button.disabled,false);assert.equal(button.textContent,'Đăng nhập');
});
test('full app startup binds existing DOM and does not automatically log in',async()=>{
  let logins=0;const app=application({login:async()=>{logins++;}});await new Promise(setImmediate);
  assert.equal(logins,0);assert.equal(typeof app.elements.get('cognitoLogin').onclick,'function');
});
test('login button prevents double clicks and recovers with a visible connection error',async()=>{
  let reject,logins=0;
  const app=application({login:()=>{logins++;return new Promise((resolve,r)=>{reject=r;});}});
  await new Promise(setImmediate);
  const button=app.elements.get('cognitoLogin');const first=button.onclick();assert.equal(button.disabled,true);
  await button.onclick();assert.equal(logins,1);reject(new Error('Connection failed'));await first;
  assert.equal(button.disabled,false);assert.equal(button.textContent,'Đăng nhập');assert.equal(app.elements.get('loginError').textContent,'Connection failed');
});
test('callback error is shown without restoring an older token',async()=>{
  let tokenReads=0;const app=application({callback:async()=>{throw new Error('Invalid state');},token:()=>{tokenReads++;return 'stale';}});
  await new Promise(setImmediate);assert.equal(tokenReads,0);assert.equal(app.elements.get('loginError').textContent,'Invalid state');
});
test('trusted /me name is shared by greeting, sidebar and avatars; placeholders are hidden',async()=>{
  const app=application();await new Promise(setImmediate);const c=app.context;
  vm.runInContext('currentUser={id:22,fullName:"Hán Quang Sang",email:"old@identity.invalid",role:"ADMIN",status:"ACTIVE"};updateUserUI();renderProfile();',c);
  assert.equal(app.elements.get('sidebarUserName').textContent,'Hán Quang Sang');
  assert.equal(app.elements.get('topAvatar').textContent,'QS');
  assert.ok(c.document.querySelector('#dashboardPage h1').innerHTML.includes('Hán Quang Sang'));
  assert.equal(app.elements.get('profileFullName').value,'Hán Quang Sang');assert.equal(app.elements.get('profileEmail').value,'');
  vm.runInContext('currentUser.fullName="Người dùng CloudDocs";updateUserUI();',c);
  assert.equal(app.elements.get('sidebarUserName').textContent,'Chưa cập nhật');assert.equal(app.elements.get('topAvatar').textContent,'CD');
});
test('leaving a session clears user-specific dashboard, admin, notifications and document data',async()=>{
  const app=application();await new Promise(setImmediate);
  for(const id of ['userTable','documentTable','recentTable','dashboardActivity','notificationList','adminStorageRows'])app.elements.get(id).innerHTML='old private data';
  app.context.showAuth();
  for(const id of ['userTable','documentTable','recentTable','dashboardActivity','notificationList','adminStorageRows'])assert.equal(app.elements.get(id).innerHTML,'');
  assert.equal(app.elements.get('sidebarUserName').textContent,'—');
});
test('late API response from a previous token is rejected',async()=>{
  let token=null;const app=application({token:()=>token});await new Promise(setImmediate);
  let resolve;app.context.fetch=()=>new Promise(r=>{resolve=r;});token='old';
  const request=app.context.api('/documents');token='new';resolve({ok:true,text:async()=>JSON.stringify([{id:1}])});
  await assert.rejects(request,/Phiên đăng nhập đã thay đổi/);
});
test('logout preserves backend audit call before Cognito redirect',async()=>{
  const order=[];const app=application({logout:()=>order.push('cognito')});await new Promise(setImmediate);
  app.context.fetch=async()=>{order.push('backend');return {ok:true,text:async()=>'{}'}};
  await app.elements.get('logoutButton').onclick();assert.deepEqual(order,['backend','cognito']);
});

test('session bootstrap and pageshow cannot enable sign-in while a callback is pending',async()=>{
  let finish,logins=0;
  const app=application({callback:()=>new Promise(resolve=>finish=resolve),login:async()=>logins++});
  const button=app.elements.get('cognitoLogin');
  assert.equal(button.disabled,true);assert.match(button.textContent,/kiểm tra phiên/);
  app.listeners.pageshow();await button.onclick();assert.equal(logins,0);assert.equal(button.disabled,true);
  finish(false);await new Promise(setImmediate);assert.equal(button.disabled,false);
});

test('sign-out clears local state immediately and blocks sign-in and duplicate logout',async()=>{
  let token=null,finish,logins=0,logouts=0,clears=0;
  const app=application({token:()=>token,clear:()=>{token=null;clears++;},login:async()=>logins++,logout:()=>logouts++});
  await new Promise(setImmediate);token='old';
  app.context.fetch=()=>new Promise(resolve=>finish=resolve);
  const pending=app.elements.get('logoutButton').onclick();
  assert.equal(token,null);assert.ok(clears>0);
  assert.equal(app.elements.get('cognitoLogin').disabled,true);
  app.listeners.pageshow();await app.elements.get('cognitoLogin').onclick();
  await app.elements.get('logoutButton').onclick();assert.equal(logins,0);assert.equal(logouts,0);
  finish({ok:true,text:async()=>'{}'});await pending;assert.equal(logouts,1);
});

test('unresponsive audit request cannot indefinitely block Cognito logout',async()=>{
  let timeout,logouts=0;
  const app=application({logout:()=>logouts++});await new Promise(setImmediate);
  app.context.fetch=()=>new Promise(()=>{});
  app.context.setTimeout=(fn,ms)=>{assert.equal(ms,1500);timeout=fn;return 1;};
  const pending=app.elements.get('logoutButton').onclick();
  timeout();await pending;assert.equal(logouts,1);
});

test('old bootstrap profile response cannot reopen the app after logout',async()=>{
  let token='old',finishCallback,finishProfile;
  const app=application({callback:()=>new Promise(resolve=>finishCallback=resolve),token:()=>token,clear:()=>{token=null;},logout(){}});
  app.context.fetch=url=>url.endsWith('/me')?new Promise(resolve=>finishProfile=resolve):Promise.resolve({ok:true,text:async()=>'{}'});
  finishCallback(false);await new Promise(setImmediate);
  await app.elements.get('logoutButton').onclick();
  finishProfile({ok:true,text:async()=>JSON.stringify({id:1,fullName:'Old user',role:'USER'})});
  await new Promise(setImmediate);
  assert.equal(app.elements.get('appShell').classList.hidden,true);
  assert.equal(app.elements.get('sidebarUserName').textContent,'—');
});

test('browser Back restoration rechecks the session before enabling sign-in',async()=>{
  const app=application();await new Promise(setImmediate);
  let finish;
  app.auth.callback=()=>new Promise(resolve=>finish=resolve);
  app.listeners.pageshow({persisted:true});
  assert.equal(app.elements.get('cognitoLogin').disabled,true);
  finish(false);await new Promise(setImmediate);assert.equal(app.elements.get('cognitoLogin').disabled,false);
});
test('missing display name can be entered and saved without email; all name locations update',async()=>{
  const app=application();await new Promise(setImmediate);const c=app.context;
  vm.runInContext('currentUser={id:22,fullName:"Chưa cập nhật",email:null,role:"ADMIN",status:"ACTIVE"};updateUserUI();',c);
  assert.equal(app.elements.get('displayNameForm').classList.hidden,false);
  app.elements.get('displayNameInput').value=' Hán Quang Sang ';
  let body;
  c.fetch=async(url,options)=>{body=JSON.parse(options.body);return {ok:true,text:async()=>JSON.stringify({id:22,fullName:body.fullName,email:null,role:'ADMIN',status:'ACTIVE'})}};
  await app.elements.get('displayNameForm').onsubmit({preventDefault(){}});
  assert.deepEqual(body,{fullName:'Hán Quang Sang'});
  assert.equal(app.elements.get('sidebarUserName').textContent,'Hán Quang Sang');
  assert.equal(app.elements.get('topAvatar').textContent,'QS');
  assert.ok(c.document.querySelector('#dashboardPage h1').innerHTML.includes('Hán Quang Sang'));
  assert.equal(app.elements.get('profileFullName').value,'Hán Quang Sang');
  assert.equal(app.elements.get('displayNameForm').classList.hidden,true);
});
test('failed name save retains prompt and shows error without changing current name',async()=>{
  const app=application();await new Promise(setImmediate);const c=app.context;
  vm.runInContext('currentUser={id:22,fullName:"Chưa cập nhật",role:"USER",status:"ACTIVE"};updateUserUI();',c);
  app.elements.get('displayNameInput').value='Alice';c.fetch=async()=>{throw new Error('offline')};
  await app.elements.get('displayNameForm').onsubmit({preventDefault(){}});
  assert.equal(app.elements.get('displayNameMessage').textContent,'offline');
  assert.equal(app.elements.get('sidebarUserName').textContent,'Chưa cập nhật');
  assert.equal(app.elements.get('saveDisplayName').disabled,false);
});
