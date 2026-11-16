const uploadReceipts=new WeakMap();
function uploadRequestId(){return globalThis.crypto.randomUUID?.()||'10000000-1000-4000-8000-100000000000'.replace(/[018]/g,c=>(Number(c)^(globalThis.crypto.getRandomValues(new Uint8Array(1))[0]&15)>>Number(c)/4).toString(16));}
window.uploadDocumentBatch=async function(files,description,folderId,token,onProgress=()=>{}){
 const owner=String(currentUser?.id??token),success=[],failed=[];
 for(const file of files){
  requireSession(token);
  let receipt=uploadReceipts.get(file);
  if(!receipt||receipt.owner!==owner){receipt={owner,requestId:uploadRequestId(),complete:false};uploadReceipts.set(file,receipt);}
  if(receipt.complete){success.push(file);continue;}
  try{
   if(file.size>100*1024*1024)throw Error('Tệp tối đa 100 MB');
   onProgress(success.length+failed.length+1,files.length);
   const dot=file.name.lastIndexOf('.'),type=dot<0?'FILE':file.name.slice(dot+1).toUpperCase();
   const body=new FormData();body.append('file',file);body.append('requestId',receipt.requestId);
   if(folderId!=null)body.append('folderId',String(folderId));
   body.append('metadata',new Blob([JSON.stringify({title:file.name,description,fileName:file.name,fileType:type,fileSize:file.size})],{type:'application/json'}));
   await api('/documents/upload',{method:'POST',body});requireSession(token);
   receipt.complete=true;success.push(file);
  }catch(error){requireSession(token);failed.push({file,message:error.message});}
 }
 return {success,failed};
};
