package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecurityTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WriteProtectedIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreate() throws Exception {
        mvc.perform(post("/api/writeProtectedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Admin item"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @Order(2)
    @WithMockUser(username = "user1", roles = "USER")
    void userCanList() throws Exception {
        mvc.perform(get("/api/writeProtectedItems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(3)
    @WithMockUser(username = "user1", roles = "USER")
    void userCannotCreate() throws Exception {
        mvc.perform(post("/api/writeProtectedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "User item"}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "user1", roles = "USER")
    void userCannotDelete() throws Exception {
        mvc.perform(delete("/api/writeProtectedItems/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    @Order(5)
    @WithMockUser(username = "user1", roles = "USER")
    void userCannotUpdate() throws Exception {
        mvc.perform(put("/api/writeProtectedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Hacked"}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanDelete() throws Exception {
        mvc.perform(delete("/api/writeProtectedItems/1"))
                .andExpect(status().isNoContent());
    }
}
