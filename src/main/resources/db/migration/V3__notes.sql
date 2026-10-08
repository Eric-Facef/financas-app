-- Bloco de notas: lembretes soltos com caixinha de "feito" (ex.: "dar R$ 7 para a mãe").
CREATE TABLE notes (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(300) NOT NULL,
    done       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notes_user ON notes (user_id, done, created_at DESC);
