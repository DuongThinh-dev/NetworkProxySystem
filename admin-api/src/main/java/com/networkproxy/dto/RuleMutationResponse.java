package com.networkproxy.dto;

public record RuleMutationResponse(
        RuleResponse rule,
        String warning
) {
}
