package com.clouddocs.work;
import com.clouddocs.work.WorkTaskDtos.*;
import com.clouddocs.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class WorkTaskControllerTest {
    final WorkTaskService service=mock(WorkTaskService.class);
    final MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkTaskController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    final UsernamePasswordAuthenticationToken actor=new UsernamePasswordAuthenticationToken("worker",null);
    @Test void collectionRoutesAreNotParsedAsNumericTaskIds()throws Exception{
        when(service.assignees("worker")).thenReturn(List.of());when(service.reportDocuments("worker")).thenReturn(List.of());
        mvc.perform(get("/api/work-tasks/assignees").principal(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/work-tasks/report-documents").principal(actor)).andExpect(status().isOk());
    }
    @Test void mutationRequiresCurrentVersion()throws Exception{
        for(String action:List.of("start","submit","approve","request-changes"))mvc.perform(post("/api/work-tasks/8/"+action).principal(actor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void pastDeadlineIs400BeforeService()throws Exception{
        mvc.perform(post("/api/work-tasks").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Report\",\"description\":\"Do work\",\"assigneeId\":2,\"dueAt\":\"2000-01-01T00:00:00Z\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void oversizedNotesAndInvalidDocumentAre400()throws Exception{
        mvc.perform(post("/api/work-tasks/8/submit").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"documentId\":-1,\"note\":\"\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/work-tasks/8/approve").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"feedback\":\""+"x".repeat(2001)+"\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void missingTaskIs404()throws Exception{when(service.detail(8L,"worker")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));mvc.perform(get("/api/work-tasks/8").principal(actor)).andExpect(status().isNotFound());}

    @Test void deleteRequiresVersionAndReturnsNoContent()throws Exception{
        mvc.perform(delete("/api/work-tasks/8").principal(actor)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(delete("/api/work-tasks/8").param("version","3").principal(actor)).andExpect(status().isNoContent());
        verify(service).delete(8L,3L,"worker");
    }
}
