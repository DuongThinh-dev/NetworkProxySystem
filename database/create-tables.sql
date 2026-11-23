USE [NetworkProxyDB];
GO

IF OBJECT_ID(N'dbo.AccessRules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AccessRules
    (
        RuleId       BIGINT IDENTITY(1,1) NOT NULL,
        ClientIp     NVARCHAR(45) NULL,
        Target       NVARCHAR(253) NULL,
        TargetPort   INT NULL,
        Protocol     VARCHAR(8) NULL,
        [Action]     VARCHAR(5) NOT NULL,
        Priority     INT NOT NULL CONSTRAINT DF_AccessRules_Priority DEFAULT (0),
        IsEnabled    BIT NOT NULL CONSTRAINT DF_AccessRules_IsEnabled DEFAULT (1),
        CreatedAt    DATETIME2(3) NOT NULL
                         CONSTRAINT DF_AccessRules_CreatedAt DEFAULT (SYSUTCDATETIME()),
        UpdatedAt    DATETIME2(3) NOT NULL
                         CONSTRAINT DF_AccessRules_UpdatedAt DEFAULT (SYSUTCDATETIME()),

        CONSTRAINT PK_AccessRules PRIMARY KEY (RuleId),
        CONSTRAINT CK_AccessRules_TargetPort
            CHECK (TargetPort IS NULL OR TargetPort BETWEEN 1 AND 65535),
        CONSTRAINT CK_AccessRules_Protocol
            CHECK (Protocol IS NULL OR Protocol IN ('HTTP', 'HTTPS', 'SSH')),
        CONSTRAINT CK_AccessRules_Action
            CHECK ([Action] IN ('ALLOW', 'BLOCK'))
    );
END;
GO

IF OBJECT_ID(N'dbo.ConnectionLogs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ConnectionLogs
    (
        LogId           BIGINT IDENTITY(1,1) NOT NULL,
        OccurredAt      DATETIME2(3) NOT NULL
                            CONSTRAINT DF_ConnectionLogs_OccurredAt DEFAULT (SYSUTCDATETIME()),
        ClientIp        NVARCHAR(45) NOT NULL,
        Target          NVARCHAR(253) NOT NULL,
        TargetPort      INT NOT NULL,
        Protocol        VARCHAR(8) NOT NULL,
        Decision        VARCHAR(5) NOT NULL,
        MatchedRuleId   BIGINT NULL,
        ReasonCode      NVARCHAR(64) NOT NULL,
        ExecutionStatus VARCHAR(10) NOT NULL,
        ErrorMessage    NVARCHAR(1000) NULL,

        CONSTRAINT PK_ConnectionLogs PRIMARY KEY (LogId),
        CONSTRAINT CK_ConnectionLogs_TargetPort
            CHECK (TargetPort BETWEEN 1 AND 65535),
        CONSTRAINT CK_ConnectionLogs_Protocol
            CHECK (Protocol IN ('HTTP', 'HTTPS', 'SSH')),
        CONSTRAINT CK_ConnectionLogs_Decision
            CHECK (Decision IN ('ALLOW', 'BLOCK')),
        CONSTRAINT CK_ConnectionLogs_ExecutionStatus
            CHECK (ExecutionStatus IN ('CONNECTED', 'BLOCKED', 'ERROR')),
        CONSTRAINT FK_ConnectionLogs_AccessRules
            FOREIGN KEY (MatchedRuleId) REFERENCES dbo.AccessRules (RuleId)
            ON DELETE SET NULL
    );
END;
GO

-- Tài khoản quản trị cho Admin API (Phase 6).
IF OBJECT_ID(N'dbo.AdminUsers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AdminUsers
    (
        UserId       BIGINT IDENTITY(1,1) NOT NULL,
        Username     NVARCHAR(64) NOT NULL,
        PasswordHash NVARCHAR(100) NOT NULL,
        DisplayName  NVARCHAR(128) NULL,
        IsEnabled    BIT NOT NULL
                         CONSTRAINT DF_AdminUsers_IsEnabled DEFAULT (1),
        CreatedAt    DATETIME2(3) NOT NULL
                         CONSTRAINT DF_AdminUsers_CreatedAt DEFAULT (SYSUTCDATETIME()),
        LastLoginAt  DATETIME2(3) NULL,

        CONSTRAINT PK_AdminUsers PRIMARY KEY (UserId),
        CONSTRAINT UQ_AdminUsers_Username UNIQUE (Username)
    );
END;
GO

-- Nhật ký thao tác quản trị (thêm/sửa/xóa rule, đăng nhập...).
IF OBJECT_ID(N'dbo.AdminAuditLogs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AdminAuditLogs
    (
        AuditId    BIGINT IDENTITY(1,1) NOT NULL,
        UserId     BIGINT NULL,
        Username   NVARCHAR(64) NULL,
        Action     NVARCHAR(32) NOT NULL,
        EntityType NVARCHAR(32) NOT NULL,
        EntityId   BIGINT NULL,
        Details    NVARCHAR(1000) NULL,
        CreatedAt  DATETIME2(3) NOT NULL
                       CONSTRAINT DF_AdminAuditLogs_CreatedAt DEFAULT (SYSUTCDATETIME()),

        CONSTRAINT PK_AdminAuditLogs PRIMARY KEY (AuditId),
        CONSTRAINT FK_AdminAuditLogs_AdminUsers
            FOREIGN KEY (UserId) REFERENCES dbo.AdminUsers (UserId)
            ON DELETE SET NULL
    );
END;
GO
