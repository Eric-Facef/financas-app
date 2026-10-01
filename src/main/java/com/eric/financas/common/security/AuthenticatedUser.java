package com.eric.financas.common.security;

import java.util.UUID;

/**
 * Principal colocado no SecurityContext após validar o JWT.
 * Use nos controllers com @AuthenticationPrincipal.
 */
public record AuthenticatedUser(UUID id, String email) {
}
