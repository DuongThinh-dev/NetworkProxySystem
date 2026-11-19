USE [NetworkProxyDB];
GO

-- Dữ liệu mẫu để minh họa luật cho phép và chặn theo domain/giao thức.
IF NOT EXISTS
(
    SELECT 1 FROM dbo.AccessRules
    WHERE ClientIp IS NULL AND Target = N'example.com'
      AND TargetPort IS NULL AND Protocol = 'HTTPS'
      AND [Action] = 'ALLOW' AND Priority = 100
)
BEGIN
    INSERT INTO dbo.AccessRules
        (ClientIp, Target, TargetPort, Protocol, [Action], Priority)
    VALUES
        (NULL, N'example.com', NULL, 'HTTPS', 'ALLOW', 100);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM dbo.AccessRules
    WHERE ClientIp IS NULL AND Target = N'facebook.com'
      AND TargetPort IS NULL AND Protocol = 'HTTPS'
      AND [Action] = 'BLOCK' AND Priority = 100
)
BEGIN
    INSERT INTO dbo.AccessRules
        (ClientIp, Target, TargetPort, Protocol, [Action], Priority)
    VALUES
        (NULL, N'facebook.com', NULL, 'HTTPS', 'BLOCK', 100);
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM dbo.AccessRules
    WHERE ClientIp IS NULL AND Target = N'example.com'
      AND TargetPort = 22 AND Protocol = 'SSH'
      AND [Action] = 'ALLOW' AND Priority = 100
)
BEGIN
    INSERT INTO dbo.AccessRules
        (ClientIp, Target, TargetPort, Protocol, [Action], Priority)
    VALUES
        (NULL, N'example.com', 22, 'SSH', 'ALLOW', 100);
END;
GO
