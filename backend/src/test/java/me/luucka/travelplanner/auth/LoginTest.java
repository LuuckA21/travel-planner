package me.luucka.travelplanner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class LoginTest extends IntegrationTest {

    static final String EMAIL = "luca@example.it";
    static final String PASSWORD = "supersecret1";

    @MockitoSpyBean UserAccountRepository users;

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").with(csrf())
                .contentType(APPLICATION_JSON).content(testUsers.loginBody(email, password));
    }

    @Test
    void loginWithValidCredentialsCreatesSession() throws Exception {
        UUID id = testUsers.create(EMAIL, PASSWORD);
        Cookie session = mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("luca"))
                .andReturn().getResponse().getCookie("SESSION");

        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        mvc.perform(login(" Luca@Example.IT ", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void loginRotatesSessionId() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        Cookie before = testUsers.login(EMAIL, PASSWORD);
        Cookie after = mvc.perform(login(EMAIL, PASSWORD).cookie(before))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mvc.perform(get("/api/auth/me").cookie(before)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(after)).andExpect(status().isOk());
    }

    @Test
    void newHashesArePbkdf2AndBcryptHashesStillMatch() throws Exception {
        UUID id = testUsers.create(EMAIL, PASSWORD);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id = ?", String.class, id))
                .startsWith("{pbkdf2@SpringSecurity_v5_8}");

        jdbc.update("UPDATE user_account SET password_hash = ? WHERE id = ?",
                "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD), id);
        mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void userLookupFailureIs500NotUnauthorized() throws Exception {
        doThrow(new DataAccessResourceFailureException("database down")).when(users).findByEmail(any());
        mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        mvc.perform(login(EMAIL, "wrongpassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
        mvc.perform(login("nobody@example.it", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedUserIs403WithCode() throws Exception {
        UUID id = testUsers.create(EMAIL, PASSWORD);
        jdbc.update("UPDATE user_account SET email_verified_at = NULL WHERE id = ?", id);
        mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(result -> assertThat(result.getResponse().getCookie("SESSION")).isNull());
    }

    @Test
    void meWithoutSessionIs401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void stateChangingRequestWithoutCsrfIs403() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        Cookie session = testUsers.login(EMAIL, PASSWORD);
        mvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF"));
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        Cookie session = testUsers.login(EMAIL, PASSWORD);
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionCookieIsHttpOnlyAndSecureBehindHttps() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        var headers = mvc.perform(login(EMAIL, PASSWORD).header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders("Set-Cookie");

        assertThat(headers).filteredOn(h -> h.startsWith("SESSION=")).singleElement()
                .satisfies(h -> assertThat(h).contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=2592000"));
    }

    @Test
    void configReportsRegistrationClosedByDefault() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(false));
    }

    @Test
    void spaCsrfCookieIsIssuedAndAcceptedAsHeader() throws Exception {
        testUsers.create(EMAIL, PASSWORD);
        Cookie xsrf = mvc.perform(get("/api/auth/config"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(APPLICATION_JSON).content(testUsers.loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }
}
