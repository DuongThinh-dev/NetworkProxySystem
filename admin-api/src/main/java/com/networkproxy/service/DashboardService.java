package com.networkproxy.service;

import com.networkproxy.dto.DashboardSummary;
import com.networkproxy.dto.LogResponse;
import com.networkproxy.dto.ProxyStats;
import com.networkproxy.repository.ClientRepository;
import com.networkproxy.repository.LogRepository;
import com.networkproxy.repository.RuleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardService {

    private final RuleRepository ruleRepository;
    private final LogRepository logRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final ProxyControlClient proxyControlClient;

    public DashboardService(
            RuleRepository ruleRepository,
            LogRepository logRepository,
            ClientRepository clientRepository,
            AuditService auditService,
            ProxyControlClient proxyControlClient
    ) {
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
        this.proxyControlClient = proxyControlClient;
    }

    public DashboardSummary summary() {
        LocalDateTime last24h = LocalDateTime.now(ZoneOffset.UTC)
                .minusHours(24);

        List<LogResponse> recentLogs = logRepository.recent(8).stream()
                .map(LogResponse::from)
                .toList();
        ProxyStats proxy = proxyControlClient.stats();

        return new DashboardSummary(
                ruleRepository.count(),
                ruleRepository.countEnabled(),
                logRepository.countAll(),
                logRepository.countByDecision("ALLOW"),
                logRepository.countByDecision("BLOCK"),
                logRepository.countByStatus("ERROR"),
                logRepository.countSince(last24h),
                clientRepository.countDistinct(),
                logRepository.topTargets(5),
                recentLogs,
                auditService.recent(5),
                proxy);
    }
}
