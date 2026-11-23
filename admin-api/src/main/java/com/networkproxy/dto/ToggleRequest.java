package com.networkproxy.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleRequest(
        @NotNull(message = "Trạng thái enabled không được để trống")
        Boolean enabled
) {
}
