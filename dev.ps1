param(
  [ValidateSet('sync', 'start', 'all', 'stop', 'game', 'game-package', 'game-certify-offline', 'game-certify-live')]
  [string]$Action = 'all',
  [switch]$SkipBuild,
  [switch]$ForceRestart,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$GameArgs
)

if ($Action -eq 'game-package') {
  & (Join-Path $PSScriptRoot 'scripts\package-engine.ps1')
  exit $LASTEXITCODE
}

if ($Action -eq 'game-certify-offline') {
  if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'scripts\package-engine.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
  $distribution = Join-Path $PSScriptRoot 'app\engine\target\distribution\capybee-engine-cli-0.1.0-SNAPSHOT'
  & java -jar (Join-Path $distribution 'bin\engine-cli.jar') certify --mode offline `
    --pack-dir (Join-Path $distribution 'packs\lanterns-of-willow-marsh') `
    --suite (Join-Path $distribution 'certification\willow-marsh-v1\suite.json') `
    --evidence-dir (Join-Path $distribution 'evidence\offline')
  exit $LASTEXITCODE
}

if ($Action -eq 'game-certify-live') {
  $distribution = Join-Path $PSScriptRoot 'app\engine\target\distribution\capybee-engine-cli-0.1.0-SNAPSHOT'
  if (-not (Test-Path -LiteralPath (Join-Path $distribution 'bin\engine-cli.jar'))) {
    Write-Error 'The release bundle is missing. Run ./dev.ps1 game-package first.'
    exit 2
  }
  if ($GameArgs.Count -gt 0 -and $GameArgs[0] -eq '--') {
    $GameArgs = if ($GameArgs.Count -gt 1) { $GameArgs[1..($GameArgs.Count - 1)] } else { @() }
  }
  & java -jar (Join-Path $distribution 'bin\engine-cli.jar') certify --mode live @GameArgs
  exit $LASTEXITCODE
}

if ($Action -eq 'game') {
  $engineJar = Join-Path $PSScriptRoot 'app\engine\cli\target\engine-cli-0.1.0-SNAPSHOT.jar'
  if (-not $SkipBuild) {
    & mvn -f (Join-Path $PSScriptRoot 'app\engine\pom.xml') -pl cli -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
  if (-not (Test-Path -LiteralPath $engineJar)) {
    Write-Error 'The CLI artifact is missing. Run without -SkipBuild first.'
    exit 2
  }
  & java -jar $engineJar play @GameArgs
  exit $LASTEXITCODE
}

$forwarded = @{}
foreach ($key in $PSBoundParameters.Keys) {
  if ($key -ne 'Action') {
    $forwarded[$key] = $PSBoundParameters[$key]
  }
}

& "$PSScriptRoot\scripts\dev-workflow.ps1" -Action $Action @forwarded
