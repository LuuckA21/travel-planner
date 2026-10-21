package me.luucka.travelplanner.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import me.luucka.travelplanner.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX = 128;

    static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    static final String VERIFICATION_SUBJECT = "Conferma il tuo indirizzo email";
    static final String VERIFICATION_BODY = "Ciao %s,%n%nconferma il tuo indirizzo email aprendo questo link "
            + "(valido 24 ore):%n%s%n%nSe non hai creato tu l'account, ignora questo messaggio.";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokens;
    private final Mailer mailer;
    private final AuthProperties properties;

    AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, AuthTokenService tokens,
            Mailer mailer, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.mailer = mailer;
        this.properties = properties;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserAccount createVerifiedUser(String email, String password, String displayName) {
        UserAccount user = new UserAccount(normalizeEmail(email), passwordEncoder.encode(password), displayName);
        user.setEmailVerifiedAt(Instant.now());
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        // A session can outlive its user; treat that as logged out.
        return users.findById(userId).map(MeResponse::of)
                .orElseThrow(() -> new InsufficientAuthenticationException("User no longer exists"));
    }

    // Not transactional: the user must be committed before the mail goes out.
    public void register(RegisterRequest request) {
        if (!properties.registrationOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "Registration is closed");
        }
        String email = normalizeEmail(request.email());
        UserAccount user;
        try {
            if (users.findByEmail(email).isPresent()) {
                throw emailTaken();
            }
            user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                    request.displayName().trim()));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UserAccount user = tokens.consume(rawToken, TokenPurpose.VERIFY_EMAIL).flatMap(users::findById)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN",
                        "The link is invalid or has expired"));
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
    }

    /** Silent for unknown or already verified emails, so it cannot be used to probe accounts. */
    public void resendVerification(String email) {
        users.findByEmail(normalizeEmail(email)).filter(u -> u.getEmailVerifiedAt() == null)
                .ifPresent(this::sendVerification);
    }

    private void sendVerification(UserAccount user) {
        try {
            String raw = tokens.issue(user.getId(), TokenPurpose.VERIFY_EMAIL, VERIFICATION_TTL);
            String link = properties.baseUrl() + "/verify-email?token=" + raw;
            mailer.send(user.getEmail(), VERIFICATION_SUBJECT,
                    VERIFICATION_BODY.formatted(user.getDisplayName(), link));
        } catch (RuntimeException e) {
            // The account exists; the user can ask for another mail via resend-verification.
            log.error("Could not send verification mail", e);
        }
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account with this email already exists");
    }
}
