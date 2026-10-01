package com.eric.financas.auth.dto;

/**
 * Uso interno (service -> controller). O refresh token vai em cookie HttpOnly, nunca no corpo.
 */
public record AuthTokens(String accessToken, String refreshToken, String email) {
}
