param(
    [string]$MavenPath = "",
    [ValidateRange(1024, 65535)][int]$ApiPort = 18081,
    [ValidateRange(1024, 65535)][int]$WebPort = 13000,
    [ValidateRange(1024, 65535)][int]$PostgresPort = 15432,
    [switch]$KeepRuntime
)
$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$VerificationId = [guid]::NewGuid().ToString()
$RunDirectory = Join-Path $ProjectRoot "artifacts/final/$VerificationId"
New-Item -ItemType Directory -Path $RunDirectory -Force | Out-Null
$RuntimeEvidence = Join-Path $RunDirectory "runtime.json"
$SummaryPath = Join-Path $ProjectRoot "artifacts/final/latest.json"
$ContainerName = "tradeops-verify-" + $VerificationId.Substring(0, 8)
$ContainerId = $null
$ApiProcess = $null
$WebProcess = $null
$Succeeded = $false
$OldEnvironment = @{}
$OriginalLocation = Get-Location
$Summary = [ordered]@{
    schemaVersion = 1; verificationId = $VerificationId; status = "FAILED"
    generatedAt = [DateTime]::UtcNow.ToString("o")
    localPassed = $false; buildPassed = $false; runtimePassed = $false
    evidencePassed = $false; databasePassed = $false; cleanupPassed = $false
    runtimeRetained = $false; runtimeEvidence = "artifacts/final/$VerificationId/runtime.json"
}
function Save-Summary {
    $Summary.generatedAt = [DateTime]::UtcNow.ToString("o")
    $json = $Summary | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText($SummaryPath, $json + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $RunDirectory "summary.json"), $json + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
}
function Set-VerificationEnvironment([string]$Name, [string]$Value) {
    if (-not $OldEnvironment.ContainsKey($Name)) { $OldEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, "Process") }
    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}
