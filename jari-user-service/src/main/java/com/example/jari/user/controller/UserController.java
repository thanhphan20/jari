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
import org.springframework.http.HttpStatus;
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
        UserDto createdUser = userService.createUser(userDto);
        ResponseDto<UserDto> response = ResponseDto.<UserDto>builder()
                .success(true)
                .message("User created successfully")
                .data(createdUser)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get the authenticated caller's own user record")
    @ApiResponse(responseCode = "200", description = "Current user retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<ResponseDto<UserDto>> getCurrentUser(
            @RequestHeader(IdentityHeaders.USER_ID) Long userId) {
        UserDto user = userService.getUserById(userId);
        ResponseDto<UserDto> response = ResponseDto.<UserDto>builder()
                .success(true)
                .message("Current user retrieved successfully")
                .data(user)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User retrieved successfully")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<UserDto>> getUserById(@PathVariable Long id) {
        UserDto user = userService.getUserById(id);
        ResponseDto<UserDto> response = ResponseDto.<UserDto>builder()
                .success(true)
                .message("User retrieved successfully")
                .data(user)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get All Users")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    @GetMapping
    public ResponseEntity<ResponseDto<List<UserDto>>> getAllUsers() {
        List<UserDto> users = userService.getAllUsers();
        ResponseDto<List<UserDto>> response = ResponseDto.<List<UserDto>>builder()
                .success(true)
                .message("Users retrieved successfully")
                .data(users)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update User")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<UserDto>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDto userDto) {
        UserDto updatedUser = userService.updateUser(id, userDto);
        ResponseDto<UserDto> response = ResponseDto.<UserDto>builder()
                .success(true)
                .message("User updated successfully")
                .data(updatedUser)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete User")
    @ApiResponse(responseCode = "200", description = "User deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        ResponseDto<Void> response = ResponseDto.<Void>builder()
                .success(true)
                .message("User deleted successfully")
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }
}
