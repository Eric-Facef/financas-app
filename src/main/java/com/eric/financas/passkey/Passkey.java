package com.eric.financas.passkey;

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
@Table(name = "passkeys")
@Getter
@NoArgsConstructor
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** ID da credencial em base64url. */
    @Column(name = "credential_id", nullable = false, unique = true, columnDefinition = "text")
    private String credentialId;

    /** Chave pública (formato COSE) em base64url. A chave privada nunca sai do celular. */
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "text")
    private String publicKeyCose;

    @Setter
    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(nullable = false, length = 80)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public Passkey(UUID userId, String credentialId, String publicKeyCose, long signatureCount, String name) {
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.signatureCount = signatureCount;
        this.name = name;
    }
}
