package com.emerald.backend.content;
import com.emerald.backend.security.SecurityConfig;import com.emerald.backend.audit.AuditService;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.context.TestPropertySource;import org.springframework.test.web.servlet.MockMvc;import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@WebMvcTest(controllers={AdminContentController.class,SiteContentController.class}) @Import(SecurityConfig.class)
@TestPropertySource(properties="emerald.admin.password-hash=$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
class AdminSecurityTest {
 @Autowired MockMvc mvc;@MockBean SiteContentRepository repository;@MockBean AuditService audit;
 @Test void anonymousReadBlocked()throws Exception{mvc.perform(get("/admin/content")).andExpect(status().isUnauthorized());}
 @Test void anonymousWriteBlocked()throws Exception{mvc.perform(put("/admin/content/hero.line1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"Test\",\"version\":-1}")).andExpect(status().isUnauthorized());verify(repository,never()).saveAndFlush(any());}
 @Test void missingCsrfBlocked()throws Exception{mvc.perform(put("/admin/content/hero.line1").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"Test\",\"version\":-1}")).andExpect(status().isForbidden());}
 @Test void bearerTokenRejected()throws Exception{mvc.perform(get("/admin/content").header("Authorization","Bearer abcdefghijklmnopqrstuvwxyz0123456789ABCDE")).andExpect(status().isUnauthorized());}
 @Test void publicReadWorks()throws Exception{mvc.perform(get("/content")).andExpect(status().isOk());}
 @Test void invalidKeyBlocked()throws Exception{mvc.perform(put("/admin/content/unknown").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"Test\",\"version\":-1}")).andExpect(status().isBadRequest());}
 @Test void blankValueBlocked()throws Exception{mvc.perform(put("/admin/content/hero.line1").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"   \",\"version\":-1}")).andExpect(status().isBadRequest());}
 @Test void staleCreationBlocked()throws Exception{mvc.perform(put("/admin/content/hero.line1").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"Test\",\"version\":2}")).andExpect(status().isConflict());}
 @Test void nonAdminBlocked()throws Exception{mvc.perform(get("/admin/content").with(user("reader").roles("USER"))).andExpect(status().isForbidden());}
}
