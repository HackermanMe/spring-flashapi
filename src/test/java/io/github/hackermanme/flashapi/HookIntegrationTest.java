package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.hooks.TestHookListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class HookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestHookListener hookListener;

    @Test
    void testCreateHooksAreInvoked() throws Exception {
        hookListener.clear();

        String json = """
                {
                    "name": "Test Product"
                }
                """;

        mockMvc.perform(post("/api/hookTestEntities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test Product"));

        assertThat(hookListener.events)
                .containsExactly("beforeCreate:Test Product", "afterCreate:Test Product");
    }

    @Test
    void testUpdateHooksAreInvoked() throws Exception {
        hookListener.clear();

        // Create first
        String createJson = """
                {
                    "name": "Original"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/hookTestEntities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = createResponse.replaceAll(".*\"id\":(\\d+).*", "$1");
        hookListener.clear();

        // Update
        String updateJson = """
                {
                    "name": "Updated"
                }
                """;

        mockMvc.perform(put("/api/hookTestEntities/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));

        assertThat(hookListener.events)
                .containsExactly("beforeUpdate:Updated", "afterUpdate:Updated");
    }

    @Test
    void testDeleteHooksAreInvoked() throws Exception {
        hookListener.clear();

        // Create first
        String createJson = """
                {
                    "name": "To Delete"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/hookTestEntities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = createResponse.replaceAll(".*\"id\":(\\d+).*", "$1");
        hookListener.clear();

        // Delete
        mockMvc.perform(delete("/api/hookTestEntities/" + id))
                .andExpect(status().isNoContent());

        assertThat(hookListener.events)
                .containsExactly("beforeDelete:To Delete", "afterDelete:To Delete");
    }

    @Test
    void testHookCanAccessRequestHeaders() throws Exception {
        hookListener.clear();

        String json = """
                {
                    "name": "Header Test"
                }
                """;

        mockMvc.perform(post("/api/hookTestEntities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Test-Header", "TestValue")
                        .content(json))
                .andExpect(status().isCreated());

        assertThat(hookListener.requestHeaders)
                .contains("X-Test-Header:TestValue");
    }
}
