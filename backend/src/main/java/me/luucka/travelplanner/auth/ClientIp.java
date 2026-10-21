package me.luucka.travelplanner.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Client IP for rate-limit keys. NPM appends the peer address to any client-supplied X-Forwarded-For, so only the
 * rightmost entry is trustworthy. ForwardedHeaderFilter consumes the header (and then exposes the leftmost entry as the
 * remote address), so it is captured by a filter registered at the same, highest order, ahead of Boot's one.
 */
@Configuration(proxyBeanMethods = false)
class ClientIp {

    private static final String ATTRIBUTE = ClientIp.class.getName() + ".RAW_XFF";

    static String of(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String header) {
            String last = header.substring(header.lastIndexOf(',') + 1).trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> forwardedForCapture() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                request.setAttribute(ATTRIBUTE, request.getHeader("X-Forwarded-For"));
                chain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
