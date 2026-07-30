package com.example.jari.user.controller;

import com.example.jari.user.dto.AuthRequest;
import com.example.jari.user.dto.RegisterRequest;
import com.example.jari.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public String register(@Valid @RequestBody RegisterRequest request) {
        return authService.saveUser(request);
    }

    @PostMapping("/token")
    public String getToken(@RequestBody AuthRequest authRequest) {
        return authService.login(authRequest);
    }

    @GetMapping("/validate")
    public String validateToken(@RequestParam("token") String token) {
        authService.validateToken(token);
        return "Token is valid";
    }
}
