package com.eric.financas.auth;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.auth.dto.AuthTokens;
import com.eric.financas.auth.dto.ChangePasswordRequest;
import com.eric.financas.auth.dto.LoginRequest;
import com.eric.financas.auth.dto.RegisterRequest;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.ConflictException;
import com.eric.financas.common.exception.UnauthorizedException;
import com.eric.financas.common.security.JwtService;
import com.eric.financas.passkey.PasskeyService;
import com.eric.financas.passkey.dto.PasskeyLoginRequest;
import com.eric.financas.user.OnboardingService;
import com.eric.financas.user.User;
import com.eric.financas.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final OnboardingService onboarding;
    private final AuditService audit;
    private final PasskeyService passkeys;

    /** Hash "fantasma" para gastar o mesmo tempo quando o e-mail não existe (evita enumeração por timing). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens, OnboardingService onboarding, AuditService audit,
                       PasskeyService passkeys) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.onboarding = onboarding;
        this.audit = audit;
        this.passkeys = passkeys;
        this.dummyHash = encoder.encode("senha-fantasma");
    }

    @Transactional
    public AuthTokens register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        User user = users.save(new User(email, encoder.encode(req.password())));
        onboarding.createDefaults(user.getId());

        audit.record(user.getId(), AuditAction.REGISTRO, "User", user.getId().toString(), Map.of("email", email));
        return tokensFor(user, RefreshToken.CLIENT_WEB);
    }

    /** Login do site (sessão "web"). */
    public AuthTokens login(LoginRequest req) {
        return login(req, RefreshToken.CLIENT_WEB);
    }

    // Sem @Transactional de propósito: a falha de login precisa ser auditada imediatamente.
    /** @param client "web" ou "watch": marca a sessão para que ela possa ser encerrada separadamente. */
    public AuthTokens login(LoginRequest req, String client) {
        String email = normalize(req.email());
        User user = users.findByEmail(email).orElse(null);

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean valid = encoder.matches(req.password(), hash) && user != null;

        if (!valid) {
            audit.record(user != null ? user.getId() : null, AuditAction.LOGIN_FALHA, "User", null,
                    Map.of("email", email));
            throw new UnauthorizedException("Credenciais inválidas");
        }

        audit.record(user.getId(), AuditAction.LOGIN_SUCESSO, "User", user.getId().toString(),
                RefreshToken.CLIENT_WATCH.equals(client) ? Map.of("aparelho", "relogio") : Map.of());
        return tokensFor(user, client);
    }

    /** Login por digital: a assinatura é validada no PasskeyService; aqui só emitimos os tokens. */
    public AuthTokens loginWithPasskey(PasskeyLoginRequest req) {
        User user = passkeys.finishLogin(req);
        audit.record(user.getId(), AuditAction.LOGIN_SUCESSO, "User", user.getId().toString(),
                Map.of("metodo", "passkey"));
        return tokensFor(user, RefreshToken.CLIENT_WEB);
    }

    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Sessão inválida");
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        User user = users.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));

        return new AuthTokens(jwt.generateAccessToken(user.getId(), user.getEmail()),
                rotation.newToken(), user.getEmail());
    }

    /**
     * Troca a senha de quem já está logado. Exige a senha atual, encerra as sessões de todos os aparelhos
     * (inclusive o relógio) e devolve uma sessão nova para o aparelho que trocou, que continua logado.
     * Sem @Transactional no método, de propósito: a tentativa com senha atual errada precisa ser auditada na hora.
     */
    public AuthTokens changePassword(UUID userId, ChangePasswordRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Sessão inválida"));

        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            audit.record(userId, AuditAction.SENHA_TROCA_FALHA, "User", userId.toString(), Map.of());
            throw new BusinessException("Senha atual incorreta");
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("A nova senha deve ser diferente da atual");
        }

        user.changePasswordHash(encoder.encode(req.newPassword()));
        users.save(user);

        String refresh = refreshTokens.replaceAllSessions(userId, RefreshToken.CLIENT_WEB);
        audit.record(userId, AuditAction.SENHA_ALTERADA, "User", userId.toString(), Map.of());
        return new AuthTokens(jwt.generateAccessToken(user.getId(), user.getEmail()), refresh, user.getEmail());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    private AuthTokens tokensFor(User user, String client) {
        return new AuthTokens(
                jwt.generateAccessToken(user.getId(), user.getEmail()),
                refreshTokens.issue(user.getId(), client),
                user.getEmail());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
