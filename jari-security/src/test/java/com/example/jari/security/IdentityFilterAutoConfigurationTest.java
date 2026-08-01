package com.example.jari.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four services used to each declare this filter themselves. Consolidating it
 * into an auto-configuration means "is the filter actually registered?" is now a
 * property of this module rather than of each service, so it is pinned here:
 * a regression would silently leave every downstream service accepting
 * unauthenticated requests.
 */
class IdentityFilterAutoConfigurationTest {

    private static final AutoConfigurations CONFIG =
            AutoConfigurations.of(IdentityFilterAutoConfiguration.class);

    @Test
    void registersTheFilterInAServletApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(CONFIG)
                .run(context -> assertThat(context).hasSingleBean(FilterRegistrationBean.class));
    }

    @Test
    void doesNotRegisterTheFilterInAReactiveApplication() {
        // The gateway is WebFlux and has no servlet container; it authenticates
        // requests itself instead of consuming identity headers.
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(CONFIG)
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void doesNotRegisterTheFilterOutsideAWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(CONFIG)
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void defaultsToOpeningHealthAndSwaggerOnly() {
        new WebApplicationContextRunner()
                .withConfiguration(CONFIG)
                .run(context -> assertThat(openPathsOf(context))
                        .containsExactly("/actuator/health", "/v3/api-docs", "/swagger-ui"));
    }

    @Test
    void honoursTheOpenPathsProperty() {
        // user-service sets this to add /auth/; if the property stopped binding, its
        // login endpoints would start returning 401 and lock everyone out.
        new WebApplicationContextRunner()
                .withConfiguration(CONFIG)
                .withPropertyValues("jari.security.open-paths=/auth/,/actuator/health")
                .run(context -> assertThat(openPathsOf(context))
                        .containsExactly("/auth/", "/actuator/health"));
    }

    @Test
    void appliesTheFilterToEveryPath() {
        new WebApplicationContextRunner()
                .withConfiguration(CONFIG)
                .run(context -> {
                    FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getUrlPatterns()).containsExactly("/*");
                });
    }

    @SuppressWarnings("unchecked")
    private static List<String> openPathsOf(AssertableWebApplicationContext context) {
        FilterRegistrationBean<IdentityHeaderFilter> registration =
                (FilterRegistrationBean<IdentityHeaderFilter>) context.getBean(FilterRegistrationBean.class);
        return (List<String>) ReflectionTestUtils.getField(registration.getFilter(), "openPathPrefixes");
    }
}
