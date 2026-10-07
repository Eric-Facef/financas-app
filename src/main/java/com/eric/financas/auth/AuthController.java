package com.eric.financas.auth;

import com.eric.financas.auth.dto.AuthTokens;
import com.eric.financas.auth.dto.LoginRequest;
import com.eric.financas.auth.dto.RegisterRequest;
import com.eric.financas.auth.dto.TokenResponse;
import com.eric.financas.common.config.AppProperties;
import com.eric.financas.common.security.JwtService;
import com.eric.financas.passkey.PasskeyService;
import com.eric.financas.passkey.dto.PasskeyLoginRequest;
import com.eric.financas.passkey.dto.PasskeyOptionsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtService jwtService;
    private final AppProperties props;
    private final PasskeyService passkeyService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, authService.login(request));
    }

    /** Passo 1 do login por digital: gera o desafio que o celular vai assinar. */
    @PostMapping("/passkey/options")
    public PasskeyOptionsResponse passkeyOptions() {
        return passkeyService.startLogin();
    }

    /** Passo 2: confere a assinatura e, se válida, entrega os mesmos tokens do login por senha. */
    @PostMapping("/passkey/login")
    public ResponseEntity<TokenResponse> passkeyLogin(@Valid @RequestBody PasskeyLoginRequest request,
                                                      HttpServletRequest http) {
        assertTrustedOrigin(http);
        return respond(HttpStatus.OK, authService.loginWithPasskey(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {
        assertTrustedOrigin(request);
        return respond(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {
        assertTrustedOrigin(request);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> respond(HttpStatus status, AuthTokens tokens) {
        TokenResponse body = TokenResponse.bearer(tokens.accessToken(), jwtService.accessTtlSeconds(), tokens.email());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), props.refreshTokenTtl()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite(props.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /**
     * Como o refresh usa cookie, exigimos que o Origin (quando enviado pelo navegador)
     * esteja na lista permitida. Protege contra CSRF mesmo com SameSite=None.
     */
    private void assertTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !props.allowedOrigins().contains(origin)) {
            throw new AccessDeniedException("Origin não permitido");
        }
    }
}
