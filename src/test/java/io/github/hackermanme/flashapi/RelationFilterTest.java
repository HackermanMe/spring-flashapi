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
class RelationFilterTest {

    @Autowired
    private MockMvc mvc;

    private Long categoryId;

    @Test
    @Order(1)
    void createCategoryAndProduct() throws Exception {
        // Create category
        var catResponse = mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Electronics\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String catBody = catResponse.getResponse().getContentAsString();
        categoryId = Long.valueOf(catBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Create product with category relation
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Laptop\", \"price\": 999.99, \"categoryId\": " + categoryId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @Order(2)
    void filterByCategoryId() throws Exception {
        mvc.perform(get("/api/products?category.id=" + categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Laptop"))
                .andExpect(jsonPath("$.data[0].price").value(999.99));
    }

    @Test
    @Order(3)
    void filterByCategoryIdWithOperator() throws Exception {
        mvc.perform(get("/api/products?category.id[gt]=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Laptop"));
    }

    @Test
    @Order(4)
    void filterByNonExistentRelationIsIgnored() throws Exception {
        // Should not crash, just ignore invalid relation
        mvc.perform(get("/api/products?nonexistent.id=1"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void deepNestedFilterRejected() throws Exception {
        // Should reject deep nesting (e.g., category.parent.id)
        mvc.perform(get("/api/products?category.parent.id=1"))
                .andExpect(status().isBadRequest());
    }
}
