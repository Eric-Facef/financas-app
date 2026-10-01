package com.eric.financas.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String email) {

    public static TokenResponse bearer(String accessToken, long expiresIn, String email) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, email);
    }
}
