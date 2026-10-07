package com.emerald.backend.contact;

import com.emerald.backend.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactController.class)
@Import(JacksonConfig.class)
class ContactControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ContactRepository contactRepository;

    @Test
    void validRequestReturns201() throws Exception {
        Contact saved = mock(Contact.class);
        UUID id = UUID.randomUUID();
        OffsetDateTime receivedAt = OffsetDateTime.now();

        when(saved.getId()).thenReturn(id);
        when(saved.getReceivedAt()).thenReturn(receivedAt);
        when(contactRepository.save(any(Contact.class))).thenReturn(saved);

        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Wael",
                                  "email":"wael@example.com",
                                  "company":"Emerald",
                                  "projectType":"Web",
                                  "message":"This is a valid test message"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.receivedAt").exists())
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    void missingRequiredFieldReturns400() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"wael@example.com",
                                  "message":"This is a valid test message"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void oversizedMessageReturns400() throws Exception {
        String message = "A".repeat(5001);

        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Wael",
                                  "email":"wael@example.com",
                                  "message":"%s"
                                }
                                """.formatted(message)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.request")
                        .value("Malformed JSON request"));
    }

    @Test
    void wrongContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void numericNameIsRejected() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":123,
                                  "email":"wael@example.com",
                                  "message":"This is a valid test message"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullRequiredFieldsAreRejected() throws Exception {
        for (String field : new String[]{"name", "email", "message"}) {
            String body = """
                    {
                      "name":"Wael",
                      "email":"wael@example.com",
                      "message":"This is a valid test message",
                      "%s":null
                    }
                    """.formatted(field);

            mockMvc.perform(post("/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void optionalNullFieldsAreAccepted() throws Exception {
        Contact saved = mock(Contact.class);

        when(saved.getId()).thenReturn(UUID.randomUUID());
        when(saved.getReceivedAt()).thenReturn(OffsetDateTime.now());
        when(contactRepository.save(any(Contact.class))).thenReturn(saved);

        for (String field : new String[]{"company", "projectType"}) {
            String body = """
                    {
                      "name":"Wael",
                      "email":"wael@example.com",
                      "message":"This is a valid test message",
                      "%s":null
                    }
                    """.formatted(field);

            mockMvc.perform(post("/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void rootJsonTypesAreRejected() throws Exception {
        for (String body : new String[]{"[]", "\"hello\"", "123", "true"}) {
            mockMvc.perform(post("/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getMethodIsRejected() throws Exception {
        mockMvc.perform(get("/contact"))
                .andExpect(status().isMethodNotAllowed());
    }
}
