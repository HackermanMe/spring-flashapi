package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.security.FlashPrincipalResolver;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {SecurityTestApplication.class, PrincipalResolverIntegrationTest.ResolverConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PrincipalResolverIntegrationTest {

    @TestConfiguration
    static class ResolverConfig {
        @Bean
        public FlashPrincipalResolver flashPrincipalResolver() {
            return auth -> {
                String name = auth.getName();
                return switch (name) {
                    case "alice" -> 1L;
                    case "bob" -> 2L;
                    default -> name;
                };
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    @WithMockUser(username = "alice", roles = "USER")
    void createAuthorForAlice() throws Exception {
        mvc.perform(post("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Alice"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @Order(2)
    @WithMockUser(username = "bob", roles = "USER")
    void createAuthorForBob() throws Exception {
        mvc.perform(post("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Bob"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "alice", roles = "USER")
    void aliceCreatesPost() throws Exception {
        mvc.perform(post("/api/blogPosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Alice's post"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanUpdateWithResolver() throws Exception {
        mvc.perform(put("/api/blogPosts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Alice's updated post"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Alice's updated post"));
    }

    @Test
    @Order(5)
    @WithMockUser(username = "bob", roles = "USER")
    void nonOwnerCannotUpdateWithResolver() throws Exception {
        mvc.perform(put("/api/blogPosts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Hacked by Bob"}
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only modify your own resources"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "bob", roles = "USER")
    void nonOwnerCannotDeleteWithResolver() throws Exception {
        mvc.perform(delete("/api/blogPosts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminBypassesOwnerCheckWithResolver() throws Exception {
        mvc.perform(put("/api/blogPosts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Admin override"}
                            """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanDeleteWithResolver() throws Exception {
        mvc.perform(delete("/api/blogPosts/1"))
                .andExpect(status().isNoContent());
    }
}
