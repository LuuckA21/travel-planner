package me.luucka.travelplanner.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Expects an already lowercased email (see {@link AuthService#normalizeEmail}). */
    Optional<UserAccount> findByEmail(String email);
}
