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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CrudIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void createProduct() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name": "Laptop",
                                "price": 999.99,
                                "stock": 50,
                                "internalCode": "LAP-001"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop"))
                .andExpect(jsonPath("$.data.price").value(999.99))
                .andExpect(jsonPath("$.data.stock").value(50))
                .andExpect(jsonPath("$.data.internalCode").doesNotExist())
                .andExpect(jsonPath("$.data.secretToken").doesNotExist());
    }

    @Test
    @Order(2)
    void listProducts() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Laptop"))
                .andExpect(jsonPath("$.data[0].internalCode").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @Order(3)
    void getProductById() throws Exception {
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop"))
                .andExpect(jsonPath("$.data.internalCode").doesNotExist())
                .andExpect(jsonPath("$.data.secretToken").doesNotExist());
    }

    @Test
    @Order(4)
    void getProductNotFound() throws Exception {
        mvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void updateProduct() throws Exception {
        mvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name": "Gaming Laptop",
                                "price": 1499.99
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.data.price").value(1499.99))
                .andExpect(jsonPath("$.data.stock").value(50));
    }

    @Test
    @Order(6)
    void readOnlyFieldIgnoredInCreate() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": 999,
                                "name": "Phone",
                                "price": 599.99,
                                "stock": 100,
                                "createdAt": "2020-01-01T00:00:00Z"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.createdAt").value(nullValue()));
    }

    @Test
    @Order(7)
    void filterByField() throws Exception {
        mvc.perform(get("/api/products?name.contains=laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Gaming Laptop"));
    }

    @Test
    @Order(8)
    void filterByPrice() throws Exception {
        mvc.perform(get("/api/products?price.gte=1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Gaming Laptop"));
    }

    @Test
    @Order(9)
    void sortByName() throws Exception {
        mvc.perform(get("/api/products?sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.data[1].name").value("Phone"));
    }

    @Test
    @Order(10)
    void softDeleteProduct() throws Exception {
        mvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        // Should not appear in normal list
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Phone"));
    }

    @Test
    @Order(11)
    void listDeletedProducts() throws Exception {
        mvc.perform(get("/api/products?deleted=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Gaming Laptop"));
    }

    @Test
    @Order(12)
    void restoreProduct() throws Exception {
        mvc.perform(post("/api/products/1/restore"))
                .andExpect(status().isNoContent());

        // Should reappear in normal list
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(13)
    void auditHistory() throws Exception {
        mvc.perform(get("/api/products/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }
}
