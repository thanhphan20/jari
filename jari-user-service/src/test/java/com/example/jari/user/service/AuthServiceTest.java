package com.example.jari.user.service;

import com.example.jari.security.JwtUtils;
import com.example.jari.user.config.CustomUserDetails;
import com.example.jari.user.dto.AuthRequest;
import com.example.jari.user.dto.RegisterRequest;
import com.example.jari.user.entity.User;
import com.example.jari.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void registrationHashesThePassword() {
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        authService.saveUser(new RegisterRequest("ada", "ada@example.com", "secret"));

        User saved = savedUser();
        assertThat(saved.getPassword()).isEqualTo("hashed").isNotEqualTo("secret");
        assertThat(saved.getUsername()).isEqualTo("ada");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void loginIssuesATokenCarryingTheResolvedUserId() {
        // The userId claim is what downstream services receive as X-Jari-User-Id, so
        // it has to come from the authenticated principal, not from the request.
        User user = User.builder().id(42L).username("ada").password("hashed").active(true).build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtils.generateToken(42L, "ada", List.of("USER"))).thenReturn("a.b.c");

        String token = authService.login(new AuthRequest("ada", "secret"));

        assertThat(token).isEqualTo("a.b.c");
        verify(jwtUtils).generateToken(42L, "ada", List.of("USER"));
    }

    @Test
    void loginPropagatesAnAuthenticationFailureAndIssuesNoToken() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new AuthRequest("ada", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtUtils, never()).generateToken(any(), any(), any());
    }

    @Test
    void validateTokenDelegatesToJwtUtilsWithTheTokensOwnSubject() {
        when(jwtUtils.extractUsername("a.b.c")).thenReturn("ada");

        authService.validateToken("a.b.c");

        verify(jwtUtils).validateToken("a.b.c", "ada");
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }
}
