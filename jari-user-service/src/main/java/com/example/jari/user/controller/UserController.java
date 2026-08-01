package com.example.jari.user.controller;

import com.example.jari.common.dto.ResponseDto;
import com.example.jari.security.IdentityHeaders;
import com.example.jari.user.dto.UserDto;
import com.example.jari.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Controller", description = "APIs for managing users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a new user")
    @ApiResponse(responseCode = "201", description = "User created successfully")
    @PostMapping
    public ResponseEntity<ResponseDto<UserDto>> createUser(@Valid @RequestBody UserDto userDto) {
        return ResponseDto.created(userService.createUser(userDto), "User created successfully");
    }

    @Operation(summary = "Get the authenticated caller's own user record")
    @ApiResponse(responseCode = "200", description = "Current user retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<ResponseDto<UserDto>> getCurrentUser(
            @RequestHeader(IdentityHeaders.USER_ID) Long userId) {
        return ResponseDto.ok(userService.getUserById(userId), "Current user retrieved successfully");
    }

    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User retrieved successfully")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<UserDto>> getUserById(@PathVariable Long id) {
        return ResponseDto.ok(userService.getUserById(id), "User retrieved successfully");
    }

    @Operation(summary = "Get All Users")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    @GetMapping
    public ResponseEntity<ResponseDto<List<UserDto>>> getAllUsers() {
        return ResponseDto.ok(userService.getAllUsers(), "Users retrieved successfully");
    }

    @Operation(summary = "Update User")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<UserDto>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDto userDto) {
        return ResponseDto.ok(userService.updateUser(id, userDto), "User updated successfully");
    }

    @Operation(summary = "Delete User")
    @ApiResponse(responseCode = "200", description = "User deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseDto.ok("User deleted successfully");
    }
}
