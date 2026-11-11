param(
    [switch]$ExternalOpenCode,
    [switch]$MysqlExternal,
    [switch]$NoDocker
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已创建 .env。请先修改密码和 Token，然后重新运行 .\start.ps1" -ForegroundColor Yellow
    exit 1
}

Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $name, $value = $line.Split("=", 2)
        $value = $value.Trim().Trim("'").Trim('"')
        [Environment]::SetEnvironmentVariable($name.Trim(), $value, "Process")
    }
}

if ($NoDocker) { $ExternalOpenCode = $true; $MysqlExternal = $true }
$javaLine = (& java -version 2>&1 | Select-Object -First 1)
if ($LASTEXITCODE -ne 0 -or $javaLine -notmatch 'version "(\d+)') {
    throw "未找到 Java 25"
}
if ([int]$Matches[1] -lt 25) { throw "需要 Java 25，当前：$javaLine" }

if (-not $MysqlExternal) {
    docker compose up -d mysql
    Write-Host "MySQL 容器已启动，等待端口...";
    $port = if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 }
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $client.Connect("127.0.0.1", $port)
            $client.Close()
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw "MySQL 端口等待超时" }
}

if (-not $ExternalOpenCode) {
    docker compose up -d opencode
    $baseUrl = if ($env:OPENCODE_BASE_URL) { $env:OPENCODE_BASE_URL } else { "http://127.0.0.1:4096" }
    $ocUser = if ($env:OPENCODE_SERVER_USERNAME) { $env:OPENCODE_SERVER_USERNAME } else { "opencode" }
    $pair = "${ocUser}:$($env:OPENCODE_SERVER_PASSWORD)"
    $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $headers = @{ Authorization = "Basic $basic" }
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/info" -Headers $headers | Out-Null; $ready = $true; break }
        catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw "OpenCode 启动超时，请运行 docker compose logs opencode" }
}

$webPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { "8080" }
Write-Host "------------------------------------------------------------"
Write-Host "Web 管理台: http://127.0.0.1:$webPort"
Write-Host "OneBot 地址: ws://<本机IP>:$webPort/onebot/v11/ws"
if (-not $ExternalOpenCode) { Write-Host "首次使用请执行: .\scripts\opencode-auth.ps1" }
Write-Host "------------------------------------------------------------"

if (Test-Path ".\mvnw.cmd") { & .\mvnw.cmd spring-boot:run } else { & mvn spring-boot:run }
