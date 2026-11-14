/* One assignee per task. All actions are authorized again by the backend. */
const workTaskState = {items:[], current:null, listSequence:0, detailSequence:0, busy:false, initialized:false, selected:new Set(), deleteBusy:false};
function workTaskMatches(task, filter, userId) {
  const mine = Number(task.assignee?.id) === Number(userId);
  const assigned = Number(task.assignedBy?.id) === Number(userId);
  if (filter === "mine") return mine;
  if (filter === "assigned") return assigned;
  if (filter === "review") return assigned && task.status === "SUBMITTED";
  if (filter === "completed") return task.status === "COMPLETED";
  if (filter === "overdue") return task.overdue;
  return true;
}
function workTaskStatus(task) {
  const labels = {TODO:"Chưa làm",IN_PROGRESS:"Đang làm",SUBMITTED:"Chờ duyệt",COMPLETED:"Hoàn thành"};
  const classes = {TODO:"todo",IN_PROGRESS:"doing",SUBMITTED:"review",COMPLETED:"completed"};
  return '<span class="work-status '+(classes[task.status]||'todo')+'">'+escapeHtml(labels[task.status]||task.status)+'</span>'+
    (task.overdue ? '<span class="work-status overdue">Quá hạn</span>' : '')+
    (task.lateSubmission ? '<span class="work-status late">Nộp muộn</span>' : '');
}
function workTaskError(error) { return error?.message || "Không thể thực hiện thao tác. Vui lòng thử lại."; }
function canDeleteWorkTask(task){return ['ADMIN','MANAGER'].includes(currentUser?.role) && Number(task.assignedBy?.id)===Number(currentUser?.id);}
function visibleWorkTasks(){
 const filter=document.getElementById('workTaskFilter').value,query=document.getElementById('workTaskSearch').value.trim().toLocaleLowerCase();
 return workTaskState.items.filter(t=>workTaskMatches(t,filter,currentUser?.id)).filter(t=>!query||[t.title,t.description,t.assignee?.fullName,t.assignedBy?.fullName].join(' ').toLocaleLowerCase().includes(query));
}
function updateWorkSelection(){
 const eligible=visibleWorkTasks().filter(canDeleteWorkTask),ids=new Set(eligible.map(t=>Number(t.id)));
 for(const id of workTaskState.selected)if(!ids.has(id))workTaskState.selected.delete(id);
 const count=workTaskState.selected.size,all=document.getElementById('selectAllWorkTasks');
 all.checked=eligible.length>0&&count===eligible.length;all.indeterminate=count>0&&count<eligible.length;all.disabled=!eligible.length||workTaskState.deleteBusy;
 document.getElementById('workTaskSelectionCount').textContent='Đã chọn '+count+' công việc';
 document.getElementById('deleteSelectedWorkTasks').disabled=!count||workTaskState.deleteBusy;
}
window.selectWorkTask=function(id,checked){if(workTaskState.deleteBusy)return;const task=workTaskState.items.find(t=>Number(t.id)===Number(id));if(!task||!canDeleteWorkTask(task))return;checked?workTaskState.selected.add(Number(id)):workTaskState.selected.delete(Number(id));updateWorkSelection();};
window.deleteWorkTasks=async function(ids){
 if(workTaskState.deleteBusy)return;
 const selected=workTaskState.items.filter(t=>ids.includes(Number(t.id))&&canDeleteWorkTask(t));
 if(!selected.length||!window.confirm('Xóa '+selected.length+' công việc? Không thể hoàn tác. Tài liệu báo cáo vẫn được giữ lại.'))return;
 const token=getToken();workTaskState.deleteBusy=true;renderWorkTasks();let removed=0,errors=[];
 try{for(const task of selected){requireSession(token);try{await api('/work-tasks/'+task.id+'?version='+task.version,{method:'DELETE'});requireSession(token);workTaskState.selected.delete(Number(task.id));removed++;}catch(error){requireSession(token);errors.push(workTaskError(error));}}
 requireSession(token);await window.loadWorkTasks();requireSession(token);await loadNotifications();requireSession(token);
 showToast((removed?'Đã xóa '+removed+' công việc. ':'')+(errors.length?errors.length+' công việc chưa xóa được: '+errors[0]:''));
 }catch(error){if(token===getToken())showToast(workTaskError(error));}finally{workTaskState.deleteBusy=false;if(token===getToken())renderWorkTasks();}
};
function renderWorkTasks() {
  const filter=document.getElementById("workTaskFilter").value;
  const query=document.getElementById("workTaskSearch").value.trim().toLocaleLowerCase();
  const items=workTaskState.items.filter(task=>workTaskMatches(task,filter,currentUser?.id))
    .filter(task=>!query || [task.title,task.description,task.assignee?.fullName,task.assignedBy?.fullName].join(' ').toLocaleLowerCase().includes(query));
  updateWorkSelection();
  document.getElementById("workTaskSummary").innerHTML=[['Công việc',items.length],['Chờ duyệt',items.filter(t=>t.status==='SUBMITTED').length],['Quá hạn',items.filter(t=>t.overdue).length]].map(([label,count])=>'<div class="work-stat"><span>'+label+'</span><strong>'+count+'</strong></div>').join('');
  const admin=['ADMIN','MANAGER'].includes(currentUser?.role);
  document.getElementById("workTaskTable").innerHTML=items.map(task=>'<tr>'+(admin?'<td><input type="checkbox" class="work-select" aria-label="Chọn công việc" '+(canDeleteWorkTask(task)&&!workTaskState.deleteBusy?'':'disabled ')+(workTaskState.selected.has(Number(task.id))?'checked ':'')+'onchange="selectWorkTask('+Number(task.id)+',this.checked)" /></td>':'')+'<td><strong data-no-translate>'+escapeHtml(task.title)+'</strong></td><td data-no-translate>'+escapeHtml(task.assignee.fullName)+'</td><td data-no-translate>'+escapeHtml(task.assignedBy.fullName)+'</td><td>'+escapeHtml(formatUpdated(task.dueAt))+'</td><td><div class="work-badges">'+workTaskStatus(task)+'</div></td><td><div class="work-actions"><button type="button" class="secondary-btn" onclick="openWorkTask('+Number(task.id)+')">Chi tiết</button>'+(canDeleteWorkTask(task)?'<button type="button" class="danger-btn" '+(workTaskState.deleteBusy?'disabled ':'')+'onclick="deleteWorkTasks(['+Number(task.id)+'])">Xóa</button>':'')+'</div></td></tr>').join('') || '<tr><td colspan="'+(admin?7:6)+'" class="trash-empty">Chưa có công việc phù hợp.</td></tr>';
}
window.loadWorkTasks = async function() {
  const token=getToken(), sequence=++workTaskState.listSequence;
  const isAdmin=currentUser?.role==='ADMIN',canAssign=['ADMIN','MANAGER'].includes(currentUser?.role);
  document.getElementById("openWorkTaskCreate").classList.toggle('hidden',!canAssign);
  document.querySelectorAll('[data-work-admin]').forEach(option=>{option.disabled=!canAssign;option.hidden=!canAssign;});
  document.querySelectorAll('[data-work-selection]').forEach(node=>node.classList.toggle('hidden',!canAssign));
  document.getElementById('workTaskBulk').classList.toggle('hidden',!canAssign);
  const filter=document.getElementById("workTaskFilter");
  for(const option of Array.from(filter.options||[])){
    if(option.value==='mine'){option.hidden=isAdmin;option.disabled=isAdmin;}
    if(option.value==='assigned')option.textContent='Đã giao';
    if(option.value==='review')option.textContent='Chờ duyệt';
  }
  if(isAdmin && filter.value==='mine')filter.value='assigned';
  if(!workTaskState.initialized){filter.value=isAdmin?'assigned':'mine';workTaskState.initialized=true;}
  if(!canAssign && ['assigned','review','all'].includes(filter.value))filter.value='mine';
  document.getElementById("workTaskTable").innerHTML='<tr><td colspan="6" class="trash-empty">Đang tải công việc...</td></tr>';
  try {
    const data=await api('/work-tasks'); requireSession(token);
    if(sequence!==workTaskState.listSequence)return;
    workTaskState.items=Array.isArray(data)?data:[];renderWorkTasks();
  } catch(error) {
    if(token!==getToken() || sequence!==workTaskState.listSequence)return;
    workTaskState.items=[];
    document.getElementById("workTaskSummary").textContent='';
    document.getElementById("workTaskTable").innerHTML='<tr><td colspan="6" class="trash-empty">'+escapeHtml(workTaskError(error))+'</td></tr>';
  }
};
function workReportCache(task) {
  const report=task.reportDocument;
  if(!report || !task.reportAvailable)return;
  if(!docs.some(d=>Number(d.id)===Number(report.id)))docs.push(normalize({id:report.id,title:report.title,fileName:report.fileName,
    fileType:report.fileName?.split('.').pop()||'FILE',owner:task.assignee.fullName,department:task.assignee.department,status:'ACTIVE',
    access:currentUser?.role==='ADMIN'?'ADMIN':Number(task.assignee.id)===Number(currentUser?.id)?'OWNER':'VIEW'}));
}
async function renderWorkTaskDetail(task, sequence) {
  const mine=Number(task.assignee.id)===Number(currentUser?.id);
  const canWork=mine && ['TODO','IN_PROGRESS'].includes(task.status);
  const canReview=['ADMIN','MANAGER'].includes(currentUser?.role) && Number(task.assignedBy.id)===Number(currentUser?.id) && task.status==='SUBMITTED';
  workReportCache(task);
  const report=task.reportDocument;
  const attachment=report ? '<section class="work-section"><h3>Báo cáo đã nộp</h3><p data-no-translate>'+escapeHtml(report.title)+'</p><p class="work-pre" data-no-translate>'+escapeHtml(task.reportNote||'Không có ghi chú.')+'</p><small>Nộp lúc: '+escapeHtml(formatUpdated(task.submittedAt))+'</small>'+
    (task.reportAvailable ? '<div class="work-actions"><button type="button" class="secondary-btn" data-work-action="preview">Xem báo cáo</button>'+((mine||currentUser?.role==='ADMIN')?'<button type="button" class="secondary-btn" data-work-action="download">Tải báo cáo</button>':'')+'</div>' : '<p class="work-error">Tài liệu báo cáo không còn khả dụng.</p>')+'</section>' : '';
  document.getElementById("workTaskDetailTitle").textContent=task.title;
  document.getElementById("workTaskDetailContent").innerHTML=
    '<div class="work-badges">'+workTaskStatus(task)+'</div><dl class="work-meta"><div><dt>Người thực hiện</dt><dd data-no-translate>'+escapeHtml(task.assignee.fullName)+'</dd></div><div><dt>Người giao việc</dt><dd data-no-translate>'+escapeHtml(task.assignedBy.fullName)+'</dd></div><div><dt>Hạn nộp</dt><dd>'+escapeHtml(formatUpdated(task.dueAt))+'</dd></div></dl>'+
    '<section class="work-section"><h3>Yêu cầu công việc</h3><p class="work-pre" data-no-translate>'+escapeHtml(task.description)+'</p></section>'+attachment+
    (task.reviewFeedback?'<section class="work-section"><h3>Phản hồi của người giao việc</h3><p class="work-pre" data-no-translate>'+escapeHtml(task.reviewFeedback)+'</p><small>'+escapeHtml(formatUpdated(task.reviewedAt))+'</small></section>':'')+
    (mine&&task.status==='TODO'?'<div class="work-actions"><button type="button" class="secondary-btn" data-work-action="start">Bắt đầu làm</button></div>':'')+
    (canWork?'<form id="workSubmitForm" class="work-section"><h3>Nộp báo cáo</h3><p class="muted">Chọn một tài liệu của bạn đã tải lên thành công. Người giao việc sẽ được quyền xem báo cáo.</p><label>Tài liệu báo cáo<select id="workReportDocument" required><option value="">Đang tải tài liệu...</option></select></label><label>Ghi chú<textarea id="workReportNote" rows="3" maxlength="2000" placeholder="Kết quả đã thực hiện, vấn đề phát sinh..."></textarea></label><p class="muted" id="workReportHelp"></p><div class="work-actions"><button type="button" class="secondary-btn" data-work-action="upload">Tải tài liệu lên</button><button id="workSubmitButton" class="primary-btn" type="submit" aria-busy="false" disabled>Nộp báo cáo</button></div></form>':'')+
    (canReview?'<section class="work-section"><h3>Duyệt báo cáo</h3><label>Nhận xét<textarea id="workReviewFeedback" maxlength="2000" rows="3" placeholder="Bắt buộc nhập lý do nếu yêu cầu sửa báo cáo"></textarea></label><div class="work-actions"><button type="button" class="secondary-btn" data-work-action="changes">Yêu cầu chỉnh sửa</button><button type="button" class="primary-btn" data-work-action="approve">Duyệt hoàn thành</button></div></section>':'')+
    '<p id="workTaskActionError" class="work-error" role="alert"></p>';
  if(canWork){
    const token=getToken();
    try {
      const choices=await api('/work-tasks/report-documents');requireSession(token);
      if(sequence!==workTaskState.detailSequence || workTaskState.current?.id!==task.id)return;
      const select=document.getElementById('workReportDocument');
      select.innerHTML='<option value="">Chọn tài liệu báo cáo</option>'+choices.map(d=>'<option value="'+Number(d.id)+'" data-no-translate>'+escapeHtml(d.title||d.fileName)+'</option>').join('');
      if(report && choices.some(d=>Number(d.id)===Number(report.id)))select.value=String(report.id);
      document.getElementById('workReportHelp').textContent=choices.length?'Báo cáo chờ duyệt hoặc đã hoàn thành sẽ không được xóa/ghi đè.':'Chưa có tài liệu phù hợp. Hãy tải tài liệu lên rồi mở lại công việc này.';
      document.getElementById('workSubmitButton').disabled=!choices.length;
     }catch(error){if(sequence===workTaskState.detailSequence&&token===getToken()){
 document.getElementById('workReportHelp').textContent=workTaskError(error);
 document.getElementById('workReportDocument').innerHTML='<option value="">Không tải được tài liệu báo cáo</option>';
 document.getElementById('workSubmitButton').disabled=true;
 }} 
  }
}
window.openWorkTask=async function(id) {
  const token=getToken(),sequence=++workTaskState.detailSequence;
  workTaskState.current=null;
  document.getElementById('workTaskDetailModal').classList.remove('hidden');
  document.getElementById('workTaskDetailTitle').textContent='Chi tiết công việc';
  document.getElementById('workTaskDetailContent').textContent='Đang tải...';
  try{const task=await api('/work-tasks/'+Number(id));requireSession(token);if(sequence!==workTaskState.detailSequence)return;
    workTaskState.current=task;await renderWorkTaskDetail(task,sequence);
  }catch(error){if(token===getToken()&&sequence===workTaskState.detailSequence)document.getElementById('workTaskDetailContent').textContent=workTaskError(error);}
};
function closeWorkTaskDetail(){workTaskState.detailSequence++;workTaskState.current=null;document.getElementById('workTaskDetailModal').classList.add('hidden');}
function closeWorkTaskCreate(){document.getElementById('workTaskCreateModal').classList.add('hidden');}
window.resetWorkTasks=function(){workTaskState.items=[];workTaskState.selected.clear();workTaskState.current=null;workTaskState.listSequence++;workTaskState.detailSequence++;workTaskState.initialized=false;
  document.getElementById('workTaskTable').replaceChildren();document.getElementById('workTaskDetailContent').replaceChildren();
  document.getElementById('workTaskSummary').textContent='';document.getElementById('workTaskSearch').value='';
  document.getElementById('workTaskCreateForm').reset();closeWorkTaskDetail();closeWorkTaskCreate();};
