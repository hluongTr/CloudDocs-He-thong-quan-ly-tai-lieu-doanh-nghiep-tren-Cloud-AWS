package com.clouddocs.department;
import org.junit.jupiter.api.Test;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import org.springframework.security.access.AccessDeniedException;import com.clouddocs.common.ApiExceptionHandler;
import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class DepartmentControllerTest {
 @Test void mutationsPassCallerAndValidateInput()throws Exception{var service=mock(DepartmentService.class);var mvc=MockMvcBuilders.standaloneSetup(new DepartmentController(service)).setControllerAdvice(new ApiExceptionHandler()).build();var actor=new UsernamePasswordAuthenticationToken("worker",null);
 when(service.create("IT",true,"worker")).thenThrow(new AccessDeniedException("Admin only"));mvc.perform(post("/api/departments").principal(actor).contentType("application/json").content("{\"name\":\"IT\",\"allowSelfSelection\":true}".replace("\\\"","\""))).andExpect(status().isForbidden());
 mvc.perform(post("/api/departments").principal(actor).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
 mvc.perform(patch("/api/departments/assignments/2").principal(actor).contentType("application/json").content("{\"name\":\"IT\"}".replace("\\\"","\""))).andExpect(status().isNoContent());verify(service).assign(2L,"IT","worker");mvc.perform(delete("/api/departments/1").principal(actor)).andExpect(status().isNoContent());verify(service).delete(1L,"worker");}
}
