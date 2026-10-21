package me.luucka.travelplanner.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RateLimitTest extends IntegrationTest {

    private MockHttpServletRequestBuilder login(String email, String forwardedFor) {
        return post("/api/auth/login").with(csrf()).header("X-Forwarded-For", forwardedFor)
                .contentType(APPLICATION_JSON).content(testUsers.loginBody(email, "wrongpassword"));
    }

    @Test
    void eleventhLoginAttemptFromSameIpIs429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("a" + i + "@example.it", "203.0.113.1")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("a11@example.it", "203.0.113.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void loginLimitIsPerEmailAcrossIps() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login(" Luca@Example.IT ", "203.0.113." + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("luca@example.it", "203.0.113.99")).andExpect(status().isTooManyRequests());
    }

    @Test
    void sixthForgotPasswordPerHourIs429() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(forgot()).andExpect(status().isAccepted());
        }
        mvc.perform(forgot()).andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedForIsUsedAsClientIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("a" + i + "@example.it", "203.0.113.1")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("other@example.it", "203.0.113.2")).andExpect(status().isUnauthorized());
    }

    @Test
    void spoofedLeftmostForwardedForDoesNotBypassIpLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("a" + i + "@example.it", "198.51.100." + i + ", 203.0.113.9"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("a11@example.it", "198.51.100.200, 203.0.113.9")).andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder forgot() {
        return post("/api/auth/forgot-password").with(csrf())
                .contentType(APPLICATION_JSON).content("{\"email\":\"luca@example.it\"}");
    }
}
