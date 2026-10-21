package me.luucka.travelplanner.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;

@TestPropertySource(properties = "app.registration-open=true")
class RegistrationTest extends IntegrationTest {

    static final String EMAIL = "luca@example.it";
    static final String PASSWORD = "supersecret1";

    @Autowired JsonMapper json;

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mvc.perform(post(path).with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register", Map.of("email", email, "password", password, "displayName", "Luca"));
    }

    private String tokenSentTo(String email) {
        String link = mailer.lastLinkTo(email);
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    void registerSendsVerificationAndBlocksLoginUntilVerified() throws Exception {
        register("Luca@Example.IT", PASSWORD).andExpect(status().isCreated());

        assertThat(mailer.lastLinkTo(EMAIL)).startsWith("http://localhost:5173/verify-email?token=");
        postJson("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        postJson("/api/auth/verify-email", Map.of("token", tokenSentTo(EMAIL))).andExpect(status().isNoContent());

        postJson("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void duplicateEmailInDifferentCaseIs409() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        register("Luca@Example.IT", PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void passwordLengthIsValidated() throws Exception {
        register(EMAIL, "123456789").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        register(EMAIL, "a".repeat(129)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        register(EMAIL, "a".repeat(128)).andExpect(status().isCreated());
    }

    @Test
    void verificationTokenIsSingleUse() throws Exception {
        register(EMAIL, PASSWORD);
        String token = tokenSentTo(EMAIL);
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isNoContent());
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownTokenIs400() throws Exception {
        postJson("/api/auth/verify-email", Map.of("token", "nope")).andExpect(status().isBadRequest());
    }

    @Test
    void expiredVerificationTokenIs400() throws Exception {
        register(EMAIL, PASSWORD);
        jdbc.update("UPDATE auth_token SET expires_at = now() - interval '1 minute'");
        postJson("/api/auth/verify-email", Map.of("token", tokenSentTo(EMAIL))).andExpect(status().isBadRequest());
    }

    @Test
    void resendVerificationIs202ForUnknownEmail() throws Exception {
        postJson("/api/auth/resend-verification", Map.of("email", "nobody@example.it")).andExpect(status().isAccepted());
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void resendVerificationSendsNewWorkingLinkOnlyWhileUnverified() throws Exception {
        register(EMAIL, PASSWORD);
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        assertThat(mailer.sent()).hasSize(2);

        postJson("/api/auth/verify-email", Map.of("token", tokenSentTo(EMAIL))).andExpect(status().isNoContent());
        postJson("/api/auth/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        assertThat(mailer.sent()).hasSize(2);
    }

    @Test
    void tokenIsStoredHashed() throws Exception {
        register(EMAIL, PASSWORD);
        String stored = jdbc.queryForObject("SELECT token_hash FROM auth_token", String.class);
        assertThat(stored).isNotEqualTo(tokenSentTo(EMAIL)).hasSize(64);
    }
}
