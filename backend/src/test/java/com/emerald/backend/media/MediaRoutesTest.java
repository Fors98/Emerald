package com.emerald.backend.media;

import com.emerald.backend.audit.AuditService;
import com.emerald.backend.contact.ContactRepository;
import com.emerald.backend.content.SiteContentRepository;
import com.emerald.backend.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Scan production MVC components: missing media registration must fail these route tests.
@WebMvcTest
@Import(SecurityConfig.class)
@TestPropertySource(properties="emerald.admin.password-hash=$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
class MediaRoutesTest {
    @TempDir static Path media;
    @DynamicPropertySource static void directory(DynamicPropertyRegistry properties) {
        properties.add("emerald.media.directory", () -> media.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AuditService audit;
    @MockBean ContactRepository contacts;
    @MockBean SiteContentRepository content;

    private byte[] picture(String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }
    private String upload(byte[] bytes, String filename) throws Exception {
        String body = mvc.perform(multipart("/admin/media")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("url").asText();
    }
    @Test void pngUploadPublicReadAndAudit() throws Exception {
        String url = upload(picture("png"), "../../untrusted.png");
        assertTrue(url.matches("/api/media/[a-f0-9-]{36}\\.png"));
        byte[] bytes = mvc.perform(get(url.substring(4))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(12, ImageIO.read(new java.io.ByteArrayInputStream(bytes)).getWidth());
        assertTrue(Files.isRegularFile(media.resolve(url.substring(url.lastIndexOf('/') + 1))));
        verify(audit).record(eq("admin"), eq("IMAGE_UPLOADED"), anyString());
    }
    @Test void jpegUploadPublicRead() throws Exception {
        String url = upload(picture("jpeg"), "photo.jpg");
        mvc.perform(get(url.substring(4))).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
    }
    @Test void reencodingDropsTrailingPayload() throws Exception {
        var bytes = new ByteArrayOutputStream(); bytes.write(picture("png"));
        bytes.write("<script>UNTRUSTED_TRAILER</script>".getBytes());
        String url = upload(bytes.toByteArray(), "photo.png");
        String stored = new String(mvc.perform(get(url.substring(4))).andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains("UNTRUSTED_TRAILER"));
    }
    @Test void anonymousUploadRejected() throws Exception {
        mvc.perform(multipart("/admin/media").file("file", picture("png")).with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void missingCsrfRejected() throws Exception {
        mvc.perform(multipart("/admin/media").file("file", picture("png")).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void nonAdminUploadRejected() throws Exception {
        mvc.perform(multipart("/admin/media").file("file", picture("png")).with(user("reader").roles("USER")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void disguisedSvgRejected() throws Exception {
        mvc.perform(multipart("/admin/media").file(new MockMultipartFile("file", "photo.png", "image/png", "<svg/>".getBytes()))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(audit);
    }
    @Test void oversizedFileRejected() throws Exception {
        mvc.perform(multipart("/admin/media").file("file", new byte[5 * 1024 * 1024 + 1])
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void excessiveDimensionsRejected() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4001, 4000, BufferedImage.TYPE_BYTE_GRAY), "png", out);
        mvc.perform(multipart("/admin/media").file("file", out.toByteArray()).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
    }
    @Test void invalidAndMissingNamesReturn404() throws Exception {
        mvc.perform(get("/media/not-a-file.png")).andExpect(status().isNotFound());
        mvc.perform(get("/media/00000000-0000-0000-0000-000000000000.png")).andExpect(status().isNotFound());
    }
}
