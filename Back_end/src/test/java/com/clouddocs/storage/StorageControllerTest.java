package com.clouddocs.storage;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StorageControllerTest {
 @Test void routesPassAuthenticatedIdentityToScopedService()throws Exception{
  var service=mock(StorageManagementService.class);var usage=new StorageQuotaService.Usage(500,1000,500,100);
  when(service.summary("admin")).thenReturn(usage);
  when(service.management("admin")).thenReturn(new StorageManagementService.Report("SYSTEM",usage,0,List.of(),List.of()));
  var mvc=MockMvcBuilders.standaloneSetup(new StorageController(service)).build();var actor=new UsernamePasswordAuthenticationToken("admin",null);
  mvc.perform(get("/api/storage/usage").principal(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.usedBytes").value(500));
  mvc.perform(get("/api/storage/management").principal(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("SYSTEM")).andExpect(jsonPath("$.usage.limitBytes").value(1000));
  verify(service).summary("admin");verify(service).management("admin");
 }
}
