package io.github.hackermanme.flashapi;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class ExportIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void setupTestData() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Laptop", "price": 999.99, "stock": 50}
                            """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Phone", "price": 599.99, "stock": 100}
                            """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Tablet", "price": 399.99, "stock": 75}
                            """))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void exportCsv() throws Exception {
        MvcResult result = mvc.perform(get("/api/products/export?format=csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"products_")))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        // BOM + header + 3 data rows
        assertThat(lines.length).isEqualTo(4);
        assertThat(lines[0]).contains("name");
        assertThat(lines[0]).contains("price");
        assertThat(lines[1]).contains("Laptop");
        assertThat(lines[2]).contains("Phone");
        assertThat(lines[3]).contains("Tablet");
    }

    @Test
    @Order(3)
    void exportCsvWithFilter() throws Exception {
        MvcResult result = mvc.perform(get("/api/products/export?format=csv&price.gte=500"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        // header + 2 data rows (Laptop + Phone)
        assertThat(lines.length).isEqualTo(3);
        assertThat(csv).doesNotContain("Tablet");
    }

    @Test
    @Order(4)
    void exportExcel() throws Exception {
        mvc.perform(get("/api/products/export?format=xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".xlsx")));
    }

    @Test
    @Order(5)
    void exportPdf() throws Exception {
        mvc.perform(get("/api/products/export?format=pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test
    @Order(6)
    void exportInvalidFormat() throws Exception {
        mvc.perform(get("/api/products/export?format=xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void exportMissingFormat() throws Exception {
        mvc.perform(get("/api/products/export"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void exportCsvWithSort() throws Exception {
        MvcResult result = mvc.perform(get("/api/products/export?format=csv&sort=price,desc"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        // First data row should be the most expensive
        assertThat(lines[1]).contains("Laptop");
        assertThat(lines[3]).contains("Tablet");
    }

    @Test
    @Order(9)
    void exportCsvExcludesHiddenFields() throws Exception {
        MvcResult result = mvc.perform(get("/api/products/export?format=csv"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        // internalCode is @FlashHidden, secretToken is @FlashHidden
        assertThat(csv).doesNotContain("internalCode");
        assertThat(csv).doesNotContain("secretToken");
    }
}
