package me.luucka.travelplanner.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.registration-open=false")
class RegistrationClosedTest extends IntegrationTest {

    @Test
    void registrationClosedIs403() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"luca@example.it\",\"password\":\"supersecret1\",\"displayName\":\"Luca\"}"))
                .andExpect(status().isForbidden());
    }
}
