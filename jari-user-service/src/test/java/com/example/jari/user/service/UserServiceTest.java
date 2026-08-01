package com.example.jari.user.service;

import com.example.jari.common.exception.ResourceNotFoundException;
import com.example.jari.user.dto.UserDto;
import com.example.jari.user.entity.User;
import com.example.jari.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createNeverStoresAPlaintextPassword() {
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(UserDto.builder().username("ada").password("secret").build());

        assertThat(savedUser().getPassword()).isEqualTo("hashed").isNotEqualTo("secret");
    }

    @Test
    void createStillHashesWhenNoPasswordIsSupplied() {
        // Falls back to a placeholder rather than persisting null, which would make
        // the row unusable for login and blow up the encoder on the next update.
        when(passwordEncoder.encode("default")).thenReturn("hashed-default");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(UserDto.builder().username("ada").build());

        assertThat(savedUser().getPassword()).isEqualTo("hashed-default");
    }

    @Test
    void createAlwaysMarksTheUserActive() {
        when(passwordEncoder.encode(any())).thenReturn("h");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(UserDto.builder().username("ada").password("p").build());

        assertThat(savedUser().isActive()).isTrue();
    }

    @Test
    void theMappedDtoNeverExposesThePasswordHash() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ada")));

        assertThat(userService.getUserById(1L).getPassword()).isNull();
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getAllMapsEveryRow() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "ada"), user(2L, "grace")));

        assertThat(userService.getAllUsers()).extracting(UserDto::getUsername)
                .containsExactly("ada", "grace");
    }

    @Test
    void updateOverwritesTheProfileFields() {
        User existing = user(1L, "ada");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(1L, UserDto.builder()
                .firstName("Ada").lastName("Lovelace").username("ada2").avatarUrl("u").active(true).build());

        assertThat(existing.getFirstName()).isEqualTo("Ada");
        assertThat(existing.getLastName()).isEqualTo("Lovelace");
        assertThat(existing.getUsername()).isEqualTo("ada2");
        assertThat(existing.getAvatarUrl()).isEqualTo("u");
    }

    @Test
    void updateLeavesThePasswordAndEmailAlone() {
        // Neither is editable through this endpoint; a password change needs the
        // encoder, and letting email through here would bypass any future
        // verification flow.
        User existing = user(1L, "ada");
        existing.setPassword("original-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(1L, UserDto.builder()
                .username("ada").password("new-plaintext").email("attacker@example.com").active(true).build());

        assertThat(existing.getPassword()).isEqualTo("original-hash");
        assertThat(existing.getEmail()).isEqualTo("ada@example.com");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateThrowsWhenAbsent() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(404L, UserDto.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAnExistingUser() {
        User existing = user(1L, "ada");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        userService.deleteUser(1L);

        verify(userRepository).delete(existing);
    }

    @Test
    void deleteThrowsRatherThanSilentlySucceedingOnAMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any());
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User user(Long id, String username) {
        return User.builder()
                .id(id).username(username).email(username + "@example.com")
                .password("hash").active(true).build();
    }
}
