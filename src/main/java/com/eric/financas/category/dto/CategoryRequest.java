package com.eric.financas.category.dto;

import com.eric.financas.category.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull CategoryKind kind,
        @Size(max = 10) String icon,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Use uma cor hexadecimal, ex.: #10b981") String color
) {
}
