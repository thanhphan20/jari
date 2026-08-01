package com.example.jari.user.controller;

import com.example.jari.user.dto.AuthRequest;
import com.example.jari.user.dto.RegisterRequest;
import com.example.jari.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
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

    // Rejected credentials must surface as 401, not the 500 that
    // GlobalExceptionHandler's catch-all would otherwise map an
    // AuthenticationException (e.g. BadCredentialsException) to.
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAuthenticationException(AuthenticationException ex) {
        return "Invalid credentials";
    }
}
