/* Sort copies so returning to default always restores server order. */
const documentSort = {field:null, step:0};
const documentSortFields = {name:2, updated:3, size:3};
window.documentSortKey=()=>documentSort.field+':'+documentSort.step;
window.sortDocumentItems = function(items, folders=false) {
 if(!documentSort.field || !documentSort.step)return items;
 const {field,step}=documentSort;
 const direction=field==='name'?1:step===1?-1:1;
 function value(item){
  if(field==='name')return String(item.name||'');
  if(field==='updated'){const time=Date.parse(item.updated);return Number.isFinite(time)?time:null;}
  if(field==='size'){const size=folders?item.itemCount:item.sizeBytes;return size!=null&&Number.isFinite(Number(size))?Number(size):null;}
  return null;
 }
 return items.map((item,index)=>({item,index,value:value(item)})).sort((a,b)=>{
  if(a.value==null||b.value==null)return a.value==null&&b.value==null?a.index-b.index:a.value==null?1:-1;
  const result=field==='name'?a.value.localeCompare(b.value,'vi',{numeric:true,sensitivity:'base'}):(a.value-b.value)*direction;
  return result||a.index-b.index;
 }).map(entry=>entry.item);
};
function renderDocumentSort(){
 const en=currentSettings.language==='en';
 const labels={name:en?'Name':'Tên',updated:en?'Updated':'Cập nhật',size:en?'Size / items':'Dung lượng / số mục'};
 document.querySelectorAll('[data-document-sort]').forEach(button=>{
  const field=button.dataset.documentSort,active=documentSort.field===field&&documentSort.step;
  let order='none',hint='↕';
  if(active){order=field==='name'?'ascending':documentSort.step===1?'descending':'ascending';hint=field==='name'?'A–Z':documentSort.step===1?'↓':'↑';}
  button.textContent=labels[field]+' '+hint;
  button.closest('th').setAttribute('aria-sort',order);
  const cycles={name:en?'A–Z → default':'A–Z → mặc định',updated:en?'Newest → oldest → default':'Mới nhất → cũ nhất → mặc định',size:en?'Largest → smallest → default':'Lớn nhất → nhỏ nhất → mặc định'};
  button.title=cycles[field];button.setAttribute('aria-label',labels[field]+': '+cycles[field]);
 });
}
window.resetDocumentSort=function(){documentSort.field=null;documentSort.step=0;renderDocumentSort();};
document.querySelectorAll('[data-document-sort]').forEach(button=>button.addEventListener('click',()=>{
 const field=button.dataset.documentSort;
 documentSort.step=documentSort.field===field?(documentSort.step+1)%documentSortFields[field]:1;
 documentSort.field=documentSort.step?field:null;renderDocumentSort();filterDocs();
}));
renderDocumentSort();
