package me.luucka.travelplanner.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreateUserCommandTest extends IntegrationTest {

    @Autowired CreateUserCommand command;
    @Autowired UserAccountRepository users;

    private int run(String email, String stdin) {
        return command.execute(email, new BufferedReader(new StringReader(stdin)));
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class);
    }

    @Test
    void createsVerifiedUserWithLowercasedEmail() throws Exception {
        assertThat(run("Me@Example.it", "supersecret1\nsupersecret1\n")).isZero();

        UserAccount user = users.findByEmail("me@example.it").orElseThrow();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getDisplayName()).isEqualTo("me");
        assertThat(testUsers.login("me@example.it", "supersecret1")).isNotNull();
    }

    @Test
    void mismatchedPasswordsCreateNoUser() {
        assertThat(run("me@example.it", "supersecret1\nsupersecret2\n")).isNotZero();
        assertThat(userCount()).isZero();
    }

    @Test
    void passwordLengthIsValidated() {
        assertThat(run("me@example.it", "short\nshort\n")).isNotZero();
        String tooLong = "x".repeat(129);
        assertThat(run("me@example.it", tooLong + "\n" + tooLong + "\n")).isNotZero();
        assertThat(run("me@example.it", "")).isNotZero();
        assertThat(userCount()).isZero();
    }

    @Test
    void existingEmailIsRejected() {
        testUsers.create("me@example.it", "supersecret1");
        assertThat(run("ME@example.it", "supersecret2\nsupersecret2\n")).isNotZero();
        assertThat(userCount()).isOne();
    }
}
