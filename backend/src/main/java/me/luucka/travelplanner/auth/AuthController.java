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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Logout is handled by Spring Security's LogoutFilter (see SecurityConfig).
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final AuthProperties properties;
    private final RateLimiter rateLimiter;
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    AuthController(AuthenticationManager authenticationManager, AuthService authService, AuthProperties properties,
                   RateLimiter rateLimiter) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String email = AuthService.normalizeEmail(body.email());
        rateLimiter.check("login-ip:" + request.getRemoteAddr(), RateLimiter.Limit.LOGIN);
        rateLimiter.check("login-email:" + email, RateLimiter.Limit.LOGIN);
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    void register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        rateLimiter.check("register-ip:" + request.getRemoteAddr(), RateLimiter.Limit.SENSITIVE);
        authService.register(body);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody TokenRequest body) {
        authService.verifyEmail(body.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        rateLimiter.check("resend-verification-ip:" + request.getRemoteAddr(), RateLimiter.Limit.SENSITIVE);
        authService.resendVerification(body.email());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        rateLimiter.check("forgot-password-ip:" + request.getRemoteAddr(), RateLimiter.Limit.SENSITIVE);
        authService.forgotPassword(body.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        authService.resetPassword(body.token(), body.password());
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
