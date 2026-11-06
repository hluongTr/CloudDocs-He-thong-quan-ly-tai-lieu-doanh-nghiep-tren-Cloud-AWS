package com.clouddocs.permission;
import com.clouddocs.auth.*;
import com.clouddocs.audit.AuditLogService;
import com.clouddocs.document.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DocumentAccessRegressionTest {
    final UserRepository users=mock(UserRepository.class);
    final DocumentPermissionRepository permissions=mock(DocumentPermissionRepository.class);
    final DocumentPermissionService service=new DocumentPermissionService(permissions,mock(DocumentRepository.class),users,mock(AuditLogService.class));
    Document document() {
        Document d=new Document();d.setOwner("owner");d.setOwnerId(8L);ReflectionTestUtils.setField(d,"id",10L);
        User user=new User();ReflectionTestUtils.setField(user,"id",7L);user.setUsername("c_identity");
        when(users.findByUsernameIgnoreCase("c_identity")).thenReturn(Optional.of(user));
        User owner=new User();ReflectionTestUtils.setField(owner,"id",8L);owner.setUsername("owner");
        when(users.findByUsernameIgnoreCase("owner")).thenReturn(Optional.of(owner));return d;
    }
    @Test void unsharedUserCannotViewDownloadOrEditOtherOwnersDocument() {
        Document d=document();assertFalse(service.canView(d,"c_identity",false));
        assertFalse(service.canDownload(d,"c_identity",false));assertFalse(service.canEdit(d,"c_identity",false));
    }
    @Test void viewPermissionDoesNotGrantDownloadOrEdit() {
        Document d=document();
        when(permissions.hasAnyPermission(eq(10L),eq(7L),anyList())).thenAnswer(call->
                ((List<?>)call.getArgument(2)).contains(DocumentPermissionType.VIEW));
        assertTrue(service.canView(d,"c_identity",false));assertFalse(service.canDownload(d,"c_identity",false));
        assertFalse(service.canEdit(d,"c_identity",false));
    }
    @Test void ownershipAndExistingAdminPolicyRemainUnchanged() {
        Document d=document();assertTrue(service.canEdit(d,"owner",false));
        assertTrue(service.canView(d,"c_identity",true));assertTrue(service.canDownload(d,"c_identity",true));
        assertTrue(service.canEdit(d,"c_identity",true));verifyNoInteractions(permissions);
    }
    @Test void staleOwnerLabelCannotGrantOwnershipToAnotherId() {
        Document d=document();d.setOwner("c_identity");
        assertFalse(service.isOwner(d,"c_identity"));assertTrue(service.isOwner(d,"owner"));
        assertFalse(service.canEdit(d,"c_identity",false));
    }
    @Test void fullPermissionMatrix() {
        for(DocumentPermissionType type:DocumentPermissionType.values()) {
            Document d=document();when(permissions.hasAnyPermission(eq(10L),eq(7L),anyList())).thenAnswer(call -> ((List<?>)call.getArgument(2)).contains(type));
            assertTrue(service.canView(d,"c_identity",false));
            assertEquals(type != DocumentPermissionType.VIEW,service.canDownload(d,"c_identity",false));
            assertEquals(type == DocumentPermissionType.EDIT,service.canEdit(d,"c_identity",false));
        }
    }
}
