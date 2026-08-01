package com.example.jari.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

/**
 * Registers {@link IdentityHeaderFilter} on every downstream service, so requiring
 * the gateway-injected identity headers is not something each service has to
 * remember to wire up (all four had a copy of the same bean definition).
 *
 * An auto-configuration rather than a plain {@code @Configuration}: only two of the
 * four services scan {@code com.example.jari.security}, so a scanned bean would
 * silently not register on the other two - the failure mode being an unprotected
 * service, which is the wrong way for this to break.
 *
 * Servlet-only by design. The gateway is WebFlux and has no servlet container, and
 * it authenticates requests itself rather than consuming identity headers.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IdentityFilterAutoConfiguration {

    /**
     * Paths exempt from the identity requirement, matched as prefixes. Health checks
     * and Swagger are open everywhere; user-service adds {@code /auth/} for the
     * register/token/validate endpoints that run before an identity exists.
     *
     * Bound as a String and split here rather than injected as a {@code List<String>}:
     * the list form only works where Spring Boot's ApplicationConversionService is
     * installed, so it silently collapses to one joined element in a plain context.
     * Splitting explicitly makes the value the same everywhere.
     */
    @Value("${jari.security.open-paths:/actuator/health,/v3/api-docs,/swagger-ui}")
    private String openPaths;

    @Bean
    @ConditionalOnMissingBean(name = "identityHeaderFilter")
    public FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilter() {
        List<String> prefixes = Arrays.stream(openPaths.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        FilterRegistrationBean<IdentityHeaderFilter> registration =
                new FilterRegistrationBean<>(new IdentityHeaderFilter(prefixes));
        registration.addUrlPatterns("/*");
        return registration;
    }
}
