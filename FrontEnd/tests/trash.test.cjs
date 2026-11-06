const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'); const vm=require('node:vm');
const source=fs.readFileSync('FrontEnd/js/app.js','utf8');
function screen() {
  const nodes=new Map(); const checks=[];
  const document={getElementById(id){if(!nodes.has(id))nodes.set(id,{textContent:'',innerHTML:'',classList:{toggle(){}},disabled:false});return nodes.get(id);},querySelectorAll(){return checks;}};
  const calls=[],toasts=[];
  const ctx=vm.createContext({window:{},document,selectedDocumentIds:new Set(),selectedTrashIds:new Set(),trashDocuments:[],bulkShareIds:[],bulkBusy:false,docs:[],currentUser:{role:'ADMIN'},currentSettings:{badge:true},notifications:[],localStorage:{getItem(){return '0';}},confirm:()=>true,getToken:()=> 'token',normalize:d=>d,escapeHtml:v=>String(v??'').replaceAll('<','&lt;').replaceAll('>','&gt;'),formatUpdated:v=>v,notificationSeenKey:()=> 'seen',showToast:m=>toasts.push(m),api:async(path,options)=>{calls.push({path,options});return [];},filterDocs(){},loadDocuments:async()=>{},loadNotifications:async()=>{},downloadDocument:async()=>true,loadShareModal:async(id,ids)=>{calls.push({id,ids});}});
  vm.runInContext(source.slice(source.indexOf('// Selection is scoped')),ctx);
  Object.assign(ctx,ctx.window);
  const start=source.indexOf('function renderNotifications()');
  vm.runInContext(source.slice(start,source.indexOf('async function loadNotifications()',start)),ctx);
  return {ctx,document,checks,calls,toasts};
}
test('select-all covers visible rows and supports indeterminate state',()=>{
  const {ctx,document,checks}=screen();ctx.docs=[{id:1,access:'OWNER'},{id:2,access:'VIEW'}];
  checks.push({dataset:{documentId:'1'}},{dataset:{documentId:'2'}});
  ctx.selectDocument(1,true);assert.equal(document.getElementById('selectAllDocuments').indeterminate,true);
  ctx.selectAllDocuments(true);assert.equal(ctx.selectedDocumentIds.size,2);
  assert.equal(document.getElementById('bulkDelete').disabled,true);
  assert.equal(document.getElementById('bulkDownload').disabled,true);
  ctx.selectAllDocuments(false);assert.equal(ctx.selectedDocumentIds.size,0);
});
test('batch delete preserves failed selections and reports partial failure',async()=>{
  const {ctx,calls,toasts}=screen();ctx.docs=[{id:1,access:'OWNER'},{id:2,access:'OWNER'}];
  ctx.selectedDocumentIds=new Set([1,2]);ctx.api=async(path,options)=>{calls.push({path,options});if(path.endsWith('/2'))throw Error('denied');};
  await ctx.bulkDocuments('delete');assert.deepEqual(calls.map(c=>c.path),['/documents/1','/documents/2']);
  assert.deepEqual([...ctx.selectedDocumentIds],[2]);assert.equal(ctx.bulkBusy,false);assert.ok(toasts.at(-1).includes("1/2"));
});
test('multi-share passes all selected document IDs to one recipient dialog',async()=>{
  const {ctx,calls}=screen();ctx.docs=[{id:1,access:'ADMIN'},{id:2,access:'ADMIN'}];ctx.selectedDocumentIds=new Set([1,2]);
  await ctx.bulkDocuments('share');assert.deepEqual([...calls[0].ids],[1,2]);
});
test('multiple downloads report failed files without aborting later files',async()=>{
  const {ctx,toasts}=screen();const ids=[];ctx.docs=[1,2,3].map(id=>({id,access:'DOWNLOAD'}));ctx.selectedDocumentIds=new Set([1,2,3]);
  ctx.downloadDocument=async id=>{ids.push(id);return id!==2;};await ctx.bulkDocuments('download');
  assert.deepEqual(ids,[1,2,3]);assert.deepEqual([...ctx.selectedDocumentIds],[2]);assert.ok(toasts.at(-1).includes("2/3"));
});
test('trash supports selecting individual rows and select-all; titles are escaped',()=>{
  const {ctx,document}=screen();ctx.trashDocuments=[{id:1,name:'<script>',size:'1 B',updated:'today'},{id:2,name:'Report',size:'2 B',updated:'today'}];
  ctx.selectAllTrash(true);assert.equal(ctx.selectedTrashIds.size,2);assert.equal(document.getElementById('selectAllTrash').checked,true);
  assert.match(document.getElementById('trashTable').innerHTML,/&lt;script&gt;/);
  ctx.selectTrash(1,false);assert.equal(document.getElementById('selectAllTrash').indeterminate,true);
});
test('canceling permanent delete sends no API requests',async()=>{
  const {ctx,calls}=screen();ctx.confirm=()=>false;await ctx.trashAction('purge',[1,2]);assert.equal(calls.length,0);
});
test('notification delete control is admin-only and independent of detail button',async()=>{
  const {ctx,document,calls}=screen();ctx.notifications=[{id:1,title:'<wrong>',message:'message',createdAt:'today'}];
  ctx.renderNotifications();let html=document.getElementById('notificationList').innerHTML;
  assert.match(html,/notification-delete/);assert.match(html,/&lt;wrong&gt;/);assert.equal((html.match(/<button/g)||[]).length,2);
  ctx.currentUser.role='USER';ctx.renderNotifications();assert.doesNotMatch(document.getElementById('notificationList').innerHTML,/notification-delete/);
  await ctx.deleteNotification(1);assert.equal(calls.length,0);
  ctx.currentUser.role='ADMIN';await ctx.deleteNotification(1);assert.equal(calls[0].path,'/notifications/1');assert.equal(calls[0].options.method,'DELETE');
});

test('batch stops sending requests when the login session changes',async()=>{
  const {ctx}=screen();let token='first';const touched=[];ctx.getToken=()=>token;
  const result=await ctx.runDocumentBatch([1,2,3],async id=>{touched.push(id);token='second';},'Batch');
  assert.deepEqual(touched,[1]);assert.deepEqual([...result.failed],[2,3]);
});
test('audit renders the profile name instead of Cognito identifiers',()=>{
  const {ctx}=screen();ctx.currentSettings.language='en';
  ctx.activityAction=action=>action;
  const start=source.indexOf('function activityDetails(');
  vm.runInContext(source.slice(start,source.indexOf('function activityDocument(',start)),ctx);
  assert.equal(ctx.activityDetails({action:'RESTORE',username:'opaque-id',fullName:'Trần Hải Lương',documentName:'Report'}),'Trần Hải Lương restored Report');
});
