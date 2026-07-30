package com.example.jari.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Rejects any request that does not carry the gateway-injected identity headers.
 * A downstream service is only reachable through the gateway in this deployment, so
 * a request with no identity headers is either a direct call bypassing the gateway
 * or the gateway rejected it upstream - either way it must not reach the service.
 *
 * Open paths (health checks, Swagger aggregation, and the identity service's own
 * register/token/validate endpoints) are exempted since they run before an identity
 * exists or are called by the gateway itself outside the authenticated path.
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    private final List<String> openPathPrefixes;

    public IdentityHeaderFilter(List<String> openPathPrefixes) {
        this.openPathPrefixes = openPathPrefixes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isOpen = openPathPrefixes.stream().anyMatch(path::startsWith);
        if (!isOpen && !StringUtils.hasText(request.getHeader(IdentityHeaders.USER_ID))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
