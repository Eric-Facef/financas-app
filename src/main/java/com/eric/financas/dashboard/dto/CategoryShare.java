package com.eric.financas.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryShare(UUID categoryId, String name, String icon, String color,
                            BigDecimal total, BigDecimal percentage) {
}
