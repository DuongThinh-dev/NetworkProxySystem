#!/usr/bin/env pwsh
# Start MySQL with docker-compose
Set-Location "e:\MangMayTinh\NetworkProxySystem"
docker-compose up -d
Write-Host "MySQL container started successfully!"
