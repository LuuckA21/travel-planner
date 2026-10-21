package me.luucka.travelplanner.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
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
}
