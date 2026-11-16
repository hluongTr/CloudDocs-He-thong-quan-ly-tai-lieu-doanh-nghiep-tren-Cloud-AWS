/* Admins can browse, rename and delete all folders; upload destinations remain owner-only. */
const folderState = {items: [], current: null, token: null, sequence: 0, busy: false};
const folderText = (vi, en) => currentSettings.language === 'en' ? en : vi;
const canOrganizeFolder = f => f.canOrganize ?? f.canManage !== false;
const folderArea = document.createElement('section');
folderArea.className = 'folder-area';
folderArea.innerHTML = '<div class="folder-toolbar"><nav id="folderBreadcrumb" aria-label="Đường dẫn thư mục"></nav><button type="button" class="secondary-btn" id="createFolder">+ Thư mục</button></div><p id="folderMessage" class="muted" role="status"></p>';
document.querySelector('#documentsPage .bulk-toolbar').before(folderArea);
const moveButton = document.createElement('button');
moveButton.type = 'button'; moveButton.className = 'secondary-btn'; moveButton.id = 'moveToFolder'; moveButton.textContent = 'Chuyển vào thư mục';
document.querySelector('#documentsPage .bulk-toolbar').append(moveButton);

const folderModal = document.createElement('div');
folderModal.className = 'modal-backdrop hidden';
folderModal.innerHTML = '<form class="modal folder-modal" role="dialog" aria-modal="true" aria-labelledby="folderDialogTitle"><div class="modal-head"><h2 id="folderDialogTitle"></h2><button class="close" type="button" data-folder-cancel aria-label="Đóng">×</button></div><div class="folder-form"><label id="folderNameLabel">Tên thư mục<input id="folderName" maxlength="120" autocomplete="off"></label><label id="folderDestinationLabel">Thư mục đích<select id="folderDestination"></select></label><p id="folderDialogMessage"></p><p id="folderError" role="alert" class="work-error"></p></div><div class="modal-actions"><button type="button" class="secondary-btn" data-folder-cancel>Hủy</button><button type="submit" class="primary-btn" id="folderConfirm">Lưu</button></div></form>';
document.body.append(folderModal);
let folderAction = null, folderReturnFocus = null;
function folderPath(id) {
 const names=[], seen=new Set();
 while(id != null && !seen.has(id)) { seen.add(id); const f=folderState.items.find(x=>x.id===id); if(!f)break; names.unshift(f.name);id=f.parentId; }
 return names.join(' / ');
}
function folderOptions() {
 return '<option value="">'+folderText('Tài liệu (gốc)','Documents (root)')+'</option>'+folderState.items.filter(canOrganizeFolder).map(f=>'<option value="'+Number(f.id)+'">'+escapeHtml(folderPath(f.id))+'</option>').join('');
}
window.resetFolders = function() {
 folderState.items=[];folderState.current=null;folderState.token=null;folderState.sequence++;
 folderModal.classList.add('hidden'); folderAction=null;
 document.getElementById('folderBreadcrumb').replaceChildren();
 document.getElementById('folderMessage').textContent='';
};
window.loadFolders = async function() {
 const token=getToken(), sequence=++folderState.sequence;
 if(folderState.token!==token) {folderState.items=[];folderState.current=null;folderState.token=token;}
 try {
  const data=await api('/folders');requireSession(token);if(sequence!==folderState.sequence)return;
  folderState.items=Array.isArray(data)?data:[];
  if(!folderState.items.some(f=>f.id===folderState.current))folderState.current=null;
  document.getElementById('folderMessage').textContent='';renderFolders();
 } catch(error) { if(token===getToken() && sequence===folderState.sequence){folderState.items=[];folderState.current=null;renderFolders();document.getElementById('folderMessage').textContent=error.message;} throw error; }
};
window.documentInCurrentFolder = function(doc, query) {
 if(query)return true; // Search across the entire visible library.
 const known=folderState.items.some(f=>f.id===doc.folderId);
 return (known?doc.folderId:null)===folderState.current;
};
window.getCurrentFolderId = () => folderState.current;
window.folderTableRows = function(query = '', department = '', asItems = false) {
 const q = query.trim().toLowerCase();
 const children = folderState.items.filter(f =>
  (q ? `${folderPath(f.id)} ${f.ownerName||''} ${f.ownerDepartment||''}`.toLowerCase().includes(q) : f.parentId === folderState.current)
  && (!department || (f.ownerDepartment||'').localeCompare(department,'vi',{sensitivity:'accent'}) === 0)
 ).sort((a,b)=>a.name.localeCompare(b.name,'vi',{numeric:true,sensitivity:'base'}) || a.id-b.id);
 const sortable=children.map(f=>{
  const files=docs.filter(d=>d.folderId===f.id && (!d.status || !['trashed','purged'].includes(d.status)));
  return {...f,itemCount:folderState.items.filter(child=>child.parentId===f.id).length+files.length,updated:files.map(d=>d.updated).filter(Boolean).sort().at(-1)};
 });
 const sorted=window.sortDocumentItems?.(sortable,true)||sortable;
 const rows = sorted.map(f => {
  const files=docs.filter(d=>d.folderId===f.id && (!d.status || !['trashed','purged'].includes(d.status)));
  const count=folderState.items.filter(child=>child.parentId===f.id).length+files.length;
  const latest=files.map(d=>d.updated).filter(Boolean).sort().at(-1);
  const actions=f.canManage===false?'<span class="row-dash">—</span>':'<button type="button" class="table-action" data-folder-rename="'+Number(f.id)+'">'+folderText('Đổi tên','Rename')+'</button><button type="button" class="delete-btn" data-folder-delete="'+Number(f.id)+'">'+folderText('Xóa','Delete')+'</button>';
  return '<tr class="folder-row" data-folder-id="'+Number(f.id)+'"><td></td><td><button type="button" class="folder-open" data-folder-open="'+Number(f.id)+'"><span class="folder-icon" aria-hidden="true">📁</span><span data-no-translate>'+escapeHtml(q?folderPath(f.id):f.name)+'</span></button></td><td data-no-translate>'+escapeHtml(f.ownerName||'—')+'</td><td data-no-translate>'+escapeHtml(f.ownerDepartment||'—')+'</td><td title="'+folderText('Cập nhật tệp gần nhất trong thư mục','Latest file update in folder')+'">'+escapeHtml(latest?formatUpdated(latest):'—')+'</td><td>'+count+' '+folderText('mục','items')+'</td><td><div class="table-actions">'+actions+'</div></td></tr>';
 });
 return asItems ? rows : rows.join('');
};
window.renderFolders = function() {
 const crumbs=[],seen=new Set();let id=folderState.current;
 while(id!=null&&!seen.has(id)){seen.add(id);const f=folderState.items.find(x=>x.id===id);if(!f)break;crumbs.unshift(f);id=f.parentId;}
 document.getElementById('folderBreadcrumb').innerHTML='<button type="button" data-folder-open="">'+folderText('Tài liệu','Documents')+'</button>'+crumbs.map(f=>'<span aria-hidden="true"> → </span><button type="button" data-folder-open="'+Number(f.id)+'" data-no-translate>'+escapeHtml(f.name)+'</button>').join('');
 moveButton.disabled=folderState.busy || !selectedDocumentIds.size || !docs.filter(d=>selectedDocumentIds.has(Number(d.id))).every(d=>d.canOrganize);
 document.getElementById('createFolder').disabled=folderState.busy || folderState.items.some(f=>f.id===folderState.current&&!canOrganizeFolder(f));
};
function openFolderDialog(kind, id) {
 if(folderState.busy)return;
 const f=folderState.items.find(x=>x.id===id);
 if((['rename','delete'].includes(kind)&&f?.canManage===false)||(kind==='create'&&folderState.items.some(x=>x.id===folderState.current&&!canOrganizeFolder(x))))return;
 folderReturnFocus=document.activeElement;
 folderAction={kind,id,token:getToken(),documentIds:[...selectedDocumentIds],parentId:folderState.current};
 document.getElementById('folderDialogTitle').textContent=kind==='create'?folderText('Tạo thư mục','Create folder'):kind==='rename'?folderText('Đổi tên thư mục','Rename folder'):kind==='move'?folderText('Chuyển tài liệu','Move documents'):folderText('Xóa thư mục','Delete folder');
 document.getElementById('folderNameLabel').classList.toggle('hidden',!['create','rename'].includes(kind));
 document.getElementById('folderDestinationLabel').classList.toggle('hidden',kind!=='move');
 const input=document.getElementById('folderName');input.required=['create','rename'].includes(kind);input.value=f?.name||'';
 document.getElementById('folderDestination').innerHTML=folderOptions();document.getElementById('folderDestination').value=(folderState.items.some(f=>f.id===folderState.current&&!canOrganizeFolder(f))?'':folderState.current??'');
 document.getElementById('folderDialogMessage').textContent=kind==='delete'?folderText('Xóa thư mục “'+f.name+'” và tất cả thư mục con? Mọi tệp bên trong sẽ vào thùng rác. Khi khôi phục, tệp trở về Tài liệu (gốc).','Delete “'+f.name+'” and all subfolders? Files will go to Trash and restore to Documents (root).'):'';
 document.getElementById('folderError').textContent='';folderModal.classList.remove('hidden');
 (kind==='move'?document.getElementById('folderDestination'):kind==='delete'?document.getElementById('folderConfirm'):input).focus();
}
function closeFolderDialog(){if(folderState.busy)return;folderModal.classList.add('hidden');folderAction=null;folderReturnFocus?.focus();}
folderModal.querySelectorAll('[data-folder-cancel]').forEach(b=>b.onclick=closeFolderDialog);
folderModal.addEventListener('keydown',e=>{
 if(e.key==='Escape'){e.preventDefault();closeFolderDialog();}
 if(e.key==='Tab'){const inputs=[...folderModal.querySelectorAll('button,input,select')].filter(n=>!n.disabled&&!n.closest('.hidden'));const first=inputs[0],last=inputs.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}
});
folderModal.querySelector('form').onsubmit=async event=>{
 event.preventDefault();if(folderState.busy||!folderAction)return;
 const action=folderAction;folderState.busy=true;document.getElementById('folderConfirm').disabled=true;
 try {
  requireSession(action.token);
  if(action.kind==='move')await api('/folders/move',{method:'POST',body:JSON.stringify({documentIds:action.documentIds,folderId:Number(document.getElementById('folderDestination').value)||null})});
  else if(action.kind==='delete')await api('/folders/'+action.id,{method:'DELETE'});
  else await api('/folders'+(action.kind==='rename'?'/'+action.id:''),{method:action.kind==='rename'?'PUT':'POST',body:JSON.stringify({name:document.getElementById('folderName').value.trim(),parentId:action.parentId})});
  requireSession(action.token);folderModal.classList.add('hidden');folderAction=null;selectedDocumentIds.clear();await loadDocuments();requireSession(action.token);showToast(folderText('Đã cập nhật thư mục','Folders updated'));
 }catch(error){if(action.token===getToken())document.getElementById('folderError').textContent=error.message;}
 finally{folderState.busy=false;document.getElementById('folderConfirm').disabled=false;renderFolders();}
};
function handleFolderClick(event){
 const target=event.target.closest('button');if(!target||folderState.busy)return;
 if(target.hasAttribute('data-folder-open')){folderState.current=Number(target.dataset.folderOpen)||null;selectedDocumentIds.clear();document.getElementById('documentSearch').value='';filterDocs();}
 if(target.hasAttribute('data-folder-rename'))openFolderDialog('rename',Number(target.dataset.folderRename));
 if(target.hasAttribute('data-folder-delete'))openFolderDialog('delete',Number(target.dataset.folderDelete));
 if(target.id==='createFolder')openFolderDialog('create');
}
folderArea.addEventListener('click',handleFolderClick);
document.getElementById('documentTable').addEventListener('click',handleFolderClick);
moveButton.onclick=()=>{if(!moveButton.disabled)openFolderDialog('move');};
// Selection updates do not rerender the file table.
document.getElementById('documentsPage').addEventListener('change',()=>renderFolders());
window.getUploadFolderId=function(){return folderState.items.some(f=>f.id===folderState.current&&canOrganizeFolder(f))?folderState.current:null;};
window.prepareUploadFolders=async function(){try{await loadFolders();return true;}catch(error){showToast(error.message);return false;}};
window.placeUploadedDocument=async function(id,folderId){if(folderId!=null)await api('/folders/move',{method:'POST',body:JSON.stringify({documentIds:[id],folderId:Number(folderId)})});};
