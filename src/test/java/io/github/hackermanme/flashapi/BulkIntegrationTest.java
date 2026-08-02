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
class BulkIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void bulkCreate() throws Exception {
        mvc.perform(post("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [
                                {"name": "Laptop", "price": 999.99, "stock": 50},
                                {"name": "Phone", "price": 599.99, "stock": 100},
                                {"name": "Tablet", "price": 399.99, "stock": 75}
                            ]
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meta.succeeded").value(3))
                .andExpect(jsonPath("$.meta.failed").value(0))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].name").value("Laptop"))
                .andExpect(jsonPath("$.data[1].name").value("Phone"))
                .andExpect(jsonPath("$.data[2].name").value("Tablet"));
    }

    @Test
    @Order(2)
    void bulkCreatePartialFailure() throws Exception {
        mvc.perform(post("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [
                                {"name": "Monitor", "price": 300.00, "stock": 20},
                                {"name": null, "price": 50.00, "stock": 10}
                            ]
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meta.succeeded").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    @Order(3)
    void bulkUpdate() throws Exception {
        mvc.perform(put("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [
                                {"id": 1, "name": "Gaming Laptop", "price": 1499.99},
                                {"id": 2, "name": "Smartphone", "price": 699.99}
                            ]
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(2))
                .andExpect(jsonPath("$.meta.failed").value(0))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.data[1].name").value("Smartphone"));
    }

    @Test
    @Order(4)
    void bulkUpdateWithMissingId() throws Exception {
        mvc.perform(put("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [
                                {"id": 1, "name": "Laptop Pro"},
                                {"name": "No ID Product"}
                            ]
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(1))
                .andExpect(jsonPath("$.meta.failed").value(1));
    }

    @Test
    @Order(5)
    void bulkUpdateNotFound() throws Exception {
        mvc.perform(put("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [
                                {"id": 999, "name": "Ghost Product"}
                            ]
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(0))
                .andExpect(jsonPath("$.meta.failed").value(1));
    }

    @Test
    @Order(6)
    void bulkDelete() throws Exception {
        mvc.perform(delete("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(2))
                .andExpect(jsonPath("$.meta.failed").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @Order(7)
    void bulkDeleteNotFound() throws Exception {
        mvc.perform(delete("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[998, 999]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(0))
                .andExpect(jsonPath("$.meta.failed").value(2));
    }

    @Test
    @Order(8)
    void bulkCreateEmptyBody() throws Exception {
        mvc.perform(post("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    void bulkDeleteRespectsSoftDelete() throws Exception {
        mvc.perform(post("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            [{"name": "Temp Product", "price": 10.00, "stock": 1}]
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].id").exists());

        mvc.perform(delete("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[3]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.succeeded").value(1));

        mvc.perform(get("/api/products?deleted=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }
}
