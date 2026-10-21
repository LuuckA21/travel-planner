package me.luucka.travelplanner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@TestPropertySource(properties = "app.registration-open=true")
class PasswordResetTest extends IntegrationTest {

    static final String EMAIL = "luca@example.it";
    static final String OLD = "oldpassword1";
    static final String NEW = "newpassword2";

    @Autowired JsonMapper json;

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mvc.perform(post(path).with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/auth/forgot-password", Map.of("email", email));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return postJson("/api/auth/reset-password", Map.of("token", token, "password", password));
    }

    private String tokenSentTo(String email) {
        String link = mailer.lastLinkTo(email);
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private ResultActions me(Cookie session) throws Exception {
        return mvc.perform(get("/api/auth/me").cookie(session));
    }

    @Test
    void resetChangesPasswordAndClosesAllSessions() throws Exception {
        testUsers.create(EMAIL, OLD);
        Cookie first = testUsers.login(EMAIL, OLD);
        Cookie second = testUsers.login(EMAIL, OLD);
        me(first).andExpect(status().isOk());
        me(second).andExpect(status().isOk());

        forgot(EMAIL).andExpect(status().isAccepted());
        assertThat(mailer.lastLinkTo(EMAIL)).startsWith("http://localhost:5173/reset-password?token=");
        reset(tokenSentTo(EMAIL), NEW).andExpect(status().isNoContent());

        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", OLD)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", NEW)).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordResponseIsSameForUnknownEmail() throws Exception {
        testUsers.create(EMAIL, OLD);
        MvcResult known = forgot(EMAIL).andReturn();
        mailer.clear();
        MvcResult unknown = forgot("nobody@example.it").andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(202).isEqualTo(known.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(known.getResponse().getContentAsString());
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void forgotPasswordWorksWithDifferentCaseEmail() throws Exception {
        testUsers.create(EMAIL, OLD);
        forgot(" Luca@Example.IT ").andExpect(status().isAccepted());
        assertThat(mailer.sent()).hasSize(1);
        reset(tokenSentTo(EMAIL), NEW).andExpect(status().isNoContent());
    }

    @Test
    void resetTokenExpiresAfterOneHour() throws Exception {
        testUsers.create(EMAIL, OLD);
        forgot(EMAIL);
        long ttlMinutes = jdbc.queryForObject(
                "SELECT round(extract(epoch FROM expires_at - now()) / 60) FROM auth_token", Long.class);
        assertThat(ttlMinutes).isBetween(59L, 60L);

        jdbc.update("UPDATE auth_token SET expires_at = now() - interval '1 minute'");
        reset(tokenSentTo(EMAIL), NEW).andExpect(status().isBadRequest());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", OLD)).andExpect(status().isOk());
    }

    @Test
    void resetTokenIsSingleUse() throws Exception {
        testUsers.create(EMAIL, OLD);
        forgot(EMAIL);
        String token = tokenSentTo(EMAIL);
        reset(token, NEW).andExpect(status().isNoContent());
        reset(token, "thirdpassword3").andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailTokenCannotResetPassword() throws Exception {
        postJson("/api/auth/register", Map.of("email", EMAIL, "password", OLD, "displayName", "Luca"))
                .andExpect(status().isCreated());
        reset(tokenSentTo(EMAIL), NEW).andExpect(status().isBadRequest());
    }

    @Test
    void resetTokenCannotVerifyEmail() throws Exception {
        postJson("/api/auth/register", Map.of("email", EMAIL, "password", OLD, "displayName", "Luca"))
                .andExpect(status().isCreated());
        forgot(EMAIL).andExpect(status().isAccepted());
        postJson("/api/auth/verify-email", Map.of("token", tokenSentTo(EMAIL))).andExpect(status().isBadRequest());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", OLD)).andExpect(status().isForbidden());
    }

    @Test
    void resetVerifiesUnverifiedEmail() throws Exception {
        postJson("/api/auth/register", Map.of("email", EMAIL, "password", OLD, "displayName", "Luca"))
                .andExpect(status().isCreated());
        forgot(EMAIL).andExpect(status().isAccepted());
        reset(tokenSentTo(EMAIL), NEW).andExpect(status().isNoContent());
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", NEW)).andExpect(status().isOk());
    }

    @Test
    void resetPasswordLengthIsValidated() throws Exception {
        testUsers.create(EMAIL, OLD);
        forgot(EMAIL);
        reset(tokenSentTo(EMAIL), "123456789").andExpect(status().isBadRequest());
    }
}
