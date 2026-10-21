package me.luucka.travelplanner.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    AppUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByEmail(AuthService.normalizeEmail(username))
                .map(AppUserDetails::of)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
