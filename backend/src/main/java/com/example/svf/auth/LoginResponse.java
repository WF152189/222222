package com.example.svf.auth;

public record LoginResponse(
        String token,
        long expiresAt,
        AuthenticatedUser user
) {
}
