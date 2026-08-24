package com.example.svf.svf.model;

public record SvfUserContext(
        String userId,
        String userName
) {
    public boolean isBlank() {
        return isBlank(userId) || isBlank(userName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
