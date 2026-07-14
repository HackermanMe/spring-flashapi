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
class MultiTenantIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void requestWithoutTenantHeaderReturns400() throws Exception {
        mvc.perform(get("/api/tenantItems"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tenant context required"));
    }

    @Test
    @Order(2)
    void createItemForTenantA() throws Exception {
        mvc.perform(post("/api/tenantItems")
                        .header("X-Tenant-Id", "tenant-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Item for A"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-A"));
    }

    @Test
    @Order(3)
    void createItemForTenantB() throws Exception {
        mvc.perform(post("/api/tenantItems")
                        .header("X-Tenant-Id", "tenant-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Item for B"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-B"));
    }

    @Test
    @Order(4)
    void tenantASeesOnlyItsOwnItems() throws Exception {
        mvc.perform(get("/api/tenantItems")
                        .header("X-Tenant-Id", "tenant-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Item for A"));
    }

    @Test
    @Order(5)
    void tenantBSeesOnlyItsOwnItems() throws Exception {
        mvc.perform(get("/api/tenantItems")
                        .header("X-Tenant-Id", "tenant-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Item for B"));
    }

    @Test
    @Order(6)
    void tenantACannotReadTenantBItemById() throws Exception {
        mvc.perform(get("/api/tenantItems/2")
                        .header("X-Tenant-Id", "tenant-A"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void tenantACannotUpdateTenantBItem() throws Exception {
        mvc.perform(put("/api/tenantItems/2")
                        .header("X-Tenant-Id", "tenant-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Hacked"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    void tenantACannotDeleteTenantBItem() throws Exception {
        mvc.perform(delete("/api/tenantItems/2")
                        .header("X-Tenant-Id", "tenant-A"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void tenantBCanUpdateItsOwnItem() throws Exception {
        mvc.perform(put("/api/tenantItems/2")
                        .header("X-Tenant-Id", "tenant-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Updated B"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated B"));
    }

    @Test
    @Order(10)
    void tenantBCanDeleteItsOwnItem() throws Exception {
        mvc.perform(delete("/api/tenantItems/2")
                        .header("X-Tenant-Id", "tenant-B"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(11)
    void tenantCannotOverrideTenantFieldOnCreate() throws Exception {
        mvc.perform(post("/api/tenantItems")
                        .header("X-Tenant-Id", "tenant-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Sneaky", "tenantId": "tenant-B"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-A"));
    }
}
