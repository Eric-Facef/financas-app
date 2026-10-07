package com.eric.financas.passkey.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasskeyLoginRequest(@NotBlank String challengeId, @NotNull JsonNode credential) {
}
