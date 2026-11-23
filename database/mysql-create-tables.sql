-- Create tables for NetworkProxyDB (MySQL version)
USE NetworkProxyDB;

-- Table: AccessRules
CREATE TABLE IF NOT EXISTS AccessRules
(
    RuleId       BIGINT AUTO_INCREMENT NOT NULL,
    ClientIp     VARCHAR(45) NULL,
    Target       VARCHAR(253) NULL,
    TargetPort   INT NULL,
    Protocol     VARCHAR(8) NULL,
    Action       VARCHAR(5) NOT NULL,
    Priority     INT NOT NULL DEFAULT 0,
    IsEnabled    TINYINT(1) NOT NULL DEFAULT 1,
    CreatedAt    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UpdatedAt    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (RuleId),
    CONSTRAINT CK_AccessRules_TargetPort
        CHECK (TargetPort IS NULL OR (TargetPort BETWEEN 1 AND 65535)),
    CONSTRAINT CK_AccessRules_Protocol
        CHECK (Protocol IS NULL OR Protocol IN ('HTTP', 'HTTPS', 'SSH')),
    CONSTRAINT CK_AccessRules_Action
        CHECK (Action IN ('ALLOW', 'BLOCK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table: ConnectionLogs
CREATE TABLE IF NOT EXISTS ConnectionLogs
(
    LogId           BIGINT AUTO_INCREMENT NOT NULL,
    OccurredAt      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ClientIp        VARCHAR(45) NOT NULL,
    Target          VARCHAR(253) NOT NULL,
    TargetPort      INT NOT NULL,
    Protocol        VARCHAR(8) NOT NULL,
    Decision        VARCHAR(5) NOT NULL,
    MatchedRuleId   BIGINT NULL,
    ReasonCode      VARCHAR(64) NOT NULL,
    ExecutionStatus VARCHAR(10) NOT NULL,
    ErrorMessage    VARCHAR(1000) NULL,

    PRIMARY KEY (LogId),
    CONSTRAINT CK_ConnectionLogs_TargetPort
        CHECK (TargetPort BETWEEN 1 AND 65535),
    CONSTRAINT CK_ConnectionLogs_Protocol
        CHECK (Protocol IN ('HTTP', 'HTTPS', 'SSH')),
    CONSTRAINT CK_ConnectionLogs_Decision
        CHECK (Decision IN ('ALLOW', 'BLOCK')),
    CONSTRAINT CK_ConnectionLogs_ExecutionStatus
        CHECK (ExecutionStatus IN ('CONNECTED', 'BLOCKED', 'ERROR')),
    CONSTRAINT FK_ConnectionLogs_AccessRules
        FOREIGN KEY (MatchedRuleId) REFERENCES AccessRules (RuleId)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table: AdminUsers
CREATE TABLE IF NOT EXISTS AdminUsers
(
    UserId       BIGINT AUTO_INCREMENT NOT NULL,
    Username     VARCHAR(64) NOT NULL,
    PasswordHash VARCHAR(100) NOT NULL,
    DisplayName  VARCHAR(128) NULL,
    IsEnabled    TINYINT(1) NOT NULL DEFAULT 1,
    CreatedAt    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    LastLoginAt  TIMESTAMP(3) NULL,

    PRIMARY KEY (UserId),
    UNIQUE KEY UQ_AdminUsers_Username (Username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table: AdminAuditLogs
CREATE TABLE IF NOT EXISTS AdminAuditLogs
(
    AuditId    BIGINT AUTO_INCREMENT NOT NULL,
    UserId     BIGINT NULL,
    Username   VARCHAR(64) NULL,
    Action     VARCHAR(32) NOT NULL,
    EntityType VARCHAR(32) NOT NULL,
    EntityId   BIGINT NULL,
    Details    VARCHAR(1000) NULL,
    CreatedAt  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (AuditId),
    CONSTRAINT FK_AdminAuditLogs_AdminUsers
        FOREIGN KEY (UserId) REFERENCES AdminUsers (UserId)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
