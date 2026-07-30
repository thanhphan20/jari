package com.example.jari.project.config;

import com.example.jari.security.IdentityHeaderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Requires the gateway-injected identity headers on every request except health
 * checks and Swagger aggregation (the gateway calls /v3/api-docs directly, outside
 * the authenticated route). Real authorization on top of this is Phase 3.
 */
@Configuration
public class IdentityFilterConfig {

    @Bean
    public FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilter() {
        IdentityHeaderFilter filter = new IdentityHeaderFilter(
                List.of("/actuator/health", "/v3/api-docs", "/swagger-ui"));
        FilterRegistrationBean<IdentityHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
