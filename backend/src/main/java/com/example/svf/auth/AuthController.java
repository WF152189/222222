package com.example.svf.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final InMemoryUserStore userStore;
    private final JwtTokenService jwtTokenService;

    public AuthController(InMemoryUserStore userStore, JwtTokenService jwtTokenService) {
        this.userStore = userStore;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            return invalidCredentials();
        }
        return userStore.authenticate(request.username(), request.password())
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(jwtTokenService.issueToken(user)))
                .orElseGet(this::invalidCredentials);
    }

    private ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "code", "INVALID_CREDENTIALS",
                        "message", "Invalid username or password"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
