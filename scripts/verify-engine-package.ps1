param([string]$Archive,[string]$DistributionRoot,[string]$ServerJar,[switch]$SkipArchiveChecks,[string]$ExtractTo)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression
function Fail([string]$Code){throw $Code}
function Rel([string]$Root,[string]$Path){[IO.Path]::GetRelativePath($Root,$Path).Replace('\','/')}
function Sha([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Strict-Json([string]$Path){
  $raw=Get-Content -Raw -LiteralPath $Path
  function Check-Element($element){
    if($element.ValueKind -eq [Text.Json.JsonValueKind]::Object){$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);foreach($property in $element.EnumerateObject()){if(-not $names.Add($property.Name)){Fail 'JSON_DUPLICATE_KEY'};Check-Element $property.Value}}
    elseif($element.ValueKind -eq [Text.Json.JsonValueKind]::Array){foreach($item in $element.EnumerateArray()){Check-Element $item}}
  }
  try{$document=[Text.Json.JsonDocument]::Parse($raw)}catch{Fail 'JSON_INVALID'}
  try{Check-Element $document.RootElement}finally{$document.Dispose()}
  try{$value=$raw|ConvertFrom-Json -Depth 100}catch{Fail 'JSON_INVALID'}
  if($null -eq $value){Fail 'JSON_INVALID'}
  $value
}
function Test-SafePath([string]$Name){
  if([string]::IsNullOrWhiteSpace($Name)-or $Name.Contains('\')-or $Name.StartsWith('/')-or $Name -match '^[A-Za-z]:' -or $Name.Contains([char]0)){Fail 'ARCHIVE_PATH_INVALID'}
  foreach($part in $Name.Split('/')){if($part -in @('','.', '..')){Fail 'ARCHIVE_PATH_INVALID'}}
}
function Open-Zip([string]$Path){
  $file=[IO.File]::OpenRead($Path)
  try{return [pscustomobject]@{File=$file;Zip=[IO.Compression.ZipArchive]::new($file,[IO.Compression.ZipArchiveMode]::Read,$false)}}catch{$file.Dispose();throw}
}
$temporary=$null
try{
  if(-not [string]::IsNullOrWhiteSpace($Archive)){
    $Archive=(Resolve-Path -LiteralPath $Archive).Path
    $opened=Open-Zip $Archive
    try{
      $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
      $folded=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
      [long]$expanded=0
      if($opened.Zip.Entries.Count -gt 256){Fail 'ARCHIVE_FILE_LIMIT'}
      foreach($entry in $opened.Zip.Entries){
        Test-SafePath $entry.FullName
        if(-not $names.Add($entry.FullName)){Fail 'ARCHIVE_DUPLICATE_PATH'}
        if(-not $folded.Add($entry.FullName)){Fail 'ARCHIVE_CASE_COLLISION'}
        if($entry.FullName.EndsWith('/')){Fail 'ARCHIVE_DIRECTORY_ENTRY_FORBIDDEN'}
        if($entry.Length -gt 33554432){Fail 'ARCHIVE_FILE_TOO_LARGE'}
        $expanded+=$entry.Length;if($expanded -gt 67108864){Fail 'ARCHIVE_EXPANSION_LIMIT'}
        $unixMode=($entry.ExternalAttributes -shr 16)-band 0xF000
        if($unixMode -eq 0xA000){Fail 'ARCHIVE_LINK_FORBIDDEN'}
      }
      if([string]::IsNullOrWhiteSpace($ExtractTo)){$temporary=Join-Path ([IO.Path]::GetTempPath()) ("capybee-verify-"+[guid]::NewGuid().ToString('N'));$DistributionRoot=$temporary}else{$DistributionRoot=[IO.Path]::GetFullPath($ExtractTo)}
      New-Item -ItemType Directory -Path $DistributionRoot|Out-Null
      $rootFull=[IO.Path]::GetFullPath($DistributionRoot)+[IO.Path]::DirectorySeparatorChar
      foreach($entry in $opened.Zip.Entries){$target=[IO.Path]::GetFullPath((Join-Path $DistributionRoot $entry.FullName));if(-not $target.StartsWith($rootFull,[StringComparison]::OrdinalIgnoreCase)){Fail 'ARCHIVE_TRAVERSAL'};$parent=Split-Path -Parent $target;New-Item -ItemType Directory -Force -Path $parent|Out-Null;$input=$entry.Open();try{$output=[IO.File]::Create($target);try{$input.CopyTo($output)}finally{$output.Dispose()}}finally{$input.Dispose()}}
    }finally{$opened.Zip.Dispose();$opened.File.Dispose()}
  }elseif([string]::IsNullOrWhiteSpace($DistributionRoot)){Fail 'PACKAGE_INPUT_REQUIRED'}
  $root=(Resolve-Path -LiteralPath $DistributionRoot).Path
  foreach($required in @('bin/engine-cli.jar','profiles/generic-adventure/profile.json','packs/lanterns-of-willow-marsh/pack.json','certification/willow-marsh-v1/suite.json','certification/dynamic-v1/blueprint.json','certification/dynamic-live-v1/suite.json','docs/CERTIFICATION.md','docs/OPERATIONS.md','LICENSE.txt','NOTICE.txt','build-info.json','distribution-manifest.json','sbom.cdx.json','checksums.sha256')){if(-not(Test-Path -LiteralPath (Join-Path $root $required) -PathType Leaf)){Fail 'REQUIRED_FILE_MISSING'}}
  $actualFiles=@(Get-ChildItem -LiteralPath $root -File -Recurse|ForEach-Object{Rel $root $_.FullName}|Sort-Object)
  $checks=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
  foreach($line in Get-Content -LiteralPath (Join-Path $root 'checksums.sha256')){if($line -notmatch '^([0-9a-f]{64})  ([^\r\n]+)$'){Fail 'CHECKSUM_FORMAT_INVALID'};$path=$Matches[2];Test-SafePath $path;if(-not $checks.TryAdd($path,$Matches[1])){Fail 'CHECKSUM_DUPLICATE'};$file=Join-Path $root $path;if(-not(Test-Path -LiteralPath $file -PathType Leaf)){Fail 'CHECKSUM_FILE_MISSING'};if((Sha $file)-ne $Matches[1]){Fail 'CHECKSUM_MISMATCH'}}
  $expectedChecked=@($actualFiles|Where-Object{$_ -ne 'checksums.sha256'})
  if(@($checks.Keys|Sort-Object).Count -ne $expectedChecked.Count -or (Compare-Object @($checks.Keys|Sort-Object) $expectedChecked)){Fail 'CHECKSUM_INCOMPLETE'}
  $manifest=Strict-Json (Join-Path $root 'distribution-manifest.json');if($manifest.schemaVersion -ne 1){Fail 'MANIFEST_SCHEMA_UNSUPPORTED'}
  $listed=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach($item in $manifest.entries){Test-SafePath $item.path;if(-not $listed.Add([string]$item.path)){Fail 'MANIFEST_DUPLICATE'};$file=Join-Path $root $item.path;if(-not(Test-Path -LiteralPath $file -PathType Leaf)){Fail 'MANIFEST_FILE_MISSING'};if((Get-Item -LiteralPath $file).Length -ne [long]$item.bytes -or (Sha $file)-ne $item.sha256){Fail 'MANIFEST_MISMATCH'}}
  $manifestExpected=@($actualFiles|Where-Object{$_ -notin @('checksums.sha256','distribution-manifest.json')})
  if($listed.Count -ne $manifestExpected.Count -or (Compare-Object @($listed|Sort-Object) $manifestExpected)){Fail 'MANIFEST_INCOMPLETE'}
  $build=Strict-Json (Join-Path $root 'build-info.json');if($build.schemaVersion -ne 1 -or $build.gitCommit -notmatch '^[0-9a-f]{40}$' -or $build.version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$'){Fail 'BUILD_IDENTITY_INVALID'}
  if(($build.dirty -or $build.version.EndsWith('-SNAPSHOT')) -and $build.capability -ne 'development'){Fail 'BUILD_CAPABILITY_INVALID'}
  $sbom=Strict-Json (Join-Path $root 'sbom.cdx.json');if($sbom.bomFormat -ne 'CycloneDX' -or [string]::IsNullOrWhiteSpace($sbom.specVersion) -or @($sbom.components).Count -lt 1){Fail 'SBOM_INVALID'}
  $jar=Open-Zip (Join-Path $root 'bin\engine-cli.jar');try{$main=$jar.Zip.GetEntry('META-INF/MANIFEST.MF');if($null -eq $main){Fail 'JAR_MANIFEST_MISSING'};$reader=[IO.StreamReader]::new($main.Open());try{$manifestText=$reader.ReadToEnd()}finally{$reader.Dispose()};if($manifestText -notmatch 'Main-Class: com\.capybee\.engine\.cli\.PlayMain'){Fail 'JAR_MAIN_CLASS_INVALID'};if($null -eq $jar.Zip.GetEntry('com/capybee/engine/cli/PackageResourceVerifier.class')){Fail 'JAR_VERIFIER_MISSING'}}finally{$jar.Zip.Dispose();$jar.File.Dispose()}
  & java '-Dfile.encoding=UTF-8' -cp (Join-Path $root 'bin\engine-cli.jar') com.capybee.engine.cli.PackageResourceVerifier $root
  if($LASTEXITCODE -ne 0){Fail 'PRODUCTION_RESOURCE_VALIDATION_FAILED'}
  if([string]::IsNullOrWhiteSpace($ServerJar) -and -not [string]::IsNullOrWhiteSpace($Archive)){$candidateServer=Join-Path (Split-Path -Parent $Archive) 'capybee-server.jar';if(Test-Path -LiteralPath $candidateServer){$ServerJar=$candidateServer}}
  if(-not [string]::IsNullOrWhiteSpace($ServerJar)){$serverZip=Open-Zip $ServerJar;try{$prefix='BOOT-INF/classes/release/profiles/generic-adventure/';$serverProfile=@($serverZip.Zip.Entries|Where-Object{$_.FullName.StartsWith($prefix)-and -not $_.FullName.EndsWith('/')});$cliProfile=@(Get-ChildItem -LiteralPath (Join-Path $root 'profiles\generic-adventure') -File -Recurse);if($serverProfile.Count -ne $cliProfile.Count){Fail 'PROFILE_PARITY_MISMATCH'};foreach($file in $cliProfile){$relative=Rel (Join-Path $root 'profiles\generic-adventure') $file.FullName;$entry=$serverZip.Zip.GetEntry($prefix+$relative);if($null -eq $entry){Fail 'PROFILE_PARITY_MISMATCH'};$input=$entry.Open();try{$memory=[IO.MemoryStream]::new();$input.CopyTo($memory);$serverHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($memory.ToArray())).ToLowerInvariant()}finally{$input.Dispose();$memory.Dispose()};if($serverHash -ne (Sha $file.FullName)){Fail 'PROFILE_PARITY_MISMATCH'}}}finally{$serverZip.Zip.Dispose();$serverZip.File.Dispose()}}
  [ordered]@{schemaVersion=1;result='passed';code='PACKAGE_VERIFIED';archiveSha256=if($Archive){Sha $Archive}else{$null};version=$build.version;gitCommit=$build.gitCommit;capability=$build.capability}|ConvertTo-Json -Compress
  exit 0
}catch{[ordered]@{schemaVersion=1;result='failed';code=$_.Exception.Message.Split(':')[0]}|ConvertTo-Json -Compress|Write-Error;exit 2}finally{if($temporary -and (Test-Path -LiteralPath $temporary)){Remove-Item -LiteralPath $temporary -Recurse -Force}}
