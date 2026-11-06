const {test}=require('node:test');
const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../js/app.js'),'utf8');
function screen() {
  let token='session';const downloads=[],revoked=[],blobs=[],nodes=new Map(),toasts=[];
  const document={getElementById:id=>nodes.get(id)||null,createElement:()=>({style:{},click(){downloads.push(this.download);},remove(){nodes.delete(this.id);}}),body:{appendChild(node){if(node.id)nodes.set(node.id,node);}}};
  const ctx=vm.createContext({document,console:{log(){},error(){}},Blob,URL:{createObjectURL(blob){blobs.push(blob);return 'blob:'+blobs.length;},revokeObjectURL(url){revoked.push(url);}},setTimeout:callback=>callback(),getToken:()=>token,api:async()=>({downloadUrl:'https://files.example/test'}),fetch:async()=>({ok:true,headers:{get:()=> 'application/octet-stream'},blob:async()=>new Blob([])}),docs:[{id:1,name:'empty.txt',fileName:'empty.txt',access:'OWNER'}],showToast:message=>toasts.push(message),escapeHtml:value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')});
  ctx.window=ctx;
  vm.runInContext(source.slice(source.indexOf('function requireSession('),source.indexOf('function setToken(')),ctx);
  vm.runInContext(source.slice(source.indexOf('window.downloadDocument ='),source.indexOf('window.deleteDocument =')),ctx);
  vm.runInContext(source.slice(source.indexOf('window.previewDocument ='),source.indexOf('// Selection is scoped')),ctx);
  vm.runInContext(source.slice(source.indexOf('function fileIcon('),source.indexOf('function initials(')),ctx);
  return {ctx,downloads,revoked,blobs,nodes,toasts,setToken:value=>token=value};
}
test('valid zero-byte files download successfully',async()=>{const s=screen();assert.equal(await s.ctx.downloadDocument(1),true);assert.deepEqual(s.downloads,['empty.txt']);assert.equal(s.revoked.length,1);});
test('a failed S3 download never produces a local download',async()=>{const s=screen();s.ctx.fetch=async()=>({ok:false,status:403});assert.equal(await s.ctx.downloadDocument(1),false);assert.equal(s.downloads.length,0);});
test('switching account while file is loading prevents the late download',async()=>{const s=screen();s.ctx.fetch=async()=>({ok:true,blob:async()=>{s.setToken('different');return new Blob(['secret']);}});assert.equal(await s.ctx.downloadDocument(1),false);assert.equal(s.downloads.length,0);});
test('empty text file preview works and file contents are escaped',async()=>{const s=screen();await s.ctx.previewDocument(1);assert.ok(s.nodes.get('previewModal').innerHTML.includes('<pre'));s.ctx.fetch=async()=>({ok:true,headers:{get:()=> 'text/plain'},blob:async()=>new Blob(['<script>alert(1)</script>'])});await s.ctx.previewDocument(1);assert.ok(s.nodes.get('previewModal').innerHTML.includes('&lt;script&gt;'));});
test('PDF preview infers MIME and revokes URLs when replaced or closed',async()=>{const s=screen();s.ctx.docs[0].fileName='file.pdf';await s.ctx.previewDocument(1);assert.equal(s.blobs[0].type,'application/pdf');await s.ctx.previewDocument(1);assert.deepEqual(s.revoked,['blob:1']);s.ctx.closePreviewModal();assert.deepEqual(s.revoked,['blob:1','blob:2']);});
test('late preview response cannot appear after switching account',async()=>{const s=screen();s.ctx.fetch=async()=>({ok:true,headers:{get:()=> 'text/plain'},blob:async()=>{s.setToken('different');return new Blob(['private']);}});await s.ctx.previewDocument(1);assert.equal(s.nodes.size,0);});
test('legacy malformed file types are escaped in table icons',()=>{const s=screen();assert.ok(!s.ctx.fileIcon('<img src=x onerror="alert(1)">').includes('<img'));});
const server=require('../../scripts/serve-frontend.cjs');
test('frontend server matches the configured callback without exposing the project root',()=>{const c=server.configuration();assert.ok(['localhost','127.0.0.1'].includes(c.hostname));const mount=path.posix.dirname(c.pathname);assert.ok(server.publicFile(c.pathname,mount).endsWith(path.join('FrontEnd','index.html')));assert.equal(server.publicFile('/Back_end/pom.xml',mount),null);});
test('static server refuses traversal, hidden files and malformed paths',()=>{for(const value of ['/FrontEnd/../README.md','/FrontEnd/%2e%2e/index.html','/FrontEnd/.env','/FrontEnd/js%5capp.js','/FrontEnd/a%00.js'])assert.equal(server.publicFile(value,'/FrontEnd'),null);assert.throws(()=>server.publicFile('/FrontEnd/%ZZ','/FrontEnd'));});
test('static server supports assets under the current callback mount',()=>{assert.ok(server.publicFile('/FrontEnd/js/app.js?v=1','/FrontEnd').endsWith(path.join('FrontEnd','js','app.js')));assert.equal(server.publicFile('/FrontEnd/tests/review.test.cjs','/FrontEnd'),null);});

test('view-only users do not see a forbidden download button for unsupported previews',async()=>{const s=screen();s.ctx.docs[0].fileName='report.docx';s.ctx.docs[0].access='VIEW';await s.ctx.previewDocument(1);assert.ok(!s.nodes.get('previewModal').innerHTML.includes('onclick="downloadDocument('));});
