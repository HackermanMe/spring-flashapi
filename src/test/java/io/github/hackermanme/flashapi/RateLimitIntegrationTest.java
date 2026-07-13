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
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void requestsWithinLimitSucceed() throws Exception {
        // RateLimitedItem allows 3 requests per window
        mvc.perform(get("/api/rateLimitedItems"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rateLimitedItems"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rateLimitedItems"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void requestBeyondLimitReturns429() throws Exception {
        // 4th request should be rejected
        mvc.perform(get("/api/rateLimitedItems"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.retryAfter").value(60));
    }

    @Test
    @Order(3)
    void nonRateLimitedEntityIsNotAffected() throws Exception {
        // Category does not have rate limiting
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/categories"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @Order(4)
    void rateLimitAppliesToAllMethods() throws Exception {
        // Create should also be rate limited (already at limit from previous tests)
        mvc.perform(post("/api/rateLimitedItems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Test"}
                            """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @Order(5)
    void xForwardedForIsRespected() throws Exception {
        // Different IP should have its own bucket
        mvc.perform(get("/api/rateLimitedItems")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rateLimitedItems")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rateLimitedItems")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());
        // 4th from same IP
        mvc.perform(get("/api/rateLimitedItems")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @Order(6)
    void differentIpHasSeparateBucket() throws Exception {
        // New IP should not be rate limited
        mvc.perform(get("/api/rateLimitedItems")
                        .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isOk());
    }
}
