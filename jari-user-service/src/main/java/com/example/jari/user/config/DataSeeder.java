package com.example.jari.user.config;

import com.example.jari.user.entity.User;
import com.example.jari.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("admin123"))
                    .active(true)
                    .build();
            userRepository.save(admin);

            User user = User.builder()
                    .username("user")
                    .email("user@example.com")
                    .password(passwordEncoder.encode("user123"))
                    .active(true)
                    .build();
            userRepository.save(user);

            log.info("Identity data seeded (admin/admin123, user/user123)");
        }
    }
}
