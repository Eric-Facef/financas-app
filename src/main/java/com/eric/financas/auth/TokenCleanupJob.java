package com.eric.financas.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void purgeExpiredTokens() {
        int removed = refreshTokenService.purgeExpired();
        log.info("Limpeza de refresh tokens expirados: {} removidos", removed);
    }
}
