param(
    [string]$MavenPath = "",
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

function Invoke-Step {
    param([string]$Name, [scriptblock]$ScriptBlock)
    Write-Host "==> $Name"
    & $ScriptBlock
    Write-Host "OK: $Name"
}

function Assert-File {
    param([string]$RelativePath)
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot $RelativePath))) {
        throw "Required file is missing: $RelativePath"
    }
}

function Resolve-MavenCommand {
    if (-not [string]::IsNullOrWhiteSpace($MavenPath) -and (Test-Path -LiteralPath $MavenPath)) {
        return $MavenPath
    }
    $maven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $maven) {
        return $maven.Source
    }
    throw "Maven command not found. Pass -MavenPath with the absolute path to mvn.cmd."
}

Set-Location -LiteralPath $ProjectRoot

Invoke-Step "Repository safety and scenario validation" {
    $required = @("AGENTS.md", "PROJECT_GUIDE.md", "docs\en\api-contract.md", "docs\en\evaluation-harness.md", "harness\scenarios.json", ".env.example")
    foreach ($path in $required) { Assert-File $path }
    $scenarios = Get-Content -LiteralPath "harness\scenarios.json" -Raw -Encoding UTF8 | ConvertFrom-Json
    if (@($scenarios).Count -lt 4) { throw "Expected at least four fixed scenarios." }
    if (@($scenarios.id | Select-Object -Unique).Count -ne @($scenarios).Count) { throw "Scenario IDs must be unique." }
    foreach ($scenario in $scenarios) {
        if ($scenario.id -notmatch '^[a-z0-9-]+$' -or @($scenario.checks).Count -eq 0) { throw "Each scenario needs an ID and assertion list." }
    }
    foreach ($path in @('.env', 'artifacts/probe.json', 'frontend/tsconfig.tsbuildinfo')) {
        git check-ignore -q -- $path
        if ($LASTEXITCODE -ne 0) { throw "Local configuration or generated file is not ignored: $path" }
    }
    $tracked = @(git ls-files)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect tracked files." }
    foreach ($path in $tracked) {
        if ($path -match '(^|/)\.env($|\.)' -and $path -notmatch '(^|/)\.env\.example$') { throw "Local environment file is tracked." }
        if ($path -match '(^artifacts/|/node_modules/|/target/|/\.next/|\.tsbuildinfo$)') { throw "Generated file is tracked: $path" }
    }
}

Invoke-Step "Verification tooling tests" {
    & node --test scripts/verification.test.mjs
    if ($LASTEXITCODE -ne 0) { throw "Verification tooling tests failed." }
}

if (-not $SkipBackend) {
    Invoke-Step "Backend tests" {
        $maven = Resolve-MavenCommand
        # This gate is server-free even when invoked from an application shell.
        $testEnvironment = @{
            SPRING_DATASOURCE_URL = 'jdbc:h2:mem:tradeops;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE'
            SPRING_DATASOURCE_USERNAME = 'sa'
            SPRING_DATASOURCE_PASSWORD = ''
            TRADEOPS_TEST_PG = 'false'
            BIS_SCHEDULE_ENABLED = 'false'
            OWNER_INITIAL_PASSWORD = ''
        }
        $previousEnvironment = @{}
        foreach ($name in $testEnvironment.Keys) {
            $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $testEnvironment[$name], 'Process')
        }
        Push-Location (Join-Path $ProjectRoot "backend")
        try {
            & $maven -q test
            if ($LASTEXITCODE -ne 0) { throw "Backend tests failed with exit code $LASTEXITCODE." }
        } finally {
            Pop-Location
            foreach ($name in $previousEnvironment.Keys) {
                [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
            }
        }
    }
}

if (-not $SkipFrontend) {
    Invoke-Step "Frontend type check" {
        Push-Location (Join-Path $ProjectRoot "frontend")
        try {
            npm run typecheck
            if ($LASTEXITCODE -ne 0) { throw "Frontend type check failed with exit code $LASTEXITCODE." }
        } finally { Pop-Location }
    }
}

Write-Host "Local verification passed. Runtime checks require started API and web services."
