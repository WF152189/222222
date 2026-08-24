package com.example.svf.auth;

public record AuthenticatedUser(
        String username,
        String userId,
        String userName,
        String role
) {
}
