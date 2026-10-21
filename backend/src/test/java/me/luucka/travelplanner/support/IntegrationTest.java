package me.luucka.travelplanner.support;

import me.luucka.travelplanner.TestcontainersConfiguration;
import me.luucka.travelplanner.auth.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TestUsers testUsers;
    @Autowired protected RecordingMailer mailer;
    @Autowired protected RateLimiter rateLimiter;
    @Autowired private WebApplicationContext context;

    // csrf() swaps the shared CsrfFilter's repository for a session-based one; restore production's
    @BeforeEach
    void restoreCsrfRepository() {
        WebTestUtils.setCsrfTokenRepository(new MockHttpServletRequest(context.getServletContext()),
                CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    // Grows as tables are added: TRUNCATE a, b, c CASCADE
    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE spring_session, spring_session_attributes, user_account, auth_token, trip, trip_destination CASCADE");
        mailer.clear();
        rateLimiter.reset();
    }
}
