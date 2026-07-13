package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
class RelationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void createCategory() throws Exception {
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Electronics"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }

    @Test
    @Order(2)
    void createProductWithCategory() throws Exception {
        // Create product — category relation set via direct DB for test simplicity
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Laptop", "price": 999.99, "stock": 50}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void listWithoutExpand() throws Exception {
        // Without expand, relations should NOT appear
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").doesNotExist());
    }

    @Test
    @Order(4)
    void listWithExpandUnknownField() throws Exception {
        // expand=nonexistent should be silently ignored
        mvc.perform(get("/api/products?expand=nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nonexistent").doesNotExist());
    }

    @Test
    @Order(5)
    void getByIdWithExpand() throws Exception {
        // For a product without a category assigned, expand=category returns null
        mvc.perform(get("/api/products/1?expand=category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value(nullValue()));
    }

    @Test
    @Order(6)
    void categoryListsProducts() throws Exception {
        // Category has @OneToMany to products
        mvc.perform(get("/api/categories?expand=products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Electronics"));
    }
}
