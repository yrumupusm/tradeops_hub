param(
    [string]$MavenPath = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$EnvFile = Join-Path $ProjectRoot ".env"

function Resolve-MavenCommand {
    if (-not [string]::IsNullOrWhiteSpace($MavenPath) -and (Test-Path -LiteralPath $MavenPath)) {
        return $MavenPath
    }
    $maven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $maven) { return $maven.Source }
    throw "Maven command not found. Pass -MavenPath with the absolute path to mvn.cmd."
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Missing .env. Copy .env.example to .env and set local values before starting the API."
}

foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
    $separator = $trimmed.IndexOf("=")
    if ($separator -lt 1) { continue }
    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    Set-Item -Path "Env:$name" -Value $value
}

Set-Location -LiteralPath (Join-Path $ProjectRoot "backend")
& (Resolve-MavenCommand) -q spring-boot:run
