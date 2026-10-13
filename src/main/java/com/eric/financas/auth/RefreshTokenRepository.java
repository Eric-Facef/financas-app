package com.eric.financas.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Apaga (e não só revoga) as sessões de um tipo de aparelho. Apagar é de propósito: um token revogado que
     * voltasse a ser usado seria tratado como roubo e derrubaria TODAS as sessões do usuário, inclusive a do celular.
     */
    @Modifying
    @Query("delete from RefreshToken r where r.userId = :userId and r.client = :client")
    int deleteByUserIdAndClient(@Param("userId") UUID userId, @Param("client") String client);

    /** A sessão ativa mais recente do tipo de aparelho (cada renovação cria uma nova, então a data = última atividade). */
    Optional<RefreshToken> findFirstByUserIdAndClientAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, String client, Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :limit")
    int deleteExpiredBefore(@Param("limit") Instant limit);
}
