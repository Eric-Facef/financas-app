package com.eric.financas.category.dto;

import com.eric.financas.category.Category;
import com.eric.financas.category.CategoryKind;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryKind kind, String icon, String color) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getIcon(), c.getColor());
    }
}
