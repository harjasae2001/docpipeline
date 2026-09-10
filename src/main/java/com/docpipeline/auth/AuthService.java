package com.docpipeline.auth;

import com.docpipeline.auth.dto.AuthResponse;
import com.docpipeline.auth.dto.LoginRequest;
import com.docpipeline.auth.dto.RegisterRequest;
import com.docpipeline.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
@Slf4j
public class AuthService {
    private final RestClient authClient;
    private final ObjectMapper objectMapper;

    public AuthService(RestClient.Builder builder, AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.authClient = builder.baseUrl(properties.getSupabase().getUrl() + "/auth/v1")
                .defaultHeader("apikey", properties.getSupabase().getPublishableKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        try {
            JsonNode response = authClient.post().uri("/signup")
                    .body(Map.of("email", request.email(), "password", request.password(),
                            "data", Map.of("full_name", request.fullName())))
                    .retrieve().body(JsonNode.class);
            return toResponse(response, request.email(), request.fullName());
        } catch (RestClientResponseException exception) {
            throw authFailure("Registration failed", exception);
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            JsonNode response = authClient.post().uri("/token?grant_type=password")
                    .body(Map.of("email", request.email(), "password", request.password()))
                    .retrieve().body(JsonNode.class);
            return toResponse(response, request.email(), null);
        } catch (RestClientResponseException exception) {
            throw authFailure("Invalid email or password", exception);
        }
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            JsonNode response = authClient.post().uri("/token?grant_type=refresh_token")
                    .body(Map.of("refresh_token", refreshToken))
                    .retrieve().body(JsonNode.class);
            return toResponse(response, null, null);
        } catch (RestClientResponseException exception) {
            throw authFailure("Session refresh failed", exception);
        }
    }

    public void logout(String accessToken) {
        try {
            authClient.post().uri("/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw authFailure("Logout failed", exception);
        }
    }

    private AuthResponse toResponse(JsonNode response, String fallbackEmail, String fallbackName) {
        if (response == null) {
            throw new IllegalArgumentException("Supabase Auth returned an empty response");
        }
        JsonNode user = response.path("user");
        String email = valueOr(user, "email", fallbackEmail);
        String fullName = valueOr(user.path("user_metadata"), "full_name", fallbackName);
        return new AuthResponse(nullableText(response, "access_token"), email, fullName,
                nullableText(response, "refresh_token"),
                response.path("expires_in").isNumber() ? response.path("expires_in").asLong() : null);
    }

    private String valueOr(JsonNode node, String field, String fallback) {
        String value = nullableText(node, field);
        return value == null ? fallback : value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private IllegalArgumentException authFailure(String fallback, RestClientResponseException exception) {
        log.warn("Supabase Auth request failed with status {}", exception.getStatusCode());
        try {
            JsonNode error = objectMapper.readTree(exception.getResponseBodyAsString());
            return new IllegalArgumentException(
                    error.path("msg").asText(error.path("message").asText(fallback)));
        } catch (Exception ignored) {
            return new IllegalArgumentException(fallback);
        }
    }
}
