package com.eric.financas.passkey.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasskeyRegisterRequest(
        @NotBlank String challengeId,
        @Size(max = 80) String name,
        @NotNull JsonNode credential
) {
}
