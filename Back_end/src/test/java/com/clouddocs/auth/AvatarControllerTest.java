package com.clouddocs.auth;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockMultipartFile;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AvatarControllerTest {
 @Test void routesUseAuthenticatedIdentityAndServePrivatePng()throws Exception{var avatars=mock(AvatarService.class);var auth=mock(AuthService.class);var actor=new UsernamePasswordAuthenticationToken("worker",null);var mvc=MockMvcBuilders.standaloneSetup(new AvatarController(avatars,auth)).build();byte[] bytes={1,2,3};when(avatars.read("worker",2L)).thenReturn(bytes);
 mvc.perform(get("/api/avatars/2").principal(actor)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
 mvc.perform(multipart("/api/auth/me/avatar").file(new MockMultipartFile("file","photo.png","image/png",bytes)).principal(actor)).andExpect(status().isOk());verify(avatars).upload(eq("worker"),any());
 mvc.perform(delete("/api/auth/me/avatar").principal(actor)).andExpect(status().isOk());verify(avatars).delete("worker");}
}
