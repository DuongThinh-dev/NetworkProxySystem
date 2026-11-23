USE [NetworkProxyDB];
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_AccessRules_Enabled_Priority'
      AND object_id = OBJECT_ID(N'dbo.AccessRules')
)
BEGIN
    CREATE INDEX IX_AccessRules_Enabled_Priority
        ON dbo.AccessRules (IsEnabled, Priority DESC, RuleId ASC)
        INCLUDE (ClientIp, Target, TargetPort, Protocol, [Action]);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_ConnectionLogs_OccurredAt'
      AND object_id = OBJECT_ID(N'dbo.ConnectionLogs')
)
BEGIN
    CREATE INDEX IX_ConnectionLogs_OccurredAt
        ON dbo.ConnectionLogs (OccurredAt DESC, LogId DESC)
        INCLUDE (ClientIp, Target, TargetPort, Protocol, Decision,
                 MatchedRuleId, ReasonCode, ExecutionStatus);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_ConnectionLogs_ClientIp_OccurredAt'
      AND object_id = OBJECT_ID(N'dbo.ConnectionLogs')
)
BEGIN
    CREATE INDEX IX_ConnectionLogs_ClientIp_OccurredAt
        ON dbo.ConnectionLogs (ClientIp, OccurredAt DESC);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_ConnectionLogs_Decision_OccurredAt'
      AND object_id = OBJECT_ID(N'dbo.ConnectionLogs')
)
BEGIN
    CREATE INDEX IX_ConnectionLogs_Decision_OccurredAt
        ON dbo.ConnectionLogs (Decision, OccurredAt DESC);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_AdminAuditLogs_CreatedAt'
      AND object_id = OBJECT_ID(N'dbo.AdminAuditLogs')
)
BEGIN
    CREATE INDEX IX_AdminAuditLogs_CreatedAt
        ON dbo.AdminAuditLogs (CreatedAt DESC, AuditId DESC);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_AdminAuditLogs_UserId_CreatedAt'
      AND object_id = OBJECT_ID(N'dbo.AdminAuditLogs')
)
BEGIN
    CREATE INDEX IX_AdminAuditLogs_UserId_CreatedAt
        ON dbo.AdminAuditLogs (UserId, CreatedAt DESC);
END;
GO
