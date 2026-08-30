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
class FieldSelectionTest {

    @Autowired
    private MockMvc mvc;

    private Long productId;

    @Test
    @Order(1)
    void createProduct() throws Exception {
        var response = mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test Product\", \"price\": 99.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        productId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @Order(2)
    void listWithFieldSelection() throws Exception {
        mvc.perform(get("/api/products?fields=id,name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].price").doesNotExist()); // price should be excluded
    }

    @Test
    @Order(3)
    void getByIdWithFieldSelection() throws Exception {
        // First get the ID from list
        var response = mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        String body = response.getResponse().getContentAsString();
        Long id = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/products/" + id + "?fields=name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Product"))
                .andExpect(jsonPath("$.data.id").doesNotExist()) // id should be excluded
                .andExpect(jsonPath("$.data.price").doesNotExist());
    }

    @Test
    @Order(4)
    void fieldSelectionIgnoresInvalidFields() throws Exception {
        mvc.perform(get("/api/products?fields=name,nonexistent,alsoInvalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].nonexistent").doesNotExist()) // invalid field ignored
                .andExpect(jsonPath("$.data[0].alsoInvalid").doesNotExist());
    }

    @Test
    @Order(5)
    void emptyFieldsReturnsAllFields() throws Exception {
        mvc.perform(get("/api/products?fields="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].price").exists());
    }
}
