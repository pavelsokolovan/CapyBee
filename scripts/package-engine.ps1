param([string]$Version,[string]$SourceRevision,[long]$SourceDateEpoch=0,[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
$engine=Join-Path $repository 'app\engine'; $server=Join-Path $repository 'app\server'
function Write-Utf8([string]$Path,[string]$Value){[IO.File]::WriteAllText($Path,($Value.Replace("`r`n","`n").TrimEnd()+"`n"),[Text.UTF8Encoding]::new($false))}
function Get-Relative([string]$Root,[string]$Path){[IO.Path]::GetRelativePath($Root,$Path).Replace('\','/')}
function Get-Sha256([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
if([string]::IsNullOrWhiteSpace($Version)){[xml]$pom=Get-Content -Raw -LiteralPath (Join-Path $engine 'pom.xml');$Version=[string]$pom.project.version}
if($Version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$'){throw 'RELEASE_VERSION_INVALID'}
$actualRevision=(& git -C $repository rev-parse HEAD).Trim()
if([string]::IsNullOrWhiteSpace($SourceRevision)){$SourceRevision=$actualRevision}
if($SourceRevision -notmatch '^[0-9a-fA-F]{40}$' -or $SourceRevision -ne $actualRevision){throw 'SOURCE_REVISION_MISMATCH'}
if($SourceDateEpoch -le 0){$SourceDateEpoch=[long](& git -C $repository show -s --format=%ct $SourceRevision).Trim()}
if($SourceDateEpoch -lt 315532800){throw 'SOURCE_DATE_EPOCH_INVALID'}
$dirtyLines=@(& git -C $repository status --porcelain=v1 --untracked-files=all);$dirty=$dirtyLines.Count -gt 0
$capability=if($dirty -or $Version.EndsWith('-SNAPSHOT')){'development'}else{'verified'}
$name="capybee-engine-cli-$Version";$distributionRoot=Join-Path $engine 'target\distribution';$distribution=Join-Path $distributionRoot $name;$archive=Join-Path $distributionRoot "$name.zip"
$serverJar=Join-Path $server 'target\capybee-server-0.0.1-SNAPSHOT.jar';$serverSbom=Join-Path $server 'target\sbom.json'
if(-not $SkipBuild){
  & mvn -B -f (Join-Path $engine 'pom.xml') -pl cli -am clean package -DskipTests "-Dproject.build.outputTimestamp=$SourceDateEpoch";if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
  & mvn -B -f (Join-Path $repository 'app\pom.xml') -pl server -am package -DskipTests "-Dproject.build.outputTimestamp=$SourceDateEpoch";if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
}
$cliJar=Join-Path $engine "cli\target\engine-cli-$Version.jar";$cliSbom=Join-Path $engine 'cli\target\sbom.json'
foreach($required in @($cliJar,$cliSbom,$serverJar,$serverSbom)){if(-not(Test-Path -LiteralPath $required -PathType Leaf)){throw "RELEASE_INPUT_MISSING: $required"}}
if(Test-Path -LiteralPath $distribution){Remove-Item -LiteralPath $distribution -Recurse -Force}
foreach($old in @($archive,"$archive.sha256",(Join-Path $distributionRoot 'candidate.json'))){if(Test-Path -LiteralPath $old){Remove-Item -LiteralPath $old -Force}}
foreach($directory in @('bin','packs','profiles','certification','docs')){New-Item -ItemType Directory -Force (Join-Path $distribution $directory)|Out-Null}
Copy-Item -LiteralPath $cliJar -Destination (Join-Path $distribution 'bin\engine-cli.jar')
Copy-Item -LiteralPath (Join-Path $engine 'reference-game-packs\lanterns-of-willow-marsh') -Destination (Join-Path $distribution 'packs') -Recurse
Copy-Item -LiteralPath (Join-Path $engine 'generator-profiles\generic-adventure') -Destination (Join-Path $distribution 'profiles') -Recurse
foreach($suite in @('willow-marsh-v1','dynamic-v1','dynamic-live-v1')){Copy-Item -LiteralPath (Join-Path $engine "certification\$suite") -Destination (Join-Path $distribution 'certification') -Recurse}
Copy-Item -LiteralPath (Join-Path $engine 'CERTIFICATION.md') -Destination (Join-Path $distribution 'docs\CERTIFICATION.md')
Copy-Item -LiteralPath (Join-Path $engine 'OPERATIONS.md') -Destination (Join-Path $distribution 'docs\OPERATIONS.md')
Copy-Item -LiteralPath (Join-Path $engine 'LICENSE.txt') -Destination $distribution;Copy-Item -LiteralPath (Join-Path $engine 'NOTICE.txt') -Destination $distribution
Copy-Item -LiteralPath $cliSbom -Destination (Join-Path $distribution 'sbom.cdx.json')
$javaVersion=[string](& java -version 2>&1|Select-Object -First 1)
$buildInfo=[ordered]@{schemaVersion=1;product='capybee-engine';version=$Version;gitCommit=$SourceRevision;dirty=$dirty;capability=$capability;sourceDateEpoch=$SourceDateEpoch;java=$javaVersion;profilePolicyVersion=1;distributionSchemaVersion=1}
Write-Utf8 (Join-Path $distribution 'build-info.json') ($buildInfo|ConvertTo-Json -Depth 8)
$contentFiles=Get-ChildItem -LiteralPath $distribution -File -Recurse|Sort-Object {Get-Relative $distribution $_.FullName}
$entries=foreach($file in $contentFiles){$relative=Get-Relative $distribution $file.FullName;$kind=if($relative.StartsWith('bin/')){'executable-input'}elseif($relative.StartsWith('docs/') -or $relative -match '^(LICENSE|NOTICE)'){'documentation'}else{'release-input'};[ordered]@{path=$relative;bytes=$file.Length;sha256=Get-Sha256 $file.FullName;kind=$kind}}
$manifest=[ordered]@{schemaVersion=1;maxFiles=256;maxExpandedBytes=67108864;maxFileBytes=33554432;allowedRoots=@('bin','packs','profiles','certification','docs');entries=@($entries)}
Write-Utf8 (Join-Path $distribution 'distribution-manifest.json') ($manifest|ConvertTo-Json -Depth 8)
$checksumFiles=Get-ChildItem -LiteralPath $distribution -File -Recurse|Sort-Object {Get-Relative $distribution $_.FullName}
$checksumLines=foreach($file in $checksumFiles){"$(Get-Sha256 $file.FullName)  $(Get-Relative $distribution $file.FullName)"}
Write-Utf8 (Join-Path $distribution 'checksums.sha256') ($checksumLines -join "`n")
& (Join-Path $PSScriptRoot 'verify-engine-package.ps1') -DistributionRoot $distribution -SkipArchiveChecks;if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Add-Type -AssemblyName System.IO.Compression
$epoch=[DateTimeOffset]::FromUnixTimeSeconds($SourceDateEpoch);if($epoch.Year -lt 1980){$epoch=[DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)}
$stream=[IO.File]::Open($archive,[IO.FileMode]::CreateNew)
try{$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$false);try{foreach($file in (Get-ChildItem -LiteralPath $distribution -File -Recurse|Sort-Object {Get-Relative $distribution $_.FullName})){$entry=$zip.CreateEntry((Get-Relative $distribution $file.FullName),[IO.Compression.CompressionLevel]::Optimal);$entry.LastWriteTime=$epoch;$input=[IO.File]::OpenRead($file.FullName);try{$output=$entry.Open();try{$input.CopyTo($output)}finally{$output.Dispose()}}finally{$input.Dispose()}}}finally{$zip.Dispose()}}finally{$stream.Dispose()}
$archiveDigest=Get-Sha256 $archive;Write-Utf8 "$archive.sha256" "$archiveDigest  $([IO.Path]::GetFileName($archive))"
Copy-Item -LiteralPath $serverJar -Destination (Join-Path $distributionRoot 'capybee-server.jar') -Force;Copy-Item -LiteralPath $serverSbom -Destination (Join-Path $distributionRoot 'capybee-server.sbom.cdx.json') -Force
$candidate=[ordered]@{schemaVersion=1;candidateId="$Version-$($SourceRevision.Substring(0,12))-$($archiveDigest.Substring(0,12))";capability=$capability;version=$Version;gitCommit=$SourceRevision;dirty=$dirty;archive=[ordered]@{file=[IO.Path]::GetFileName($archive);sha256=$archiveDigest};cliJarSha256=Get-Sha256 (Join-Path $distribution 'bin\engine-cli.jar');serverJarSha256=Get-Sha256 (Join-Path $distributionRoot 'capybee-server.jar');cliSbomSha256=Get-Sha256 (Join-Path $distribution 'sbom.cdx.json');serverSbomSha256=Get-Sha256 (Join-Path $distributionRoot 'capybee-server.sbom.cdx.json')}
Write-Utf8 (Join-Path $distributionRoot 'candidate.json') ($candidate|ConvertTo-Json -Depth 8)
& (Join-Path $PSScriptRoot 'verify-engine-package.ps1') -Archive $archive;if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Write-Host "Built immutable candidate $($candidate.candidateId): $archive"
