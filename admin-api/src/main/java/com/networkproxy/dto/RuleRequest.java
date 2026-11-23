package com.networkproxy.dto;

import com.networkproxy.entity.AccessRule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RuleRequest(
        @Size(max = 45, message = "Client IP tối đa 45 ký tự")
        String clientIp,

        @Size(max = 253, message = "Target tối đa 253 ký tự")
        String target,

        @Min(value = 1, message = "Port phải từ 1 đến 65535")
        @Max(value = 65535, message = "Port phải từ 1 đến 65535")
        Integer targetPort,

        @Pattern(regexp = "(?i)HTTP|HTTPS|SSH",
                message = "Protocol phải là HTTP, HTTPS hoặc SSH")
        String protocol,

        @NotNull(message = "Action không được để trống")
        AccessRule.Action action,

        @Min(value = 0, message = "Priority phải lớn hơn hoặc bằng 0")
        Integer priority,

        Boolean enabled
) {
}
