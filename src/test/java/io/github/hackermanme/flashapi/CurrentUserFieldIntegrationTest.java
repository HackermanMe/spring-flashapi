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
class CurrentUserFieldIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    @WithMockUser(username = "1", roles = "USER")
    void createAuthorFirst() throws Exception {
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
    @WithMockUser(username = "1", roles = "USER")
    void createPostAutoInjectsAuthor() throws Exception {
        mvc.perform(post("/api/blogPosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "My first post"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("My first post"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "1", roles = "USER")
    void getPostShowsAuthorViaExpand() throws Exception {
        mvc.perform(get("/api/blogPosts/1?expand=author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value(1))
                .andExpect(jsonPath("$.data.author.name").value("Alice"));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "1", roles = "USER")
    void createPostIgnoresClientAuthorId() throws Exception {
        mvc.perform(post("/api/blogPosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Spoofed post", "authorId": 999}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2));

        mvc.perform(get("/api/blogPosts/2?expand=author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value(1));
    }

    @Test
    @Order(5)
    @WithMockUser(username = "1", roles = "USER")
    void updateCannotChangeAuthor() throws Exception {
        mvc.perform(put("/api/blogPosts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Updated title", "authorId": 999}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"));

        mvc.perform(get("/api/blogPosts/1?expand=author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value(1));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "2", roles = "USER")
    void differentUserGetsTheirOwnAuthor() throws Exception {
        mvc.perform(post("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Bob"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2));

        mvc.perform(post("/api/blogPosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Bob's post"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(3));

        mvc.perform(get("/api/blogPosts/3?expand=author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value(2))
                .andExpect(jsonPath("$.data.author.name").value("Bob"));
    }
}
