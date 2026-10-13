-- Identifica de qual "aparelho" veio cada sessão, para poder encerrar só a do relógio.
-- Sessões que já existem (celular/navegador) viram 'web'.
ALTER TABLE refresh_tokens ADD COLUMN client VARCHAR(20) NOT NULL DEFAULT 'web';
CREATE INDEX idx_refresh_tokens_user_client ON refresh_tokens (user_id, client);
