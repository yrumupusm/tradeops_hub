param(
    [string]$ApiBase = "http://127.0.0.1:18081/api/v1",
    [string]$WebBase = "http://127.0.0.1:13000",
    [string]$EvidencePath = "artifacts/runtime/latest.json",
    [string]$VerificationId = [guid]::NewGuid().ToString()
)
$ErrorActionPreference = "Stop"
& node (Join-Path $PSScriptRoot "runtime-scenarios.mjs") --api $ApiBase --web $WebBase --evidence $EvidencePath --verification-id $VerificationId
if ($LASTEXITCODE -ne 0) { throw "Runtime verification failed. Inspect the safe scenario summary in artifacts." }
& node (Join-Path $PSScriptRoot "verify-evidence.mjs") --evidence $EvidencePath --verification-id $VerificationId
if ($LASTEXITCODE -ne 0) { throw "Runtime evidence verification failed." }
Write-Host "Runtime verification passed ($VerificationId)."
