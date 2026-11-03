param([string]$EvidenceDir='app/engine/target/evidence/server-postgres',[switch]$UseExistingPostgres)
$ErrorActionPreference='Stop';$repository=Split-Path -Parent $PSScriptRoot;$container='capybee-release-postgres-'+[guid]::NewGuid().ToString('N').Substring(0,12);$started=$false;$serverProcess=$null
function Start-PackagedServer([string]$Jar,[int]$Port,[string]$Log){
  $arguments=@('-jar',$Jar,"--server.port=$Port",'--capybee.game.enabled=false','--capybee.game.dynamic.enabled=false','--spring.security.oauth2.client.provider.google.issuer-uri=','--spring.security.oauth2.client.provider.google.authorization-uri=http://127.0.0.1/authorize','--spring.security.oauth2.client.provider.google.token-uri=http://127.0.0.1/token','--spring.security.oauth2.client.provider.google.user-info-uri=http://127.0.0.1/user','--spring.security.oauth2.client.provider.google.user-name-attribute=sub')
  $process=Start-Process java -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $Log -RedirectStandardError "$Log.err"
  for($attempt=0;$attempt -lt 60;$attempt++){if($process.HasExited){throw 'PACKAGED_SERVER_START_FAILED'};try{$response=Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2;if($response.StatusCode -eq 200){return $process}}catch{};Start-Sleep -Seconds 1}
  throw 'PACKAGED_SERVER_START_TIMEOUT'
}
try{
  if(-not $UseExistingPostgres){& docker run --name $container -e POSTGRES_DB=capybee -e POSTGRES_USER=capybee -e POSTGRES_PASSWORD=capybee -p 5432:5432 -d postgres:16-alpine|Out-Null;if($LASTEXITCODE -ne 0){exit $LASTEXITCODE};$started=$true;for($i=0;$i -lt 30;$i++){& docker exec $container pg_isready -U capybee -d capybee|Out-Null;if($LASTEXITCODE -eq 0){break};Start-Sleep -Seconds 1};if($i -ge 30){throw 'POSTGRES_START_TIMEOUT'}}
  $env:CI='true';$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/capybee';$env:SPRING_DATASOURCE_USERNAME='capybee';$env:SPRING_DATASOURCE_PASSWORD='capybee'
  $env:GOOGLE_CLIENT_ID='release-certification';$env:GOOGLE_CLIENT_SECRET='release-certification'
  & mvn -B -f (Join-Path $repository 'app\pom.xml') -pl server -am -Dtest=PostgresDynamicMigrationTest '-Dsurefire.failIfNoSpecifiedTests=false' test
  if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
  $candidate=Get-Content -Raw -LiteralPath (Join-Path $repository 'app\engine\target\distribution\candidate.json')|ConvertFrom-Json
  $out=Join-Path $repository $EvidenceDir;New-Item -ItemType Directory -Force $out|Out-Null
  $jar=Join-Path $repository 'app\engine\target\distribution\capybee-server.jar';if(-not(Test-Path -LiteralPath $jar)){throw 'PACKAGED_SERVER_MISSING'}
  $serverProcess=Start-PackagedServer $jar 18081 (Join-Path $out 'startup-1.log');Stop-Process -Id $serverProcess.Id -Force;$serverProcess.WaitForExit();$serverProcess=$null
  $env:CAPYBEE_GAME_DYNAMIC_ENABLED='false';$env:GENERATION_PROVIDER_MODE='disabled'
  $serverProcess=Start-PackagedServer $jar 18081 (Join-Path $out 'startup-2.log');Stop-Process -Id $serverProcess.Id -Force;$serverProcess.WaitForExit();$serverProcess=$null
  $evidence=[ordered]@{schemaVersion=1;result='passed';code='SERVER_POSTGRES_COMPATIBILITY_PASSED';candidateId=$candidate.candidateId;serverJarSha256=$candidate.serverJarSha256;databaseImage='postgres:16-alpine';migrationVersion='13';restartCount=1;generatedContentIncluded=$false}
  [IO.File]::WriteAllText((Join-Path $out 'evidence.json'),(($evidence|ConvertTo-Json -Depth 8)+"`n"),[Text.UTF8Encoding]::new($false))
}finally{if($serverProcess -and -not $serverProcess.HasExited){Stop-Process -Id $serverProcess.Id -Force};if($started){& docker rm -f $container|Out-Null}}
