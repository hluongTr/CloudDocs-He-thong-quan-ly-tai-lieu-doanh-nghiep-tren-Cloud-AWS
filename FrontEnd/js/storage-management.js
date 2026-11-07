/* Storage metadata is scoped by the backend; shared files never count twice. */
const managedStorage={data:null,sequence:0,limit:50};
function storagePercent(bytes,total){return total>0?(bytes/total*100).toLocaleString(currentSettings.language==='en'?'en-US':'vi-VN',{maximumFractionDigits:2})+'%':'0%';}
window.resetStorageManagement=function(){managedStorage.sequence++;managedStorage.data=null;managedStorage.limit=50;
 for(const id of ['managedFiles','managedAccounts','managedLegend'])document.getElementById(id).replaceChildren();
 for(const id of ['managedUsage','managedRemaining','managedTrash','managedDonutTotal'])document.getElementById(id).textContent='—';
 document.getElementById('managedDonut').style.background='#dce1e9';document.getElementById('managedProgress').style.width='0%';
 document.getElementById('managedFileSearch').value='';document.getElementById('storageManagementError').textContent='';
 document.getElementById('managedAccountsPanel').classList.add('hidden');
};
function renderManagedFiles(){const data=managedStorage.data;if(!data)return;
 const q=document.getElementById('managedFileSearch').value.trim().toLocaleLowerCase();
 const files=data.files.filter(f=>!q||[f.title,f.fileName,f.owner,f.fileType].join(' ').toLocaleLowerCase().includes(q));
 document.getElementById('managedFiles').innerHTML=files.slice(0,managedStorage.limit).map(f=>'<tr><td><strong data-no-translate>'+escapeHtml(f.title||f.fileName||'Chưa đặt tên')+'</strong><small class="managed-filename" data-no-translate>'+escapeHtml(f.fileName||'')+' · '+escapeHtml(f.fileType||'OTHER')+'</small></td><td data-no-translate>'+escapeHtml(f.owner)+'</td><td data-no-translate>'+formatStorageBytes(f.fileSize)+'</td><td>'+(f.fileSize==null||f.fileSize<0?'—':storagePercent(f.fileSize,data.usage.usedBytes))+'</td><td>'+(f.status==='TRASHED'?'<span class="work-status overdue">Thùng rác</span>':'<span class="work-status completed">Hoạt động</span>')+'</td><td><button class="secondary-btn" type="button" data-managed-file="'+Number(f.id)+'">Mở</button></td></tr>').join('')||'<tr><td colspan="6" class="trash-empty">Không có tài liệu phù hợp.</td></tr>';
 document.getElementById('managedResultCount').textContent='Hiển thị '+Math.min(files.length,managedStorage.limit)+' / '+files.length+' tài liệu';
 document.getElementById('managedShowMore').classList.toggle('hidden',files.length<=managedStorage.limit);
}
function renderStorageManagement(data){managedStorage.data=data;const u=data.usage,system=data.scope==='SYSTEM';
 renderStorageQuota(u);
 document.getElementById('storageManagementScope').textContent=system?'Toàn hệ thống · Tổng hợp tài liệu của tất cả tài khoản':'Dung lượng cá nhân · Chỉ tính tài liệu do bạn sở hữu';
 const prefix=u.missingSizeCount>0?'≥ ':'';
 document.getElementById('managedUsage').textContent=prefix+formatStorageBytes(u.usedBytes)+' / '+formatStorageBytes(u.limitBytes);
 document.getElementById('managedRemaining').textContent=(u.missingSizeCount>0?'≤ ':'')+formatStorageBytes(u.remainingBytes);
 document.getElementById('managedFileCount').textContent=u.ownedDocuments+' tài liệu · '+storagePercent(u.usedBytes,u.limitBytes)+' hạn mức';
 document.getElementById('managedTrash').textContent=formatStorageBytes(data.trashBytes);
 document.getElementById('managedProgress').style.width=Math.min(100,u.usedBytes/u.limitBytes*100)+'%';
 document.getElementById('managedDonutTotal').textContent=prefix+formatStorageBytes(u.usedBytes);
 const entries=Object.entries(u.storageByType).filter(([,v])=>Number.isFinite(v)&&v>=0).sort((a,b)=>b[1]-a[1]);
 const colors=['#315bea','#8665d8','#29a888','#e7a044','#db6d8d','#0891b2','#be7c37','#64748b'];let end=0;
 const segments=entries.map(([type,bytes],i)=>{const start=end;end+=u.usedBytes?bytes/u.usedBytes*100:0;return colors[i%colors.length]+' '+start+'% '+end+'%';});
 document.getElementById('managedDonut').style.background=u.usedBytes?'conic-gradient('+segments.join(',')+')':'#dce1e9';
 document.getElementById('managedLegend').innerHTML=entries.map(([type,bytes],i)=>'<div><span class="managed-type"><i style="background:'+colors[i%colors.length]+'"></i>'+escapeHtml(type)+'</span><span>'+formatStorageBytes(bytes)+' · '+storagePercent(bytes,u.usedBytes)+'</span></div>').join('')||'<p class="muted">Chưa có dữ liệu dung lượng.</p>';
 const notes=['Gồm tài liệu hoạt động, thùng rác và dung lượng đã khai báo khi tải lên. File được chia sẻ không tính thêm lần nữa.'];
 if(u.unconfirmedDocuments)notes.push(u.unconfirmedDocuments+' tài liệu chờ xác nhận tải lên ('+formatStorageBytes(u.unconfirmedBytes)+').');
 if(u.missingSizeCount)notes.push(u.missingSizeCount+' tài liệu thiếu kích thước; tổng đang hiển thị là mức tối thiểu.');
 notes.push('Đây là dung lượng theo dữ liệu ứng dụng, không bao gồm các phiên bản hoặc file ngoài hệ thống trong S3.');
 document.getElementById('managedAccounting').textContent=notes.join(' ');
 document.getElementById('managedAccountsPanel').classList.toggle('hidden',!system);
 document.getElementById('managedAccounts').innerHTML=(data.accounts||[]).map(a=>'<tr><td data-no-translate>'+escapeHtml(a.displayName)+'</td><td>'+escapeHtml(a.role)+'</td><td>'+formatStorageBytes(a.usedBytes)+'</td><td>'+formatStorageBytes(a.limitBytes)+'</td><td>'+storagePercent(a.usedBytes,a.limitBytes)+'</td></tr>').join('');
 renderManagedFiles();
}
window.loadStorageManagement=async function(){const token=getToken(),sequence=++managedStorage.sequence;
 document.getElementById('storageManagementError').textContent='';document.getElementById('storageManagementStatus').textContent='Đang tải thống kê dung lượng…';document.getElementById('storageManagementContent').classList.add('hidden');
 document.getElementById('refreshStorageManagement').disabled=true;
 try{const data=await api('/storage/management');requireSession(token);if(sequence!==managedStorage.sequence)return;
 managedStorage.limit=50;renderStorageManagement(data);document.getElementById('storageManagementContent').classList.remove('hidden');
 }catch(error){if(token===getToken()&&sequence===managedStorage.sequence){managedStorage.data=null;document.getElementById('storageManagementError').textContent='Không tải được quản lý dung lượng: '+error.message;}}
 finally{if(sequence===managedStorage.sequence){document.getElementById('refreshStorageManagement').disabled=false;document.getElementById('storageManagementStatus').textContent='';}}
};
document.getElementById('refreshStorageManagement').addEventListener('click',window.loadStorageManagement);
document.getElementById('managedFileSearch').addEventListener('input',()=>{managedStorage.limit=50;renderManagedFiles();});
document.getElementById('managedShowMore').addEventListener('click',()=>{managedStorage.limit+=50;renderManagedFiles();});
document.getElementById('managedFiles').addEventListener('click',async event=>{const button=event.target.closest('[data-managed-file]');if(!button)return;
 const file=managedStorage.data?.files.find(f=>Number(f.id)===Number(button.dataset.managedFile));if(!file)return;const token=getToken();
 try{await showPage(file.status==='TRASHED'?'trash':'documents');requireSession(token);if(file.status!=='TRASHED'){document.getElementById('documentSearch').value=file.title||file.fileName;filterDocs();}}
 catch(error){if(token===getToken())showToast(error.message);}
});
