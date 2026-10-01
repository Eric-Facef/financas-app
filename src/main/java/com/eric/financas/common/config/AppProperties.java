package com.eric.financas.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record AppProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET precisa ter no mínimo 32 caracteres") String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean cookieSecure,
        @NotBlank String cookieSameSite,
        @NotEmpty List<String> allowedOrigins,
        @Min(4) @Max(16) int bcryptStrength
) {
}
