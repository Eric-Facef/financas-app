package com.eric.financas.auth;

import com.eric.financas.common.config.AppProperties;
import com.eric.financas.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens opacos, com hash no banco, rotação a cada uso e detecção de reuso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AppProperties props;

    public record Rotation(UUID userId, String newToken) {
    }

    /** Emite um novo refresh token e devolve o valor bruto (única vez em que ele existe). */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new RefreshToken(userId, hash(raw), Instant.now().plus(props.refreshTokenTtl())));
        return raw;
    }

    /**
     * Valida o token, revoga e emite outro. Se um token JÁ revogado for reapresentado
     * (possível roubo), todas as sessões do usuário são encerradas.
     * noRollbackFor: a revogação em massa precisa persistir mesmo lançando a exceção.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));

        if (current.isRevoked()) {
            log.warn("Reuso de refresh token detectado para o usuário {}", current.getUserId());
            repository.revokeAllByUserId(current.getUserId(), Instant.now());
            throw new UnauthorizedException("Sessão inválida");
        }
        if (current.isExpired()) {
            throw new UnauthorizedException("Sessão expirada");
        }

        current.setRevokedAt(Instant.now());
        return new Rotation(current.getUserId(), issue(current.getUserId()));
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(Instant.now());
            }
        });
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(Instant.now());
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
