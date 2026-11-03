param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference='Stop';$verifier=Join-Path $PSScriptRoot 'verify-engine-package.ps1';$temp=Join-Path ([IO.Path]::GetTempPath()) ('capybee-release-tests-'+[guid]::NewGuid().ToString('N'))
function Expect-Rejection([string]$Root,[string]$Expected){$output=& pwsh -NoProfile -File $verifier -DistributionRoot $Root 2>&1;$code=$LASTEXITCODE;if($code -eq 0 -or ($output -join "`n") -notmatch $Expected){throw "Expected $Expected rejection, exit=$code output=$output"}}
try{
  New-Item -ItemType Directory $temp|Out-Null;$clean=Join-Path $temp 'clean';& $verifier -Archive $Archive -ExtractTo $clean;if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
  $tampered=Join-Path $temp 'tampered';Copy-Item -LiteralPath $clean -Destination $tampered -Recurse;[IO.File]::AppendAllText((Join-Path $tampered 'docs\OPERATIONS.md'),'tamper');Expect-Rejection $tampered 'CHECKSUM_MISMATCH'
  $unexpected=Join-Path $temp 'unexpected';Copy-Item -LiteralPath $clean -Destination $unexpected -Recurse;[IO.File]::WriteAllText((Join-Path $unexpected 'unexpected.exe'),'x');Expect-Rejection $unexpected 'CHECKSUM_INCOMPLETE'
  $missing=Join-Path $temp 'missing';Copy-Item -LiteralPath $clean -Destination $missing -Recurse;Remove-Item -LiteralPath (Join-Path $missing 'NOTICE.txt');Expect-Rejection $missing 'REQUIRED_FILE_MISSING'
  Write-Host 'Release-package failure injection passed.'
}finally{if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp -Recurse -Force}}
