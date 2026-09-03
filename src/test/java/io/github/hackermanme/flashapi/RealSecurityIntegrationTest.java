package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.annotation.EnableFlashApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests with Spring Security filters ENABLED (addFilters defaults to true).
 * Simulates a real project with .anyRequest().authenticated().
 *
 * WriteProtectedItem: @FlashSecured(roles = "authenticated", write = "ADMIN")
 * → read = any authenticated user, write = ADMIN only
 */
@SpringBootTest(classes = RealSecurityIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class RealSecurityIntegrationTest {

    @SpringBootApplication
    @EnableCaching
    @EnableFlashApi(basePackages = "io.github.hackermanme.flashapi.entity")
    static class TestApp {

        @Configuration
        @EnableWebSecurity
        static class SecurityConfig {
            @Bean
            public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                        .csrf(csrf -> csrf.disable())
                        .authorizeHttpRequests(auth -> auth
                                .anyRequest().authenticated()
                        );
                return http.build();
            }
        }
    }

    @Autowired
    private MockMvc mvc;

    // Bug 2 scenario: anonymous user blocked by Spring Security before FlashAPI evaluates
    @Test
    @Order(1)
    @WithAnonymousUser
    void anonymousGetBlockedBySpringSecurityNotFlashApi() throws Exception {
        mvc.perform(get("/api/writeProtectedItems"))
                .andExpect(status().isForbidden());
    }

    // Bug 1 scenario: authenticated USER can read
    @Test
    @Order(2)
    @WithMockUser(username = "alice", roles = "USER")
    void authenticatedUserCanList() throws Exception {
        mvc.perform(get("/api/writeProtectedItems"))
                .andExpect(status().isOk());
    }

    // Bug 1 scenario: USER tries to CREATE → should be 403 from FlashAPI
    @Test
    @Order(3)
    @WithMockUser(username = "alice", roles = "USER")
    void userCannotCreateWithFiltersEnabled() throws Exception {
        mvc.perform(post("/api/writeProtectedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Should be denied"}
                            """))
                .andExpect(status().isForbidden());
    }

    // ADMIN can CREATE
    @Test
    @Order(4)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateWithFiltersEnabled() throws Exception {
        mvc.perform(post("/api/writeProtectedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Admin created"}
                            """))
                .andExpect(status().isCreated());
    }

    // USER tries to DELETE → should be 403
    @Test
    @Order(5)
    @WithMockUser(username = "alice", roles = "USER")
    void userCannotDeleteWithFiltersEnabled() throws Exception {
        mvc.perform(delete("/api/writeProtectedItems/1"))
                .andExpect(status().isForbidden());
    }

    // ADMIN can DELETE
    @Test
    @Order(6)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanDeleteWithFiltersEnabled() throws Exception {
        mvc.perform(delete("/api/writeProtectedItems/1"))
                .andExpect(status().isNoContent());
    }
}
