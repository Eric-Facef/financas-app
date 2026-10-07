package com.eric.financas.passkey;

import com.yubico.webauthn.data.ByteArray;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

/**
 * "User handle" do WebAuthn = identificador estável e anônimo do usuário (aqui, os 16 bytes do UUID).
 * Não usamos e-mail, pois o handle fica guardado dentro do celular.
 */
final class UserHandles {

    private UserHandles() {
    }

    static ByteArray fromUserId(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return new ByteArray(buffer.array());
    }

    static Optional<UUID> toUserId(ByteArray handle) {
        byte[] bytes = handle.getBytes();
        if (bytes.length != 16) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }
}
