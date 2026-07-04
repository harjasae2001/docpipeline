package com.docpipeline.auth.dto;

public record AuthResponse(
        String token,
        String email,
        String fullName
) {}
