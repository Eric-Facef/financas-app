package com.eric.financas.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Criar (done ausente = pendente) e editar (texto e/ou caixinha de feito). */
public record NoteRequest(
        @NotBlank @Size(max = 300) String text,
        Boolean done
) {
}
