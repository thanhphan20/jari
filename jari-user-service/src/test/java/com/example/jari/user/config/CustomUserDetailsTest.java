package com.example.jari.user.config;

import com.example.jari.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    void exposesTheUsersIdSoLoginCanPutItInTheToken() {
        // This getter is the only reason CustomUserDetails exists rather than
        // Spring's own User: AuthService needs the id for the userId claim.
        assertThat(new CustomUserDetails(user(42L, true)).getId()).isEqualTo(42L);
    }

    @Test
    void exposesTheStoredHashAndUsernameForAuthentication() {
        CustomUserDetails details = new CustomUserDetails(user(1L, true));

        assertThat(details.getUsername()).isEqualTo("ada");
        assertThat(details.getPassword()).isEqualTo("hash");
    }

    @Test
    void anInactiveUserIsDisabledAndCannotLogIn() {
        assertThat(new CustomUserDetails(user(1L, false)).isEnabled()).isFalse();
    }

    @Test
    void anActiveUserIsEnabled() {
        assertThat(new CustomUserDetails(user(1L, true)).isEnabled()).isTrue();
    }

    @Test
    void grantsRoleUser() {
        assertThat(new CustomUserDetails(user(1L, true)).getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void accountLifecycleFlagsAreNotModelledAndStayTrue() {
        // There are no expiry or lock columns; returning false here would lock
        // everyone out, so these are pinned rather than left to drift.
        CustomUserDetails details = new CustomUserDetails(user(1L, true));

        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    private static User user(Long id, boolean active) {
        return User.builder().id(id).username("ada").password("hash").active(active).build();
    }
}
