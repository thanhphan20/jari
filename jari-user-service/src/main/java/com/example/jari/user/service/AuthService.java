package com.example.jari.user.service;

import com.example.jari.security.JwtUtils;
import com.example.jari.user.config.CustomUserDetails;
import com.example.jari.user.dto.AuthRequest;
import com.example.jari.user.dto.RegisterRequest;
import com.example.jari.user.entity.User;
import com.example.jari.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    public String saveUser(RegisterRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();
        userRepository.save(user);
        return "user added to the system";
    }

    public String login(AuthRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));
        if (!authentication.isAuthenticated()) {
            throw new RuntimeException("invalid access");
        }
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return jwtUtils.generateToken(principal.getId(), principal.getUsername(), List.of("USER"));
    }

    public void validateToken(String token) {
        jwtUtils.validateToken(token, jwtUtils.extractUsername(token));
    }
}
