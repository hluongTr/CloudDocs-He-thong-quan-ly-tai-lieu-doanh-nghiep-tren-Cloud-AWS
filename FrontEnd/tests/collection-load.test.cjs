const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(process.env.REVIEW_APP_SOURCE || path.join(__dirname, '../js/app.js'), 'utf8');
const deferred = () => { let resolve,reject; const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject}; };
function screen() {
 let token='first';
 const nodes=new Map(),rendered=[];
 const ctx=vm.createContext({
  console:{error(){}},getToken:()=>token,collectionLoadSequence:0,activePage:'shared',
  docs:[],currentCollection:[],favoriteIds:new Set([7]),
  pageNames:{shared:['Shared','',''],recent:['Recent','',''],favorites:['Favorites','','']},
  document:{getElementById(id){if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',classList:{remove(){}}});return nodes.get(id);}},
  loadFavoriteIds:async()=>{},loadDocuments:async()=>{},normalize:v=>v,
  renderCollection:items=>rendered.push(items.map(x=>x.id)),favoriteDocuments:()=>[],
 });
 const start=source.indexOf('async function loadCollection(');
 vm.runInContext(source.slice(start,source.indexOf('// =========================',start)),ctx);
 return {ctx,rendered,setToken:v=>token=v};
}

test('a slower received-share response cannot replace the Recent collection',async()=>{
 const {ctx:c,rendered}=screen(),old=deferred();
 c.api=route=>route.endsWith('/shared')?old.promise:Promise.resolve([{id:2}]);
 const pending=c.loadCollection('shared');await new Promise(resolve=>setImmediate(resolve));
 c.activePage='recent';await c.loadCollection('recent');
 old.resolve([{id:1}]);await pending;
 assert.deepEqual(rendered,[[2]]);assert.equal(c.currentCollection[0].id,2);
});

test('latest refresh wins even when both requests target the same collection', {timeout:3000}, async()=>{
 const {ctx:c,rendered}=screen(),old=deferred();let count=0;
 c.api=()=>++count===1?old.promise:Promise.resolve([{id:2}]);
 const pending=c.loadCollection('shared');await new Promise(resolve=>setImmediate(resolve));
 await c.loadCollection('shared');old.resolve([{id:1}]);await pending;
 assert.deepEqual(rendered,[[2]]);
});

test('failed requests from an old login cannot clear the next account collection',async()=>{
 const {ctx:c,rendered,setToken}=screen(),old=deferred();c.api=()=>old.promise;
 const pending=c.loadCollection('shared');await new Promise(resolve=>setImmediate(resolve));
 setToken('second');c.currentCollection=[{id:8}];old.reject(Error('old session'));await pending;
 assert.equal(c.currentCollection[0].id,8);assert.equal(rendered.length,0);
});

test('leaving a collection prevents its pending response changing cached documents',async()=>{
 const {ctx:c,rendered}=screen(),old=deferred();c.api=()=>old.promise;
 const pending=c.loadCollection('shared');await new Promise(resolve=>setImmediate(resolve));
 c.activePage='documents';old.resolve([{id:1}]);await pending;
 assert.equal(c.docs.length,0);assert.equal(rendered.length,0);
});

test('stale favorite successes and errors preserve the current account favorites',async()=>{
 for(const fail of [false,true]){
  const {ctx:c,setToken}=screen(),old=deferred();c.api=()=>old.promise;
  const start=source.indexOf('async function loadFavoriteIds(');
  vm.runInContext(source.slice(start,source.indexOf('window.toggleFavorite',start)),c);
  const pending=c.loadFavoriteIds();setToken('second');c.favoriteIds=new Set([8]);
  fail?old.reject(Error('old session')):old.resolve([1]);await pending;
  assert.deepEqual([...c.favoriteIds],[8]);
 }
});
