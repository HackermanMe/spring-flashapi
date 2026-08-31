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
class OwnerSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanCreateItem() throws Exception {
        mvc.perform(post("/api/ownedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Alice's item", "ownerId": "alice"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @Order(2)
    @WithMockUser(username = "bob", roles = "USER")
    void bobCreatesHisItem() throws Exception {
        mvc.perform(post("/api/ownedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Bob's item", "ownerId": "bob"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanUpdateOwnItem() throws Exception {
        mvc.perform(put("/api/ownedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Alice's updated item"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alice's updated item"));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "bob", roles = "USER")
    void nonOwnerCannotUpdateOthersItem() throws Exception {
        mvc.perform(put("/api/ownedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Hacked by Bob"}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only modify your own resources"));
    }

    @Test
    @Order(5)
    @WithMockUser(username = "bob", roles = "USER")
    void nonOwnerCannotDeleteOthersItem() throws Exception {
        mvc.perform(delete("/api/ownedItems/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminCanUpdateAnyItem() throws Exception {
        mvc.perform(put("/api/ownedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Admin override"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminCanDeleteAnyItem() throws Exception {
        mvc.perform(delete("/api/ownedItems/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanDeleteOwnItem() throws Exception {
        mvc.perform(delete("/api/ownedItems/1"))
                .andExpect(status().isNoContent());
    }
}
