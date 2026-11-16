const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(process.env.ACTIONS_SOURCE || path.join(__dirname, '../js/app.js'), 'utf8');
const lists = fs.readFileSync(process.env.LISTS_SOURCE || path.join(__dirname, '../js/list-pagination.js'), 'utf8');

function screen(role, access) {
 const calls=[];
 const context=vm.createContext({currentUser:{role},currentSettings:{language:'vi'},
  favoriteIds:new Set([1]),selectedDocumentIds:new Set([1]),activePage:'documents',bulkBusy:false,
  docs:[{id:1,name:'Report',access}],escapeHtml:v=>String(v??''),fileIcon:()=>'',ownerCell:()=>'',
  formatUpdated:v=>v,api:async(...args)=>calls.push(args),confirm:()=>true,
  showToast(){},loadDocuments:async()=>{},loadDashboard:async()=>{},loadCollection:async()=>{}});
 context.window=context;
 vm.runInContext(source.slice(source.indexOf('function row('),source.indexOf('function getToken(')),context);
 const single=source.indexOf('window.deleteDocument =');
 vm.runInContext(source.slice(single,source.indexOf('window.openEditModal',single)),context);
 const bulk=source.indexOf('window.bulkDocuments =');
 vm.runInContext(source.slice(bulk,source.indexOf('async function loadTrash()',bulk)),context);
 return {context,calls};
}

test('ADMIN and OWNER only see trash on Documents, never on collections or Dashboard',()=>{
 for(const [role,access] of [['ADMIN','ADMIN'],['USER','OWNER']]){
  const {context:c}=screen(role,access),doc=c.docs[0];
  assert.match(c.row(doc,true,true,'documents'),/deleteDocument\(1\)/);
  assert.match(c.row(doc,true,true,'documents'),/Chuyển vào thùng rác/);
  for(const page of ['recent','favorites','shared','dashboard'])
   assert.doesNotMatch(c.row(doc,true,true,page),/deleteDocument|delete-btn|openShareModal/);
 }
});

test('favorites remove the star; only shared documents keep permitted editing',()=>{
 const {context:c}=screen('ADMIN','ADMIN');
 assert.match(c.row(c.docs[0],true,true,'favorites'),/toggleFavorite\(1\).*Bỏ yêu thích/);
 for(const page of ['recent','favorites','dashboard'])
  assert.doesNotMatch(c.row(c.docs[0],true,true,page),/openEditModal/);
 assert.match(c.row({...c.docs[0],access:'EDIT'},true,true,'shared'),/openEditModal/);
 assert.doesNotMatch(c.row({...c.docs[0],access:'VIEW'},true,true,'shared'),/openEditModal|downloadDocument/);
});

test('single and bulk trash handlers send no requests outside Documents for either role',async()=>{
 for(const [role,access] of [['ADMIN','ADMIN'],['USER','OWNER']]){
  const {context:c,calls}=screen(role,access);
  for(const page of ['recent','favorites','shared','dashboard']){
   c.activePage=page;
   await c.deleteDocument(1);
   await c.bulkDocuments('delete');
   await c.bulkDocuments('share');
  }
  assert.equal(calls.length,0);
  c.activePage='documents';await c.deleteDocument(1);
  assert.equal(calls.length,1);assert.equal(calls[0][1].method,'DELETE');
 }
});

test('collection toolbar offers bulk download without delete or share',()=>{
 const block=lists.slice(lists.indexOf("if(key==='collection'){"),lists.indexOf("if(key==='sent'){"));
 assert.match(block,/collectionDownload/);
 assert.doesNotMatch(block,/collectionDelete|collectionShare|bulkDocuments\(\\'delete/);
});
