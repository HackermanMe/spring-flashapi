package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SearchIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void setupData() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "MacBook Pro", "price": 2499.99, "stock": 10, "internalCode": "APPLE-001"}
                            """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "ThinkPad X1", "price": 1599.99, "stock": 25, "internalCode": "LENOVO-001"}
                            """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Surface Pro", "price": 1299.99, "stock": 15, "internalCode": "MS-001"}
                            """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "iPad Pro", "price": 1099.99, "stock": 30, "internalCode": "APPLE-002"}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void searchByNamePartial() throws Exception {
        mvc.perform(get("/api/products").param("search", "pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    @Order(3)
    void searchIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/products").param("search", "MACBOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("MacBook Pro"));
    }

    @Test
    @Order(4)
    void searchAcrossMultipleStringFields() throws Exception {
        // "APPLE" appears in internalCode field
        mvc.perform(get("/api/products").param("search", "APPLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(5)
    void searchWithNoResults() throws Exception {
        mvc.perform(get("/api/products").param("search", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @Order(6)
    void searchCombinedWithFilter() throws Exception {
        // Search for "pro" but filter price > 1500
        mvc.perform(get("/api/products")
                        .param("search", "pro")
                        .param("price.gt", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("MacBook Pro"));
    }

    @Test
    @Order(7)
    void searchCombinedWithPagination() throws Exception {
        mvc.perform(get("/api/products")
                        .param("search", "pro")
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    @Order(8)
    void searchCombinedWithSort() throws Exception {
        mvc.perform(get("/api/products")
                        .param("search", "pro")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].name").value("iPad Pro"))
                .andExpect(jsonPath("$.data[2].name").value("MacBook Pro"));
    }

    @Test
    @Order(9)
    void emptySearchReturnsAll() throws Exception {
        mvc.perform(get("/api/products").param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));
    }
}
