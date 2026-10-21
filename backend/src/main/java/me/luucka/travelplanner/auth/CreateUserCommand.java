package me.luucka.travelplanner.auth;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** {@code create-user <email>}: prompts for a password, creates a verified user and exits. */
@Component
class CreateUserCommand implements ApplicationRunner {

    private final AuthService authService;
    private final UserAccountRepository users;
    private final ApplicationContext context;

    CreateUserCommand(AuthService authService, UserAccountRepository users, ApplicationContext context) {
        this.authService = authService;
        this.users = users;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> nonOptions = args.getNonOptionArgs();
        if (nonOptions.isEmpty() || !nonOptions.getFirst().equals("create-user")) {
            return;
        }
        int code;
        if (nonOptions.size() != 2) {
            System.err.println("Usage: create-user <email>");
            code = 1;
        } else {
            Console console = System.console();
            code = console != null
                    ? execute(nonOptions.get(1), prompt -> {
                        char[] password = console.readPassword(prompt);
                        return password == null ? null : new String(password);
                    })
                    : execute(nonOptions.get(1), new BufferedReader(new InputStreamReader(System.in)));
        }
        System.exit(SpringApplication.exit(context, () -> code));
    }

    /** Reads the password twice from {@code in}; returns the process exit code. */
    int execute(String email, BufferedReader in) {
        return execute(email, prompt -> {
            try {
                return in.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private int execute(String rawEmail, Function<String, String> readPassword) {
        String email = AuthService.normalizeEmail(rawEmail);
        int at = email.indexOf('@');
        if (at < 1 || at == email.length() - 1) {
            System.err.println("Invalid email: " + email);
            return 1;
        }
        if (users.findByEmail(email).isPresent()) {
            System.err.println("A user with this email already exists: " + email);
            return 1;
        }
        String password = readPassword.apply("Password: ");
        String repeated = readPassword.apply("Repeat password: ");
        if (password == null || password.length() < AuthService.PASSWORD_MIN || password.length() > AuthService.PASSWORD_MAX) {
            System.err.println("Password must be " + AuthService.PASSWORD_MIN + " to " + AuthService.PASSWORD_MAX
                    + " characters long");
            return 1;
        }
        if (!password.equals(repeated)) {
            System.err.println("Passwords do not match");
            return 1;
        }
        authService.createVerifiedUser(email, password, email.substring(0, at));
        System.out.println("Created verified user " + email);
        return 0;
    }
}
