package com.clouddocs.document;

import com.clouddocs.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;

class DocumentUploadControllerTest {
 final DocumentUploadService uploads=mock(DocumentUploadService.class);
 final MockMvc mvc=MockMvcBuilders.standaloneSetup(new DocumentUploadController(uploads),new DocumentController(mock(DocumentService.class))).setControllerAdvice(new ApiExceptionHandler()).build();
 final UsernamePasswordAuthenticationToken actor=new UsernamePasswordAuthenticationToken("actor",null);
 final MockMultipartFile file=new MockMultipartFile("file","file.txt","text/plain",new byte[]{1});
 final MockMultipartFile metadata=new MockMultipartFile("metadata","metadata.json","application/json","{\"title\":\"file.txt\",\"fileName\":\"file.txt\",\"fileType\":\"TXT\",\"fileSize\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
 @Test void multipartUploadPassesAuthenticatedUserRequestIdAndFolder() throws Exception {
  UUID id=UUID.randomUUID();mvc.perform(multipart("/api/documents/upload").file(file).file(metadata).param("requestId",id.toString()).param("folderId","4").principal(actor)).andExpect(status().isOk());
  verify(uploads).upload(any(),any(),eq(id),eq(4L),eq("actor"));
 }
 @Test void missingFileNeverCreatesMetadata() throws Exception {
  mvc.perform(multipart("/api/documents/upload").file(metadata).param("requestId",UUID.randomUUID().toString()).principal(actor)).andExpect(status().isBadRequest());verifyNoInteractions(uploads);
 }
 @Test void malformedReceiptIsRejectedBeforeService() throws Exception {
  mvc.perform(multipart("/api/documents/upload").file(file).file(metadata).param("requestId","invalid").principal(actor)).andExpect(status().isBadRequest());verifyNoInteractions(uploads);
 }
 @Test void legacyMetadataOnlyAndPresignedRoutesCannotLeaveNewGhosts() throws Exception {
  mvc.perform(post("/api/documents").contentType("application/json").content("{\"title\":\"file.txt\",\"fileName\":\"file.txt\",\"fileType\":\"TXT\",\"fileSize\":1}").principal(actor)).andExpect(status().isConflict());
  mvc.perform(post("/api/documents/4/upload-url").principal(actor)).andExpect(status().isConflict());
 }
}
