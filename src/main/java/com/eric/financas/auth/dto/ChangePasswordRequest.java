package com.eric.financas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        // 72 = limite de bytes do BCrypt (mesma regra do cadastro)
        @NotBlank @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres") String newPassword
) {
}
