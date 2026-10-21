package me.luucka.travelplanner.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {

    private final SecureRandom random = new SecureRandom();
    private final AuthTokenRepository tokens;

    AuthTokenService(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Returns the raw token (sent to the user); only its SHA-256 hash is stored. */
    @Transactional
    public String issue(UUID userId, TokenPurpose purpose, Duration ttl) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new AuthToken(userId, purpose, hash(raw), Instant.now().plus(ttl)));
        return raw;
    }

    /** Marks the token used; empty if unknown, expired, already used or of another purpose. */
    @Transactional
    public Optional<UUID> consume(String rawToken, TokenPurpose purpose) {
        String hash = hash(rawToken);
        if (tokens.markUsed(hash, purpose, Instant.now()) == 0) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash).map(AuthToken::getUserId);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
