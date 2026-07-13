package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void specEndpointReturnsValidOpenApi() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.info.title").value("FlashAPI"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    @Order(2)
    void specContainsProductPaths() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete").exists());
    }

    @Test
    @Order(3)
    void specContainsProductSchemas() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.components.schemas.ProductResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ProductListResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ProductCreateInput").exists())
                .andExpect(jsonPath("$.components.schemas.ProductUpdateInput").exists());
    }

    @Test
    @Order(4)
    void specContainsListParameters() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'size')]").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'sort')]").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'search')]").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.parameters[?(@.name == 'expand')]").exists());
    }

    @Test
    @Order(5)
    void specContainsExportPath() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/products/export']").exists())
                .andExpect(jsonPath("$.paths['/api/products/export'].get").exists());
    }

    @Test
    @Order(6)
    void specContainsBulkPath() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/products/bulk']").exists())
                .andExpect(jsonPath("$.paths['/api/products/bulk'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products/bulk'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/bulk'].delete").exists());
    }

    @Test
    @Order(7)
    void specSchemaHasCorrectFieldTypes() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.components.schemas.ProductCreateInput.properties.name.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ProductCreateInput.properties.price.type").value("number"));
    }

    @Test
    @Order(8)
    void swaggerUiIsServed() throws Exception {
        mvc.perform(get("/api/docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("swagger-ui")))
                .andExpect(content().string(containsString("/api/docs/openapi.json")));
    }

    @Test
    @Order(9)
    void swaggerUiIndexHtmlAlsoWorks() throws Exception {
        mvc.perform(get("/api/docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
    }

    @Test
    @Order(10)
    void specContainsTags() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/products'].get.tags[0]").value("Product"));
    }

    @Test
    @Order(11)
    void specContainsCategoryEntity() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(jsonPath("$.paths['/api/categories']").exists())
                .andExpect(jsonPath("$.components.schemas.CategoryResponse").exists());
    }
}
