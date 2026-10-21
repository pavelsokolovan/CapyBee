param(
  [ValidateSet('sync', 'start', 'all', 'stop', 'game')]
  [string]$Action = 'all',
  [switch]$SkipBuild,
  [switch]$ForceRestart,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$GameArgs
)

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
