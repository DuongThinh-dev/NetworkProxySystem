package com.networkproxy.controller;

import com.networkproxy.auth.AdminPrincipal;
import com.networkproxy.auth.AuthInterceptor;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.dto.RuleMutationResponse;
import com.networkproxy.dto.RuleRequest;
import com.networkproxy.dto.RuleResponse;
import com.networkproxy.dto.ToggleRequest;
import com.networkproxy.service.RuleService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public PageResponse<RuleResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ruleService.list(q, enabled, page, size);
    }

    @GetMapping("/{id}")
    public RuleResponse get(@PathVariable long id) {
        return ruleService.get(id);
    }

    @PostMapping
    public RuleMutationResponse create(
            @Valid @RequestBody RuleRequest request,
            HttpSession session
    ) {
        return ruleService.create(request, principal(session));
    }

    @PutMapping("/{id}")
    public RuleMutationResponse update(
            @PathVariable long id,
            @Valid @RequestBody RuleRequest request,
            HttpSession session
    ) {
        return ruleService.update(id, request, principal(session));
    }

    @DeleteMapping("/{id}")
    public RuleMutationResponse delete(
            @PathVariable long id,
            HttpSession session
    ) {
        return ruleService.delete(id, principal(session));
    }

    @PatchMapping("/{id}/toggle")
    public RuleMutationResponse toggle(
            @PathVariable long id,
            @Valid @RequestBody ToggleRequest request,
            HttpSession session
    ) {
        return ruleService.setEnabled(
                id,
                request.enabled(),
                principal(session));
    }

    private AdminPrincipal principal(HttpSession session) {
        return (AdminPrincipal) session.getAttribute(AuthInterceptor.SESSION_KEY);
    }
}
