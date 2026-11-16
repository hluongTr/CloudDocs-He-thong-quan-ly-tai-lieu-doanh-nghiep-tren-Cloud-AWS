/* Shared client-side filtering, sorting and twenty-row pagination for secondary lists. */
const pagedLists=new Map(),listWidgets=new Map();
const listConfigs={collection:{table:'collectionTable',page:'collectionPage',render:()=>renderCollection(currentCollection)},trash:{table:'trashTable',page:'trashPage',render:()=>renderTrash()},tasks:{table:'workTaskTable',page:'workTasksPage',render:()=>renderWorkTasks()},sent:{table:'sentShareRows',page:'sentSharesPage',render:()=>renderSentShares()}};
function listWidget(key){
 if(listWidgets.has(key))return listWidgets.get(key);
 const cfg=listConfigs[key],table=document.getElementById(cfg.table),panel=table.closest('.table-panel');
 const controls=document.createElement('div');controls.className='toolbar list-page-controls';
 controls.innerHTML=(['collection','trash'].includes(key)?'<label class="search-field"><input type="search" data-list-search placeholder="Tìm kiếm..."></label><select data-list-type aria-label="Loại tệp"><option value="">Tất cả loại</option></select><select data-list-department aria-label="Phòng ban"><option value="">Tất cả phòng ban</option></select>':'')+'<select data-list-sort aria-label="Sắp xếp"><option value="">Thứ tự mặc định</option><option value="name">Tên A–Z</option><option value="new">Mới nhất</option><option value="old">Cũ nhất</option>'+(['collection','trash'].includes(key)?'<option value="large">Dung lượng lớn nhất</option><option value="small">Dung lượng nhỏ nhất</option>':'')+'</select>';
 const pager=document.createElement('nav');pager.className='document-pagination';pager.setAttribute('aria-label','Phân trang danh sách');panel.before(controls);panel.after(pager);
 const widget={controls,pager};listWidgets.set(key,widget);
 if(key==='collection'){
  table.closest('table').querySelector('thead tr').innerHTML='<th><input type="checkbox" id="selectAllCollection" aria-label="Chọn tệp trên trang" onchange="selectAllCollection(this.checked)"></th><th>TÊN</th><th>CHỦ SỞ HỮU</th><th>PHÒNG BAN</th><th>CẬP NHẬT</th><th>DUNG LƯỢNG</th><th>THAO TÁC</th>';
  const bulk=document.createElement('div');bulk.className='bulk-toolbar';bulk.innerHTML='<span id="collectionSelectionCount">Đã chọn 0</span><button id="collectionDownload" class="secondary-btn" onclick="bulkDocuments(\'download\')">Tải xuống</button>';panel.before(bulk);
 }
 if(key==='sent'){
  const th=document.createElement('th');th.innerHTML='<input id="selectAllSentShares" type="checkbox" aria-label="Chọn chia sẻ trên trang" onchange="selectAllSentShares(this.checked)">';table.closest('table').querySelector('thead tr').prepend(th);
  const bulk=document.createElement('div');bulk.className='bulk-toolbar';bulk.innerHTML='<span id="sentSelectionCount">Đã chọn 0</span><button id="revokeSelectedSent" class="danger-btn" onclick="revokeSelectedSentShares()">Thu hồi đã chọn</button>';panel.before(bulk);
 }
 const rerender=()=>{if(key==='collection')selectedDocumentIds.clear();if(key==='trash')selectedTrashIds.clear();if(key==='tasks')workTaskState.selected.clear();cfg.render();};
 controls.addEventListener('input',rerender);controls.addEventListener('change',rerender);
 pager.addEventListener('click',e=>{const button=e.target.closest('[data-list-page]');if(!button||button.disabled)return;const state=pagedLists.get(key),n=Number(button.dataset.listPage);if(!state||!Number.isInteger(n)||n<1||n>state.pages)return;state.page=n;rerender();});
 return widget;
}
window.pageList=function(key,items,context=''){
 const {controls,pager}=listWidget(key),value=selector=>controls.querySelector(selector)?.value||'';
 for(const [selector,field,label] of [['[data-list-type]','type','Tất cả loại'],['[data-list-department]','dept','Tất cả phòng ban']]){
  const select=controls.querySelector(selector);if(!select)continue;const old=select.value,names=[...new Set(items.map(x=>x[field]).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'vi'));select.innerHTML='<option value="">'+label+'</option>'+names.map(n=>'<option value="'+escapeHtml(n)+'">'+escapeHtml(n)+'</option>').join('');select.value=names.includes(old)?old:'';
 }
 const q=value('[data-list-search]').trim().toLowerCase(),type=value('[data-list-type]'),dept=value('[data-list-department]'),sort=value('[data-list-sort]');
 const scope=JSON.stringify([context,q,type,dept,sort,getToken()]);
 let state=pagedLists.get(key);if(!state||state.scope!==scope){state={scope,page:1,pages:1,visible:[],filtered:[]};pagedLists.set(key,state);}
 const filtered=items.filter(x=>(!q||[x.name,x.fileName,x.owner,x.dept].join(' ').toLowerCase().includes(q))&&(!type||x.type===type)&&(!dept||x.dept===dept));
 if(sort)filtered.sort((a,b)=>{
  if(sort==='name')return String(a.name||a.title||a.documentName||'').localeCompare(String(b.name||b.title||b.documentName||''),'vi',{numeric:true,sensitivity:'base'});
  const field=['large','small'].includes(sort)?'sizeBytes':null;
  const av=field?a[field]:Date.parse(a.updated||a.createdAt||a.dueAt),bv=field?b[field]:Date.parse(b.updated||b.createdAt||b.dueAt);
  if(av==null||!Number.isFinite(av))return bv==null||!Number.isFinite(bv)?0:1;if(bv==null||!Number.isFinite(bv))return -1;
  return ['new','large'].includes(sort)?bv-av:av-bv;
 });
 state.filtered=filtered;state.pages=Math.max(1,Math.ceil(filtered.length/20));state.page=Math.min(state.page,state.pages);
 const start=(state.page-1)*20;state.visible=filtered.slice(start,start+20);
 const pages=[...new Set([1,state.page-1,state.page,state.page+1,state.pages])].filter(n=>n>0&&n<=state.pages).sort((a,b)=>a-b);let last=0;
 pager.innerHTML='<span role="status">'+(filtered.length?(start+1)+'–'+Math.min(start+20,filtered.length):'0')+' / '+filtered.length+' mục</span><div class="pagination-controls"><button type="button" data-list-page="'+(state.page-1)+'" '+(state.page===1?'disabled':'')+'>Trước</button>'+pages.map(n=>{const gap=last&&n-last>1?'…':'';last=n;return gap+'<button type="button" data-list-page="'+n+'" '+(n===state.page?'aria-current="page"':'')+'>'+n+'</button>';}).join('')+'<button type="button" data-list-page="'+(state.page+1)+'" '+(state.page===state.pages?'disabled':'')+'>Sau</button></div>';
 return state.visible;
};
window.listPageItems=key=>pagedLists.get(key)?.visible;
window.updateSentSelection=function(visible){if(!listWidgets.has('sent'))return;const eligible=visible.filter(s=>s.canManage),count=sentShares.selected.size,all=document.getElementById('selectAllSentShares');all.checked=eligible.length>0&&count===eligible.length;all.indeterminate=count>0&&!all.checked;all.disabled=sentShares.busy||!eligible.length;document.getElementById('sentSelectionCount').textContent='Đã chọn '+count;document.getElementById('revokeSelectedSent').disabled=sentShares.busy||!count;};
window.selectAllCollection=function(checked){selectedDocumentIds.clear();document.querySelectorAll('#collectionTable .document-select').forEach(input=>{if(!input.disabled){input.checked=checked;if(checked)selectedDocumentIds.add(Number(input.dataset.documentId));}});window.updateCollectionSelection();};
window.updateCollectionSelection=function(){if(!listWidgets.has('collection'))return;const eligible=[...document.querySelectorAll('#collectionTable .document-select')].filter(n=>!n.disabled),chosen=currentCollection.filter(d=>selectedDocumentIds.has(Number(d.id)));const all=document.getElementById('selectAllCollection');all.checked=eligible.length>0&&eligible.every(n=>selectedDocumentIds.has(Number(n.dataset.documentId)));all.indeterminate=!all.checked&&eligible.some(n=>selectedDocumentIds.has(Number(n.dataset.documentId)));document.getElementById('collectionSelectionCount').textContent='Đã chọn '+chosen.length;document.getElementById('collectionDownload').disabled=bulkBusy||!chosen.length||!chosen.every(d=>['OWNER','ADMIN','EDIT','DOWNLOAD'].includes(d.access));};
window.resetPagedLists=function(){pagedLists.clear();renderedTablePages.clear();for(const {pager,controls} of listWidgets.values()){pager.replaceChildren();controls.querySelectorAll('input,select').forEach(node=>node.value='');}for(const id of ['userTable','activityLogTable']){const pager=document.getElementById(id+'Pager');if(pager){pager.replaceChildren();pager.hidden=true;}}};

