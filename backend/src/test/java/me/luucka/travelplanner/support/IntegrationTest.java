package me.luucka.travelplanner.support;

import me.luucka.travelplanner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    // Grows as tables are added: TRUNCATE a, b, c CASCADE
    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE spring_session, spring_session_attributes CASCADE");
    }
}
