package com.networkproxy.service;

import com.networkproxy.auth.AdminPrincipal;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.dto.RuleMutationResponse;
import com.networkproxy.dto.RuleRequest;
import com.networkproxy.dto.RuleResponse;
import com.networkproxy.entity.AccessRule;
import com.networkproxy.exception.ApiException;
import com.networkproxy.repository.RuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RuleService {

    private final RuleRepository ruleRepository;
    private final AuditService auditService;
    private final ProxyControlClient proxyControlClient;

    public RuleService(
            RuleRepository ruleRepository,
            AuditService auditService,
            ProxyControlClient proxyControlClient
    ) {
        this.ruleRepository = ruleRepository;
        this.auditService = auditService;
        this.proxyControlClient = proxyControlClient;
    }

    public PageResponse<RuleResponse> list(
            String q,
            Boolean enabled,
            int page,
            int size
    ) {
        PageResponse<AccessRule> result = ruleRepository.findPage(
                q,
                enabled,
                Pagination.page(page),
                Pagination.size(size));
        return new PageResponse<>(
                result.content().stream().map(RuleResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public RuleResponse get(long id) {
        return RuleResponse.from(find(id));
    }

    public RuleMutationResponse create(
            RuleRequest request,
            AdminPrincipal principal
    ) {
        AccessRule rule = normalize(request);
        long id = ruleRepository.insert(rule);
        AccessRule saved = find(id);
        auditService.record(principal, "CREATE", "ACCESS_RULE", id,
                describe(saved));
        return new RuleMutationResponse(
                RuleResponse.from(saved),
                proxyControlClient.reloadRules());
    }

    public RuleMutationResponse update(
            long id,
            RuleRequest request,
            AdminPrincipal principal
    ) {
        find(id);
        AccessRule rule = normalize(request);
        ruleRepository.update(id, rule);
        AccessRule saved = find(id);
        auditService.record(principal, "UPDATE", "ACCESS_RULE", id,
                describe(saved));
        return new RuleMutationResponse(
                RuleResponse.from(saved),
                proxyControlClient.reloadRules());
    }

    public RuleMutationResponse delete(
            long id,
            AdminPrincipal principal
    ) {
        AccessRule existing = find(id);
        ruleRepository.delete(id);
        auditService.record(principal, "DELETE", "ACCESS_RULE", id,
                describe(existing));
        return new RuleMutationResponse(
                null,
                proxyControlClient.reloadRules());
    }

    public RuleMutationResponse setEnabled(
            long id,
            boolean enabled,
            AdminPrincipal principal
    ) {
        find(id);
        ruleRepository.setEnabled(id, enabled);
        AccessRule saved = find(id);
        auditService.record(principal,
                enabled ? "ENABLE" : "DISABLE",
                "ACCESS_RULE",
                id,
                describe(saved));
        return new RuleMutationResponse(
                RuleResponse.from(saved),
                proxyControlClient.reloadRules());
    }

    private AccessRule find(long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy rule #" + id));
    }

    private AccessRule normalize(RuleRequest request) {
        String clientIp = trimToNull(request.clientIp());
        String target = normalizeTarget(request.target());
        String protocol = normalizeProtocol(request.protocol());
        int priority = request.priority() == null ? 0 : request.priority();
        boolean enabled = request.enabled() == null || request.enabled();

        if (clientIp != null && clientIp.length() > 45) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Client IP tối đa 45 ký tự");
        }
        if (target != null && target.length() > 253) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Target tối đa 253 ký tự");
        }

        return new AccessRule(
                0,
                clientIp,
                target,
                request.targetPort(),
                protocol,
                request.action(),
                priority,
                enabled,
                null,
                null);
    }

    private static String normalizeTarget(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeProtocol(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!normalized.equals("HTTP")
                && !normalized.equals("HTTPS")
                && !normalized.equals("SSH")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Protocol phải là HTTP, HTTPS hoặc SSH");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String describe(AccessRule rule) {
        return "Rule #" + rule.id()
                + " | client=" + valueOrAny(rule.clientIp())
                + " | target=" + valueOrAny(rule.target())
                + " | port=" + (rule.targetPort() == null
                ? "any" : rule.targetPort())
                + " | protocol=" + valueOrAny(rule.protocol())
                + " | action=" + rule.action()
                + " | priority=" + rule.priority()
                + " | enabled=" + rule.enabled();
    }

    private static String valueOrAny(String value) {
        return value == null ? "any" : value;
    }
}