// User and audit tables are rendered together with their action handlers.
// Page their existing rows without fetching again or changing those actions.
const renderedTablePages = new Map();
window.paginateRenderedTable = function (id, count) {
 const body = document.getElementById(id);
 if (!body) return;
 const panel = body.closest('.table-panel');
 if (!panel) return;
 const scope = JSON.stringify([id, getToken()]);
 const totalPages = Math.max(1, Math.ceil(count / 20));
 let page = Math.min(renderedTablePages.get(scope) || 1, totalPages);
 let pager = document.getElementById(id + 'Pager');
 if (!pager) {
  pager = document.createElement('nav');
  pager.id = id + 'Pager';
  pager.className = 'document-pagination';
  pager.setAttribute('aria-label', 'Phân trang danh sách');
 }
 // Audit refresh replaces the panel, so keep the pager beside the current one.
 panel.after(pager);
 const rows = Array.from(body.children);
 function draw() {
  renderedTablePages.set(scope, page);
  const start = (page - 1) * 20;
  rows.forEach((row, i) => { row.style.display = !count || (i >= start && i < start + 20) ? '' : 'none'; });
  pager.hidden = count <= 20;
  if (pager.hidden) { pager.replaceChildren(); return; }
  const en = currentSettings.language === 'en';
  const pages = [...new Set([1, page - 1, page, page + 1, totalPages])]
   .filter(n => n >= 1 && n <= totalPages).sort((a,b) => a-b);
  let previous = 0;
  pager.innerHTML = '<span role="status">' + (start + 1) + '–' + Math.min(start + 20, count) + ' / ' + count + ' ' + (en ? 'items' : 'mục') + '</span><div class="pagination-controls">' +
   '<button type="button" data-table-page="' + (page-1) + '" ' + (page===1 ? 'disabled' : '') + '>' + (en ? 'Previous' : 'Trước') + '</button>' +
   pages.map(n => { const gap = previous && n-previous>1 ? '…' : ''; previous=n; return gap + '<button type="button" data-table-page="' + n + '" ' + (n===page ? 'aria-current="page"' : '') + '>' + n + '</button>'; }).join('') +
   '<button type="button" data-table-page="' + (page+1) + '" ' + (page===totalPages ? 'disabled' : '') + '>' + (en ? 'Next' : 'Sau') + '</button></div>';
 }
 pager.onclick = event => {
  const button = event.target.closest('[data-table-page]');
  if (!button || button.disabled) return;
  const next = Number(button.dataset.tablePage);
  if (!Number.isInteger(next) || next < 1 || next > totalPages) return;
  page = next;
  draw();
 };
 draw();
};
