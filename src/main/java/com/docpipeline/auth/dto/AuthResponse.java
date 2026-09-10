package com.docpipeline.auth.dto;

public record AuthResponse(
        String token,
        String email,
        String fullName,
        String refreshToken,
        Long expiresIn
) {
    public AuthResponse(String token, String email, String fullName) {
        this(token, email, fullName, null, null);
    }
}
