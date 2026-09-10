param([string]$MavenPath = "")
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$MavenPath) { $MavenPath = (Get-Command mvn.cmd -ErrorAction Stop).Source }
& (Join-Path $PSScriptRoot 'verify-local.ps1') -MavenPath $MavenPath
if ($LASTEXITCODE -ne 0) { throw 'Hub verification failed.' }
Push-Location (Join-Path $root 'services/law-search')
try {
    foreach ($file in @('app.js','admin.js')) {
        & node --check "src/main/resources/static/$file"
        if ($LASTEXITCODE -ne 0) { throw 'Law JavaScript syntax check failed.' }
    }
    # System properties pin tests to temporary H2 and mock providers, including when .env exists.
    & $MavenPath test `
      '-Dspring.config.import=optional:classpath:/no-local-env.properties' `
      '-Dspring.datasource.url=jdbc:h2:mem:monorepoverification;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1' `
      '-Dspring.datasource.driver-class-name=org.h2.Driver' `
      '-Dspring.datasource.username=sa' '-Dspring.datasource.password=' `
      '-Dspring.jpa.hibernate.ddl-auto=create-drop' `
      '-Dapp.llm.provider=mock' '-Dapp.embedding.provider=mock' `
      '-Dapp.embedding.dimensions=8' '-Dapp.vector.provider=inmemory' `
      '-Dapp.reranker.provider=mock' '-Dapp.embedding.batch-delay-millis=0'
    if ($LASTEXITCODE -ne 0) { throw 'Isolated law verification failed.' }
} finally { Pop-Location }
Write-Output 'Monorepo verification passed. No real database or provider is required.'
