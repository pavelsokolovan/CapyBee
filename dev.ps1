param(
  [ValidateSet('sync', 'start', 'all', 'stop')]
  [string]$Action = 'all',
  [switch]$SkipBuild,
  [switch]$ForceRestart
)

$forwarded = @{}
foreach ($key in $PSBoundParameters.Keys) {
  if ($key -ne 'Action') {
    $forwarded[$key] = $PSBoundParameters[$key]
  }
}

& "$PSScriptRoot\scripts\dev-workflow.ps1" -Action $Action @forwarded
