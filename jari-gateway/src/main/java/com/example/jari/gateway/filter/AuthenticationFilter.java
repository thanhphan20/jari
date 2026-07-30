package com.example.jari.gateway.filter;

import com.example.jari.security.IdentityHeaders;
import com.example.jari.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private RouterValidator routerValidator;

    @Autowired
    private JwtUtils jwtUtils;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Never trust a client-supplied identity header - strip on every route,
            // secured or not, then set it ourselves below if the token is valid.
            ServerHttpRequest.Builder mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(IdentityHeaders.USER_ID);
                        headers.remove(IdentityHeaders.USERNAME);
                    });

            if (routerValidator.isSecured.test(exchange.getRequest())) {
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    return unauthorized(exchange);
                }

                String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }

                try {
                    String username = jwtUtils.extractUsername(authHeader);
                    if (!jwtUtils.validateToken(authHeader, username)) {
                        return unauthorized(exchange);
                    }
                    Long userId = jwtUtils.extractUserId(authHeader);
                    mutatedRequest.headers(headers -> {
                        headers.set(IdentityHeaders.USER_ID, String.valueOf(userId));
                        headers.set(IdentityHeaders.USERNAME, username);
                    });
                } catch (Exception e) {
                    return unauthorized(exchange);
                }
            }

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest.build()).build();
            return chain.filter(mutatedExchange);
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {

    }
}
