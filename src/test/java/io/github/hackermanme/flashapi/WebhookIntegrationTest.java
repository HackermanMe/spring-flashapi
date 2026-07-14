package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.webhook.WebhookDispatcher;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {TestApplication.class, WebhookIntegrationTest.WebhookTestConfig.class})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WebhookIntegrationTest {

    @TestConfiguration
    static class WebhookTestConfig {
        @Bean
        @Primary
        public WebhookDispatcher webhookDispatcher() {
            return Mockito.spy(new WebhookDispatcher(java.util.List.of(), 0, 5));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebhookDispatcher webhookDispatcher;

    @Test
    @Order(1)
    void createFiresWebhook() throws Exception {
        mvc.perform(post("/api/webhookItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Test Item"}
                            """))
                .andExpect(status().isCreated());

        verify(webhookDispatcher).dispatch(
                argThat(meta -> meta.entityName().equals("WebhookItem")),
                eq("CREATE"),
                any());
    }

    @Test
    @Order(2)
    void updateFiresWebhook() throws Exception {
        mvc.perform(put("/api/webhookItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Updated Item"}
                            """))
                .andExpect(status().isOk());

        verify(webhookDispatcher).dispatch(
                argThat(meta -> meta.entityName().equals("WebhookItem")),
                eq("UPDATE"),
                any());
    }

    @Test
    @Order(3)
    void deleteFiresWebhook() throws Exception {
        mvc.perform(delete("/api/webhookItems/1"))
                .andExpect(status().isNoContent());

        verify(webhookDispatcher).dispatch(
                argThat(meta -> meta.entityName().equals("WebhookItem")),
                eq("DELETE"),
                any());
    }

    @Test
    @Order(4)
    void listDoesNotFireWebhook() throws Exception {
        reset(webhookDispatcher);

        mvc.perform(get("/api/webhookItems"))
                .andExpect(status().isOk());

        verifyNoInteractions(webhookDispatcher);
    }
}
