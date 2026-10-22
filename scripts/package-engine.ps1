$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$engine = Join-Path $repository 'app\engine'
$version = '0.1.0-SNAPSHOT'
$name = "capybee-engine-cli-$version"
$distributionRoot = Join-Path $engine 'target\distribution'
$distribution = Join-Path $distributionRoot $name
$archive = Join-Path $distributionRoot "$name.zip"

& mvn -B -f (Join-Path $engine 'pom.xml') -pl cli -am clean package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (Test-Path -LiteralPath $distribution) { Remove-Item -LiteralPath $distribution -Recurse -Force }
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
New-Item -ItemType Directory -Force (Join-Path $distribution 'bin') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $distribution 'packs') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $distribution 'certification') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $distribution 'docs') | Out-Null

Copy-Item -LiteralPath (Join-Path $engine "cli\target\engine-cli-$version.jar") -Destination (Join-Path $distribution 'bin\engine-cli.jar')
Copy-Item -LiteralPath (Join-Path $engine 'reference-game-packs\lanterns-of-willow-marsh') -Destination (Join-Path $distribution 'packs') -Recurse
Copy-Item -LiteralPath (Join-Path $engine 'certification\willow-marsh-v1') -Destination (Join-Path $distribution 'certification') -Recurse
Copy-Item -LiteralPath (Join-Path $engine 'CERTIFICATION.md') -Destination (Join-Path $distribution 'docs\CERTIFICATION.md')
Copy-Item -LiteralPath (Join-Path $engine 'OPERATIONS.md') -Destination (Join-Path $distribution 'docs\OPERATIONS.md')
Copy-Item -LiteralPath (Join-Path $engine 'LICENSE.txt') -Destination $distribution
Copy-Item -LiteralPath (Join-Path $engine 'NOTICE.txt') -Destination $distribution

$commit = (& git -C $repository rev-parse HEAD).Trim()
$dirty = -not [string]::IsNullOrWhiteSpace((& git -C $repository status --porcelain))
$javaVersion = [string](& java -version 2>&1 | Select-Object -First 1)
$buildInfo = [ordered]@{ schemaVersion = 1; version = $version; gitCommit = $commit; dirty = $dirty; capability = 'development'; java = $javaVersion }
$buildInfo | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $distribution 'build-info.json') -Encoding utf8NoBOM
$components = @(
  @{ type='library'; group='com.capybee'; name='engine-core'; version=$version },
  @{ type='library'; group='com.capybee'; name='engine-session'; version=$version },
  @{ type='library'; group='com.capybee'; name='engine-adapter-anthropic'; version=$version },
  @{ type='library'; group='tools.jackson.core'; name='jackson-databind'; version='3.1.2' },
  @{ type='library'; group='org.slf4j'; name='slf4j-nop'; version='2.0.17' }
)
$sbom = [ordered]@{ bomFormat='CycloneDX'; specVersion='1.5'; serialNumber="urn:uuid:$([guid]::NewGuid())"; version=1; metadata=@{ component=@{ type='application'; name='engine-cli'; version=$version } }; components=$components }
$sbom | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $distribution 'sbom.cdx.json') -Encoding utf8NoBOM

$checksumFile = Join-Path $distribution 'checksums.sha256'
$files = Get-ChildItem -LiteralPath $distribution -File -Recurse | Where-Object Name -ne 'checksums.sha256' | Sort-Object FullName
$checksums = foreach ($file in $files) {
  $relative = [IO.Path]::GetRelativePath($distribution, $file.FullName).Replace('\', '/')
  "{0}  {1}" -f (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
}
$checksums | Set-Content -LiteralPath $checksumFile -Encoding ascii
Compress-Archive -LiteralPath $distribution -DestinationPath $archive -CompressionLevel Optimal
(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() | Set-Content -LiteralPath "$archive.sha256" -Encoding ascii
Write-Host "Built $archive"
