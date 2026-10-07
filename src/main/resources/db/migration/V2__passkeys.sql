-- Credenciais de login por digital/rosto (passkeys). Só a chave PÚBLICA fica no servidor.
CREATE TABLE passkeys (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id   TEXT        NOT NULL UNIQUE,
    public_key_cose TEXT        NOT NULL,
    signature_count BIGINT      NOT NULL DEFAULT 0,
    name            VARCHAR(80) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMPTZ
);
CREATE INDEX idx_passkeys_user ON passkeys (user_id);
