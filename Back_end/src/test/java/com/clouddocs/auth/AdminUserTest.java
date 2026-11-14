package com.clouddocs.auth;
import com.clouddocs.document.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminUserTest {
    UserRepository users=mock(UserRepository.class);
    AdminUserController controller=new AdminUserController(users,mock(DocumentRepository.class),mock(com.clouddocs.work.WorkTaskRepository.class));
    User admin() {
        User u=new User();u.setUsername("target");u.setRole("ADMIN");u.setStatus("ACTIVE");
        when(users.findById(1L)).thenReturn(Optional.of(u));return u;
    }
    @Test void lastLinkedActiveAdminCannotBeDemotedLockedOrDeleted() {
        User u=admin();when(users.countActiveCognitoAdmins()).thenReturn(1L);
        var actor=new UsernamePasswordAuthenticationToken("another",null);
        assertThrows(IllegalStateException.class,()->controller.updateRole(1L,new AdminUserController.RoleRequest("USER"),actor));
        assertThrows(IllegalStateException.class,()->controller.updateStatus(1L,new AdminUserController.StatusRequest("LOCKED"),actor));
        assertThrows(IllegalStateException.class,()->controller.delete(1L,actor));
        assertEquals("ADMIN",u.getRole());assertEquals("ACTIVE",u.getStatus());
        verify(users,times(3)).lockAdmins();verify(users,never()).save(any());
    }
    @Test void canChangeOtherAdminWhenAnotherLinkedActiveAdminRemains() {
        User u=admin();when(users.countActiveCognitoAdmins()).thenReturn(2L);when(users.save(u)).thenReturn(u);
        controller.updateRole(1L,new AdminUserController.RoleRequest("USER"),new UsernamePasswordAuthenticationToken("another",null));
        assertEquals("USER",u.getRole());
    }
    @Test void cannotDemoteOrLockSelf() {
        admin();var actor=new UsernamePasswordAuthenticationToken("target",null);
        assertThrows(IllegalStateException.class,()->controller.updateRole(1L,new AdminUserController.RoleRequest("USER"),actor));
        assertThrows(IllegalStateException.class,()->controller.updateStatus(1L,new AdminUserController.StatusRequest("LOCKED"),actor));
    }
    @Test void pendingAccountCanBeApprovedByAdmin() {
        User u=admin();u.setRole("USER");u.setStatus("PENDING_APPROVAL");when(users.save(u)).thenReturn(u);
        controller.updateStatus(1L,new AdminUserController.StatusRequest("ACTIVE"),new UsernamePasswordAuthenticationToken("another",null));
        assertEquals("ACTIVE",u.getStatus());assertEquals("USER",u.getRole());
    }
    @Test void deletingAccountKeepsIdentityAndAuditRowsButBlocksItsReuse() {
        User user=admin();user.setRole("USER");
        controller.delete(1L,new UsernamePasswordAuthenticationToken("another",null));
        assertEquals("DELETED",user.getStatus());verify(users).save(user);verify(users,never()).delete(any(User.class));
        assertThrows(IllegalArgumentException.class,()->controller.updateStatus(1L,new AdminUserController.StatusRequest("ACTIVE"),new UsernamePasswordAuthenticationToken("another",null)));
    }
    @Test void cannotDeleteAccountWithUnfinishedAssignedOrCreatedTasks() {
        User target=admin();target.setRole("USER");
        var tasks=mock(com.clouddocs.work.WorkTaskRepository.class);
        when(tasks.hasOpenTasks(target.getId())).thenReturn(true);
        var guarded=new AdminUserController(users,mock(DocumentRepository.class),tasks);
        assertThrows(IllegalStateException.class,()->guarded.delete(1L,new UsernamePasswordAuthenticationToken("another",null)));
        assertEquals("ACTIVE",target.getStatus());verify(users,never()).save(any());
    }

 @Test void managerRoleRequiresAnAssignedDepartment(){User u=admin();u.setRole("USER");u.setDepartment("Chưa cập nhật");var actor=new UsernamePasswordAuthenticationToken("another",null);assertThrows(IllegalStateException.class,()->controller.updateRole(1L,new AdminUserController.RoleRequest("MANAGER"),actor));u.setDepartment("IT");when(users.save(u)).thenReturn(u);assertEquals("MANAGER",controller.updateRole(1L,new AdminUserController.RoleRequest("MANAGER"),actor).role());}
 @Test void lastAdminCannotBeDemotedThroughManagerRole(){User u=admin();u.setDepartment("IT");when(users.countActiveCognitoAdmins()).thenReturn(1L);assertThrows(IllegalStateException.class,()->controller.updateRole(1L,new AdminUserController.RoleRequest("MANAGER"),new UsernamePasswordAuthenticationToken("another",null)));}

}
