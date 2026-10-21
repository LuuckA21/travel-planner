package me.luucka.travelplanner.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The authenticated principal, stored in the session; the password hash is erased after login. */
public class AppUserDetails implements UserDetails, CredentialsContainer {

    // Java-serialized into spring_session_attributes; keep stable so stored sessions survive field changes
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final boolean emailVerified;
    private String passwordHash;

    public AppUserDetails(UUID id, String email, String passwordHash, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
    }

    public static AppUserDetails of(UserAccount user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getEmailVerifiedAt() != null);
    }

    public UUID id() {
        return id;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
