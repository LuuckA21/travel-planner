package me.luucka.travelplanner.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import me.luucka.travelplanner.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Logout is handled by Spring Security's LogoutFilter (see SecurityConfig).
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final AuthProperties properties;
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    AuthController(AuthenticationManager authenticationManager, AuthService authService, AuthProperties properties) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                AuthService.normalizeEmail(body.email()), body.password()));
        AppUserDetails user = (AppUserDetails) auth.getPrincipal();
        if (!user.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verify your email first");
        }
        sessionStrategy.onAuthentication(auth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return authService.me(user.id());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AppUserDetails user) {
        return authService.me(user.id());
    }

    @GetMapping("/config")
    AuthConfigResponse config() {
        return new AuthConfigResponse(properties.registrationOpen());
    }
}
