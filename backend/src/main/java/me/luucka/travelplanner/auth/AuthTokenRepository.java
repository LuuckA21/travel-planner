package me.luucka.travelplanner.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    /** Atomic compare-and-set: of two concurrent requests with the same token, only one gets 1. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update AuthToken t set t.usedAt = :now, t.updatedAt = :now
            where t.tokenHash = :hash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now""")
    int markUsed(@Param("hash") String hash, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}
