const documentPagination = {page:1, pageSize:20, scope:null, pages:1};
const documentPager = document.createElement('nav');
documentPager.className='document-pagination';
documentPager.setAttribute('aria-label','Phân trang tài liệu');
document.querySelector('#documentsPage .table-panel').after(documentPager);
window.resetDocumentPagination=function(){documentPagination.page=1;documentPagination.scope=null;documentPager.replaceChildren();};
window.paginateDocumentRows=function(rows,scope){
 if(scope!==documentPagination.scope){documentPagination.page=1;documentPagination.scope=scope;}
 const total=rows.length,pages=Math.max(1,Math.ceil(total/documentPagination.pageSize));
 documentPagination.pages=pages;
 documentPagination.page=Math.max(1,Math.min(documentPagination.page,pages));
 const page=documentPagination.page,start=(page-1)*documentPagination.pageSize,en=currentSettings.language==='en';
 const numbers=[...new Set([1,page-1,page,page+1,pages])].filter(n=>n>0&&n<=pages).sort((a,b)=>a-b);
 let previous=0;
 const links=numbers.map(n=>{const gap=previous&&n-previous>1?'<span aria-hidden="true">…</span>':'';previous=n;return gap+'<button type="button" data-doc-page="'+n+'" '+(page===n?'aria-current="page"':'')+' aria-label="'+(en?'Page ':'Trang ')+n+'">'+n+'</button>';}).join('');
 documentPager.innerHTML='<span class="pagination-summary" role="status">'+(total?(start+1)+'–'+Math.min(start+20,total)+' / '+total+' '+(en?'items':'mục'):(en?'0 items':'0 mục'))+'</span><div class="pagination-controls"><button type="button" data-doc-page="'+(page-1)+'" '+(page===1?'disabled':'')+'>'+(en?'Previous':'Trước')+'</button>'+links+'<button type="button" data-doc-page="'+(page+1)+'" '+(page===pages?'disabled':'')+'>'+(en?'Next':'Sau')+'</button></div>';
 return rows.slice(start,start+documentPagination.pageSize);
};
documentPager.addEventListener('click',event=>{
 const button=event.target.closest('button[data-doc-page]');if(!button||button.disabled)return;
 const page=Number(button.dataset.docPage);if(!Number.isInteger(page)||page<1||page>documentPagination.pages)return;
 documentPagination.page=page;selectedDocumentIds.clear();filterDocs();
 document.querySelector('#documentsPage .table-panel').scrollIntoView?.({block:'nearest'});
});