async function executeWorkAction(action) {
  const task=workTaskState.current;if(!task||workTaskState.busy)return;
  if(action==='preview')return window.previewDocument(task.reportDocument.id);
  if(action==='download')return window.downloadDocument(task.reportDocument.id);
  if(action==='upload'){const uploadToken=getToken();closeWorkTaskDetail();await showPage('documents');if(uploadToken===getToken())document.getElementById('openUpload').click();return;}
  const token=getToken();const request={version:task.version};
  if(action==='submit'){
    const documentId=Number(document.getElementById('workReportDocument').value);
    if(!documentId){document.getElementById('workTaskActionError').textContent='Chọn tài liệu để nộp báo cáo.';return;}
    request.documentId=documentId;request.note=document.getElementById('workReportNote').value.trim();
  }
  if(action==='approve'||action==='changes'){
    request.feedback=document.getElementById('workReviewFeedback').value.trim();
    if(action==='changes'&&!request.feedback){document.getElementById('workTaskActionError').textContent='Nhập lý do yêu cầu chỉnh sửa.';return;}
  }
  workTaskState.busy=true;
  const buttons=Array.from(document.querySelectorAll('#workTaskDetailContent button')).map(button=>({button,disabled:button.disabled}));
  buttons.forEach(({button})=>button.disabled=true);
  const activeButton=action==='submit'?document.getElementById('workSubmitButton'):document.querySelector('#workTaskDetailContent [data-work-action="'+action+'"]');
  activeButton?.setAttribute('aria-busy','true');
  document.getElementById('workTaskActionError').textContent='';
  try{
    const endpoint=action==='changes'?'request-changes':action;
    await api('/work-tasks/'+task.id+'/'+endpoint,{method:'POST',body:JSON.stringify(request)});requireSession(token);
    showToast({start:'Đã bắt đầu công việc.',submit:'Đã nộp báo cáo.',approve:'Đã duyệt hoàn thành.',changes:'Đã yêu cầu chỉnh sửa.'}[action]);
    await window.loadWorkTasks();requireSession(token);await window.openWorkTask(task.id);requireSession(token);await loadNotifications();
  }catch(error){if(token===getToken()){const box=document.getElementById('workTaskActionError');if(box)box.textContent=workTaskError(error);}}
  finally{workTaskState.busy=false;buttons.forEach(({button,disabled})=>button.disabled=disabled);activeButton?.setAttribute('aria-busy','false');}
}
document.getElementById('workTaskFilter').addEventListener('change',renderWorkTasks);
document.getElementById('workTaskSearch').addEventListener('input',renderWorkTasks);
document.getElementById('refreshWorkTasks').addEventListener('click',window.loadWorkTasks);
document.getElementById('closeWorkTaskDetail').onclick=closeWorkTaskDetail;
document.getElementById('closeWorkTaskCreate').onclick=closeWorkTaskCreate;
document.getElementById('cancelWorkTaskCreate').onclick=closeWorkTaskCreate;
for(const id of ['workTaskCreateModal','workTaskDetailModal'])document.getElementById(id).addEventListener('click',event=>{if(event.target.id===id)(id==='workTaskCreateModal'?closeWorkTaskCreate:closeWorkTaskDetail)();});
document.getElementById('workTaskDetailContent').addEventListener('click',event=>{const button=event.target.closest('[data-work-action]');if(button)executeWorkAction(button.dataset.workAction);});
document.getElementById('workTaskDetailContent').addEventListener('submit',event=>{if(event.target.id==='workSubmitForm'){event.preventDefault();executeWorkAction('submit');}});
document.getElementById('openWorkTaskCreate').addEventListener('click',async()=>{
  if(!['ADMIN','MANAGER'].includes(currentUser?.role))return;
  const token=getToken();document.getElementById('workTaskCreateForm').reset();document.getElementById('workTaskCreateError').textContent='';
  document.getElementById('workTaskAssignee').innerHTML='<option value="">Đang tải nhân viên...</option>';
  document.getElementById('confirmWorkTaskCreate').disabled=true;
  document.getElementById('workTaskDue').min=new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16);
  document.getElementById('workTaskCreateModal').classList.remove('hidden');
  try{const users=await api('/work-tasks/assignees');requireSession(token);
    document.getElementById('workTaskAssignee').innerHTML='<option value="">Chọn người thực hiện</option>'+users.map(u=>'<option value="'+Number(u.id)+'" data-no-translate>'+escapeHtml(u.fullName)+' · '+escapeHtml(u.department)+'</option>').join('');
    document.getElementById('confirmWorkTaskCreate').disabled=!users.length;
    if(!users.length)document.getElementById('workTaskCreateError').textContent='Chưa có nhân viên USER đang hoạt động để giao việc.';
  }catch(error){if(token===getToken())document.getElementById('workTaskCreateError').textContent=workTaskError(error);}
});
document.getElementById('workTaskCreateForm').addEventListener('submit',async event=>{
  event.preventDefault();const button=document.getElementById('confirmWorkTaskCreate');if(button.disabled||!['ADMIN','MANAGER'].includes(currentUser?.role))return;
  const token=getToken();button.disabled=true;document.getElementById('workTaskCreateError').textContent='';
  try{
    const due=new Date(document.getElementById('workTaskDue').value);
    if(Number.isNaN(due.getTime())||due.getTime()<=Date.now())throw Error('Hạn nộp phải ở tương lai.');
    const task=await api('/work-tasks',{method:'POST',body:JSON.stringify({title:document.getElementById('workTaskTitle').value.trim(),description:document.getElementById('workTaskDescription').value.trim(),assigneeId:Number(document.getElementById('workTaskAssignee').value),dueAt:due.toISOString()})});
    requireSession(token);closeWorkTaskCreate();showToast('Đã giao việc và gửi thông báo cho nhân viên.');
    document.getElementById('workTaskFilter').value='assigned';await window.loadWorkTasks();requireSession(token);await window.openWorkTask(task.id);
  }catch(error){if(token===getToken())document.getElementById('workTaskCreateError').textContent=workTaskError(error);}
  finally{button.disabled=false;}
});

document.getElementById('selectAllWorkTasks').addEventListener('change',event=>{if(workTaskState.deleteBusy)return;workTaskState.selected.clear();if(event.target.checked)visibleWorkTasks().filter(canDeleteWorkTask).forEach(t=>workTaskState.selected.add(Number(t.id)));renderWorkTasks();});
document.getElementById('deleteSelectedWorkTasks').addEventListener('click',()=>window.deleteWorkTasks([...workTaskState.selected]));
