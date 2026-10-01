package com.eric.financas.auth;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.auth.dto.AuthTokens;
import com.eric.financas.auth.dto.LoginRequest;
import com.eric.financas.auth.dto.RegisterRequest;
import com.eric.financas.common.exception.ConflictException;
import com.eric.financas.common.exception.UnauthorizedException;
import com.eric.financas.common.security.JwtService;
import com.eric.financas.user.OnboardingService;
import com.eric.financas.user.User;
import com.eric.financas.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final OnboardingService onboarding;
    private final AuditService audit;

    /** Hash "fantasma" para gastar o mesmo tempo quando o e-mail não existe (evita enumeração por timing). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens, OnboardingService onboarding, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.onboarding = onboarding;
        this.audit = audit;
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
        return tokensFor(user);
    }

    // Sem @Transactional de propósito: a falha de login precisa ser auditada imediatamente.
    public AuthTokens login(LoginRequest req) {
        String email = normalize(req.email());
        User user = users.findByEmail(email).orElse(null);

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean valid = encoder.matches(req.password(), hash) && user != null;

        if (!valid) {
            audit.record(user != null ? user.getId() : null, AuditAction.LOGIN_FALHA, "User", null,
                    Map.of("email", email));
            throw new UnauthorizedException("Credenciais inválidas");
        }

        audit.record(user.getId(), AuditAction.LOGIN_SUCESSO, "User", user.getId().toString(), Map.of());
        return tokensFor(user);
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

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    private AuthTokens tokensFor(User user) {
        return new AuthTokens(
                jwt.generateAccessToken(user.getId(), user.getEmail()),
                refreshTokens.issue(user.getId()),
                user.getEmail());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
