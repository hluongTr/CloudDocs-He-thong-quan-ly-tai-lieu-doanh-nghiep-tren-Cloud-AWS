package com.clouddocs.storage;
import com.clouddocs.auth.*;
import com.clouddocs.document.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.time.Instant;

@Service @Transactional(readOnly=true)
public class StorageManagementService {
    private final UserRepository users;private final DocumentRepository documents;private final StorageQuotaService quotas;
    public StorageManagementService(UserRepository users,DocumentRepository documents,StorageQuotaService quotas){this.users=users;this.documents=documents;this.quotas=quotas;}
    private User actor(String name){User u=users.findByUsernameIgnoreCase(name).orElseThrow(()->new AccessDeniedException("Không tìm thấy tài khoản"));
        if(!"ACTIVE".equalsIgnoreCase(u.getStatus()))throw new AccessDeniedException("Tài khoản không hoạt động");return u;}
    public StorageQuotaService.Usage summary(String name){User actor=actor(name);return "ADMIN".equalsIgnoreCase(actor.getRole())
        ?quotas.systemUsageFor(documents.findAll(),users.findAllByOrderByCreatedAtDesc()):quotas.usage(actor);}
    public Report management(String name){User actor=actor(name);boolean admin="ADMIN".equalsIgnoreCase(actor.getRole());
        List<Document> stored=(admin?documents.findAll():documents.findByOwnerId(actor.getId())).stream().filter(d->!d.isPurged()).toList();
        List<User> accounts=admin?users.findAllByOrderByCreatedAtDesc():List.of(actor);
        var usage=admin?quotas.systemUsageFor(stored,accounts):quotas.usageFor(actor,stored);
        var names=DocumentOwnerNames.resolve(stored,users);
        var files=stored.stream().sorted(Comparator.comparing((Document d)->d.getFileSize()==null||d.getFileSize()<0?null:d.getFileSize(),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Document::getId,Comparator.nullsLast(Comparator.naturalOrder())))
            .map(d->new FileItem(d.getId(),d.getTitle(),d.getFileName(),d.getFileType(),d.getFileSize(),DocumentOwnerNames.name(d,names),d.getStatus(),d.getUpdatedAt())).toList();
        Map<Long,Long> byOwner=new HashMap<>();for(Document d:stored)if(d.getOwnerId()!=null&&d.getFileSize()!=null&&d.getFileSize()>=0)byOwner.merge(d.getOwnerId(),d.getFileSize(),Math::addExact);
        var accountRows=admin?accounts.stream().filter(u->!"DELETED".equalsIgnoreCase(u.getStatus())).map(u->new Account(ProfilePresentation.name(u),u.getRole(),byOwner.getOrDefault(u.getId(),0L),quotas.limitFor(u))).sorted(Comparator.comparingLong(Account::usedBytes).reversed()).toList():List.<Account>of();
        long trash=stored.stream().filter(Document::isTrashed).map(Document::getFileSize).filter(v->v!=null&&v>=0).mapToLong(Long::longValue).sum();
        return new Report(admin?"SYSTEM":"PERSONAL",usage,trash,files,accountRows);
    }
    public record FileItem(Long id,String title,String fileName,String fileType,Long fileSize,String owner,String status,Instant updatedAt){}
    public record Account(String displayName,String role,long usedBytes,long limitBytes){}
    public record Report(String scope,StorageQuotaService.Usage usage,long trashBytes,List<FileItem> files,List<Account> accounts){}
}