function Invoke-Logged([string]$Command, [string[]]$Arguments, [string]$LogPath, [string]$FailureCode) {
    Get-Command $Command -ErrorAction Stop | Out-Null
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell wraps native stderr as ErrorRecord objects. A warning is not an exit failure.
        $ErrorActionPreference = "Continue"
        & $Command @Arguments *> $LogPath
        $commandExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($commandExit -ne 0) { throw $FailureCode }
}
function Assert-FreePort([int]$Port) {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "VERIFICATION_PORT_UNAVAILABLE: $Port" } finally { $listener.Stop() }
}
function Wait-Http([string]$Url, [Diagnostics.Process]$Process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { throw "VERIFICATION_SERVICE_EXITED" }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2 -MaximumRedirection 0
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "VERIFICATION_SERVICE_TIMEOUT"
}
Save-Summary
try {
    Set-Location -LiteralPath $ProjectRoot
    if (@($ApiPort, $WebPort, $PostgresPort | Select-Object -Unique).Count -ne 3) { throw "VERIFICATION_PORTS_MUST_DIFFER" }
    foreach ($port in @($ApiPort, $WebPort, $PostgresPort)) { Assert-FreePort $port }
    if ([string]::IsNullOrWhiteSpace($MavenPath)) {
        $MavenPath = (Get-Command mvn.cmd -ErrorAction Stop).Source
    }
    if (-not (Test-Path -LiteralPath $MavenPath)) { throw "MAVEN_NOT_FOUND" }
    $java = (Get-Command java -ErrorAction Stop).Source
    $node = (Get-Command node -ErrorAction Stop).Source
    docker info --format "{{.ServerVersion}}" *> (Join-Path $RunDirectory "docker-check.log")
    if ($LASTEXITCODE -ne 0) { throw "DOCKER_UNAVAILABLE" }

    Write-Host "==> Local tests and verification-tool tests"
    Invoke-Logged "powershell.exe" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "verify-local.ps1"), "-MavenPath", $MavenPath) (Join-Path $RunDirectory "local.log") "LOCAL_VERIFICATION_FAILED"
    $Summary.localPassed = $true

    # Set both application and datasource variables so inherited local settings cannot select another DB.
    $bytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($bytes)
    Set-VerificationEnvironment "POSTGRES_PASSWORD" $password
    Set-VerificationEnvironment "POSTGRES_USER" "tradeops_verify"
    Set-VerificationEnvironment "POSTGRES_DB" "tradeops_verify"
    Set-VerificationEnvironment "POSTGRES_PORT" "$PostgresPort"
    Set-VerificationEnvironment "DB_HOST" "127.0.0.1"
    Set-VerificationEnvironment "SPRING_DATASOURCE_URL" "jdbc:postgresql://127.0.0.1:$PostgresPort/tradeops_verify"
    Set-VerificationEnvironment "SPRING_DATASOURCE_USERNAME" "tradeops_verify"
    Set-VerificationEnvironment "SPRING_DATASOURCE_PASSWORD" $password
    Set-VerificationEnvironment "JWT_SECRET" ([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([guid]::NewGuid().ToString() + [guid]::NewGuid().ToString())))
    Set-VerificationEnvironment "JWT_ISSUER" "tradeops-verification"
    Set-VerificationEnvironment "SEED_DEMO_USERS" "true"
    Set-VerificationEnvironment "API_PORT" "$ApiPort"
    Set-VerificationEnvironment "SERVER_ADDRESS" "127.0.0.1"
    Set-VerificationEnvironment "TRADEOPS_WEB_ALLOWED_ORIGIN" "http://127.0.0.1:$WebPort"
    Set-VerificationEnvironment "NEXT_PUBLIC_API_BASE" "http://127.0.0.1:$ApiPort/api/v1"
    Set-VerificationEnvironment "VERIFY_LOCAL_PASSWORD" "portfolio-demo"

    Write-Host "==> Build API and web"
    Push-Location (Join-Path $ProjectRoot "backend")
    try {
        Invoke-Logged $MavenPath @("-q", "-DskipTests", "package") (Join-Path $RunDirectory "api-build.log") "API_BUILD_FAILED"
    } finally { Pop-Location }
    Push-Location (Join-Path $ProjectRoot "frontend")
    try {
        Invoke-Logged "npm.cmd" @("run", "build") (Join-Path $RunDirectory "web-build.log") "WEB_BUILD_FAILED"
    } finally { Pop-Location }
    $Summary.buildPassed = $true

    Write-Host "==> Start isolated PostgreSQL"
    # A private tmpfs database: no existing volume is read, changed, or removed.
    $dockerResult = & docker run --rm --detach --name $ContainerName --label "io.tradeops.verification=$VerificationId" --publish "127.0.0.1:${PostgresPort}:5432" --tmpfs /var/lib/postgresql/data:rw -e POSTGRES_PASSWORD -e POSTGRES_USER -e POSTGRES_DB postgres:16-alpine
    if ($LASTEXITCODE -ne 0) { throw "POSTGRES_START_FAILED" }
    $ContainerId = ($dockerResult | Select-Object -Last 1).Trim()
    if ($ContainerId -notmatch '^[a-f0-9]{64}$') { throw "POSTGRES_CONTAINER_ID_INVALID" }
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        & docker exec $ContainerId pg_isready -U tradeops_verify -d tradeops_verify *> (Join-Path $RunDirectory "postgres-ready.log")
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "POSTGRES_START_TIMEOUT" }

    Write-Host "==> Start isolated API and web"
    $jar = Join-Path $ProjectRoot "backend/target/tradeops-api-0.1.0-SNAPSHOT.jar"
    $ApiProcess = Start-Process -FilePath $java -ArgumentList @("-jar", ('"' + $jar + '"')) -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $RunDirectory "api.log") -RedirectStandardError (Join-Path $RunDirectory "api.err.log")
    $next = Join-Path $ProjectRoot "frontend/node_modules/next/dist/bin/next"
    $WebProcess = Start-Process -FilePath $node -ArgumentList @(('"' + $next + '"'), "start", "--hostname", "127.0.0.1", "--port", "$WebPort") -WorkingDirectory (Join-Path $ProjectRoot "frontend") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $RunDirectory "web.log") -RedirectStandardError (Join-Path $RunDirectory "web.err.log")
    Wait-Http "http://127.0.0.1:$ApiPort/api/v1/health" $ApiProcess
    Wait-Http "http://127.0.0.1:$WebPort" $WebProcess

    Write-Host "==> Runtime scenarios and evidence"
    & (Join-Path $PSScriptRoot "verify-runtime.ps1") -ApiBase "http://127.0.0.1:$ApiPort/api/v1" -WebBase "http://127.0.0.1:$WebPort" -EvidencePath $RuntimeEvidence -VerificationId $VerificationId
    $Summary.runtimePassed = $true
    $Summary.evidencePassed = $true

    Write-Host "==> PostgreSQL persistence assertions"
    $sql = "select (select count(*) from watchlist_snapshots), (select count(*) from watchlist_snapshot_records), (select count(*) from watchlist_changes), (select count(*) from watchlist_source_runs), (select count(*) from import_runs), (select count(*) from trade_transactions), (select count(*) from import_row_rejections), (select count(*) from screening_reviews), (select count(*) from screening_reviews r join audit_events a on a.entity_id=cast(r.id as varchar) and a.correlation_id=r.correlation_id and a.actor_username=r.actor_username and a.entity_type='SCREENING_REVIEW' and a.event_type='SCREENING_REVIEW_RECORDED')"
    $counts = & docker exec $ContainerId psql -U tradeops_verify -d tradeops_verify -At -c $sql
    if ($LASTEXITCODE -ne 0 -or ($counts -join "").Trim() -ne "2|8|7|4|1|3|2|2|2") { throw "POSTGRES_PERSISTENCE_ASSERTION_FAILED" }
    $Summary.databasePassed = $true
    $Succeeded = $true
    if ($KeepRuntime) {
        $info = @{verificationId=$VerificationId;containerId=$ContainerId;apiProcessId=$ApiProcess.Id;webProcessId=$WebProcess.Id;apiPort=$ApiPort;webPort=$WebPort}
        [IO.File]::WriteAllText((Join-Path $RunDirectory "runtime-info.json"), ($info | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
    }
} catch {
    Write-Host "Final verification failed. See ignored logs and safe evidence under artifacts/final/$VerificationId."
    throw
} finally {
    $cleanupOk = $true
    if ($KeepRuntime -and $Succeeded) {
        $Summary.runtimeRetained = $true
        Write-Host "Runtime retained for browser review. Resource IDs: artifacts/final/$VerificationId/runtime-info.json"
    } else {
        foreach ($process in @($WebProcess, $ApiProcess)) {
            if ($null -ne $process -and -not $process.HasExited) {
                try { $process.Kill(); $process.WaitForExit(10000) | Out-Null } catch { $cleanupOk = $false }
            }
        }
        if ($ContainerId -match '^[a-f0-9]{64}$') {
            try {
                Invoke-Logged "docker" @("stop", "--timeout", "10", $ContainerId) (Join-Path $RunDirectory "cleanup.log") "CONTAINER_CLEANUP_FAILED"
            } catch { $cleanupOk = $false }
        }
        $Summary.cleanupPassed = $cleanupOk
    }
    foreach ($name in $OldEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $OldEnvironment[$name], "Process") }
    Set-Location -LiteralPath $OriginalLocation.Path
    if ($Succeeded -and $cleanupOk) { $Summary.status = "PASSED" }
    Save-Summary
    if (-not $cleanupOk) { throw "VERIFICATION_CLEANUP_FAILED" }
}
Write-Host "Final verification passed. Evidence: artifacts/final/$VerificationId/summary.json"
