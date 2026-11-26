
-- Create indexes for NetworkProxyDB (MySQL version)
USE NetworkProxyDB;

-- Index for AccessRules: enabled rules with priority
CREATE INDEX IX_AccessRules_Enabled_Priority
    ON AccessRules (IsEnabled, Priority DESC, RuleId ASC);

-- Index for ConnectionLogs: query by time
CREATE INDEX IX_ConnectionLogs_OccurredAt
    ON ConnectionLogs (OccurredAt DESC, LogId DESC);

-- Index for ConnectionLogs: query by client IP and time
CREATE INDEX IX_ConnectionLogs_ClientIp_OccurredAt
    ON ConnectionLogs (ClientIp, OccurredAt DESC);

-- Index for ConnectionLogs: query by decision (ALLOW/BLOCK) and time
CREATE INDEX IX_ConnectionLogs_Decision_OccurredAt
    ON ConnectionLogs (Decision, OccurredAt DESC);

-- Index for AdminAuditLogs: query by time
CREATE INDEX IX_AdminAuditLogs_CreatedAt
    ON AdminAuditLogs (CreatedAt DESC, AuditId DESC);

-- Index for AdminAuditLogs: query by user and time
CREATE INDEX IX_AdminAuditLogs_UserId_CreatedAt
    ON AdminAuditLogs (UserId, CreatedAt DESC);
