package com.clouddocs.permission;
import com.clouddocs.auth.*;import com.clouddocs.document.*;import com.clouddocs.audit.*;import org.junit.jupiter.api.*;import org.springframework.test.util.ReflectionTestUtils;import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class ManagerDocumentAccessTest {
 final UserRepository users=mock(UserRepository.class);final DocumentPermissionRepository permissions=mock(DocumentPermissionRepository.class);final DocumentRepository documents=mock(DocumentRepository.class);final DocumentPermissionService service=new DocumentPermissionService(permissions,documents,users,mock(AuditLogService.class));User manager,owner;Document doc;
 @BeforeEach void setup(){manager=new User();manager.setUsername("manager");manager.setRole("MANAGER");manager.setDepartment("IT");ReflectionTestUtils.setField(manager,"id",1L);owner=new User();owner.setUsername("owner");owner.setDepartment("IT");ReflectionTestUtils.setField(owner,"id",2L);doc=new Document();doc.setOwnerId(2L);doc.setDepartment("HR");ReflectionTestUtils.setField(doc,"id",9L);when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(manager));when(users.findById(2L)).thenReturn(Optional.of(owner));when(documents.findById(9L)).thenReturn(Optional.of(doc));}
 @Test void managerViewsDepartmentMembersDocumentsWithoutEditDownloadOrShare(){assertTrue(service.canView(doc,"manager",false));assertEquals("VIEW",service.effectiveAccess(doc,manager));assertFalse(service.canEdit(doc,"manager",false));assertFalse(service.canDownload(doc,"manager",false));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.list(9L,"manager"));}
 @Test void arbitraryDocumentLabelCannotGrantCrossDepartmentAccess(){owner.setDepartment("HR");doc.setDepartment("IT");assertFalse(service.canView(doc,"manager",false));assertEquals("NONE",service.effectiveAccess(doc,manager));}
 @Test void unassignedUsersAreNotARealDepartment(){manager.setDepartment("Chưa cập nhật");owner.setDepartment("Chưa cập nhật");assertFalse(service.canView(doc,"manager",false));}
 @Test void ordinaryUserCannotReadPeersEvenInSameDepartment(){manager.setRole("USER");assertFalse(service.canView(doc,"manager",false));}
 @Test void movingOwnerImmediatelyRemovesImplicitViewButPreservesExplicitShares(){assertTrue(service.canView(doc,"manager",false));owner.setDepartment("HR");assertFalse(service.canView(doc,"manager",false));when(permissions.hasAnyPermission(eq(9L),eq(1L),anyList())).thenReturn(true);assertTrue(service.canView(doc,"manager",false));}
 @Test void trashedFilesRemainUnavailable(){doc.setStatus("TRASHED");assertFalse(service.canView(doc,"manager",false));}

 @Test void scopedRepositoryQueryParsesWithActualHibernateMetadataWithoutDatabase()throws Exception{
  var configuration=new org.hibernate.cfg.Configuration().addAnnotatedClass(User.class).addAnnotatedClass(Document.class).addAnnotatedClass(DocumentPermission.class).addAnnotatedClass(com.clouddocs.department.Department.class).addAnnotatedClass(com.clouddocs.audit.AuditLog.class);
  configuration.setProperty("hibernate.dialect","org.hibernate.dialect.MySQLDialect");configuration.setProperty("hibernate.boot.allow_jdbc_metadata_access","false");configuration.setProperty("hibernate.hbm2ddl.auto","none");
  try(var factory=configuration.buildSessionFactory();var session=factory.openSession()){
   String query=DocumentRepository.class.getMethod("findAccessibleForManager",Long.class,String.class).getAnnotation(org.springframework.data.jpa.repository.Query.class).value();assertDoesNotThrow(()->session.createQuery(query,Document.class));String outgoing=DocumentPermissionRepository.class.getMethod("findOutgoing",Long.class,boolean.class).getAnnotation(org.springframework.data.jpa.repository.Query.class).value();assertDoesNotThrow(()->session.createQuery(outgoing,DocumentPermission.class));
  }
 }


 @Test void directSharedAccessDoesNotPutForeignFileInPersonalLibrary(){owner.setDepartment("HR");when(permissions.hasAnyPermission(eq(9L),eq(1L),anyList())).thenReturn(true);assertTrue(service.canView(doc,"manager",false));assertFalse(service.inLibrary(doc,manager));}
 @Test void departmentScopeStaysInManagerLibraryWhileForeignUserShareDoesNot(){assertTrue(service.inLibrary(doc,manager));manager.setRole("USER");assertFalse(service.inLibrary(doc,manager));manager.setRole("ADMIN");assertTrue(service.inLibrary(doc,manager));}
}
