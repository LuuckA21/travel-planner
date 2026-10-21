package me.luucka.travelplanner.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import me.luucka.travelplanner.auth.AppUserDetails;
import me.luucka.travelplanner.auth.AuthService;
import me.luucka.travelplanner.auth.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("test")
public class TestUsers {

    @Autowired private AuthService authService;
    @Autowired private UserAccountRepository users;
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    /** Creates a verified user; display name is the part of the email before '@'. */
    public UUID create(String email, String password) {
        return authService.createVerifiedUser(email, password, email.substring(0, email.indexOf('@'))).getId();
    }

    public RequestPostProcessor as(UUID userId) {
        return user(AppUserDetails.of(users.findById(userId).orElseThrow()));
    }

    /** Real login; returns the SESSION cookie to send back on later requests. */
    public Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON).content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
    }

    public String loginBody(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }
}
