package com.eric.financas.passkey.dto;

import com.eric.financas.passkey.Passkey;

import java.time.Instant;
import java.util.UUID;

public record PasskeyResponse(UUID id, String name, Instant createdAt, Instant lastUsedAt) {

    public static PasskeyResponse from(Passkey p) {
        return new PasskeyResponse(p.getId(), p.getName(), p.getCreatedAt(), p.getLastUsedAt());
    }
}
