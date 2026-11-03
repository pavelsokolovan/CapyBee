param(
  [ValidateSet('sync', 'start', 'all', 'stop', 'game', 'game-package', 'game-verify', 'game-certify-offline', 'game-certify-live', 'game-release-manifest')]
  [string]$Action = 'all',
  [switch]$SkipBuild,
  [switch]$ForceRestart,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$GameArgs
)

# Keep UTF-8 configuration local to this launcher process. Java is also given
# explicit stream encodings so redirected output is deterministic on Windows.
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom
$javaUtf8Args = @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dfile.encoding=UTF-8')

if ($Action -eq 'game-package') {
  & (Join-Path $PSScriptRoot 'scripts\package-engine.ps1')
  exit $LASTEXITCODE
}

function Get-EngineCandidate {
  $candidatePath = Join-Path $PSScriptRoot 'app\engine\target\distribution\candidate.json'
  if (-not (Test-Path -LiteralPath $candidatePath)) { throw 'The release candidate is missing. Run ./dev.ps1 game-package first.' }
  Get-Content -Raw -LiteralPath $candidatePath | ConvertFrom-Json
}

if ($Action -eq 'game-verify') {
  $candidate = Get-EngineCandidate
  $archive = Join-Path $PSScriptRoot "app\engine\target\distribution\$($candidate.archive.file)"
  & (Join-Path $PSScriptRoot 'scripts\verify-engine-package.ps1') -Archive $archive
  exit $LASTEXITCODE
}

if ($Action -eq 'game-certify-offline') {
  if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'scripts\package-engine.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
  $candidate = Get-EngineCandidate
  $archive = Join-Path $PSScriptRoot "app\engine\target\distribution\$($candidate.archive.file)"
  $extraction = Join-Path $PSScriptRoot "app\engine\target\clean-room\$($candidate.candidateId)"
  $evidence = Join-Path $PSScriptRoot "app\engine\target\evidence\$($candidate.candidateId)"
  if (Test-Path -LiteralPath $extraction) { Remove-Item -LiteralPath $extraction -Recurse -Force }
  try {
    & (Join-Path $PSScriptRoot 'scripts\verify-engine-package.ps1') -Archive $archive -ExtractTo $extraction
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    foreach ($name in @('ANTHROPIC_API_KEY','GENERATION_MODEL_ID','PROVIDER_PRICING_JSON')) { Set-Item -Path "env:$name" -Value '' }
    $env:GENERATION_PROVIDER_MODE = 'disabled'; $env:ANTHROPIC_DEBUG = 'false'
    & java @javaUtf8Args -jar (Join-Path $extraction 'bin\engine-cli.jar') certify --mode offline `
      --pack-dir (Join-Path $extraction 'packs\lanterns-of-willow-marsh') `
      --suite (Join-Path $extraction 'certification\willow-marsh-v1\suite.json') `
      --evidence-dir (Join-Path $evidence 'pack')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java @javaUtf8Args -jar (Join-Path $extraction 'bin\engine-cli.jar') certify --mode offline --dynamic `
      --profile-dir (Join-Path $extraction 'profiles\generic-adventure') `
      --fixture-dir (Join-Path $extraction 'certification\dynamic-v1') `
      --evidence-dir (Join-Path $evidence 'dynamic')
    exit $LASTEXITCODE
  } finally { if (Test-Path -LiteralPath $extraction) { Remove-Item -LiteralPath $extraction -Recurse -Force } }
}

if ($Action -eq 'game-certify-live') {
  $candidate = Get-EngineCandidate
  $distribution = Join-Path $PSScriptRoot "app\engine\target\distribution\capybee-engine-cli-$($candidate.version)"
  if (-not (Test-Path -LiteralPath (Join-Path $distribution 'bin\engine-cli.jar'))) {
    Write-Error 'The release bundle is missing. Run ./dev.ps1 game-package first.'
    exit 2
  }
  if ($GameArgs.Count -gt 0 -and $GameArgs[0] -eq '--') {
    $GameArgs = if ($GameArgs.Count -gt 1) { $GameArgs[1..($GameArgs.Count - 1)] } else { @() }
  }
  & java @javaUtf8Args -jar (Join-Path $distribution 'bin\engine-cli.jar') certify --mode live @GameArgs
  exit $LASTEXITCODE
}

if ($Action -eq 'game-release-manifest') {
  & (Join-Path $PSScriptRoot 'scripts\new-release-manifest.ps1') @GameArgs
  exit $LASTEXITCODE
}

if ($Action -eq 'game') {
  [xml]$enginePom = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'app\engine\pom.xml')
  $engineJar = Join-Path $PSScriptRoot "app\engine\cli\target\engine-cli-$($enginePom.project.version).jar"
  if (-not $SkipBuild) {
    & mvn -f (Join-Path $PSScriptRoot 'app\engine\pom.xml') -pl cli -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
  if (-not (Test-Path -LiteralPath $engineJar)) {
    Write-Error 'The CLI artifact is missing. Run without -SkipBuild first.'
    exit 2
  }
  & java @javaUtf8Args -jar $engineJar play @GameArgs
  exit $LASTEXITCODE
}

$forwarded = @{}
foreach ($key in $PSBoundParameters.Keys) {
  if ($key -ne 'Action') {
    $forwarded[$key] = $PSBoundParameters[$key]
  }
}

& "$PSScriptRoot\scripts\dev-workflow.ps1" -Action $Action @forwarded
