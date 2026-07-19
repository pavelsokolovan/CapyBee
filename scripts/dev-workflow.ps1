param(
  [ValidateSet('sync', 'start', 'all', 'stop')]
  [string]$Action = 'all',
  [switch]$SkipBuild,
  [switch]$ForceRestart
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$uiPath = Join-Path $repoRoot 'app/ui'
$serverPath = Join-Path $repoRoot 'app/server'
$uiDistPath = Join-Path $uiPath 'dist'
$serverStaticResourcePath = Join-Path $serverPath 'src/main/resources/static'
$serverStaticClassesPath = Join-Path $serverPath 'target/classes/static'
$stateDir = Join-Path $repoRoot '.dev'
$stateFile = Join-Path $stateDir 'processes.json'

function Ensure-Path {
  param([string]$Path)
  if (-not (Test-Path $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

function Invoke-UiBuild {
  if ($SkipBuild) {
    Write-Host '[sync] SkipBuild is set, skipping UI build.'
    return
  }

  Write-Host '[sync] Building UI...'
  Push-Location $uiPath
  try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
      throw "UI build failed with exit code $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }
}

function Copy-BuildArtifacts {
  if (-not (Test-Path $uiDistPath)) {
    throw "UI dist folder not found: $uiDistPath. Run build first or remove -SkipBuild."
  }

  Ensure-Path -Path $serverStaticResourcePath
  Ensure-Path -Path $serverStaticClassesPath

  Write-Host '[sync] Copying UI dist -> server src resources static...'
  $null = & robocopy $uiDistPath $serverStaticResourcePath /E /IS /IT
  $copy1Code = $LASTEXITCODE
  if ($copy1Code -ge 8) {
    throw "robocopy to resources/static failed with code $copy1Code"
  }

  Write-Host '[sync] Copying UI dist -> server target/classes static...'
  $null = & robocopy $uiDistPath $serverStaticClassesPath /E /IS /IT
  $copy2Code = $LASTEXITCODE
  if ($copy2Code -ge 8) {
    throw "robocopy to target/classes/static failed with code $copy2Code"
  }

  Write-Host '[sync] Static files are in sync.'
}

function Read-ProcessState {
  if (-not (Test-Path $stateFile)) {
    return $null
  }

  try {
    return Get-Content -Path $stateFile -Raw | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Write-ProcessState {
  param(
    [int]$BackendPid,
    [int]$FrontendPid
  )

  Ensure-Path -Path $stateDir
  @{
    backendPid = $BackendPid
    frontendPid = $FrontendPid
    updatedAt = (Get-Date).ToString('o')
  } | ConvertTo-Json | Set-Content -Path $stateFile
}

function Stop-ManagedProcess {
  param(
    [int]$ProcessId,
    [string]$Name
  )

  if ($ProcessId -le 0) {
    return
  }

  $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($null -eq $proc) {
    Write-Host "[stop] $Name process $ProcessId is already stopped."
    return
  }

  Write-Host "[stop] Stopping $Name process $ProcessId..."
  Stop-Process -Id $ProcessId -Force
}

function Stop-DevProcesses {
  $state = Read-ProcessState
  if ($null -eq $state) {
    Write-Host '[stop] No process state found. Nothing to stop.'
    return
  }

  Stop-ManagedProcess -ProcessId ([int]$state.backendPid) -Name 'backend'
  Stop-ManagedProcess -ProcessId ([int]$state.frontendPid) -Name 'frontend'

  Remove-Item -Path $stateFile -Force -ErrorAction SilentlyContinue
  Write-Host '[stop] Dev processes stopped.'
}

function Start-DevProcesses {
  $existing = Read-ProcessState
  if ($existing -and -not $ForceRestart) {
    $backendExists = Get-Process -Id ([int]$existing.backendPid) -ErrorAction SilentlyContinue
    $frontendExists = Get-Process -Id ([int]$existing.frontendPid) -ErrorAction SilentlyContinue
    if ($backendExists -or $frontendExists) {
      Write-Host '[start] Existing managed processes found. Use -ForceRestart or run action stop first.'
      return
    }
  }

  if ($ForceRestart) {
    Stop-DevProcesses
  }

  Write-Host '[start] Starting backend (Spring Boot)...'
  $backendProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/d', '/c', 'mvn spring-boot:run' -WorkingDirectory $serverPath -PassThru

  Write-Host '[start] Starting frontend (Vite dev)...'
  $frontendProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/d', '/c', 'npm.cmd run dev' -WorkingDirectory $uiPath -PassThru

  Write-ProcessState -BackendPid $backendProc.Id -FrontendPid $frontendProc.Id

  Write-Host "[start] Backend PID: $($backendProc.Id)"
  Write-Host "[start] Frontend PID: $($frontendProc.Id)"
  Write-Host '[start] Use scripts/dev-workflow.ps1 -Action stop to stop both.'
}

switch ($Action) {
  'sync' {
    Invoke-UiBuild
    Copy-BuildArtifacts
  }
  'start' {
    Start-DevProcesses
  }
  'all' {
    Invoke-UiBuild
    Copy-BuildArtifacts
    Start-DevProcesses
  }
  'stop' {
    Stop-DevProcesses
  }
}
