package com.eric.financas.common.security;

import com.eric.financas.common.config.AppProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters!!";

    private static JwtService serviceWithTtl(Duration ttl) {
        return new JwtService(new AppProperties(SECRET, ttl, Duration.ofDays(14), false, "Lax",
                List.of("http://localhost"), 4));
    }

    @Test
    void generatesAndParsesValidToken() {
        JwtService service = serviceWithTtl(Duration.ofMinutes(15));
        UUID id = UUID.randomUUID();

        AuthenticatedUser user = service.parse(service.generateAccessToken(id, "eric@example.com"));

        assertEquals(id, user.id());
        assertEquals("eric@example.com", user.email());
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = serviceWithTtl(Duration.ofSeconds(-10));
        String token = service.generateAccessToken(UUID.randomUUID(), "eric@example.com");

        assertThrows(JwtException.class, () -> service.parse(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = serviceWithTtl(Duration.ofMinutes(15));
        String token = service.generateAccessToken(UUID.randomUUID(), "eric@example.com");

        assertThrows(JwtException.class, () -> service.parse(token + "x"));
    }
}
