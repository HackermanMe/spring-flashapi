package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.cache.FlashCacheManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CacheIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FlashCacheManager flashCacheManager;

    @Test
    @Order(1)
    void cacheManagerIsAvailable() {
        assertThat(flashCacheManager.isAvailable()).isTrue();
        assertThat(cacheManager).isNotNull();
    }

    @Test
    @Order(2)
    void createCachedItem() throws Exception {
        mvc.perform(post("/api/cachedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Cached Thing"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Cached Thing"));
    }

    @Test
    @Order(3)
    void listPopulatesCache() throws Exception {
        mvc.perform(get("/api/cachedItems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        var cache = cacheManager.getCache("flashapi:cachedItems");
        assertThat(cache).isNotNull();
        assertThat(cache.get("list:0:20:null:{}")).isNotNull();
    }

    @Test
    @Order(4)
    void getByIdPopulatesCache() throws Exception {
        mvc.perform(get("/api/cachedItems/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cached Thing"));

        var cache = cacheManager.getCache("flashapi:cachedItems");
        assertThat(cache).isNotNull();
        assertThat(cache.get("id:1")).isNotNull();
    }

    @Test
    @Order(5)
    void updateEvictsCache() throws Exception {
        // Cache should be populated from previous tests
        var cache = cacheManager.getCache("flashapi:cachedItems");
        assertThat(cache).isNotNull();
        assertThat(cache.get("list:0:20:null:{}")).isNotNull();

        mvc.perform(put("/api/cachedItems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Updated Thing"}
                            """))
                .andExpect(status().isOk());

        // Cache should be evicted
        assertThat(cache.get("list:0:20:null:{}")).isNull();
        assertThat(cache.get("id:1")).isNull();
    }

    @Test
    @Order(6)
    void listAfterUpdateReturnsUpdatedData() throws Exception {
        mvc.perform(get("/api/cachedItems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Updated Thing"));
    }

    @Test
    @Order(7)
    void deleteEvictsCache() throws Exception {
        // Populate cache
        mvc.perform(get("/api/cachedItems")).andExpect(status().isOk());
        var cache = cacheManager.getCache("flashapi:cachedItems");
        assertThat(cache.get("list:0:20:null:{}")).isNotNull();

        mvc.perform(delete("/api/cachedItems/1"))
                .andExpect(status().isNoContent());

        // Cache should be evicted
        assertThat(cache.get("list:0:20:null:{}")).isNull();
    }

    @Test
    @Order(8)
    void createEvictsCache() throws Exception {
        // Populate cache with empty list
        mvc.perform(get("/api/cachedItems")).andExpect(status().isOk());
        var cache = cacheManager.getCache("flashapi:cachedItems");
        assertThat(cache.get("list:0:20:null:{}")).isNotNull();

        mvc.perform(post("/api/cachedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "New Item"}
                            """))
                .andExpect(status().isCreated());

        // Cache should be evicted
        assertThat(cache.get("list:0:20:null:{}")).isNull();
    }

    @Test
    @Order(9)
    void nonCachedEntityDoesNotUseCache() throws Exception {
        // Category has cache = false (default)
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Electronics"}
                            """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/categories")).andExpect(status().isOk());

        var cache = cacheManager.getCache("flashapi:categories");
        // Cache object may exist but should have no entries since cache is disabled for Category
        if (cache != null) {
            assertThat(cache.get("list:0:20:null:{}")).isNull();
        }
    }
}
