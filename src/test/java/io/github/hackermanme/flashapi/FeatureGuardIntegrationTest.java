package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FeatureGuardIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void createWithinLimitSucceeds() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/guardedItems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"item" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @Order(2)
    void createBeyondLimitReturns403() throws Exception {
        mvc.perform(post("/api/guardedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"item4\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.entity").value("GuardedItem"))
                .andExpect(jsonPath("$.limit").value(3));
    }

    @Test
    @Order(3)
    void bulkCreateBeyondLimitReturns403() throws Exception {
        mvc.perform(delete("/api/guardedItems/1"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/guardedItems/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\": \"a\"}, {\"name\": \"b\"}, {\"name\": \"c\"}]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value(3));
    }

    @Test
    @Order(4)
    void readOperationsAreNotGuarded() throws Exception {
        mvc.perform(get("/api/guardedItems"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void nonGuardedEntityIsUnlimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"cat" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
    }
}
