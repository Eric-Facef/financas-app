package com.eric.financas.passkey;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * rpId = domínio do site (ex.: financas-app-3u4q.onrender.com). A passkey fica "presa" a esse domínio.
 */
@Validated
@ConfigurationProperties(prefix = "app.passkeys")
public record PasskeyProperties(@NotBlank String rpId, @NotBlank String rpName) {
}
