package com.example.svf.auth;

public record LoginRequest(
        String username,
        String password
) {
}
