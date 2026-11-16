package com.clouddocs.document;

import com.clouddocs.auth.UserRepository;
import com.clouddocs.folder.FolderService;
import com.clouddocs.storage.S3StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Service
public class DocumentUploadService {
    private final DocumentService documents;
    private final DocumentRepository repository;
    private final UserRepository users;
    private final S3StorageService storage;
    private final FolderService folders;
    private final String bucket;
    public DocumentUploadService(DocumentService documents, DocumentRepository repository, UserRepository users,
            S3StorageService storage, FolderService folders, @Value("${aws.s3.bucket}") String bucket) {
        this.documents=documents;this.repository=repository;this.users=users;this.storage=storage;this.folders=folders;this.bucket=bucket;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DocumentDtos.DocumentResponse upload(MultipartFile file, DocumentDtos.CreateDocumentRequest metadata,
            UUID requestId, Long folderId, String username) {
        if(file==null || requestId==null)throw new IllegalArgumentException("Thiếu tệp hoặc mã lần tải");
        String filename=file.getOriginalFilename();
        if(filename==null || filename.isBlank() || filename.length()>255 || filename.contains("/") || filename.contains("\\")
                || filename.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Tên tệp không hợp lệ");
        var actor=users.findByUsernameIgnoreCase(username).orElseThrow();
        users.findForStorageUpdate(actor.getId()).orElseThrow();
        String prefix="documents/uploads/"+actor.getId()+"/"+requestId+"/";
        var existing=repository.findFirstByOwnerIdAndS3KeyStartingWith(actor.getId(),prefix);
        if(existing.isPresent()) {
            var d=existing.get();
            if(!d.isAvailable() || !filename.equals(d.getFileName()) || !java.util.Objects.equals(file.getSize(),d.getFileSize()))
                throw new IllegalStateException("Mã lần tải đã được dùng cho tài liệu khác hoặc đã xóa");
            return documents.findById(d.getId(),username);
        }
        var request=new DocumentDtos.CreateDocumentRequest(metadata.title(),metadata.description(),filename,metadata.fileType(),file.getSize(),null);
        var created=documents.create(request,username);
        var d=repository.findById(created.id()).orElseThrow();
        // Random attempt suffix prevents rollback cleanup from deleting a later successful retry.
        String key=prefix+UUID.randomUUID(); d.setS3Key(key);repository.save(d);
        boolean synchronizedTx=TransactionSynchronizationManager.isSynchronizationActive();
        if(synchronizedTx)TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)cleanup(key);}
        });
        try(var input=file.getInputStream()) {
            storage.uploadReplacement(bucket,key,input,file.getSize());
            documents.completeS3Upload(d.getId(),username);
            if(folderId!=null)folders.move(java.util.List.of(d.getId()),folderId,username);
            return documents.findById(d.getId(),username);
        } catch(java.io.IOException ex) {
            if(!synchronizedTx)cleanup(key);
            throw new IllegalStateException("Không tải được tệp lên S3. Tài liệu chưa được lưu",ex);
        } catch(RuntimeException ex) {
            if(!synchronizedTx)cleanup(key);
            throw ex;
        }
    }
    private void cleanup(String key) {
        try{storage.deleteIfExists(bucket,key);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Không dọn được object upload lỗi: {}",key,ex);}
    }
}
