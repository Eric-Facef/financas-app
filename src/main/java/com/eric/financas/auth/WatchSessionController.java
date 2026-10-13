package com.eric.financas.auth;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Sessão do app do relógio vista pelo site (aba Segurança).
 * Fica fora de /api/v1/auth/** de propósito: aquele caminho é público, este exige o login do usuário.
 */
@Tag(name = "Relógio")
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
public class WatchSessionController {

    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public record WatchStatus(boolean connected, Instant lastActivity) {
    }

    /** O relógio está logado? E quando foi a última vez que ele falou com o servidor? */
    @GetMapping
    public WatchStatus status(@AuthenticationPrincipal AuthenticatedUser user) {
        RefreshTokenService.ClientStatus s = refreshTokens.statusOf(user.id(), RefreshToken.CLIENT_WATCH);
        return new WatchStatus(s.connected(), s.lastActivity());
    }

    /**
     * Encerra a sessão do relógio. O celular e o navegador continuam logados.
     * O relógio percebe na próxima renovação (o token de acesso dele vale no máximo 15 minutos).
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutWatch(@AuthenticationPrincipal AuthenticatedUser user) {
        int removed = refreshTokens.revokeClient(user.id(), RefreshToken.CLIENT_WATCH);
        audit.record(user.id(), AuditAction.RELOGIO_DESLOGADO, "User", user.id().toString(),
                Map.of("sessoes", String.valueOf(removed)));
    }
}
