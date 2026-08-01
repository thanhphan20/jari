package com.example.jari.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IdentityHeaderFilterTest {

    private static final List<String> OPEN = List.of("/actuator/health", "/v3/api-docs", "/auth/");

    private final IdentityHeaderFilter filter = new IdentityHeaderFilter(OPEN);

    @Test
    void rejectsARequestWithNoIdentityHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(get("/projects"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsABlankIdentityHeader() throws Exception {
        MockHttpServletRequest request = get("/projects");
        request.addHeader(IdentityHeaders.USER_ID, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesARequestCarryingTheIdentityHeader() throws Exception {
        MockHttpServletRequest request = get("/projects");
        request.addHeader(IdentityHeaders.USER_ID, "7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void letsOpenPathsThroughWithoutAnIdentity() throws Exception {
        for (String path : List.of("/actuator/health", "/v3/api-docs", "/auth/token")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(get(path), response, chain);

            assertThat(response.getStatus()).as(path).isEqualTo(200);
            verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void openPathsMatchOnPrefixOnlyAtTheStartOfThePath() throws Exception {
        // "/tasks/auth/" must not inherit "/auth/"'s exemption just by containing it.
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(get("/tasks/auth/token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
