package com.p03.inventory.po.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PoCancelRequest(@NotBlank @Size(max = 4000) String reason) {
}
