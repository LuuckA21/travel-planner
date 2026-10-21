package me.luucka.travelplanner.auth;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        http.csrf(csrf -> csrf.spa())
                // Load the deferred token on every request so the SPA always has an XSRF-TOKEN cookie.
                .addFilterAfter(eagerCsrfToken(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/verify-email",
                                "/api/auth/resend-verification", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/auth/config", "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // 401 as a Problem Detail, rendered by ApiExceptionHandler
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (request, response, ex) -> resolver.resolveException(request, response, null, ex)))
                // No session per unauthenticated API call just to remember the request
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    private static Filter eagerCsrfToken() {
        return (request, response, chain) -> {
            ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
            chain.doFilter(request, response);
        };
    }

    // SESSION cookie: HttpOnly, SameSite=Lax, Secure when the request is (via X-Forwarded-Proto).
    // Explicit so it does not depend on the servlet context's session cookie config (MockMvc has none).
    @Bean
    DefaultCookieSerializer cookieSerializer() {
        return new DefaultCookieSerializer();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
