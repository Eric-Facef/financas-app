package com.eric.financas.passkey;

import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda, por poucos minutos, o desafio entre "começar" e "terminar" o cadastro/login.
 * Fica em memória (serve para 1 instância, como no plano gratuito do Render) e cada desafio só vale uma vez.
 */
@Component
public class PasskeyChallengeStore {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_PENDING = 5_000;   // evita encher a memória com chamadas públicas

    private record Entry(Object value, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String put(Object value) {
        Instant now = Instant.now();
        entries.values().removeIf(e -> e.expiresAt().isBefore(now));
        if (entries.size() >= MAX_PENDING) {
            throw new BusinessException("Muitas tentativas em andamento. Tente novamente em instantes.");
        }

        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        entries.put(id, new Entry(value, now.plus(TTL)));
        return id;
    }

    /** Devolve e REMOVE o desafio (uso único). */
    public <T> T take(String id, Class<T> type) {
        Entry entry = entries.remove(id);
        if (entry == null || entry.expiresAt().isBefore(Instant.now()) || !type.isInstance(entry.value())) {
            throw new UnauthorizedException("Desafio inválido ou expirado. Tente novamente.");
        }
        return type.cast(entry.value());
    }
}
