CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(80)   NOT NULL,
    type            VARCHAR(20)   NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
    initial_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE TABLE categories (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(60) NOT NULL,
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    icon       VARCHAR(10),
    color      VARCHAR(7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name, kind)
);

CREATE TABLE transactions (
    id          UUID PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id  UUID           NOT NULL REFERENCES accounts (id),
    category_id UUID REFERENCES categories (id),
    type        VARCHAR(15)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount      NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(160)   NOT NULL,
    occurred_on DATE           NOT NULL,
    transfer_id UUID,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_tx_user_date ON transactions (user_id, occurred_on DESC);
CREATE INDEX idx_tx_account ON transactions (account_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tx_transfer ON transactions (transfer_id) WHERE transfer_id IS NOT NULL;

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID,
    action     VARCHAR(40) NOT NULL,
    entity     VARCHAR(40) NOT NULL,
    entity_id  VARCHAR(64),
    details    JSONB,
    ip         VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_date ON audit_log (user_id, created_at DESC);
