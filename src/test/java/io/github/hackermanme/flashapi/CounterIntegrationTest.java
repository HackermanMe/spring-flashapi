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
class CounterIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void createArticle() throws Exception {
        mvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Test article"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    @Order(2)
    void createLikeIncrementsCounter() throws Exception {
        mvc.perform(post("/api/articleLikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"articleId": 1, "userId": "alice"}
                            """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));
    }

    @Test
    @Order(3)
    void secondLikeIncrementsAgain() throws Exception {
        mvc.perform(post("/api/articleLikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"articleId": 1, "userId": "bob"}
                            """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(2));
    }

    @Test
    @Order(4)
    void deleteLikeDecrementsCounter() throws Exception {
        mvc.perform(delete("/api/articleLikes/1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));
    }

    @Test
    @Order(5)
    void counterFieldIsReadOnly() throws Exception {
        mvc.perform(put("/api/articles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title": "Updated", "likeCount": 999}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated"));

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));
    }
}
