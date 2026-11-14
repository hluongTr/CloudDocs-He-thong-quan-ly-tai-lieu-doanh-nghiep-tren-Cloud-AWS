package com.clouddocs.document;
import com.clouddocs.common.ApiExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class DocumentControllerContractTest {
    final DocumentService service=mock(DocumentService.class);
    final MockMvc mvc=MockMvcBuilders.standaloneSetup(new DocumentController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    final UsernamePasswordAuthenticationToken actor=new UsernamePasswordAuthenticationToken("actor",null);
    @Test void literalTrashRouteIsNotParsedAsNumericDocumentId() throws Exception {
        when(service.findTrash("actor")).thenReturn(List.of());
        mvc.perform(get("/api/documents/trash").principal(actor)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).findTrash("actor");
    }
    @Test void restoreAndPermanentDeleteUseAuthenticatedActor() throws Exception {
        mvc.perform(post("/api/documents/9/restore").principal(actor)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/documents/9/permanent").principal(actor)).andExpect(status().isNoContent());
        verify(service).restore(9L,"actor");verify(service).purge(9L,"actor");
    }
    @Test void unauthorizedTrashOperationIs403() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(service).restore(9L,"actor");
        mvc.perform(post("/api/documents/9/restore").principal(actor)).andExpect(status().isForbidden());
    }
    @Test void missingDocumentIs404() throws Exception {
        when(service.findById(9L,"actor")).thenThrow(new DocumentNotFoundException(9L));
        mvc.perform(get("/api/documents/9").principal(actor)).andExpect(status().isNotFound());
    }
    @Test void missingSizeAndOversizedInputAre400BeforeService() throws Exception {
        mvc.perform(post("/api/documents").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"file\",\"fileName\":\"file.txt\",\"fileType\":\"TXT\",\"department\":\"IT\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\""+"x".repeat(256)+"\",\"fileName\":\"file.txt\",\"fileType\":\"TXT\",\"fileSize\":0,\"department\":\"IT\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void ordinaryUpdateCannotDirectlyRestoreOrPurge() throws Exception {
        mvc.perform(put("/api/documents/9").principal(actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"file\",\"department\":\"IT\",\"status\":\"PURGED\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void zeroByteLocalDownloadUsesSafeEncodedFilenameHeader() throws Exception {
        when(service.downloadLocalFile(9L,"actor")).thenReturn(new DocumentService.LocalDownloadResult(
                new ByteArrayResource(new byte[0]),"Báo cáo.txt","text/plain",0));
        mvc.perform(get("/api/documents/9/download").principal(actor)).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("filename*=UTF-8")))
            .andExpect(content().bytes(new byte[0]));
    }
    @Test void concurrentWriteReturnsRetryable409InsteadOfGeneric500() throws Exception {
        doThrow(new org.springframework.dao.CannotAcquireLockException("lock conflict")).when(service).restore(9L,"actor");
        mvc.perform(post("/api/documents/9/restore").principal(actor)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONCURRENT_CHANGE"));
    }

 @Test void replacementUsesMultipartMetadataAndAuthenticatedIdentity()throws Exception{
  var file=new org.springframework.mock.web.MockMultipartFile("file","new.pdf","application/pdf",new byte[]{1,2});
  var metadata=new org.springframework.mock.web.MockMultipartFile("metadata","metadata.json","application/json","{\"title\":\"Edited\",\"description\":\"\",\"department\":\"IT\",\"status\":\"ACTIVE\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  mvc.perform(multipart("/api/documents/9/replacement").file(file).file(metadata).principal(actor)).andExpect(status().isOk());verify(service).replaceFile(eq(9L),any(),argThat(r->r.title().equals("Edited")),eq("actor"));
 }
}
