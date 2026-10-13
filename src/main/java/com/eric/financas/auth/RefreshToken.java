package com.eric.financas.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken {

    /** Sessão do site/PWA (padrão). */
    public static final String CLIENT_WEB = "web";
    /** Sessão do app do relógio. */
    public static final String CLIENT_WATCH = "watch";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 do token (o valor bruto nunca é guardado). */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** De onde veio a sessão (web ou watch). A rotação mantém o mesmo valor. */
    @Column(nullable = false, length = 20)
    private String client = CLIENT_WEB;

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this(userId, tokenHash, expiresAt, CLIENT_WEB);
    }

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, String client) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.client = client;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
