package com.networkproxy.dto;

import java.util.List;

public record DashboardSummary(
        long totalRules,
        long enabledRules,
        long totalLogs,
        long allowedLogs,
        long blockedLogs,
        long errorLogs,
        long last24hLogs,
        long distinctClients,
        List<TopTarget> topTargets,
        List<LogResponse> recentLogs,
        List<AuditLogResponse> recentAudit,
        ProxyStats proxy
) {
}
