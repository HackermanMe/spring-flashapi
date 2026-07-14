package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests @FlashSecured authorization rules.
 *
 * SecuredItem annotation: @FlashSecured(roles = "ADMIN", read = "USER", create = "EDITOR", delete = "ADMIN")
 *
 * Resolution:
 * - LIST → read = "USER"
 * - READ → read = "USER"
 * - CREATE → create = "EDITOR"
 * - UPDATE → write (empty) → roles = "ADMIN"
 * - DELETE → delete = "ADMIN"
 */
@SpringBootTest(classes = SecurityTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void noAuthReturns401OnList() throws Exception {
        mvc.perform(get("/api/securedItems"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @WithAnonymousUser
    void anonymousReturns401OnList() throws Exception {
        mvc.perform(get("/api/securedItems"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @WithMockUser(roles = "USER")
    void userCanList() throws Exception {
        mvc.perform(get("/api/securedItems"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    @WithMockUser(roles = "USER")
    void userCannotCreate() throws Exception {
        mvc.perform(post("/api/securedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Denied", "description": "x"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @WithMockUser(roles = "EDITOR")
    void editorCanCreate() throws Exception {
        mvc.perform(post("/api/securedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Item1", "description": "created by editor"}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(6)
    @WithMockUser(roles = "USER")
    void userCanRead() throws Exception {
        mvc.perform(get("/api/securedItems/1"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    @WithMockUser(roles = "USER")
    void userCannotUpdate() throws Exception {
        mvc.perform(put("/api/securedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Hacked", "description": "no"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdate() throws Exception {
        mvc.perform(put("/api/securedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Updated", "description": "by admin"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    @WithMockUser(roles = "EDITOR")
    void editorCannotDelete() throws Exception {
        mvc.perform(delete("/api/securedItems/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @WithMockUser(roles = "ADMIN")
    void adminCanDelete() throws Exception {
        mvc.perform(delete("/api/securedItems/1"))
                .andExpect(status().isNoContent());
    }
}
